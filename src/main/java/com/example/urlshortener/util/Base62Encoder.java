package com.example.urlshortener.util;

public class Base62Encoder {

    private static final String CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    public static final int FIXED_LENGTH = 7;

    public static String encode(long id) {
        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            int remainder = (int) (id % 62);
            sb.append(CHARSET.charAt(remainder));
            id = id / 62;
        }
        return sb.reverse().toString();
    }

    public static String encodeFixed(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("id must be non-negative");
        }
        String code = encode(id);
        if (code.isEmpty()) {
            code = "0"; // handle id == 0
        }
        if (code.length() > FIXED_LENGTH) {
            throw new IllegalStateException("Exceeded 7-char Base62 space; increase FIXED_LENGTH");
        }
        int pad = FIXED_LENGTH - code.length();
        if (pad == 0) {
            return code;
        }
        StringBuilder sb = new StringBuilder(FIXED_LENGTH);
        for (int i = 0; i < pad; i++) {
            sb.append('0');
        }
        sb.append(code);
        return sb.toString();
    }

    // Optional: decode if you ever want to reverse it
    public static long decode(String shortCode) {
        long id = 0;
        for (int i = 0; i < shortCode.length(); i++) {
            id = id * 62 + CHARSET.indexOf(shortCode.charAt(i));
        }
        return id;
    }
}