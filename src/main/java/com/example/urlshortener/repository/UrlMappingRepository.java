package com.example.urlshortener.repository;

import com.example.urlshortener.model.UrlMapping;
import com.example.urlshortener.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {
    Optional<UrlMapping> findByShortCode(String shortCode);
    Optional<UrlMapping> findByOriginalUrlAndUser(String originalUrl, User user);

    @Query("select u from UrlMapping u where u.shortCode = :shortCode and (u.expiresAt is null or u.expiresAt > CURRENT_TIMESTAMP)")
    Optional<UrlMapping> findActiveByShortCode(@Param("shortCode") String shortCode);

    @Query("select u from UrlMapping u where u.originalUrl = :originalUrl and u.user = :user and (u.expiresAt is null or u.expiresAt > CURRENT_TIMESTAMP)")
    Optional<UrlMapping> findActiveByOriginalUrlAndUser(@Param("originalUrl") String originalUrl, @Param("user") User user);

    @Modifying
    @Query("update UrlMapping u set u.clickCount = u.clickCount + 1 where u.shortCode = :shortCode and (u.expiresAt is null or u.expiresAt > CURRENT_TIMESTAMP)")
    int incrementClickCount(@Param("shortCode") String shortCode);

    @Modifying
    @Query("delete from UrlMapping u where u.expiresAt is not null and u.expiresAt <= CURRENT_TIMESTAMP")
    int deleteExpired();
}
