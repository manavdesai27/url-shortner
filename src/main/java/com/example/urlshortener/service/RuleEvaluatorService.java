package com.example.urlshortener.service;

import com.example.urlshortener.model.DeviceType;
import com.example.urlshortener.model.Rule;
import com.example.urlshortener.model.RuleSet;
import com.example.urlshortener.model.RuleType;
import com.example.urlshortener.repository.RuleSetRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Evaluates routing rules for a given short code and request context.
 * Order of evaluation is by Rule.priority (ascending) as persisted in the DB.
 * Falls back to the provided defaultUrl if no rule matches or on any error.
 */
@Service
public class RuleEvaluatorService {

    private final RuleSetRepository ruleSetRepository;
    private static final Logger log = LoggerFactory.getLogger(RuleEvaluatorService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    public RuleEvaluatorService(RuleSetRepository ruleSetRepository) {
        this.ruleSetRepository = ruleSetRepository;
    }

    public String evaluateDestination(String shortCode, String defaultUrl, HttpServletRequest request) {
        try {
            Optional<RuleSet> rsOpt = ruleSetRepository.findEnabledByShortCodeWithRules(shortCode);
            if (rsOpt.isEmpty()) {
                return defaultUrl;
            }
            RuleSet rs = rsOpt.get();
            if (!rs.isEnabled()) {
                return defaultUrl;
            }
            List<Rule> rules = rs.getRules();
            if (rules == null || rules.isEmpty()) {
                return defaultUrl;
            }

            // Build request context
            String ua = request != null ? request.getHeader("User-Agent") : null;
            DeviceType device = DeviceDetector.detect(ua);

            // Prefer edge-provided country header if available
            String country = headerLower(request, "CF-IPCountry");
            if (country == null) {
                country = headerLower(request, "X-Country-Code");
            }
            if (country != null) {
                country = country.toUpperCase(Locale.ROOT);
            }

            // TODO
            // // Local dev override header
            // if (country == null) {
            //     String dev = headerLower(request, "X-Dev-Country");
            //     if (dev != null) {
            //         country = dev.toUpperCase(Locale.ROOT);
            //         log.info("RULES dev-override country={}", country);
            //     }
            // }
            // // Optional Accept-Language fallback (naive parse of first locale like en-IN -> IN)
            // if (country == null) {
            //     String al = headerLower(request, "Accept-Language");
            //     if (al != null) {
            //         int dash = al.indexOf('-');
            //         if (dash > 0 && dash + 2 <= al.length()) {
            //             String maybe = al.substring(dash + 1, dash + 3);
            //             if (maybe.length() == 2) {
            //                 country = maybe.toUpperCase(Locale.ROOT);
            //                 log.info("RULES accept-language fallback country={}", country);
            //             }
            //         }
            //     }
            // }

            Instant now = Instant.now();

            // Stable user key for sticky A/B: use (XFF or remoteAddr) + UA + shortCode
            String clientIp = extractClientIp(request);
            String userKey = (clientIp == null ? "" : clientIp) + "|" + (ua == null ? "" : ua) + "|" + shortCode;
            int userBucket = positiveModulo(hashToInt(userKey), 100);
            log.info("RULES ctx shortCode={} device={} country={} userBucket={} rulesPresent={} enabled={}",
                    shortCode, device, country, userBucket, (rules != null && !rules.isEmpty()), rs.isEnabled());

            for (Rule rule : rules) {
                if (rule == null || !rule.isActive() || rule.getType() == null) {
                    continue;
                }
                String cfg = rule.getConfigJson();
                RuleType type = rule.getType();

                switch (type) {
                    case TIME:
                        if (matchesTime(cfg, now)) {
                            String chosen = safeOrDefault(rule.getTargetUrl(), defaultUrl);
                            log.info("RULES match TIME ruleId={} prio={} target={}", rule.getId(), rule.getPriority(), chosen);
                            return chosen;
                        }
                        break;
                    case DEVICE:
                        if (matchesDevice(cfg, device)) {
                            String chosen = safeOrDefault(rule.getTargetUrl(), defaultUrl);
                            log.info("RULES match DEVICE ruleId={} prio={} target={}", rule.getId(), rule.getPriority(), chosen);
                            return chosen;
                        }
                        break;
                    case COUNTRY:
                        if (matchesCountry(cfg, country)) {
                            String chosen = safeOrDefault(rule.getTargetUrl(), defaultUrl);
                            log.info("RULES match COUNTRY ruleId={} prio={} target={}", rule.getId(), rule.getPriority(), chosen);
                            return chosen;
                        }
                        break;
                    case AB:
                        String abUrl = chooseAbUrl(cfg, userBucket);
                        if (abUrl != null && !abUrl.isBlank()) {
                            log.info("RULES match AB ruleId={} prio={} userBucket={} target={}", rule.getId(), rule.getPriority(), userBucket, abUrl);
                            return abUrl;
                        }
                        break;
                    default:
                        // Unknown rule type -> ignore
                        break;
                }
            }

            log.info("RULES no-match shortCode={} fallingBackToDefault", shortCode);
            return defaultUrl;
        } catch (Exception e) {
            // Be conservative: any error should not break redirects
            return defaultUrl;
        }
    }

    private static boolean matchesTime(String json, Instant now) {
        if (json == null || json.isBlank()) return false;
        try {
            JsonNode node = MAPPER.readTree(json);
            JsonNode start = node.get("startIso");
            JsonNode end = node.get("endIso");

            boolean afterStart = true;
            boolean beforeEnd = true;

            if (start != null && start.isTextual()) {
                Instant s = Instant.parse(start.asText());
                afterStart = now.compareTo(s) >= 0;
            }
            if (end != null && end.isTextual()) {
                Instant e = Instant.parse(end.asText());
                beforeEnd = now.compareTo(e) <= 0;
            }
            return afterStart && beforeEnd;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean matchesDevice(String json, DeviceType device) {
        if (json == null || json.isBlank()) return false;
        try {
            JsonNode node = MAPPER.readTree(json);
            JsonNode arr = node.get("devices");
            if (arr == null || !arr.isArray()) return false;
            Iterator<JsonNode> it = arr.elements();
            while (it.hasNext()) {
                String dv = it.next().asText().toUpperCase(Locale.ROOT);
                if (dv.equals(device.name())) {
                    return true;
                }
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean matchesCountry(String json, String country) {
        if (json == null || json.isBlank() || country == null) return false;
        try {
            JsonNode node = MAPPER.readTree(json);
            JsonNode arr = node.get("countries");
            if (arr == null || !arr.isArray()) return false;
            Iterator<JsonNode> it = arr.elements();
            while (it.hasNext()) {
                String cc = it.next().asText().toUpperCase(Locale.ROOT);
                if (cc.equals(country)) {
                    return true;
                }
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Chooses a URL from buckets definition:
     * { "buckets": [ { "pct": 70, "url": "https://a" }, { "pct": 30, "url": "https://b" } ] }
     */
    private static String chooseAbUrl(String json, int userBucket) {
        if (json == null || json.isBlank()) return null;
        try {
            JsonNode node = MAPPER.readTree(json);
            JsonNode arr = node.get("buckets");
            if (arr == null || !arr.isArray()) return null;

            int cumulative = 0;
            for (JsonNode bucket : arr) {
                int pct = bucket.has("pct") ? Math.max(0, Math.min(100, bucket.get("pct").asInt())) : 0;
                String url = bucket.has("url") ? bucket.get("url").asText() : null;
                cumulative += pct;
                if (userBucket < cumulative) {
                    return url;
                }
            }
            // If total < 100, treat remainder as no-op (fall back to null/default)
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String extractClientIp(HttpServletRequest request) {
        if (request == null) return null;
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // Take first IP in the list
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        return request.getRemoteAddr();
    }

    private static String headerLower(HttpServletRequest request, String name) {
        if (request == null) return null;
        String v = request.getHeader(name);
        return v == null ? null : v.toLowerCase(Locale.ROOT);
    }

    private static int hashToInt(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            // Take first 4 bytes as signed int
            return new BigInteger(1, new byte[]{digest[0], digest[1], digest[2], digest[3]}).intValue();
        } catch (Exception e) {
            // Fall back to String#hashCode if SHA-256 is unavailable
            return input.hashCode();
        }
    }

    private static int positiveModulo(int value, int mod) {
        int r = value % mod;
        return r < 0 ? r + mod : r;
    }

    private static String safeOrDefault(String candidate, String fallback) {
        if (candidate == null || candidate.isBlank()) {
            return fallback;
        }
        return candidate;
    }
}