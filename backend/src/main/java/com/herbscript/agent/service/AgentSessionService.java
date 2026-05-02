package com.herbscript.agent.service;

import com.herbscript.agent.context.AgentContextBuilder;
import com.herbscript.agent.dto.AgentContextSummaryResponse;
import com.herbscript.agent.dto.AgentSessionCreateRequest;
import com.herbscript.agent.dto.AgentSessionDetailResponse;
import com.herbscript.agent.dto.AgentSessionResponse;
import com.herbscript.agent.dto.AgentSessionSummaryResponse;
import com.herbscript.agent.memory.AgentSessionMemoryService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentSessionService {

    private final JdbcTemplate jdbcTemplate;
    private final AgentSessionMemoryService memoryService;
    private final AgentContextBuilder contextBuilder;

    public AgentSessionService(
            JdbcTemplate jdbcTemplate,
            AgentSessionMemoryService memoryService,
            AgentContextBuilder contextBuilder
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.memoryService = memoryService;
        this.contextBuilder = contextBuilder;
    }

    @Transactional
    public AgentSessionResponse create(Long userId, AgentSessionCreateRequest request) {
        jdbcTemplate.update(
                """
                INSERT INTO agent_session (user_id, anchor_type, anchor_id, title, session_status)
                VALUES (?, ?, ?, ?, 'active')
                """,
                userId,
                request.anchorType(),
                request.anchorId(),
                request.title().trim()
        );
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return getSession(id);
    }

    public List<AgentSessionSummaryResponse> list(String anchorType, Long anchorId) {
        String sql = """
                SELECT s.id, s.anchor_type, s.anchor_id, s.title, s.session_status,
                       DATE_FORMAT(s.last_message_at, '%Y-%m-%d %H:%i:%s') AS last_message_at,
                       DATE_FORMAT(s.created_at, '%Y-%m-%d %H:%i:%s') AS created_at,
                       (
                         SELECT m.content FROM agent_message m
                         WHERE m.session_id = s.id AND m.role = 'assistant'
                         ORDER BY m.created_at DESC, m.id DESC
                         LIMIT 1
                       ) AS last_assistant_summary
                FROM agent_session s
                WHERE s.deleted = 0
                """ + (anchorType != null && !anchorType.isBlank() ? " AND s.anchor_type = ?" : "")
                + (anchorId != null ? " AND s.anchor_id = ?" : "")
                + " ORDER BY s.last_message_at DESC, s.updated_at DESC, s.id DESC";

        if (anchorType != null && !anchorType.isBlank() && anchorId != null) {
            return jdbcTemplate.query(sql, (rs, rowNum) -> mapSummary(rs), anchorType, anchorId);
        }
        if (anchorType != null && !anchorType.isBlank()) {
            return jdbcTemplate.query(sql, (rs, rowNum) -> mapSummary(rs), anchorType);
        }
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapSummary(rs));
    }

    public AgentSessionDetailResponse detail(Long sessionId) {
        AgentSessionResponse session = getSession(sessionId);
        AgentContextSummaryResponse summary = contextBuilder.build(session.anchorType(), session.anchorId()).summary();
        return new AgentSessionDetailResponse(session, summary, memoryService.listMessages(sessionId));
    }

    @Transactional
    public void delete(Long sessionId) {
        int updated = jdbcTemplate.update("UPDATE agent_session SET deleted = 1 WHERE id = ? AND deleted = 0", sessionId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        }
    }

    public AgentSessionResponse getSession(Long sessionId) {
        List<AgentSessionResponse> sessions = jdbcTemplate.query(
                """
                SELECT id, anchor_type, anchor_id, title, session_status,
                       DATE_FORMAT(last_message_at, '%Y-%m-%d %H:%i:%s') AS last_message_at,
                       DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at
                FROM agent_session
                WHERE id = ? AND deleted = 0
                """,
                (rs, rowNum) -> new AgentSessionResponse(
                        rs.getLong("id"),
                        rs.getString("anchor_type"),
                        rs.getObject("anchor_id") == null ? null : rs.getLong("anchor_id"),
                        rs.getString("title"),
                        rs.getString("session_status"),
                        rs.getString("last_message_at"),
                        rs.getString("created_at")
                ),
                sessionId
        );
        if (sessions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        }
        return sessions.get(0);
    }

    private AgentSessionSummaryResponse mapSummary(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AgentSessionSummaryResponse(
                rs.getLong("id"),
                rs.getString("anchor_type"),
                rs.getObject("anchor_id") == null ? null : rs.getLong("anchor_id"),
                rs.getString("title"),
                rs.getString("session_status"),
                rs.getString("last_message_at"),
                rs.getString("last_assistant_summary"),
                rs.getString("created_at")
        );
    }
}
