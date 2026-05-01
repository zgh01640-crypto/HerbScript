package com.herbscript.prescription.dto;

import com.herbscript.patient.dto.PatientDraftRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record PrescriptionSaveRequest(
        String hospitalName,
        String prescriptionType,
        Long patientId,
        @Valid
        PatientDraftRequest patientDraft,
        @NotBlank(message = "患者姓名不能为空")
        String patientName,
        @NotBlank(message = "性别不能为空")
        String gender,
        @NotNull(message = "年龄不能为空")
        @Min(value = 0, message = "年龄不能小于 0")
        Integer age,
        String department,
        String diagnosis,
        @NotNull(message = "剂数不能为空")
        @Min(value = 1, message = "剂数至少为 1")
        Integer doseCount,
        @NotBlank(message = "处方日期不能为空")
        String prescriptionDate,
        String doctorName,
        String usageMethod,
        String remark,
        @NotEmpty(message = "至少需要一味药材")
        List<@Valid PrescriptionItemSaveRequest> items
) {
}
