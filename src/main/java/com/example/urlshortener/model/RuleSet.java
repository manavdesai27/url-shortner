package com.example.urlshortener.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "rule_set",
        indexes = {
                @Index(name = "idx_ruleset_link_unique", columnList = "url_mapping_id", unique = true)
        }
)
public class RuleSet {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rule_set_seq")
    @SequenceGenerator(name = "rule_set_seq", sequenceName = "rule_set_seq", allocationSize = 50)
    private Long id;

    // One RuleSet per UrlMapping (unique)
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "url_mapping_id", nullable = false, unique = true)
    private UrlMapping urlMapping;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    // Bump on each update to help cache versioning if needed
    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "ruleSet", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("priority ASC, id ASC")
    private List<Rule> rules = new ArrayList<>();

    public RuleSet() {}

    public Long getId() {
        return id;
    }

    public UrlMapping getUrlMapping() {
        return urlMapping;
    }

    public void setUrlMapping(UrlMapping urlMapping) {
        this.urlMapping = urlMapping;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<Rule> getRules() {
        return rules;
    }

    public void setRules(List<Rule> rules) {
        this.rules = rules;
    }

    public void addRule(Rule rule) {
        rule.setRuleSet(this);
        this.rules.add(rule);
    }

    public void removeRule(Rule rule) {
        rule.setRuleSet(null);
        this.rules.remove(rule);
    }
}