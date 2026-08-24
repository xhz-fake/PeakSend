package com.sky.utils;

import java.util.UUID;

public class TraceIdUtil {

    private TraceIdUtil() {
    }

    public static String normalizeOrCreate(String traceId) {
        if (traceId == null || traceId.trim().isEmpty()) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        return traceId.trim();
    }
}
