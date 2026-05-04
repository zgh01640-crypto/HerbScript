package com.herbscript.modelconfig.dto;

public record ModelConfigTestResponse(
        boolean success,
        String status,
        String message,
        long latencyMs,
        Integer httpStatus,
        String provider,
        String doubaoModel,
        String doubaoBaseUrl,
        boolean activeProfileUsed
) {
}
