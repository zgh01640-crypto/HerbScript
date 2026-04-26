package com.herbscript.recognition.provider;

import com.herbscript.prescription.dto.PrescriptionItemSaveRequest;
import java.util.List;

public record RecognitionDraftData(
        String patientName,
        String gender,
        Integer age,
        String department,
        String diagnosis,
        Integer doseCount,
        String prescriptionDate,
        String doctorName,
        String usageMethod,
        List<String> warnings,
        List<String> lowConfidenceFields,
        List<PrescriptionItemSaveRequest> items,
        String rawText
) {
}
