package com.herbscript.agent.context;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AgentContextBuilder {

    private final PatientContextBuilder patientContextBuilder;
    private final PrescriptionContextBuilder prescriptionContextBuilder;

    public AgentContextBuilder(
            PatientContextBuilder patientContextBuilder,
            PrescriptionContextBuilder prescriptionContextBuilder
    ) {
        this.patientContextBuilder = patientContextBuilder;
        this.prescriptionContextBuilder = prescriptionContextBuilder;
    }

    public AgentContext build(String anchorType, Long anchorId) {
        return switch (anchorType) {
            case "patient" -> patientContextBuilder.build(anchorId);
            case "prescription" -> prescriptionContextBuilder.build(anchorId);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "暂不支持的智能体锚点类型");
        };
    }
}
