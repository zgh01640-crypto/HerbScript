package com.herbscript.patient.dto;

import com.herbscript.prescription.dto.PrescriptionSummaryResponse;
import java.util.List;

public record PatientDetailResponse(
        Long id,
        String patientNo,
        String name,
        String gender,
        Integer age,
        String phone,
        String remark,
        Integer prescriptionCount,
        String lastPrescriptionDate,
        List<PrescriptionSummaryResponse> prescriptions
) {
}
