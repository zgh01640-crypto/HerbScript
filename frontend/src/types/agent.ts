export type AgentAnchorType = "patient" | "prescription";

export interface AgentStructuredResponse {
  summary: string;
  observations: string[];
  risks: string[];
  suggestions: string[];
  answerConfidence?: string | null;
  remainingUncertainties?: string[] | null;
}

export interface AgentMessage {
  id: number;
  role: "user" | "assistant" | string;
  content: string;
  structuredPayload?: AgentStructuredResponse | null;
  createdAt?: string | null;
}

export interface AgentSessionSummary {
  id: number;
  anchorType: AgentAnchorType;
  anchorId?: number | null;
  title: string;
  sessionStatus: string;
  lastMessageAt?: string | null;
  lastAssistantSummary?: string | null;
  createdAt: string;
}

export interface AgentContextSummary {
  anchorType: AgentAnchorType;
  anchorId: number;
  title: string;
  prescriptionNo?: string | null;
  dateLabel?: string | null;
  diagnosis?: string | null;
  doseCount?: number | null;
  prescriptionCount?: number | null;
  herbNames: string[];
}

export interface AgentSessionDetail {
  session: AgentSessionSummary;
  context: AgentContextSummary;
  messages: AgentMessage[];
}

export interface AgentToolCall {
  id: number;
  messageId?: number | null;
  toolName: string;
  toolLabel?: string | null;
  status: string;
  latencyMs?: number | null;
  inputJson?: string | null;
  outputJson?: string | null;
  createdAt: string;
}

export interface AgentTrace {
  id: number;
  messageId?: number | null;
  modelName?: string | null;
  promptTokens?: number | null;
  completionTokens?: number | null;
  totalTokens?: number | null;
  latencyMs?: number | null;
  tracePayload?: string | null;
  createdAt: string;
}

export interface AgentNote {
  id: number;
  sessionId?: number | null;
  anchorType: AgentAnchorType;
  anchorId: number;
  noteType: string;
  title: string;
  content: string;
  answerConfidence?: string | null;
  remainingUncertaintiesJson?: string | null;
  pinned: boolean;
  createdAt: string;
}

export interface AgentChatResponse {
  sessionId: number;
  message: AgentMessage;
  structured?: AgentStructuredResponse | null;
  toolCalls: AgentToolCall[];
}
