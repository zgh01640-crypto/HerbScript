package com.herbscript.patient.dto;

public record PatientMatchCandidateResponse(
        Long id,
        String patientNo,
        String name,
        String gender,
        Integer age,
        String phone,
        String matchLevel,
        Double matchScore,
        Integer prescriptionCount,
        String lastPrescriptionDate
) {
}
