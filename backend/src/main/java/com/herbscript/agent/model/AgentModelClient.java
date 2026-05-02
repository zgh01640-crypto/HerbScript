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
import com.herbscript.modelconfig.ModelConfigService;
import com.herbscript.modelconfig.ModelRuntimeConfig;
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
                    toStringList(parsed.path("suggestions"))
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
}
