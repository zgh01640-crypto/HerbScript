package com.herbscript.agent.dto;

import java.util.List;

public record AgentContextSummaryResponse(
        String anchorType,
        Long anchorId,
        String patientName,
        String prescriptionNo,
        String prescriptionDate,
        String diagnosis,
        Integer doseCount,
        Integer prescriptionCount,
        List<String> commonHerbs
) {
}
