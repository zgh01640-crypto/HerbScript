package com.herbscript.agent.tool.tools;

import com.herbscript.agent.tool.AgentTool;
import com.herbscript.agent.tool.dto.ToolExecutionRequest;
import com.herbscript.agent.tool.dto.ToolExecutionResult;
import com.herbscript.prescription.PrescriptionQueryService;
import com.herbscript.prescription.dto.PrescriptionDetailResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ComparePrescriptionsTool implements AgentTool {

    private final PrescriptionQueryService prescriptionQueryService;

    public ComparePrescriptionsTool(PrescriptionQueryService prescriptionQueryService) {
        this.prescriptionQueryService = prescriptionQueryService;
    }

    @Override
    public String name() {
        return "compare_prescriptions";
    }

    @Override
    public String description() {
        return "比较两张处方差异";
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionRequest request) {
        long start = System.currentTimeMillis();
        Long leftId = ((Number) request.arguments().get("leftPrescriptionId")).longValue();
        Long rightId = ((Number) request.arguments().get("rightPrescriptionId")).longValue();
        PrescriptionDetailResponse left = prescriptionQueryService.getDetail(leftId).orElse(null);
        PrescriptionDetailResponse right = prescriptionQueryService.getDetail(rightId).orElse(null);
        if (left == null || right == null) {
            return new ToolExecutionResult(name(), "error", null, "处方不存在", (int) (System.currentTimeMillis() - start));
        }

        List<Map<String, Object>> changedFields = new ArrayList<>();
        if (!safeEquals(left.diagnosis(), right.diagnosis())) {
            changedFields.add(buildChangedField("diagnosis", left.diagnosis(), right.diagnosis()));
        }
        if (!safeEquals(left.doseCount(), right.doseCount())) {
            changedFields.add(buildChangedField("doseCount", left.doseCount(), right.doseCount()));
        }

        Map<String, BigDecimal> leftItems = new HashMap<>();
        left.items().forEach(item -> leftItems.put(item.herbName(), item.dosage()));
        Map<String, BigDecimal> rightItems = new HashMap<>();
        right.items().forEach(item -> rightItems.put(item.herbName(), item.dosage()));

        List<String> addedHerbs = rightItems.keySet().stream().filter(key -> !leftItems.containsKey(key)).sorted().toList();
        List<String> removedHerbs = leftItems.keySet().stream().filter(key -> !rightItems.containsKey(key)).sorted().toList();
        List<Map<String, Object>> changedDosages = rightItems.entrySet().stream()
                .filter(entry -> leftItems.containsKey(entry.getKey()) && leftItems.get(entry.getKey()).compareTo(entry.getValue()) != 0)
                .map(entry -> Map.<String, Object>of(
                        "herbName", entry.getKey(),
                        "left", leftItems.get(entry.getKey()),
                        "right", entry.getValue(),
                        "unit", "g"
                ))
                .toList();

        Map<String, Object> payload = Map.of(
                "changedFields", changedFields,
                "addedHerbs", addedHerbs,
                "removedHerbs", removedHerbs,
                "changedDosages", changedDosages
        );
        return new ToolExecutionResult(name(), "success", payload, null, (int) (System.currentTimeMillis() - start));
    }

    private boolean safeEquals(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private Map<String, Object> buildChangedField(String field, Object left, Object right) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("field", field);
        payload.put("left", left);
        payload.put("right", right);
        return payload;
    }
}
