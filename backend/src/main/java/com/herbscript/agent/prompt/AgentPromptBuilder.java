package com.herbscript.agent.prompt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.herbscript.agent.context.AgentContext;
import com.herbscript.agent.dto.AgentMessageResponse;
import com.herbscript.agent.tool.AgentTool;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AgentPromptBuilder {

    private static final int MAX_HISTORY_MESSAGES = 4;
    private static final int MAX_ARRAY_ITEMS = 4;
    private static final int MAX_STRING_LENGTH = 180;

    private final ObjectMapper objectMapper;

    public AgentPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> buildMessages(
            AgentContext context,
            List<AgentMessageResponse> history,
            String userQuestion
    ) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", buildSystemPrompt(context)));
        messages.add(Map.of("role", "user", "content", buildContextPayload(context)));

        history.stream()
                .filter(message -> message.content() != null && !message.content().isBlank())
                .skip(Math.max(history.size() - MAX_HISTORY_MESSAGES, 0))
                .forEach(message -> messages.add(Map.of(
                        "role", normalizeRole(message.role()),
                        "content", truncate(message.content(), 220)
                )));

        messages.add(Map.of("role", "user", "content", "用户当前问题：" + userQuestion));
        return messages;
    }

    public List<Map<String, Object>> buildToolPlanMessages(
            AgentContext context,
            String userQuestion,
            List<AgentTool> availableTools
    ) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", buildToolPlanSystemPrompt(context)));
        messages.add(Map.of("role", "user", "content", buildToolPlanUserPrompt(context, userQuestion, availableTools)));
        return messages;
    }

    public List<Map<String, Object>> buildAdditionalToolPlanMessages(
            AgentContext context,
            String userQuestion,
            List<AgentTool> availableTools,
            List<String> usedTools
    ) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", buildAdditionalToolPlanSystemPrompt(context)));
        messages.add(Map.of("role", "user", "content", buildAdditionalToolPlanUserPrompt(context, userQuestion, availableTools, usedTools)));
        return messages;
    }

    private String buildSystemPrompt(AgentContext context) {
        return """
                你是 HerbScript 中医辅助智能体。
                你需要基于系统提供的患者、处方、历史记录与工具结果，生成结构化辅助分析。
                不能替代执业医师做最终诊断或治疗决策，结论必须审慎。

                只返回 JSON，字段固定为：
                answer, summary, observations, risks, suggestions, answerConfidence, remainingUncertainties

                规则：
                1. answer 与 summary 为字符串；observations、risks、suggestions 为字符串数组。
                2. answerConfidence 只能是 high、medium、low 之一。
                3. remainingUncertainties 为字符串数组，用来说明这轮回答仍然存在的未解决不确定性；如果没有，可返回空数组。
                4. 回答必须紧扣当前锚点：""" + context.anchorType() + """
                5. 如果存在历史处方或对比结果，优先总结变化点。
                6. 语言专业、克制、适合系统内展示。
                """;
    }

    private String buildToolPlanSystemPrompt(AgentContext context) {
        return """
                你是 HerbScript 智能体的工具规划器。
                你需要根据当前锚点和用户问题，从候选工具中选择最有必要的一组工具，按执行顺序返回。
                不能臆造工具名，也不要选择无关工具。

                只返回 JSON，字段固定为：
                tools, rationale, informationNeeds, toolArguments, enoughInformation

                规则：
                1. tools 为字符串数组，元素必须来自候选工具列表。
                2. 最多选择 4 个工具。
                3. 如果问题只需要少量信息，尽量少选工具。
                4. toolArguments 为对象，key 为工具名，value 为该工具的参数意图。
                5. limit 这类参数请尽量给出 3-8 之间的合理值。
                6. compare_prescriptions 可使用 comparisonTarget，例如 latest_history、second_latest_history、oldest_history，或使用 historyOffset 指定历史处方偏移。
                7. enoughInformation 为布尔值，表示基于当前上下文与本轮计划，信息是否足以回答问题。
                8. 当前锚点为：""" + context.anchorType() + """
                """;
    }

    private String buildAdditionalToolPlanSystemPrompt(AgentContext context) {
        return """
                你是 HerbScript 智能体的二次工具规划器。
                第一轮工具已经执行完毕。请根据当前已获得的信息，判断是否还缺少关键事实。
                如果还缺，请从剩余候选工具中选择最多 2 个继续补充；如果不缺，则返回空数组。

                只返回 JSON，字段固定为：
                tools, rationale, informationNeeds, toolArguments, enoughInformation

                规则：
                1. tools 为字符串数组，元素必须来自剩余候选工具列表。
                2. informationNeeds 为字符串数组，用来说明还缺什么信息或想补什么事实。
                3. toolArguments 为对象，key 为工具名，value 为该工具的参数意图。
                4. 如果现有信息已经足够回答，就返回 tools: [] 且 enoughInformation: true。
                5. compare_prescriptions 可使用 comparisonTarget，例如 latest_history、second_latest_history、oldest_history，或使用 historyOffset 指定历史处方偏移。
                6. enoughInformation 为布尔值，表示当前信息是否已经足够回答问题。
                7. 当前锚点为：""" + context.anchorType() + """
                """;
    }

    private String buildContextPayload(AgentContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contextSummary", context.summary());
        payload.put("payload", compactPayload(context.payload()));
        try {
            return "系统上下文如下，请严格基于这些信息作答：\n" + objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "系统上下文如下，请严格基于当前上下文作答。";
        }
    }

    private String buildToolPlanUserPrompt(AgentContext context, String userQuestion, List<AgentTool> availableTools) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("anchorType", context.anchorType());
        payload.put("contextSummary", context.summary());
        payload.put("userQuestion", userQuestion);
        payload.put("availableTools", availableTools.stream().map(tool -> Map.of(
                "name", tool.name(),
                "description", tool.description()
        )).toList());
        try {
            return "请根据以下信息规划最合适的工具执行顺序：\n" + objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "请根据当前问题选择最必要的工具。";
        }
    }

    private String buildAdditionalToolPlanUserPrompt(
            AgentContext context,
            String userQuestion,
            List<AgentTool> availableTools,
            List<String> usedTools
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("anchorType", context.anchorType());
        payload.put("contextSummary", context.summary());
        payload.put("currentPayload", compactPayload(context.payload()));
        payload.put("userQuestion", userQuestion);
        payload.put("usedTools", usedTools);
        payload.put("remainingTools", availableTools.stream().map(tool -> Map.of(
                "name", tool.name(),
                "description", tool.description()
        )).toList());
        try {
            return "请判断基于当前结果是否还需要追加工具：\n" + objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "请判断是否还需要追加工具。";
        }
    }

    private String normalizeRole(String role) {
        if ("assistant".equals(role) || "user".equals(role) || "system".equals(role)) {
            return role;
        }
        return "user";
    }

    private Object compactPayload(Map<String, Object> payload) {
        JsonNode node = objectMapper.valueToTree(payload);
        return pruneNode(node, 0);
    }

    private Object pruneNode(JsonNode node, int depth) {
        if (node == null || node.isNull()) {
            return null;
        }

        if (node.isValueNode()) {
            if (node.isTextual()) {
                return truncate(node.asText(), MAX_STRING_LENGTH);
            }
            return objectMapper.convertValue(node, Object.class);
        }

        if (depth >= 3) {
            if (node.isArray()) {
                return "[truncated]";
            }
            return "{truncated}";
        }

        if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            List<Object> items = new ArrayList<>();
            for (int i = 0; i < Math.min(arrayNode.size(), MAX_ARRAY_ITEMS); i += 1) {
                items.add(pruneNode(arrayNode.get(i), depth + 1));
            }
            if (arrayNode.size() > MAX_ARRAY_ITEMS) {
                items.add("... +" + (arrayNode.size() - MAX_ARRAY_ITEMS) + " more");
            }
            return items;
        }

        ObjectNode objectNode = (ObjectNode) node;
        Map<String, Object> result = new LinkedHashMap<>();
        objectNode.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            if (shouldSkipField(key)) {
                return;
            }
            result.put(key, pruneNode(entry.getValue(), depth + 1));
        });
        return result;
    }

    private boolean shouldSkipField(String key) {
        return "logs".equals(key)
                || "recognitionDraft".equals(key)
                || "rawText".equals(key)
                || "sourceImageUrl".equals(key)
                || "createdAt".equals(key)
                || "updatedAt".equals(key);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
