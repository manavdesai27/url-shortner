package com.example.urlshortener.config;

import com.example.urlshortener.repository.UrlMappingRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ExpiredUrlCleanup {

    private final UrlMappingRepository urlMappingRepository;

    public ExpiredUrlCleanup(UrlMappingRepository urlMappingRepository) {
        this.urlMappingRepository = urlMappingRepository;
    }

    // Runs hourly. Adjust cron if you want a different cadence.
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void purgeExpiredMappings() {
        try {
            urlMappingRepository.deleteExpired();
        } catch (Exception ignored) {
            // Optionally log; avoiding hard failure for scheduled maintenance
        }
    }
}
