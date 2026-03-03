package com.example.urlshortener.controller;

import com.example.urlshortener.model.Rule;
import com.example.urlshortener.model.RuleSet;
import com.example.urlshortener.model.RuleType;
import com.example.urlshortener.model.UrlMapping;
import com.example.urlshortener.model.User;
import com.example.urlshortener.repository.RuleSetRepository;
import com.example.urlshortener.repository.UrlMappingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Rules management APIs (MVP)
 * - GET  /api/links/{shortCode}/rules
 * - PUT  /api/links/{shortCode}/rules
 *
 * Notes:
 * - Owner-only access enforced via @AuthenticationPrincipal and ownership check.
 * - PUT fully replaces the RuleSet (transactional).
 * - Config JSON is stored as TEXT in Rule.configJson (pass-through structure).
 */
@RestController
@RequestMapping("/links")
public class LinkRulesController {

    @Autowired
    private UrlMappingRepository urlMappingRepository;

    @Autowired
    private RuleSetRepository ruleSetRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Transactional(readOnly = true)
    @GetMapping("/{shortCode}/rules")
    public ResponseEntity<?> getRules(@PathVariable String shortCode,
                                      @AuthenticationPrincipal User user) {
        Optional<UrlMapping> mappingOpt = urlMappingRepository.findByShortCode(shortCode);
        if (mappingOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Short URL not found"));
        }
        UrlMapping mapping = mappingOpt.get();
        if (!Objects.equals(mapping.getUser().getId(), user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Forbidden"));
        }

        RuleSetDto dto = loadRuleSetDto(mapping.getId());
        return ResponseEntity.ok(dto);
    }

    @PutMapping("/{shortCode}/rules")
    @Transactional
    public ResponseEntity<?> putRules(@PathVariable String shortCode,
                                      @Valid @RequestBody RuleSetDto requestDto,
                                      @AuthenticationPrincipal User user) {
        Optional<UrlMapping> mappingOpt = urlMappingRepository.findByShortCode(shortCode);
        if (mappingOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Short URL not found"));
        }
        UrlMapping mapping = mappingOpt.get();
        if (!Objects.equals(mapping.getUser().getId(), user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Forbidden"));
        }

        // Upsert RuleSet and replace rules
        RuleSet ruleSet = ruleSetRepository.findByUrlMappingIdWithRules(mapping.getId()).orElse(null);
        boolean creating = false;
        if (ruleSet == null) {
            ruleSet = new RuleSet();
            ruleSet.setUrlMapping(mapping);
            creating = true;
        }

        // Validate and normalize incoming DTO
        boolean enabled = requestDto.getEnabled() != null ? requestDto.getEnabled() : true;
        List<RuleDto> incomingRules = requestDto.getRules() != null ? requestDto.getRules() : Collections.emptyList();

        // Replace rules atomically
        if (!creating) {
            ruleSet.getRules().clear(); // orphanRemoval=true will delete old
        }
        for (RuleDto r : incomingRules) {
            if (r.getType() == null || r.getTargetUrl() == null || r.getTargetUrl().isBlank()) {
                // Skip invalid entries
                continue;
            }
            RuleType type;
            try {
                type = RuleType.valueOf(r.getType().toUpperCase(Locale.ROOT));
            } catch (Exception ex) {
                continue;
            }

            Rule entity = new Rule();
            entity.setRuleSet(ruleSet);
            entity.setType(type);
            entity.setPriority(r.getPriority() != null ? r.getPriority() : 100);
            entity.setTargetUrl(r.getTargetUrl());
            entity.setActive(r.getActive() == null || r.getActive());

            // Store config as JSON string if present
            if (r.getConfig() != null && !r.getConfig().isEmpty()) {
                try {
                    entity.setConfigJson(MAPPER.writeValueAsString(r.getConfig()));
                } catch (Exception ex) {
                    // Skip malformed configs
                    continue;
                }
            }

            ruleSet.addRule(entity);
        }

        ruleSet.setEnabled(enabled);
        // Bump version so caches (future) can invalidate
        ruleSet.setVersion(ruleSet.getVersion() == null ? 1 : ruleSet.getVersion() + 1);

        RuleSet saved = ruleSetRepository.save(ruleSet);

        RuleSetDto response = loadRuleSetDto(saved.getUrlMapping().getId());
        return ResponseEntity.ok(response);
    }

    private RuleSetDto loadRuleSetDto(Long linkId) {
        RuleSetDto dto = new RuleSetDto();
        ruleSetRepository.findByUrlMappingIdWithRules(linkId).ifPresentOrElse(rs -> {
            dto.setEnabled(rs.isEnabled());
            dto.setVersion(rs.getVersion());
            List<RuleDto> list = new ArrayList<>();
            if (rs.getRules() != null) {
                for (Rule r : rs.getRules()) {
                    RuleDto d = new RuleDto();
                    d.setId(r.getId());
                    d.setType(r.getType() != null ? r.getType().name() : null);
                    d.setPriority(r.getPriority());
                    d.setTargetUrl(r.getTargetUrl());
                    d.setActive(r.isActive());
                    if (r.getConfigJson() != null && !r.getConfigJson().isBlank()) {
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> cfg = MAPPER.readValue(r.getConfigJson(), Map.class);
                            d.setConfig(cfg);
                        } catch (Exception ignored) {
                        }
                    }
                    list.add(d);
                }
            }
            dto.setRules(list);
        }, () -> {
            dto.setEnabled(false);
            dto.setVersion(0);
            dto.setRules(Collections.emptyList());
        });
        return dto;
    }

    // DTOs (simple, minimal validation)
    public static class RuleSetDto {
        private Boolean enabled;
        private Integer version;
        private List<RuleDto> rules;

        public Boolean getEnabled() {
            return enabled;
        }
        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
        public Integer getVersion() {
            return version;
        }
        public void setVersion(Integer version) {
            this.version = version;
        }
        public List<RuleDto> getRules() {
            return rules;
        }
        public void setRules(List<RuleDto> rules) {
            this.rules = rules;
        }
    }

    public static class RuleDto {
        private Long id;
        private String type; // TIME | DEVICE | COUNTRY | AB
        private Integer priority;
        private String targetUrl;
        private Boolean active;
        private Map<String, Object> config; // pass-through JSON

        public Long getId() {
            return id;
        }
        public void setId(Long id) {
            this.id = id;
        }
        public String getType() {
            return type;
        }
        public void setType(String type) {
            this.type = type;
        }
        public Integer getPriority() {
            return priority;
        }
        public void setPriority(Integer priority) {
            this.priority = priority;
        }
        public String getTargetUrl() {
            return targetUrl;
        }
        public void setTargetUrl(String targetUrl) {
            this.targetUrl = targetUrl;
        }
        public Boolean getActive() {
            return active;
        }
        public void setActive(Boolean active) {
            this.active = active;
        }
        public Map<String, Object> getConfig() {
            return config;
        }
        public void setConfig(Map<String, Object> config) {
            this.config = config;
        }
    }
}