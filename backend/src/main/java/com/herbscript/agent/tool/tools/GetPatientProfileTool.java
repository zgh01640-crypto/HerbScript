package com.herbscript.agent.tool.tools;

import com.herbscript.agent.tool.AgentTool;
import com.herbscript.agent.tool.dto.ToolExecutionRequest;
import com.herbscript.agent.tool.dto.ToolExecutionResult;
import com.herbscript.patient.PatientService;
import com.herbscript.prescription.PrescriptionQueryService;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GetPatientProfileTool implements AgentTool {

    private final PatientService patientService;
    private final PrescriptionQueryService prescriptionQueryService;

    public GetPatientProfileTool(PatientService patientService, PrescriptionQueryService prescriptionQueryService) {
        this.patientService = patientService;
        this.prescriptionQueryService = prescriptionQueryService;
    }

    @Override
    public String name() {
        return "get_patient_profile";
    }

    @Override
    public String description() {
        return "读取患者主档信息";
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionRequest request) {
        long start = System.currentTimeMillis();
        Long patientId = ((Number) request.arguments().get("patientId")).longValue();
        Object payload = patientService.getDetail(patientId, prescriptionQueryService.listByPatientId(patientId));
        return new ToolExecutionResult(name(), "success", payload, null, (int) (System.currentTimeMillis() - start));
    }
}
