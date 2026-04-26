package com.herbscript.prescription.dto;

import com.herbscript.recognition.dto.RecognitionDraftResponse;
import java.util.List;

public record PrescriptionDetailResponse(
        Long id,
        String prescriptionNo,
        String patientName,
        String gender,
        Integer age,
        String department,
        String diagnosis,
        Integer doseCount,
        String prescriptionDate,
        String doctorName,
        String usageMethod,
        String entryMode,
        String status,
        String createdByName,
        String createdAt,
        String sourceModel,
        String sourceImageUrl,
        List<PrescriptionItemResponse> items,
        List<OperationLogResponse> logs,
        RecognitionDraftResponse recognitionDraft
) {
}
