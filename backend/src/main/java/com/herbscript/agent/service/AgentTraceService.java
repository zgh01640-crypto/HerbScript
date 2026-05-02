package com.herbscript.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.herbscript.agent.dto.AgentToolCallResponse;
import com.herbscript.agent.dto.AgentTraceResponse;
import com.herbscript.agent.skill.dto.SkillExecutionResult;
import com.herbscript.agent.tool.dto.ToolExecutionResult;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AgentTraceService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AgentTraceService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Long saveToolCall(Long sessionId, Long messageId, String toolLabel, Object input, ToolExecutionResult result) {
        jdbcTemplate.update(
                """
                INSERT INTO agent_tool_call
                (session_id, message_id, tool_name, tool_label, input_json, output_json, status, error_message, latency_ms)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                sessionId,
                messageId,
                result.toolName(),
                toolLabel,
                toJson(input),
                toJson(result.payload()),
                result.status(),
                result.errorMessage(),
                result.latencyMs()
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    public void saveSkillRun(Long sessionId, Long messageId, SkillExecutionResult result) {
        jdbcTemplate.update(
                """
                INSERT INTO agent_skill_run
                (session_id, message_id, skill_name, status, output_json, latency_ms)
                VALUES (?, ?, ?, 'success', ?, 0)
                """,
                sessionId,
                messageId,
                result.skillName(),
                toJson(result.structured())
        );
    }

    public Long saveTrace(
            Long sessionId,
            Long messageId,
            String modelName,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            Integer latencyMs,
            Object payload
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO agent_trace
                (session_id, message_id, model_name, prompt_tokens, completion_tokens, total_tokens, latency_ms, trace_payload)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                sessionId,
                messageId,
                modelName,
                promptTokens,
                completionTokens,
                totalTokens,
                latencyMs,
                toJson(payload)
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    public List<AgentToolCallResponse> listToolCalls(Long sessionId) {
        return jdbcTemplate.query(
                """
                SELECT id, message_id, tool_name, tool_label, status, latency_ms, input_json, output_json,
                       DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at
                FROM agent_tool_call
                WHERE session_id = ?
                ORDER BY created_at DESC, id DESC
                """,
                (rs, rowNum) -> new AgentToolCallResponse(
                        rs.getLong("id"),
                        rs.getObject("message_id") == null ? null : rs.getLong("message_id"),
                        rs.getString("tool_name"),
                        rs.getString("tool_label"),
                        rs.getString("status"),
                        rs.getObject("latency_ms") == null ? null : rs.getInt("latency_ms"),
                        rs.getString("input_json"),
                        rs.getString("output_json"),
                        rs.getString("created_at")
                ),
                sessionId
        );
    }

    public List<AgentTraceResponse> listTraces(Long sessionId) {
        return jdbcTemplate.query(
                """
                SELECT id, message_id, model_name, prompt_tokens, completion_tokens, total_tokens, latency_ms, trace_payload,
                       DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at
                FROM agent_trace
                WHERE session_id = ?
                ORDER BY created_at DESC, id DESC
                """,
                (rs, rowNum) -> new AgentTraceResponse(
                        rs.getLong("id"),
                        rs.getObject("message_id") == null ? null : rs.getLong("message_id"),
                        rs.getString("model_name"),
                        rs.getObject("prompt_tokens") == null ? null : rs.getInt("prompt_tokens"),
                        rs.getObject("completion_tokens") == null ? null : rs.getInt("completion_tokens"),
                        rs.getObject("total_tokens") == null ? null : rs.getInt("total_tokens"),
                        rs.getObject("latency_ms") == null ? null : rs.getInt("latency_ms"),
                        rs.getString("trace_payload"),
                        rs.getString("created_at")
                ),
                sessionId
        );
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
