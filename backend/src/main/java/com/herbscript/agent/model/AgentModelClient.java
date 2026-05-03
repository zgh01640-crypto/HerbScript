package com.herbscript.agent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.herbscript.agent.dto.AgentStructuredResponse;
import com.herbscript.agent.prompt.AgentPromptBuilder;
import com.herbscript.agent.skill.AgentSkill;
import com.herbscript.agent.skill.dto.SkillExecutionRequest;
import com.herbscript.agent.skill.dto.SkillExecutionResult;
import com.herbscript.agent.context.AgentContext;
import com.herbscript.agent.dto.AgentMessageResponse;
import com.herbscript.agent.tool.AgentTool;
import com.herbscript.modelconfig.ModelConfigService;
import com.herbscript.modelconfig.ModelRuntimeConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AgentModelClient {

    private final ModelConfigService modelConfigService;
    private final AgentPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public AgentModelClient(
            ModelConfigService modelConfigService,
            AgentPromptBuilder promptBuilder,
            ObjectMapper objectMapper
    ) {
        this.modelConfigService = modelConfigService;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
    }

    public AgentModelResult generate(
            AgentContext context,
            List<AgentMessageResponse> history,
            String userQuestion,
            AgentSkill fallbackSkill
    ) {
        ModelRuntimeConfig config = modelConfigService.getRuntimeConfig();
        if (config.doubaoApiKey() == null || config.doubaoApiKey().isBlank()) {
            return fallback(context, userQuestion, fallbackSkill);
        }

        long start = System.currentTimeMillis();
        try {
            RestClient restClient = RestClient.builder()
                    .baseUrl(config.doubaoBaseUrl())
                    .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .build();

            Map<String, Object> requestBody = Map.of(
                    "model", config.doubaoModel(),
                    "temperature", 0.2,
                    "messages", promptBuilder.buildMessages(context, history, userQuestion)
            );

            String responseBody = restClient.post()
                    .uri(config.doubaoChatPath())
                    .header("Authorization", "Bearer " + config.doubaoApiKey())
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.isNull()) {
                throw new IllegalArgumentException("Agent 模型返回为空");
            }

            String content = contentNode.isTextual() ? contentNode.asText() : contentNode.toString();
            String normalized = stripMarkdownCodeFence(content);
            JsonNode parsed = objectMapper.readTree(normalized);

            String answer = parsed.path("answer").asText(parsed.path("summary").asText("未生成回答"));
            AgentStructuredResponse structured = new AgentStructuredResponse(
                    parsed.path("summary").asText(answer),
                    toStringList(parsed.path("observations")),
                    toStringList(parsed.path("risks")),
                    toStringList(parsed.path("suggestions")),
                    parsed.path("answerConfidence").asText("medium"),
                    toStringList(parsed.path("remainingUncertainties"))
            );

            JsonNode usage = root.path("usage");
            return new AgentModelResult(
                    answer,
                    structured,
                    usage.path("prompt_tokens").isMissingNode() ? null : usage.path("prompt_tokens").asInt(),
                    usage.path("completion_tokens").isMissingNode() ? null : usage.path("completion_tokens").asInt(),
                    usage.path("total_tokens").isMissingNode() ? null : usage.path("total_tokens").asInt(),
                    (int) (System.currentTimeMillis() - start),
                    false
            );
        } catch (Exception ex) {
            if (config.fallbackToMockOnError()) {
                return fallback(context, userQuestion, fallbackSkill);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "智能体模型调用失败");
        }
    }

    public AgentToolPlan planTools(
            AgentContext context,
            String userQuestion,
            List<AgentTool> availableTools,
            List<String> defaultTools
    ) {
        ModelRuntimeConfig config = modelConfigService.getRuntimeConfig();
        if (config.doubaoApiKey() == null || config.doubaoApiKey().isBlank()) {
            return new AgentToolPlan(defaultTools, "未配置模型密钥，使用默认工具顺序。", List.of("缺少模型规划能力"), Map.of(), false, true);
        }

        try {
            RestClient restClient = RestClient.builder()
                    .baseUrl(config.doubaoBaseUrl())
                    .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .build();

            Map<String, Object> requestBody = Map.of(
                    "model", config.doubaoModel(),
                    "temperature", 0.1,
                    "messages", promptBuilder.buildToolPlanMessages(context, userQuestion, availableTools)
            );

            String responseBody = restClient.post()
                    .uri(config.doubaoChatPath())
                    .header("Authorization", "Bearer " + config.doubaoApiKey())
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.isNull()) {
                return new AgentToolPlan(defaultTools, "工具规划返回为空，使用默认顺序。", List.of("模型未返回规划结果"), Map.of(), false, true);
            }

            String content = contentNode.isTextual() ? contentNode.asText() : contentNode.toString();
            JsonNode parsed = objectMapper.readTree(stripMarkdownCodeFence(content));
            List<String> candidateNames = availableTools.stream().map(AgentTool::name).toList();
            List<String> selectedTools = toStringList(parsed.path("tools")).stream()
                    .filter(candidateNames::contains)
                    .distinct()
                    .limit(4)
                    .toList();
            Map<String, Map<String, Object>> toolArguments = extractToolArguments(parsed.path("toolArguments"), candidateNames);
            boolean enoughInformation = parsed.path("enoughInformation").asBoolean(false);

            if (selectedTools.isEmpty() && !enoughInformation) {
                return new AgentToolPlan(defaultTools, "模型未返回有效工具，使用默认顺序。", toStringList(parsed.path("informationNeeds")), Map.of(), false, true);
            }

            return new AgentToolPlan(
                    selectedTools,
                    parsed.path("rationale").asText("模型已根据问题规划工具顺序。"),
                    toStringList(parsed.path("informationNeeds")),
                    toolArguments,
                    enoughInformation,
                    false
            );
        } catch (Exception ex) {
            return new AgentToolPlan(defaultTools, "工具规划失败，使用默认顺序。", List.of("规划请求失败"), Map.of(), false, true);
        }
    }

    public AgentToolPlan planAdditionalTools(
            AgentContext context,
            String userQuestion,
            List<AgentTool> availableTools,
            List<String> usedTools
    ) {
        if (availableTools.isEmpty()) {
            return new AgentToolPlan(List.of(), "当前没有可追加的工具。", List.of("没有剩余可选工具"), Map.of(), true, true);
        }

        ModelRuntimeConfig config = modelConfigService.getRuntimeConfig();
        if (config.doubaoApiKey() == null || config.doubaoApiKey().isBlank()) {
            return new AgentToolPlan(List.of(), "未配置模型密钥，跳过追加工具规划。", List.of("缺少模型规划能力"), Map.of(), false, true);
        }

        try {
            RestClient restClient = RestClient.builder()
                    .baseUrl(config.doubaoBaseUrl())
                    .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .build();

            Map<String, Object> requestBody = Map.of(
                    "model", config.doubaoModel(),
                    "temperature", 0.1,
                    "messages", promptBuilder.buildAdditionalToolPlanMessages(
                            context,
                            userQuestion,
                            availableTools,
                            usedTools
                    )
            );

            String responseBody = restClient.post()
                    .uri(config.doubaoChatPath())
                    .header("Authorization", "Bearer " + config.doubaoApiKey())
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (contentNode.isMissingNode() || contentNode.isNull()) {
                return new AgentToolPlan(List.of(), "模型未给出追加工具。", List.of("模型未返回追加规划结果"), Map.of(), true, true);
            }

            String content = contentNode.isTextual() ? contentNode.asText() : contentNode.toString();
            JsonNode parsed = objectMapper.readTree(stripMarkdownCodeFence(content));
            List<String> candidateNames = availableTools.stream().map(AgentTool::name).toList();
            List<String> selectedTools = toStringList(parsed.path("tools")).stream()
                    .filter(candidateNames::contains)
                    .distinct()
                    .limit(2)
                    .toList();
            Map<String, Map<String, Object>> toolArguments = extractToolArguments(parsed.path("toolArguments"), candidateNames);
            boolean enoughInformation = parsed.path("enoughInformation").asBoolean(selectedTools.isEmpty());

            return new AgentToolPlan(
                    selectedTools,
                    parsed.path("rationale").asText("模型评估后无需额外工具。"),
                    toStringList(parsed.path("informationNeeds")),
                    toolArguments,
                    enoughInformation,
                    selectedTools.isEmpty() && enoughInformation
            );
        } catch (Exception ex) {
            return new AgentToolPlan(List.of(), "追加工具规划失败，本轮不再追加工具。", List.of("追加规划请求失败"), Map.of(), false, true);
        }
    }

    private AgentModelResult fallback(AgentContext context, String userQuestion, AgentSkill fallbackSkill) {
        SkillExecutionResult result = fallbackSkill.execute(new SkillExecutionRequest(context, userQuestion));
        return new AgentModelResult(
                result.content(),
                result.structured(),
                0,
                0,
                0,
                0,
                true
        );
    }

    private String stripMarkdownCodeFence(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed.trim();
    }

    private List<String> toStringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
                .map(JsonNode::asText)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> extractToolArguments(JsonNode node, List<String> candidateNames) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }

        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            if (!candidateNames.contains(entry.getKey()) || !entry.getValue().isObject()) {
                return;
            }
            result.put(entry.getKey(), objectMapper.convertValue(entry.getValue(), Map.class));
        });
        return result;
    }
}
