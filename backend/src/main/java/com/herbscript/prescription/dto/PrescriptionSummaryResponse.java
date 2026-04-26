package com.herbscript.prescription.dto;

public record PrescriptionSummaryResponse(
        Long id,
        String prescriptionNo,
        String patientName,
        String gender,
        Integer age,
        String prescriptionDate,
        Integer doseCount,
        String entryMode,
        String status,
        String createdByName,
        String createdAt
) {
}
