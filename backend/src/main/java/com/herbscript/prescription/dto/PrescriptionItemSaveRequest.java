package com.herbscript.prescription.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PrescriptionItemSaveRequest(
        Integer sortNo,
        @NotBlank(message = "药材名称不能为空")
        String herbName,
        String rawHerbName,
        @NotNull(message = "剂量不能为空")
        @DecimalMin(value = "0.01", message = "剂量必须大于 0")
        BigDecimal dosage,
        @NotBlank(message = "单位不能为空")
        String unit,
        String specialInstruction
) {
}
