package com.herbscript.patient.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PatientDraftRequest(
        @NotBlank(message = "患者姓名不能为空")
        String name,
        @NotBlank(message = "性别不能为空")
        String gender,
        @NotNull(message = "年龄不能为空")
        @Min(value = 0, message = "年龄不能小于 0")
        Integer age,
        String phone,
        String remark
) {
}
