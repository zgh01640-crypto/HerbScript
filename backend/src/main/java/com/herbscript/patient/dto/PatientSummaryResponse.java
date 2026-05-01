package com.herbscript.patient.dto;

public record PatientSummaryResponse(
        Long id,
        String patientNo,
        String name,
        String gender,
        Integer age,
        String phone,
        Integer prescriptionCount,
        String lastPrescriptionDate
) {
}
