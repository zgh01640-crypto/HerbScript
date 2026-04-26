package com.herbscript.prescription.dto;

public record PrescriptionQuery(
        String keyword,
        String entryMode,
        String status
) {
}
