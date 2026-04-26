package com.herbscript.prescription.dto;

import java.math.BigDecimal;

public record PrescriptionItemResponse(
        Long id,
        Integer sortNo,
        String herbName,
        String rawHerbName,
        BigDecimal dosage,
        String unit,
        String specialInstruction,
        Double confidence,
        String effectHint
) {
}
