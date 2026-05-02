package com.herbscript.agent.service;

import com.herbscript.agent.dto.AgentNoteResponse;
import com.herbscript.agent.dto.AgentNoteSaveRequest;
import com.herbscript.agent.dto.AgentNoteUpdateRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentNoteService {

    private final JdbcTemplate jdbcTemplate;

    public AgentNoteService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public AgentNoteResponse save(Long userId, AgentNoteSaveRequest request) {
        jdbcTemplate.update(
                """
                INSERT INTO agent_note
                (session_id, anchor_type, anchor_id, note_type, title, content, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                request.sessionId(),
                request.anchorType(),
                request.anchorId(),
                request.noteType(),
                request.title().trim(),
                request.content().trim(),
                userId
        );
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return getById(id);
    }

    public List<AgentNoteResponse> list(String anchorType, Long anchorId) {
        return jdbcTemplate.query(
                """
                SELECT id, session_id, anchor_type, anchor_id, note_type, title, content,
                       DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at
                FROM agent_note
                WHERE deleted = 0 AND anchor_type = ? AND anchor_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT 10
                """,
                (rs, rowNum) -> new AgentNoteResponse(
                        rs.getLong("id"),
                        rs.getObject("session_id") == null ? null : rs.getLong("session_id"),
                        rs.getString("anchor_type"),
                        rs.getLong("anchor_id"),
                        rs.getString("note_type"),
                        rs.getString("title"),
                        rs.getString("content"),
                        rs.getString("created_at")
                ),
                anchorType,
                anchorId
        );
    }

    @Transactional
    public void delete(Long noteId) {
        int updated = jdbcTemplate.update(
                "UPDATE agent_note SET deleted = 1 WHERE id = ? AND deleted = 0",
                noteId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "智能体记录不存在");
        }
    }

    @Transactional
    public AgentNoteResponse updateTitle(Long noteId, AgentNoteUpdateRequest request) {
        int updated = jdbcTemplate.update(
                "UPDATE agent_note SET title = ? WHERE id = ? AND deleted = 0",
                request.title().trim(),
                noteId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "智能体记录不存在");
        }
        return getById(noteId);
    }

    private AgentNoteResponse getById(Long id) {
        return jdbcTemplate.queryForObject(
                """
                SELECT id, session_id, anchor_type, anchor_id, note_type, title, content,
                       DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at
                FROM agent_note
                WHERE id = ? AND deleted = 0
                """,
                (rs, rowNum) -> new AgentNoteResponse(
                        rs.getLong("id"),
                        rs.getObject("session_id") == null ? null : rs.getLong("session_id"),
                        rs.getString("anchor_type"),
                        rs.getLong("anchor_id"),
                        rs.getString("note_type"),
                        rs.getString("title"),
                        rs.getString("content"),
                        rs.getString("created_at")
                ),
                id
        );
    }
}
