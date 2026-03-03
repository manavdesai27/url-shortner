package com.example.urlshortener.service;

import com.example.urlshortener.model.DeviceType;

public class DeviceDetector {

    public static DeviceType detect(String userAgent) {
        if (userAgent == null) {
            return DeviceType.DESKTOP;
        }
        String ua = userAgent.toLowerCase();

        // Very lightweight heuristics for MVP
        if (ua.contains("bot") || ua.contains("spider") || ua.contains("crawl")) {
            return DeviceType.BOT;
        }

        boolean isTablet = ua.contains("ipad")
                || ua.contains("tablet")
                || (ua.contains("android") && !ua.contains("mobile"));

        boolean isMobile = ua.contains("mobi")
                || ua.contains("iphone")
                || ua.contains("ipod")
                || (ua.contains("android") && ua.contains("mobile"));

        if (isTablet) {
            return DeviceType.TABLET;
        }
        if (isMobile) {
            return DeviceType.MOBILE;
        }
        return DeviceType.DESKTOP;
    }

    private DeviceDetector() {
        // utility
    }
}