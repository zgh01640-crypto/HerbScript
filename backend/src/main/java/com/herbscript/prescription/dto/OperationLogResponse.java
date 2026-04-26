package com.herbscript.prescription.dto;

public record OperationLogResponse(
        Long id,
        String time,
        String content
) {
}
