package com.example.urlshortener.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "rule",
        indexes = {
                @Index(name = "idx_rule_ruleset", columnList = "rule_set_id"),
                @Index(name = "idx_rule_priority", columnList = "priority")
        }
)
public class Rule {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rule_seq")
    @SequenceGenerator(name = "rule_seq", sequenceName = "rule_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_set_id", nullable = false)
    private RuleSet ruleSet;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private RuleType type;

    @Column(name = "priority", nullable = false)
    private int priority = 100;

    @Column(name = "target_url", nullable = false, length = 2048)
    private String targetUrl;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    // Stores rule-specific configuration as JSON (e.g., start/end for TIME, devices list, countries list, or AB buckets)
    // Map as TEXT (not LOB) to avoid lob stream access issues when serializing outside of an active session
    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public Rule() {}

    public Long getId() {
        return id;
    }

    public RuleSet getRuleSet() {
        return ruleSet;
    }

    public void setRuleSet(RuleSet ruleSet) {
        this.ruleSet = ruleSet;
    }

    public RuleType getType() {
        return type;
    }

    public void setType(RuleType type) {
        this.type = type;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}