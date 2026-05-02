package com.herbscript.agent.skill.skills;

import com.herbscript.agent.context.AgentContext;
import com.herbscript.agent.dto.AgentStructuredResponse;
import com.herbscript.agent.skill.AgentSkill;
import com.herbscript.agent.skill.dto.SkillExecutionRequest;
import com.herbscript.agent.skill.dto.SkillExecutionResult;
import com.herbscript.prescription.dto.PrescriptionDetailResponse;
import com.herbscript.prescription.dto.PrescriptionSummaryResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PrescriptionAnalysisSkill implements AgentSkill {

    @Override
    public String name() {
        return "prescription_analysis_skill";
    }

    @Override
    public String description() {
        return "分析单张处方及与历史处方的差异";
    }

    @Override
    @SuppressWarnings("unchecked")
    public SkillExecutionResult execute(SkillExecutionRequest request) {
        AgentContext context = request.context();
        PrescriptionDetailResponse detail = (PrescriptionDetailResponse) context.payload().get("prescription");
        List<PrescriptionSummaryResponse> history = (List<PrescriptionSummaryResponse>) context.payload().get("patientHistory");
        Map<String, Object> comparison = (Map<String, Object>) context.payload().get("comparison");

        List<String> observations = new ArrayList<>();
        observations.add("当前处方诊断为「" + safe(detail.diagnosis()) + "」，剂数为 " + detail.doseCount() + " 剂。");
        observations.add("当前药味共 " + detail.items().size() + " 味，前 3 味为：" +
                detail.items().stream().limit(3).map(item -> item.herbName() + item.dosage() + item.unit()).reduce((a, b) -> a + "、" + b).orElse("无"));
        if (history.size() > 1) {
            observations.add("该患者存在历史处方 " + history.size() + " 张，可继续比较上一张处方差异。");
        }
        if (comparison != null) {
            List<?> addedHerbs = (List<?>) comparison.getOrDefault("addedHerbs", List.of());
            List<?> removedHerbs = (List<?>) comparison.getOrDefault("removedHerbs", List.of());
            List<?> changedDosages = (List<?>) comparison.getOrDefault("changedDosages", List.of());
            if (!addedHerbs.isEmpty()) {
                observations.add("相较上一张处方，新增药味：" + joinNames(addedHerbs));
            }
            if (!removedHerbs.isEmpty()) {
                observations.add("相较上一张处方，减少药味：" + joinNames(removedHerbs));
            }
            if (!changedDosages.isEmpty()) {
                observations.add("存在 " + changedDosages.size() + " 味药的剂量发生变化，可重点复核。");
            }
        }

        List<String> risks = new ArrayList<>();
        if ("pending_review".equals(detail.status())) {
            risks.add("当前处方仍处于待校对状态，结论需以人工确认后的内容为准。");
        } else {
            risks.add("智能体分析仅供辅助参考，不能替代执业医师临床判断。");
        }

        List<String> suggestions = List.of(
                "建议结合上一张处方进一步分析药味增减和剂量变化。",
                "如用于归档，可生成结构化处方摘要。"
        );
        String summary = "该处方以「" + safe(detail.diagnosis()) + "」为核心诊疗目标，当前药味与剂数可用于后续与历史处方做纵向比较。";

        return new SkillExecutionResult(
                name(),
                summary,
                new AgentStructuredResponse(summary, observations, risks, suggestions),
                List.of("get_prescription_detail", "compare_prescriptions")
        );
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String joinNames(List<?> names) {
        return names.stream().map(String::valueOf).reduce((a, b) -> a + "、" + b).orElse("-");
    }
}
