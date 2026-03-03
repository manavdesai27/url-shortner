package com.example.urlshortener.repository;

import com.example.urlshortener.model.RuleSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RuleSetRepository extends JpaRepository<RuleSet, Long> {

    @Query("select distinct rs from RuleSet rs " +
           "join rs.urlMapping m " +
           "left join fetch rs.rules r " +
           "where m.shortCode = :shortCode and rs.enabled = true")
    Optional<RuleSet> findEnabledByShortCodeWithRules(@Param("shortCode") String shortCode);

    @Query("select distinct rs from RuleSet rs " +
           "left join fetch rs.rules r " +
           "where rs.urlMapping.id = :linkId")
    Optional<RuleSet> findByUrlMappingIdWithRules(@Param("linkId") Long linkId);
}
