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
import com.herbscript.agent.model.AgentToolPlan;
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
import java.util.Set;
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
        AgentToolPlan toolPlan = planTools(baseContext, request.message().trim());
        if (sink != null) {
            sink.emit("tool_plan", Map.of(
                    "tools", toolPlan.selectedTools(),
                    "rationale", toolPlan.rationale(),
                    "informationNeeds", toolPlan.informationNeeds(),
                    "toolArguments", toolPlan.toolArguments(),
                    "enoughInformation", toolPlan.enoughInformation(),
                    "fallbackUsed", toolPlan.fallbackUsed()
            ));
        }
        ToolExecutionBundle toolBundle = toolPlan.enoughInformation() && toolPlan.selectedTools().isEmpty()
                ? new ToolExecutionBundle(new HashMap<>(baseContext.payload()), new ArrayList<>())
                : executeTools(request.sessionId(), userMessageId, baseContext, toolPlan, sink);
        AgentContext context = new AgentContext(
                baseContext.anchorType(),
                baseContext.anchorId(),
                baseContext.summary(),
                toolBundle.payload(),
                baseContext.preferredQuestions()
        );
        AgentToolPlan followupPlan = planAdditionalTools(context, request.message().trim(), toolBundle.calls());
        if (sink != null) {
            sink.emit("tool_replan", Map.of(
                    "tools", followupPlan.selectedTools(),
                    "rationale", followupPlan.rationale(),
                    "informationNeeds", followupPlan.informationNeeds(),
                    "toolArguments", followupPlan.toolArguments(),
                    "enoughInformation", followupPlan.enoughInformation(),
                    "fallbackUsed", followupPlan.fallbackUsed()
            ));
        }
        if (!followupPlan.selectedTools().isEmpty()) {
            toolBundle = executeAdditionalTools(request.sessionId(), userMessageId, context, toolBundle, followupPlan, sink);
            context = new AgentContext(
                    baseContext.anchorType(),
                    baseContext.anchorId(),
                    baseContext.summary(),
                    toolBundle.payload(),
                    baseContext.preferredQuestions()
            );
        }
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
                buildTracePayload(context, result, userMessageId, toolBundle.calls(), toolPlan, followupPlan)
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
            List<AgentToolCallResponse> toolCalls,
            AgentToolPlan toolPlan,
            AgentToolPlan followupPlan
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("anchorType", context.anchorType());
        payload.put("anchorId", context.anchorId());
        payload.put("mode", result.fallbackUsed() ? "fallback_skill" : "llm");
        payload.put("toolPlanningMode", toolPlan.fallbackUsed() ? "default" : "model_planned");
        payload.put("plannedTools", toolPlan.selectedTools());
        payload.put("toolPlanRationale", toolPlan.rationale());
        payload.put("toolPlanEnoughInformation", toolPlan.enoughInformation());
        payload.put("toolArguments", toolPlan.toolArguments());
        payload.put("additionalToolPlanningMode", followupPlan == null || followupPlan.fallbackUsed() ? "default_or_skipped" : "model_replanned");
        payload.put("additionalPlannedTools", followupPlan == null ? List.of() : followupPlan.selectedTools());
        payload.put("additionalToolPlanRationale", followupPlan == null ? null : followupPlan.rationale());
        payload.put("additionalToolPlanEnoughInformation", followupPlan == null ? null : followupPlan.enoughInformation());
        payload.put("additionalToolArguments", followupPlan == null ? Map.of() : followupPlan.toolArguments());
        payload.put("usedTools", toolCalls.stream().map(AgentToolCallResponse::toolName).toList());
        payload.put("userMessageId", userMessageId);
        return payload;
    }

    @SuppressWarnings("unchecked")
    private ToolExecutionBundle executeTools(
            Long sessionId,
            Long messageId,
            AgentContext context,
            AgentToolPlan toolPlan,
            AgentStreamSink sink
    ) {
        Map<String, Object> payload = new HashMap<>(context.payload());
        List<AgentToolCallResponse> calls = new ArrayList<>();
        List<String> selectedTools = normalizePlannedTools(context.anchorType(), toolPlan.selectedTools());

        if ("patient".equals(context.anchorType())) {
            Long patientId = context.anchorId();
            if (selectedTools.contains("get_patient_profile")) {
                ToolExecutionResult patientProfile = runTool(
                        sessionId,
                        messageId,
                        "get_patient_profile",
                        Map.of("patientId", patientId),
                        calls,
                        sink
                );
                payload.put("patient", patientProfile.payload());
            }
            if (selectedTools.contains("get_patient_prescriptions")) {
                ToolExecutionResult prescriptions = runTool(
                        sessionId,
                        messageId,
                        "get_patient_prescriptions",
                        buildToolArguments("get_patient_prescriptions", toolPlan, Map.of("patientId", patientId, "limit", 6)),
                        calls,
                        sink
                );
                payload.put("recentPrescriptions", prescriptions.payload());
            }
            if (selectedTools.contains("get_common_herbs")) {
                ToolExecutionResult commonHerbs = runTool(
                        sessionId,
                        messageId,
                        "get_common_herbs",
                        buildToolArguments("get_common_herbs", toolPlan, Map.of("patientId", patientId, "limit", 5)),
                        calls,
                        sink
                );
                payload.put("commonHerbs", commonHerbs.payload());
            }
            return new ToolExecutionBundle(payload, calls);
        }

        Long prescriptionId = context.anchorId();
        ToolExecutionResult detailResult = null;
        if (selectedTools.contains("get_prescription_detail")) {
            detailResult = runTool(
                    sessionId,
                    messageId,
                    "get_prescription_detail",
                    Map.of("prescriptionId", prescriptionId),
                    calls,
                    sink
            );
            payload.put("prescription", detailResult.payload());
        }

        if (detailResult != null && detailResult.payload() instanceof PrescriptionDetailResponse detail && detail.patientId() != null) {
            List<PrescriptionSummaryResponse> history = List.of();

            if (selectedTools.contains("get_patient_prescriptions")) {
                ToolExecutionResult historyResult = runTool(
                        sessionId,
                        messageId,
                        "get_patient_prescriptions",
                        buildToolArguments("get_patient_prescriptions", toolPlan, Map.of("patientId", detail.patientId(), "limit", 6)),
                        calls,
                        sink
                );
                history = ((List<PrescriptionSummaryResponse>) historyResult.payload()).stream()
                        .filter(item -> !item.id().equals(detail.id()))
                        .toList();
                payload.put("patientHistory", history);
            }

            if (selectedTools.contains("get_common_herbs")) {
                ToolExecutionResult commonHerbsResult = runTool(
                        sessionId,
                        messageId,
                        "get_common_herbs",
                        buildToolArguments("get_common_herbs", toolPlan, Map.of("patientId", detail.patientId(), "limit", 5)),
                        calls,
                        sink
                );
                payload.put("commonHerbs", commonHerbsResult.payload());
            }

        if (selectedTools.contains("compare_prescriptions") && !history.isEmpty()) {
            ToolExecutionResult comparisonResult = runTool(
                    sessionId,
                    messageId,
                    "compare_prescriptions",
                    buildComparisonArguments(toolPlan, history, detail.id()),
                    calls,
                    sink
            );
            payload.put("comparison", comparisonResult.payload());
        }
        }

        return new ToolExecutionBundle(payload, calls);
    }

    private AgentToolPlan planTools(AgentContext context, String userQuestion) {
        List<String> defaultTools = defaultToolSequence(context.anchorType());
        List<String> availableToolNames = availableToolNames(context.anchorType());
        return agentModelClient.planTools(
                context,
                userQuestion,
                toolRegistry.listByNames(availableToolNames),
                defaultTools
        );
    }

    private List<String> defaultToolSequence(String anchorType) {
        if ("patient".equals(anchorType)) {
            return List.of("get_patient_profile", "get_patient_prescriptions", "get_common_herbs");
        }
        return List.of("get_prescription_detail", "get_patient_prescriptions", "compare_prescriptions");
    }

    private List<String> availableToolNames(String anchorType) {
        if ("patient".equals(anchorType)) {
            return List.of("get_patient_profile", "get_patient_prescriptions", "get_common_herbs");
        }
        return List.of("get_prescription_detail", "get_patient_prescriptions", "compare_prescriptions", "get_common_herbs");
    }

    private List<String> normalizePlannedTools(String anchorType, List<String> selectedTools) {
        List<String> normalized = new ArrayList<>();
        if ("patient".equals(anchorType)) {
            for (String toolName : List.of("get_patient_profile", "get_patient_prescriptions", "get_common_herbs")) {
                if (selectedTools.contains(toolName)) {
                    normalized.add(toolName);
                }
            }
            return normalized.isEmpty() ? defaultToolSequence(anchorType) : normalized;
        }

        Set<String> selectedSet = Set.copyOf(selectedTools);
        if (selectedSet.contains("compare_prescriptions")) {
            selectedSet = new java.util.LinkedHashSet<>(selectedSet);
        }
        if (selectedSet.contains("compare_prescriptions")
                || selectedSet.contains("get_patient_prescriptions")
                || selectedSet.contains("get_common_herbs")) {
            normalized.add("get_prescription_detail");
        }
        if (selectedSet.contains("get_prescription_detail")) {
            if (!normalized.contains("get_prescription_detail")) {
                normalized.add("get_prescription_detail");
            }
        }
        if (selectedSet.contains("get_patient_prescriptions") || selectedSet.contains("compare_prescriptions")) {
            normalized.add("get_patient_prescriptions");
        }
        if (selectedSet.contains("compare_prescriptions")) {
            normalized.add("compare_prescriptions");
        }
        if (selectedSet.contains("get_common_herbs")) {
            normalized.add("get_common_herbs");
        }
        return normalized.isEmpty() ? defaultToolSequence(anchorType) : normalized;
    }

    private AgentToolPlan planAdditionalTools(
            AgentContext context,
            String userQuestion,
            List<AgentToolCallResponse> calls
    ) {
        List<String> usedTools = calls.stream().map(AgentToolCallResponse::toolName).distinct().toList();
        List<String> remainingToolNames = availableToolNames(context.anchorType()).stream()
                .filter(toolName -> !usedTools.contains(toolName))
                .toList();
        if (remainingToolNames.isEmpty()) {
            return new AgentToolPlan(List.of(), "当前没有剩余工具需要追加。", List.of("第一轮工具已覆盖当前可用能力"), Map.of(), true, true);
        }
        return agentModelClient.planAdditionalTools(
                context,
                userQuestion,
                toolRegistry.listByNames(remainingToolNames),
                usedTools
        );
    }

    private ToolExecutionBundle executeAdditionalTools(
            Long sessionId,
            Long messageId,
            AgentContext context,
            ToolExecutionBundle existingBundle,
            AgentToolPlan toolPlan,
            AgentStreamSink sink
    ) {
        Map<String, Object> payload = new HashMap<>(existingBundle.payload());
        List<AgentToolCallResponse> calls = new ArrayList<>(existingBundle.calls());
        List<String> selectedTools = normalizePlannedTools(context.anchorType(), toolPlan.selectedTools()).stream()
                .filter(toolName -> calls.stream().noneMatch(call -> call.toolName().equals(toolName)))
                .toList();

        if (selectedTools.isEmpty()) {
            return existingBundle;
        }

        if ("patient".equals(context.anchorType())) {
            Long patientId = context.anchorId();
            if (selectedTools.contains("get_patient_profile")) {
                payload.put("patient", runTool(sessionId, messageId, "get_patient_profile", Map.of("patientId", patientId), calls, sink).payload());
            }
            if (selectedTools.contains("get_patient_prescriptions")) {
                payload.put("recentPrescriptions", runTool(
                        sessionId,
                        messageId,
                        "get_patient_prescriptions",
                        buildToolArguments("get_patient_prescriptions", toolPlan, Map.of("patientId", patientId, "limit", 6)),
                        calls,
                        sink
                ).payload());
            }
            if (selectedTools.contains("get_common_herbs")) {
                payload.put("commonHerbs", runTool(
                        sessionId,
                        messageId,
                        "get_common_herbs",
                        buildToolArguments("get_common_herbs", toolPlan, Map.of("patientId", patientId, "limit", 5)),
                        calls,
                        sink
                ).payload());
            }
            return new ToolExecutionBundle(payload, calls);
        }

        Object currentPrescription = payload.get("prescription");
        if (selectedTools.contains("get_prescription_detail")) {
            currentPrescription = runTool(sessionId, messageId, "get_prescription_detail", Map.of("prescriptionId", context.anchorId()), calls, sink).payload();
            payload.put("prescription", currentPrescription);
        }

        if (currentPrescription instanceof PrescriptionDetailResponse detail && detail.patientId() != null) {
            List<PrescriptionSummaryResponse> history = payload.get("patientHistory") instanceof List<?> list
                    ? list.stream().filter(PrescriptionSummaryResponse.class::isInstance).map(PrescriptionSummaryResponse.class::cast).toList()
                    : List.of();

            if (selectedTools.contains("get_patient_prescriptions")) {
                ToolExecutionResult historyResult = runTool(
                        sessionId,
                        messageId,
                        "get_patient_prescriptions",
                        buildToolArguments("get_patient_prescriptions", toolPlan, Map.of("patientId", detail.patientId(), "limit", 6)),
                        calls,
                        sink
                );
                history = ((List<PrescriptionSummaryResponse>) historyResult.payload()).stream()
                        .filter(item -> !item.id().equals(detail.id()))
                        .toList();
                payload.put("patientHistory", history);
            }

            if (selectedTools.contains("get_common_herbs")) {
                payload.put("commonHerbs", runTool(
                        sessionId,
                        messageId,
                        "get_common_herbs",
                        buildToolArguments("get_common_herbs", toolPlan, Map.of("patientId", detail.patientId(), "limit", 5)),
                        calls,
                        sink
                ).payload());
            }

            if (selectedTools.contains("compare_prescriptions") && !history.isEmpty()) {
                payload.put("comparison", runTool(
                        sessionId,
                        messageId,
                        "compare_prescriptions",
                        buildComparisonArguments(toolPlan, history, detail.id()),
                        calls,
                        sink
                ).payload());
            }
        }

        return new ToolExecutionBundle(payload, calls);
    }

    private Map<String, Object> buildToolArguments(String toolName, AgentToolPlan toolPlan, Map<String, Object> defaults) {
        Map<String, Object> resolved = new LinkedHashMap<>(defaults);
        Map<String, Object> planArgs = toolPlan.toolArguments().get(toolName);
        if (planArgs == null || planArgs.isEmpty()) {
            return resolved;
        }

        if (planArgs.get("limit") instanceof Number number) {
            int normalizedLimit = Math.max(3, Math.min(number.intValue(), 8));
            resolved.put("limit", normalizedLimit);
        }
        return resolved;
    }

    private Map<String, Object> buildComparisonArguments(
            AgentToolPlan toolPlan,
            List<PrescriptionSummaryResponse> history,
            Long rightPrescriptionId
    ) {
        Long defaultLeftPrescriptionId = history.get(0).id();
        Map<String, Object> resolved = new LinkedHashMap<>();
        resolved.put("leftPrescriptionId", defaultLeftPrescriptionId);
        resolved.put("rightPrescriptionId", rightPrescriptionId);

        Map<String, Object> planArgs = toolPlan.toolArguments().get("compare_prescriptions");
        if (planArgs == null || planArgs.isEmpty()) {
            return resolved;
        }

        if (planArgs.get("historyOffset") instanceof Number number) {
            int offset = Math.max(0, Math.min(number.intValue(), history.size() - 1));
            resolved.put("leftPrescriptionId", history.get(offset).id());
            return resolved;
        }

        Object comparisonTarget = planArgs.get("comparisonTarget");
        if (!(comparisonTarget instanceof String target)) {
            return resolved;
        }

        if ("latest_history".equals(target) || target.isBlank()) {
            return resolved;
        }
        if ("second_latest_history".equals(target) && history.size() > 1) {
            resolved.put("leftPrescriptionId", history.get(1).id());
            return resolved;
        }
        if (("oldest_history".equals(target) || "earliest_history".equals(target)) && !history.isEmpty()) {
            resolved.put("leftPrescriptionId", history.get(history.size() - 1).id());
            return resolved;
        }
        if ("middle_history".equals(target) && !history.isEmpty()) {
            resolved.put("leftPrescriptionId", history.get(history.size() / 2).id());
        }
        return resolved;
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
                    "inputJson", arguments,
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
