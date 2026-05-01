package com.herbscript.patient.dto;

import jakarta.validation.constraints.NotNull;

public record PatientMergeRequest(
        @NotNull Long sourcePatientId
) {
}
