package com.herbscript.agent.context;

import com.herbscript.agent.dto.AgentContextSummaryResponse;
import com.herbscript.agent.tool.ToolRegistry;
import com.herbscript.agent.tool.dto.ToolExecutionRequest;
import com.herbscript.agent.tool.dto.ToolExecutionResult;
import com.herbscript.patient.PatientService;
import com.herbscript.patient.dto.PatientDetailResponse;
import com.herbscript.prescription.PrescriptionQueryService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PatientContextBuilder {

    private final PatientService patientService;
    private final PrescriptionQueryService prescriptionQueryService;
    private final ToolRegistry toolRegistry;

    public PatientContextBuilder(
            PatientService patientService,
            PrescriptionQueryService prescriptionQueryService,
            ToolRegistry toolRegistry
    ) {
        this.patientService = patientService;
        this.prescriptionQueryService = prescriptionQueryService;
        this.toolRegistry = toolRegistry;
    }

    public AgentContext build(Long patientId) {
        PatientDetailResponse patient = patientService.getDetail(patientId, prescriptionQueryService.listByPatientId(patientId));
        ToolExecutionResult commonHerbs = toolRegistry.get("get_common_herbs")
                .execute(new ToolExecutionRequest(Map.of("patientId", patientId, "limit", 5)));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> herbs = (List<Map<String, Object>>) commonHerbs.payload();
        List<String> herbNames = herbs.stream()
                .map(item -> String.valueOf(item.get("herbName")))
                .toList();

        AgentContextSummaryResponse summary = new AgentContextSummaryResponse(
                "patient",
                patient.id(),
                patient.name(),
                null,
                patient.lastPrescriptionDate(),
                null,
                null,
                patient.prescriptionCount(),
                herbNames
        );

        return new AgentContext(
                "patient",
                patient.id(),
                summary,
                Map.of(
                        "patient", patient,
                        "recentPrescriptions", patient.prescriptions(),
                        "commonHerbs", herbs
                ),
                List.of(
                        "请总结该患者最近处方变化",
                        "请分析该患者常用药味",
                        "请生成本次复诊随访建议",
                        "请指出需要重点关注的风险点"
                )
        );
    }
}
