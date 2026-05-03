package com.herbscript.agent.model;

import java.util.List;
import java.util.Map;

public record AgentToolPlan(
        List<String> selectedTools,
        String rationale,
        List<String> informationNeeds,
        Map<String, Map<String, Object>> toolArguments,
        boolean enoughInformation,
        boolean fallbackUsed
) {
}
