import { http } from "./http";
import type {
  AgentAnchorType,
  AgentChatResponse,
  AgentNote,
  AgentSessionDetail,
  AgentSessionSummary,
  AgentToolCall,
  AgentTrace
} from "../types/agent";

type ApiResponse<T> = {
  code: number;
  message: string;
  data: T;
};

export const agentService = {
  async createSession(anchorType: AgentAnchorType, anchorId: number, title: string): Promise<AgentSessionSummary> {
    const response = await http.post<ApiResponse<AgentSessionSummary>>("/api/agent/sessions", {
      anchorType,
      anchorId,
      title
    });
    return response.data;
  },

  async listSessions(anchorType?: AgentAnchorType, anchorId?: number): Promise<AgentSessionSummary[]> {
    const response = await http.get<ApiResponse<AgentSessionSummary[]>>("/api/agent/sessions", {
      anchorType: anchorType ?? "",
      anchorId: anchorId ? String(anchorId) : ""
    });
    return response.data;
  },

  async getSessionDetail(sessionId: number): Promise<AgentSessionDetail> {
    const response = await http.get<ApiResponse<AgentSessionDetail>>(`/api/agent/sessions/${sessionId}`);
    return response.data;
  },

  async chat(sessionId: number, anchorType: AgentAnchorType, anchorId: number, message: string): Promise<AgentChatResponse> {
    const response = await http.post<ApiResponse<AgentChatResponse>>("/api/agent/chat", {
      sessionId,
      anchorType,
      anchorId,
      message
    });
    return response.data;
  },

  async streamChat(
    sessionId: number,
    anchorType: AgentAnchorType,
    anchorId: number,
    message: string,
    onEvent: (eventName: string, payload: unknown) => void
  ): Promise<void> {
    const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8081";
    const response = await fetch(new URL("/api/agent/chat/stream", API_BASE_URL).toString(), {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "text/event-stream",
        ...(localStorage.getItem("herbscript_token")
          ? { Authorization: `Bearer ${localStorage.getItem("herbscript_token")}` }
          : {})
      },
      body: JSON.stringify({
        sessionId,
        anchorType,
        anchorId,
        message
      })
    });

    if (!response.ok || !response.body) {
      throw new Error(`请求失败: ${response.status}`);
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder("utf-8");
    let buffer = "";

    while (true) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }

      buffer += decoder.decode(value, { stream: true });
      const chunks = buffer.split("\n\n");
      buffer = chunks.pop() ?? "";

      for (const chunk of chunks) {
        const lines = chunk.split("\n");
        let eventName = "message";
        let data = "";

        for (const line of lines) {
          if (line.startsWith("event:")) {
            eventName = line.slice(6).trim();
          } else if (line.startsWith("data:")) {
            data += line.slice(5).trim();
          }
        }

        if (!data) {
          continue;
        }

        try {
          onEvent(eventName, JSON.parse(data));
        } catch {
          onEvent(eventName, data);
        }
      }
    }
  },

  async listToolCalls(sessionId: number): Promise<AgentToolCall[]> {
    const response = await http.get<ApiResponse<AgentToolCall[]>>(`/api/agent/sessions/${sessionId}/tool-calls`);
    return response.data;
  },

  async listTraces(sessionId: number): Promise<AgentTrace[]> {
    const response = await http.get<ApiResponse<AgentTrace[]>>(`/api/agent/sessions/${sessionId}/traces`);
    return response.data;
  },

  async listNotes(anchorType: AgentAnchorType, anchorId: number): Promise<AgentNote[]> {
    const response = await http.get<ApiResponse<AgentNote[]>>("/api/agent/notes", {
      anchorType,
      anchorId: String(anchorId)
    });
    return response.data;
  },

  async saveNote(
    sessionId: number | null,
    anchorType: AgentAnchorType,
    anchorId: number,
    noteType: string,
    title: string,
    content: string
  ): Promise<AgentNote> {
    const response = await http.post<ApiResponse<AgentNote>>("/api/agent/notes", {
      sessionId,
      anchorType,
      anchorId,
      noteType,
      title,
      content
    });
    return response.data;
  },

  async deleteSession(sessionId: number): Promise<void> {
    await http.delete<ApiResponse<null>>(`/api/agent/sessions/${sessionId}`);
  }
};
