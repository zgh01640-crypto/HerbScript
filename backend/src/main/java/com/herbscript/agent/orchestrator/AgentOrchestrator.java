package com.herbscript.agent.orchestrator;

import com.herbscript.agent.context.AgentContext;
import com.herbscript.agent.context.AgentContextBuilder;
import com.herbscript.agent.dto.AgentChatRequest;
import com.herbscript.agent.dto.AgentChatResponse;
import com.herbscript.agent.dto.AgentMessageResponse;
import com.herbscript.agent.dto.AgentToolCallResponse;
import com.herbscript.agent.memory.AgentSessionMemoryService;
import com.herbscript.agent.model.AgentModelClient;
import com.herbscript.agent.model.AgentModelResult;
import com.herbscript.agent.service.AgentTraceService;
import com.herbscript.agent.skill.AgentSkill;
import com.herbscript.agent.skill.SkillRegistry;
import com.herbscript.agent.tool.ToolRegistry;
import com.herbscript.agent.tool.dto.ToolExecutionRequest;
import com.herbscript.agent.tool.dto.ToolExecutionResult;
import com.herbscript.modelconfig.ModelConfigService;
import com.herbscript.prescription.dto.PrescriptionDetailResponse;
import com.herbscript.prescription.dto.PrescriptionSummaryResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AgentOrchestrator {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AgentContextBuilder contextBuilder;
    private final SkillRegistry skillRegistry;
    private final ToolRegistry toolRegistry;
    private final AgentSessionMemoryService memoryService;
    private final AgentTraceService traceService;
    private final ModelConfigService modelConfigService;
    private final AgentModelClient agentModelClient;

    public AgentOrchestrator(
            AgentContextBuilder contextBuilder,
            SkillRegistry skillRegistry,
            ToolRegistry toolRegistry,
            AgentSessionMemoryService memoryService,
            AgentTraceService traceService,
            ModelConfigService modelConfigService,
            AgentModelClient agentModelClient
    ) {
        this.contextBuilder = contextBuilder;
        this.skillRegistry = skillRegistry;
        this.toolRegistry = toolRegistry;
        this.memoryService = memoryService;
        this.traceService = traceService;
        this.modelConfigService = modelConfigService;
        this.agentModelClient = agentModelClient;
    }

    public AgentChatResponse chat(AgentChatRequest request) {
        return executeChat(request, null);
    }

    public AgentChatResponse chatStream(AgentChatRequest request, AgentStreamSink sink) {
        return executeChat(request, sink);
    }

    private AgentChatResponse executeChat(AgentChatRequest request, AgentStreamSink sink) {
        Long userMessageId = memoryService.appendMessage(request.sessionId(), "user", request.message().trim(), null);
        AgentContext baseContext = contextBuilder.build(request.anchorType(), request.anchorId());
        ToolExecutionBundle toolBundle = executeTools(request.sessionId(), userMessageId, baseContext, sink);
        AgentContext context = new AgentContext(
                baseContext.anchorType(),
                baseContext.anchorId(),
                baseContext.summary(),
                toolBundle.payload(),
                baseContext.preferredQuestions()
        );
        AgentSkill fallbackSkill = selectSkill(context.anchorType());
        if (sink != null) {
            sink.emit("model_start", Map.of(
                    "modelName", modelConfigService.getRuntimeConfig().doubaoModel(),
                    "mode", "generating"
            ));
        }
        AgentModelResult result = agentModelClient.generate(
                context,
                memoryService.listMessages(request.sessionId()),
                request.message().trim(),
                fallbackSkill
        );

        Long assistantMessageId = memoryService.appendMessage(
                request.sessionId(),
                "assistant",
                result.content(),
                result.structured()
        );
        var traceId = traceService.saveTrace(
                request.sessionId(),
                assistantMessageId,
                modelConfigService.getRuntimeConfig().doubaoModel(),
                result.promptTokens(),
                result.completionTokens(),
                result.totalTokens(),
                result.latencyMs(),
                buildTracePayload(context, result, userMessageId, toolBundle.calls())
        );

        if (sink != null) {
            streamAssistantResult(sink, assistantMessageId, result, toolBundle.calls(), traceId);
        }

        return new AgentChatResponse(
                request.sessionId(),
                new AgentMessageResponse(assistantMessageId, "assistant", result.content(), result.structured(), null),
                result.structured(),
                toolBundle.calls()
        );
    }

    private AgentSkill selectSkill(String anchorType) {
        if ("patient".equals(anchorType)) {
            return skillRegistry.get("patient_summary_skill");
        }
        return skillRegistry.get("prescription_analysis_skill");
    }

    private Map<String, Object> buildTracePayload(
            AgentContext context,
            AgentModelResult result,
            Long userMessageId,
            List<AgentToolCallResponse> toolCalls
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("anchorType", context.anchorType());
        payload.put("anchorId", context.anchorId());
        payload.put("mode", result.fallbackUsed() ? "fallback_skill" : "llm");
        payload.put("usedTools", toolCalls.stream().map(AgentToolCallResponse::toolName).toList());
        payload.put("userMessageId", userMessageId);
        return payload;
    }

    @SuppressWarnings("unchecked")
    private ToolExecutionBundle executeTools(Long sessionId, Long messageId, AgentContext context, AgentStreamSink sink) {
        Map<String, Object> payload = new HashMap<>(context.payload());
        List<AgentToolCallResponse> calls = new ArrayList<>();

        if ("patient".equals(context.anchorType())) {
            Long patientId = context.anchorId();
            ToolExecutionResult patientProfile = runTool(
                    sessionId,
                    messageId,
                    "get_patient_profile",
                    Map.of("patientId", patientId),
                    calls,
                    sink
            );
            ToolExecutionResult prescriptions = runTool(
                    sessionId,
                    messageId,
                    "get_patient_prescriptions",
                    Map.of("patientId", patientId, "limit", 6),
                    calls,
                    sink
            );
            ToolExecutionResult commonHerbs = runTool(
                    sessionId,
                    messageId,
                    "get_common_herbs",
                    Map.of("patientId", patientId, "limit", 5),
                    calls,
                    sink
            );
            payload.put("patient", patientProfile.payload());
            payload.put("recentPrescriptions", prescriptions.payload());
            payload.put("commonHerbs", commonHerbs.payload());
            return new ToolExecutionBundle(payload, calls);
        }

        Long prescriptionId = context.anchorId();
        ToolExecutionResult detailResult = runTool(
                sessionId,
                messageId,
                "get_prescription_detail",
                Map.of("prescriptionId", prescriptionId),
                calls,
                sink
        );
        payload.put("prescription", detailResult.payload());

        if (detailResult.payload() instanceof PrescriptionDetailResponse detail && detail.patientId() != null) {
            ToolExecutionResult historyResult = runTool(
                    sessionId,
                    messageId,
                    "get_patient_prescriptions",
                    Map.of("patientId", detail.patientId(), "limit", 6),
                    calls,
                    sink
            );
            List<PrescriptionSummaryResponse> history = ((List<PrescriptionSummaryResponse>) historyResult.payload()).stream()
                    .filter(item -> !item.id().equals(detail.id()))
                    .toList();
            payload.put("patientHistory", history);

            if (!history.isEmpty()) {
                Long leftPrescriptionId = history.get(0).id();
                ToolExecutionResult comparisonResult = runTool(
                        sessionId,
                        messageId,
                        "compare_prescriptions",
                        Map.of(
                                "leftPrescriptionId", leftPrescriptionId,
                                "rightPrescriptionId", detail.id()
                        ),
                        calls,
                        sink
                );
                payload.put("comparison", comparisonResult.payload());
            }
        }

        return new ToolExecutionBundle(payload, calls);
    }

    private ToolExecutionResult runTool(
            Long sessionId,
            Long messageId,
            String toolName,
            Map<String, Object> arguments,
            List<AgentToolCallResponse> calls,
            AgentStreamSink sink
    ) {
        var tool = toolRegistry.get(toolName);
        if (sink != null) {
            sink.emit("tool_start", Map.of(
                    "toolName", toolName,
                    "toolLabel", tool.description(),
                    "status", "running",
                    "createdAt", LocalDateTime.now().format(TIME_FORMATTER)
            ));
        }
        ToolExecutionResult result = tool.execute(new ToolExecutionRequest(arguments));
        Long toolCallId = traceService.saveToolCall(sessionId, messageId, tool.description(), arguments, result);
        AgentToolCallResponse response = new AgentToolCallResponse(
                toolCallId,
                messageId,
                result.toolName(),
                tool.description(),
                result.status(),
                result.latencyMs(),
                arguments,
                result.payload(),
                LocalDateTime.now().format(TIME_FORMATTER)
        );
        calls.add(response);
        if (sink != null) {
            sink.emit("tool_done", response);
        }
        return result;
    }

    private void streamAssistantResult(
            AgentStreamSink sink,
            Long assistantMessageId,
            AgentModelResult result,
            List<AgentToolCallResponse> toolCalls,
            Long traceId
    ) {
        for (String chunk : splitIntoChunks(result.content())) {
            sink.emit("message_chunk", Map.of(
                    "messageId", assistantMessageId,
                    "contentChunk", chunk
            ));
            sleep(90);
        }

        sink.emit("structured", result.structured());
        sink.emit("tool_calls", toolCalls);
        sink.emit("trace", Map.of(
                "id", traceId,
                "modelName", modelConfigService.getRuntimeConfig().doubaoModel(),
                "promptTokens", result.promptTokens(),
                "completionTokens", result.completionTokens(),
                "totalTokens", result.totalTokens(),
                "latencyMs", result.latencyMs(),
                "mode", result.fallbackUsed() ? "fallback_skill" : "llm"
        ));
        sink.emit("complete", Map.of(
                "messageId", assistantMessageId
        ));
    }

    private List<String> splitIntoChunks(String content) {
        List<String> chunks = new ArrayList<>();
        for (String piece : content.split("(?<=[。！？；])")) {
            String trimmed = piece.trim();
            if (!trimmed.isBlank()) {
                chunks.add(trimmed);
            }
        }
        if (!chunks.isEmpty()) {
            return chunks;
        }

        int step = 18;
        for (int index = 0; index < content.length(); index += step) {
            chunks.add(content.substring(index, Math.min(index + step, content.length())));
        }
        return chunks;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private record ToolExecutionBundle(
            Map<String, Object> payload,
            List<AgentToolCallResponse> calls
    ) {
    }
}
