package com.herbscript.agent.skill.skills;

import com.herbscript.agent.context.AgentContext;
import com.herbscript.agent.dto.AgentStructuredResponse;
import com.herbscript.agent.skill.AgentSkill;
import com.herbscript.agent.skill.dto.SkillExecutionRequest;
import com.herbscript.agent.skill.dto.SkillExecutionResult;
import com.herbscript.patient.dto.PatientDetailResponse;
import com.herbscript.prescription.dto.PrescriptionSummaryResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PatientSummarySkill implements AgentSkill {

    @Override
    public String name() {
        return "patient_summary_skill";
    }

    @Override
    public String description() {
        return "生成患者历史处方与用药总结";
    }

    @Override
    @SuppressWarnings("unchecked")
    public SkillExecutionResult execute(SkillExecutionRequest request) {
        AgentContext context = request.context();
        PatientDetailResponse patient = (PatientDetailResponse) context.payload().get("patient");
        List<PrescriptionSummaryResponse> prescriptions = (List<PrescriptionSummaryResponse>) context.payload().get("recentPrescriptions");
        List<Map<String, Object>> herbs = (List<Map<String, Object>>) context.payload().get("commonHerbs");

        List<String> observations = new ArrayList<>();
        observations.add("患者当前累计处方 " + patient.prescriptionCount() + " 张，最近处方日期为 " + safe(patient.lastPrescriptionDate()) + "。");
        if (!prescriptions.isEmpty()) {
            observations.add("最近一次处方日期为 " + safe(prescriptions.get(0).prescriptionDate()) + "，当前可进一步展开做处方差异分析。");
        }
        if (!herbs.isEmpty()) {
            observations.add("高频药味包括：" + herbs.stream().map(item -> item.get("herbName") + "×" + item.get("count")).limit(3).reduce((a, b) -> a + "，" + b).orElse(""));
        }

        List<String> risks = List.of("当前结果仅基于既往处方数据生成，仍需结合临床症状与医师判断复核。");
        List<String> suggestions = List.of(
                "建议结合最近两次处方变化重点核对诊断与剂数变化。",
                "若需要复诊沟通，可进一步生成随访摘要。"
        );
        String summary = patient.name() + "近期处方整体围绕同一治疗主线展开，药味结构较稳定，可重点关注最近一张处方与既往差异。";

        return new SkillExecutionResult(
                name(),
                summary,
                new AgentStructuredResponse(summary, observations, risks, suggestions),
                List.of("get_patient_profile", "get_patient_prescriptions", "get_common_herbs")
        );
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
