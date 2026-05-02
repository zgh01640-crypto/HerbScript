package com.herbscript.agent.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.herbscript.agent.dto.AgentMessageResponse;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AgentSessionMemoryService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AgentSessionMemoryService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Long appendMessage(Long sessionId, String role, String content, Object structuredPayload) {
        jdbcTemplate.update(
                "INSERT INTO agent_message (session_id, role, content, structured_payload) VALUES (?, ?, ?, ?)",
                sessionId,
                role,
                content,
                toJson(structuredPayload)
        );
        jdbcTemplate.update("UPDATE agent_session SET last_message_at = CURRENT_TIMESTAMP WHERE id = ?", sessionId);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    public List<AgentMessageResponse> listMessages(Long sessionId) {
        return jdbcTemplate.query(
                """
                SELECT id, role, content, structured_payload,
                       DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at
                FROM agent_message
                WHERE session_id = ?
                ORDER BY created_at ASC, id ASC
                """,
                (rs, rowNum) -> new AgentMessageResponse(
                        rs.getLong("id"),
                        rs.getString("role"),
                        rs.getString("content"),
                        rs.getString("structured_payload"),
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
