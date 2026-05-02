package com.herbscript.agent.tool.tools;

import com.herbscript.agent.tool.AgentTool;
import com.herbscript.agent.tool.dto.ToolExecutionRequest;
import com.herbscript.agent.tool.dto.ToolExecutionResult;
import com.herbscript.prescription.PrescriptionQueryService;
import org.springframework.stereotype.Component;

@Component
public class GetPrescriptionDetailTool implements AgentTool {

    private final PrescriptionQueryService prescriptionQueryService;

    public GetPrescriptionDetailTool(PrescriptionQueryService prescriptionQueryService) {
        this.prescriptionQueryService = prescriptionQueryService;
    }

    @Override
    public String name() {
        return "get_prescription_detail";
    }

    @Override
    public String description() {
        return "读取单张处方详情";
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionRequest request) {
        long start = System.currentTimeMillis();
        Long prescriptionId = ((Number) request.arguments().get("prescriptionId")).longValue();
        Object payload = prescriptionQueryService.getDetail(prescriptionId).orElse(null);
        return new ToolExecutionResult(name(), "success", payload, null, (int) (System.currentTimeMillis() - start));
    }
}
