package com.herbscript.agent.tool.tools;

import com.herbscript.agent.tool.AgentTool;
import com.herbscript.agent.tool.dto.ToolExecutionRequest;
import com.herbscript.agent.tool.dto.ToolExecutionResult;
import com.herbscript.prescription.PrescriptionQueryService;
import com.herbscript.prescription.dto.PrescriptionSummaryResponse;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GetPatientPrescriptionsTool implements AgentTool {

    private final PrescriptionQueryService prescriptionQueryService;

    public GetPatientPrescriptionsTool(PrescriptionQueryService prescriptionQueryService) {
        this.prescriptionQueryService = prescriptionQueryService;
    }

    @Override
    public String name() {
        return "get_patient_prescriptions";
    }

    @Override
    public String description() {
        return "读取患者历史处方摘要";
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionRequest request) {
        long start = System.currentTimeMillis();
        Long patientId = ((Number) request.arguments().get("patientId")).longValue();
        int limit = request.arguments().get("limit") instanceof Number number ? number.intValue() : 5;
        List<PrescriptionSummaryResponse> payload = prescriptionQueryService.listByPatientId(patientId)
                .stream()
                .limit(limit)
                .toList();
        return new ToolExecutionResult(name(), "success", payload, null, (int) (System.currentTimeMillis() - start));
    }
}
