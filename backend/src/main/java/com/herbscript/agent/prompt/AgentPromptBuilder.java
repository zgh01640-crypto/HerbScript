package com.herbscript.agent.prompt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.herbscript.agent.context.AgentContext;
import com.herbscript.agent.dto.AgentMessageResponse;
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

    private String buildSystemPrompt(AgentContext context) {
        return """
                你是 HerbScript 中医辅助智能体。
                你需要基于系统提供的患者、处方、历史记录与工具结果，生成结构化辅助分析。
                不能替代执业医师做最终诊断或治疗决策，结论必须审慎。

                只返回 JSON，字段固定为：
                answer, summary, observations, risks, suggestions

                规则：
                1. answer 与 summary 为字符串；observations、risks、suggestions 为字符串数组。
                2. 回答必须紧扣当前锚点：""" + context.anchorType() + """
                3. 如果存在历史处方或对比结果，优先总结变化点。
                4. 语言专业、克制、适合系统内展示。
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
