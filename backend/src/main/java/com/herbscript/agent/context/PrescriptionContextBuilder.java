package com.herbscript.agent.context;

import com.herbscript.agent.dto.AgentContextSummaryResponse;
import com.herbscript.prescription.PrescriptionQueryService;
import com.herbscript.prescription.dto.PrescriptionDetailResponse;
import com.herbscript.prescription.dto.PrescriptionSummaryResponse;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PrescriptionContextBuilder {

    private final PrescriptionQueryService prescriptionQueryService;

    public PrescriptionContextBuilder(PrescriptionQueryService prescriptionQueryService) {
        this.prescriptionQueryService = prescriptionQueryService;
    }

    public AgentContext build(Long prescriptionId) {
        PrescriptionDetailResponse detail = prescriptionQueryService.getDetail(prescriptionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "处方不存在"));

        List<PrescriptionSummaryResponse> history = detail.patientId() == null
                ? List.of()
                : prescriptionQueryService.listByPatientId(detail.patientId());

        AgentContextSummaryResponse summary = new AgentContextSummaryResponse(
                "prescription",
                detail.id(),
                detail.patientName(),
                detail.prescriptionNo(),
                detail.prescriptionDate(),
                detail.diagnosis(),
                detail.doseCount(),
                history.size(),
                detail.items().stream().limit(5).map(item -> item.herbName()).toList()
        );

        return new AgentContext(
                "prescription",
                detail.id(),
                summary,
                Map.of(
                        "prescription", detail,
                        "patientHistory", history
                ),
                List.of(
                        "请分析这张处方的主治方向",
                        "请总结药味配伍特点",
                        "请与上一张处方比较差异",
                        "请生成适合归档的结构化总结"
                )
        );
    }
}
