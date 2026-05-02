package com.herbscript.agent.controller;

import com.herbscript.agent.dto.AgentChatRequest;
import com.herbscript.agent.dto.AgentChatResponse;
import com.herbscript.agent.dto.AgentNoteResponse;
import com.herbscript.agent.dto.AgentNoteSaveRequest;
import com.herbscript.agent.dto.AgentNoteUpdateRequest;
import com.herbscript.agent.dto.AgentSessionCreateRequest;
import com.herbscript.agent.dto.AgentSessionDetailResponse;
import com.herbscript.agent.dto.AgentSessionResponse;
import com.herbscript.agent.dto.AgentSessionSummaryResponse;
import com.herbscript.agent.dto.AgentToolCallResponse;
import com.herbscript.agent.dto.AgentTraceResponse;
import com.herbscript.agent.orchestrator.AgentOrchestrator;
import com.herbscript.agent.service.AgentNoteService;
import com.herbscript.agent.service.AgentSessionService;
import com.herbscript.agent.service.AgentTraceService;
import com.herbscript.common.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentSessionService sessionService;
    private final AgentOrchestrator agentOrchestrator;
    private final AgentTraceService traceService;
    private final AgentNoteService noteService;

    public AgentController(
            AgentSessionService sessionService,
            AgentOrchestrator agentOrchestrator,
            AgentTraceService traceService,
            AgentNoteService noteService
    ) {
        this.sessionService = sessionService;
        this.agentOrchestrator = agentOrchestrator;
        this.traceService = traceService;
        this.noteService = noteService;
    }

    @PostMapping("/sessions")
    public ApiResponse<AgentSessionResponse> createSession(@Valid @RequestBody AgentSessionCreateRequest request) {
        return ApiResponse.success(sessionService.create(1L, request));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<AgentSessionSummaryResponse>> listSessions(
            @RequestParam(required = false) String anchorType,
            @RequestParam(required = false) Long anchorId
    ) {
        return ApiResponse.success(sessionService.list(anchorType, anchorId));
    }

    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<AgentSessionDetailResponse> getSession(@PathVariable Long sessionId) {
        return ApiResponse.success(sessionService.detail(sessionId));
    }

    @PostMapping("/chat")
    public ApiResponse<AgentChatResponse> chat(@Valid @RequestBody AgentChatRequest request) {
        sessionService.getSession(request.sessionId());
        return ApiResponse.success(agentOrchestrator.chat(request));
    }

    @PostMapping(path = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody AgentChatRequest request) {
        sessionService.getSession(request.sessionId());

        SseEmitter emitter = new SseEmitter(0L);
        CompletableFuture.runAsync(() -> {
            try {
                agentOrchestrator.chatStream(request, (eventName, payload) -> {
                    try {
                        emitter.send(SseEmitter.event().name(eventName).data(payload));
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                });
                emitter.complete();
            } catch (Exception ex) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(Map.of("message", ex.getMessage())));
                } catch (Exception ignored) {
                    // ignore secondary send failure
                }
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    @GetMapping("/sessions/{sessionId}/tool-calls")
    public ApiResponse<List<AgentToolCallResponse>> listToolCalls(@PathVariable Long sessionId) {
        sessionService.getSession(sessionId);
        return ApiResponse.success(traceService.listToolCalls(sessionId));
    }

    @GetMapping("/sessions/{sessionId}/traces")
    public ApiResponse<List<AgentTraceResponse>> listTraces(@PathVariable Long sessionId) {
        sessionService.getSession(sessionId);
        return ApiResponse.success(traceService.listTraces(sessionId));
    }

    @GetMapping("/notes")
    public ApiResponse<List<AgentNoteResponse>> listNotes(
            @RequestParam String anchorType,
            @RequestParam Long anchorId
    ) {
        return ApiResponse.success(noteService.list(anchorType, anchorId));
    }

    @PostMapping("/notes")
    public ApiResponse<AgentNoteResponse> saveNote(@Valid @RequestBody AgentNoteSaveRequest request) {
        return ApiResponse.success(noteService.save(1L, request));
    }

    @PutMapping("/notes/{noteId}")
    public ApiResponse<AgentNoteResponse> updateNoteTitle(
            @PathVariable Long noteId,
            @Valid @RequestBody AgentNoteUpdateRequest request
    ) {
        return ApiResponse.success(noteService.updateTitle(noteId, request));
    }

    @DeleteMapping("/notes/{noteId}")
    public ApiResponse<Void> deleteNote(@PathVariable Long noteId) {
        noteService.delete(noteId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable Long sessionId) {
        sessionService.delete(sessionId);
        return ApiResponse.success(null);
    }
}
