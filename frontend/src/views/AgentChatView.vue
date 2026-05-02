<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import AppShell from "../components/AppShell.vue";
import SectionCard from "../components/SectionCard.vue";
import { agentService } from "../services/agentService";
import type {
  AgentAnchorType,
  AgentContextSummary,
  AgentMessage,
  AgentNote,
  AgentStructuredResponse,
  AgentSessionSummary,
  AgentToolCall,
  AgentTrace
} from "../types/agent";

type AgentMessageView = AgentMessage & {
  pending?: boolean;
  streaming?: boolean;
  structuredVisible?: boolean;
};

type AgentTimelineItem = {
  id: string;
  label: string;
  detail?: string;
  status: "running" | "success" | "error";
  createdAt?: string;
};

const route = useRoute();
const router = useRouter();

const loading = ref(false);
const sessionLoading = ref(false);
const sending = ref(false);
const sessions = ref<AgentSessionSummary[]>([]);
const activeSession = ref<AgentSessionSummary | null>(null);
const contextSummary = ref<AgentContextSummary | null>(null);
const messages = ref<AgentMessageView[]>([]);
const toolCalls = ref<AgentToolCall[]>([]);
const liveToolCalls = ref<AgentToolCall[]>([]);
const traces = ref<AgentTrace[]>([]);
const notes = ref<AgentNote[]>([]);
const promptInput = ref("");
const outputDraft = ref("");
const streamingCursorId = ref<number | null>(null);
const retryPrompt = ref("");
const MIN_THINKING_DURATION = 900;
const thinkingStageIndex = ref(0);
const thinkingTimer = ref<number | null>(null);
const contentChunkQueue = ref<string[]>([]);
const chunkFlushRunning = ref(false);
const expandedToolCallIds = ref<number[]>([]);
const liveTimeline = ref<AgentTimelineItem[]>([]);

const anchorType = computed<AgentAnchorType | null>(() => {
  const value = route.query.anchorType;
  return value === "patient" || value === "prescription" ? value : null;
});
const anchorId = computed<number | null>(() => {
  const raw = route.query.anchorId;
  const value = Number(raw);
  return Number.isFinite(value) && value > 0 ? value : null;
});
const sessionIdFromRoute = computed<number | null>(() => {
  const raw = route.query.sessionId;
  const value = Number(raw);
  return Number.isFinite(value) && value > 0 ? value : null;
});
const pageReady = computed(() => Boolean(anchorType.value && anchorId.value));
const emptyState = computed(() => !pageReady.value);
const placeholderTitle = computed(() => {
  if (anchorType.value === "patient") {
    return "患者智能体";
  }
  if (anchorType.value === "prescription") {
    return "处方智能体";
  }
  return "智能体工作台";
});
const quickPrompts = computed(() => {
  if (anchorType.value === "patient") {
    return [
      "请总结这个患者最近的处方情况",
      "请分析这个患者的常用药味",
      "请生成一段适合随访沟通的总结",
      "请指出当前最需要关注的风险点"
    ];
  }

  if (anchorType.value === "prescription") {
    return [
      "请分析这张处方的主治方向",
      "请总结这张处方的药味配伍特点",
      "请生成适合归档的结构化处方总结",
      "请指出这张处方需要重点复核的地方"
    ];
  }

  return [];
});
const thinkingStages = computed(() => {
  if (anchorType.value === "patient") {
    return [
      "正在读取患者主档与历史处方",
      "正在整理高频药味与历史趋势",
      "正在生成结构化分析结论"
    ];
  }

  return [
    "正在读取处方详情与药味明细",
    "正在整理历史处方与差异信息",
    "正在生成结构化分析结论"
  ];
});
const currentThinkingStage = computed(() => thinkingStages.value[thinkingStageIndex.value] ?? "智能体正在生成回复");
const latestTrace = computed(() => traces.value[0] ?? null);
const displayToolCalls = computed(() => (sending.value && liveToolCalls.value.length ? liveToolCalls.value : toolCalls.value));
const displayTimeline = computed(() => {
  if (sending.value && liveTimeline.value.length) {
    return liveTimeline.value;
  }

  return buildTraceTimelineFromState();
});
const latestTraceMode = computed(() => {
  const payload = latestTrace.value?.tracePayload;
  if (!payload) {
    return "";
  }
  try {
    const parsed = JSON.parse(payload) as { mode?: string };
    return parsed.mode === "fallback_skill" ? "技能回退" : parsed.mode === "llm" ? "真实模型" : "";
  } catch {
    return "";
  }
});
const draftTitle = computed(() => (anchorType.value === "patient" ? "随访记录草稿" : "处方摘要草稿"));
const draftNoteType = computed(() => (anchorType.value === "patient" ? "followup_note" : "prescription_summary"));
const draftSaveLabel = computed(() => (anchorType.value === "patient" ? "保存为随访记录" : "保存为处方摘要"));
const visibleExpandedToolCallIds = computed(() => {
  const currentIds = new Set(displayToolCalls.value.map((item) => item.id));
  return expandedToolCallIds.value.filter((id) => currentIds.has(id));
});
const buildDefaultTitle = () => {
  const title = String(route.query.title || "").trim();
  if (title) {
    return title;
  }
  if (anchorType.value === "patient") {
    return `患者会话 ${anchorId.value}`;
  }
  if (anchorType.value === "prescription") {
    return `处方分析 ${anchorId.value}`;
  }
  return "智能体会话";
};

const syncRouteSession = async (sessionId: number) => {
  if (sessionIdFromRoute.value === sessionId) {
    return;
  }

  await router.replace({
    path: "/agent",
    query: {
      ...route.query,
      sessionId: String(sessionId)
    }
  });
};

const hydrateSession = async (sessionId: number) => {
  sessionLoading.value = true;
  try {
    await refreshSessionState(sessionId);
    await syncRouteSession(sessionId);
  } finally {
    sessionLoading.value = false;
  }
};

const refreshSessionState = async (sessionId: number) => {
  const notePromise = anchorType.value && anchorId.value
    ? agentService.listNotes(anchorType.value, anchorId.value)
    : Promise.resolve([]);
  const [detail, sessionToolCalls, sessionTraces, sessionNotes] = await Promise.all([
    agentService.getSessionDetail(sessionId),
    agentService.listToolCalls(sessionId),
    agentService.listTraces(sessionId),
    notePromise
  ]);

  activeSession.value = detail.session;
  contextSummary.value = detail.context;
  messages.value = detail.messages.map((message) => ({
    ...normalizeStructuredMessage(message),
    structuredVisible: true
  }));
  toolCalls.value = sessionToolCalls;
  liveToolCalls.value = [];
  expandedToolCallIds.value = [];
  traces.value = sessionTraces;
  notes.value = sessionNotes;
};

const createAndOpenSession = async () => {
  if (!anchorType.value || !anchorId.value) {
    return;
  }
  const session = await agentService.createSession(anchorType.value, anchorId.value, buildDefaultTitle());
  sessions.value = [session, ...sessions.value.filter((item) => item.id !== session.id)];
  await hydrateSession(session.id);
};

const loadSessions = async () => {
  if (!anchorType.value || !anchorId.value) {
    sessions.value = [];
    activeSession.value = null;
    contextSummary.value = null;
    messages.value = [];
    toolCalls.value = [];
    notes.value = [];
    return;
  }

  loading.value = true;
  try {
    sessions.value = await agentService.listSessions(anchorType.value, anchorId.value);

    const targetSessionId = sessionIdFromRoute.value ?? sessions.value[0]?.id;
    if (targetSessionId) {
      await hydrateSession(targetSessionId);
    } else {
      await createAndOpenSession();
    }
  } finally {
    loading.value = false;
  }
};

const openSession = async (session: AgentSessionSummary) => {
  await hydrateSession(session.id);
};

const startNewSession = async () => {
  await createAndOpenSession();
};

const sendMessage = async (preset?: string) => {
  const message = (preset ?? promptInput.value).trim();
  if (!message || !anchorType.value || !anchorId.value) {
    return;
  }

  if (!activeSession.value) {
    await createAndOpenSession();
  }

  if (!activeSession.value) {
    return;
  }

  const optimisticMessage: AgentMessageView = {
    id: Date.now(),
    role: "user",
    content: message,
    createdAt: new Date().toISOString()
  };

  const pendingAssistantMessage: AgentMessageView = {
    id: Date.now() + 1,
    role: "assistant",
    content: "",
    createdAt: new Date().toISOString(),
    pending: true
  };

  messages.value = [...messages.value, optimisticMessage, pendingAssistantMessage];
  promptInput.value = "";
  sending.value = true;
  streamingCursorId.value = pendingAssistantMessage.id;
  contentChunkQueue.value = [];
  chunkFlushRunning.value = false;
  liveTimeline.value = [];
  startThinkingStages();
  liveToolCalls.value = [];
  const requestStartAt = Date.now();

  try {
    let accumulatedContent = "";
    let latestStructured: AgentStructuredResponse | null = null;

    await agentService.streamChat(
      activeSession.value.id,
      anchorType.value,
      anchorId.value,
      message,
      (eventName, payload) => {
        if (eventName === "tool_start" && payload && typeof payload === "object") {
          const tool = payload as AgentToolCall;
          liveToolCalls.value = [...liveToolCalls.value, tool];
          pushOrUpdateTimeline({
            id: `tool-${tool.toolName}`,
            label: tool.toolLabel || tool.toolName,
            detail: "正在调用工具",
            status: "running",
            createdAt: tool.createdAt
          });
          return;
        }

        if (eventName === "tool_done" && payload && typeof payload === "object") {
          const tool = payload as AgentToolCall;
          liveToolCalls.value = [
            ...liveToolCalls.value.filter((item) => item.toolName !== tool.toolName || item.status !== "running"),
            tool
          ];
          pushOrUpdateTimeline({
            id: `tool-${tool.toolName}`,
            label: tool.toolLabel || tool.toolName,
            detail: tool.latencyMs ? `${tool.latencyMs}ms` : "调用完成",
            status: "success",
            createdAt: tool.createdAt
          });
          return;
        }

        if (eventName === "error" && payload && typeof payload === "object") {
          const errorMessage = String((payload as { message?: string }).message ?? "智能体执行失败");
          pushOrUpdateTimeline({
            id: "error",
            label: "执行失败",
            detail: errorMessage,
            status: "error",
            createdAt: new Date().toISOString().slice(0, 19).replace("T", " ")
          });
          return;
        }

        if (eventName === "model_start" && payload && typeof payload === "object") {
          const model = payload as { modelName?: string; mode?: string };
          pushOrUpdateTimeline({
            id: "model-start",
            label: "模型生成回复",
            detail: model.modelName || "正在调用模型",
            status: "running",
            createdAt: new Date().toISOString().slice(0, 19).replace("T", " ")
          });
          return;
        }

        if (eventName === "message_chunk" && payload && typeof payload === "object") {
          const chunk = String((payload as { contentChunk?: string }).contentChunk ?? "");
          enqueueContentChunk(chunk, pendingAssistantMessage.id, (nextValue) => {
            accumulatedContent = nextValue;
          });
          return;
        }

        if (eventName === "structured" && payload && typeof payload === "object") {
          latestStructured = payload as AgentStructuredResponse;
          messages.value = messages.value.map((item) =>
            item.id === pendingAssistantMessage.id
              ? {
                  ...item,
                  pending: false,
                  streaming: false,
                  content: accumulatedContent,
                  structuredPayload: latestStructured,
                  structuredVisible: true
                }
              : item
          );
          return;
        }

        if (eventName === "trace" && payload && typeof payload === "object") {
          const trace = payload as AgentTrace & { mode?: string };
          traces.value = [
            {
              ...trace,
              tracePayload: JSON.stringify({ mode: trace.mode ?? "llm" }),
              createdAt: new Date().toISOString().slice(0, 19).replace("T", " ")
            },
            ...traces.value
          ];
          pushOrUpdateTimeline({
            id: "model-start",
            label: trace.mode === "fallback_skill" ? "技能回退生成" : "模型生成回复",
            detail: `${trace.modelName || "模型"} · ${trace.totalTokens ?? 0} tokens · ${Math.round((trace.latencyMs ?? 0) / 1000)}s`,
            status: "success",
            createdAt: new Date().toISOString().slice(0, 19).replace("T", " ")
          });
          return;
        }

        if (eventName === "complete") {
          void waitForChunkQueueDrain(pendingAssistantMessage.id, (nextValue) => {
            accumulatedContent = nextValue;
          }).then(() => {
            pushOrUpdateTimeline({
              id: "complete",
              label: "回复完成",
              detail: "本轮结构化结果已生成",
              status: "success",
              createdAt: new Date().toISOString().slice(0, 19).replace("T", " ")
            });
            messages.value = messages.value.map((item) =>
              item.id === pendingAssistantMessage.id
                ? {
                    ...item,
                    pending: false,
                    streaming: false,
                    content: accumulatedContent,
                    structuredPayload: latestStructured,
                    structuredVisible: true
                  }
                : item
            );
          });
        }
      }
    );

    const elapsed = Date.now() - requestStartAt;
    if (elapsed < MIN_THINKING_DURATION) {
      await wait(MIN_THINKING_DURATION - elapsed);
    }
    expandedToolCallIds.value = [];
    sessions.value = await agentService.listSessions(anchorType.value, anchorId.value);
    activeSession.value = sessions.value.find((item) => item.id === activeSession.value?.id) ?? activeSession.value;
    void refreshSessionState(activeSession.value.id);
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : "智能体回复失败";
    retryPrompt.value = message;
    contentChunkQueue.value = [];
    chunkFlushRunning.value = false;
    liveToolCalls.value = liveToolCalls.value.map((item) => (
      item.status === "running"
        ? {
            ...item,
            status: "error"
          }
        : item
    ));
    pushOrUpdateTimeline({
      id: "error",
      label: "执行失败",
      detail: errorMessage,
      status: "error",
      createdAt: new Date().toISOString().slice(0, 19).replace("T", " ")
    });
    messages.value = messages.value.map((item) =>
      item.id === pendingAssistantMessage.id
        ? {
            ...item,
            pending: false,
            streaming: false,
            content: errorMessage
          }
        : item
    );
    ElMessage.error(errorMessage);
  } finally {
    sending.value = false;
    streamingCursorId.value = null;
    stopThinkingStages();
  }
};

const wait = (ms: number) => new Promise((resolve) => window.setTimeout(resolve, ms));

const pushOrUpdateTimeline = (item: AgentTimelineItem) => {
  const index = liveTimeline.value.findIndex((entry) => entry.id === item.id);
  if (index === -1) {
    liveTimeline.value = [...liveTimeline.value, item];
    return;
  }

  liveTimeline.value = liveTimeline.value.map((entry, currentIndex) => (
    currentIndex === index ? { ...entry, ...item } : entry
  ));
};

const buildTraceTimelineFromState = () => {
  const timeline: AgentTimelineItem[] = toolCalls.value
    .slice()
    .reverse()
    .map((call) => ({
      id: `tool-${call.id}`,
      label: call.toolLabel || call.toolName,
      detail: call.latencyMs ? `${call.latencyMs}ms` : call.status,
      status: call.status === "running" ? "running" : call.status === "error" ? "error" : "success",
      createdAt: call.createdAt
    }));

  if (latestTrace.value) {
    timeline.push({
      id: `trace-${latestTrace.value.id}`,
      label: latestTraceMode.value || "模型生成回复",
      detail: `${latestTrace.value.modelName || "模型"} · ${latestTrace.value.totalTokens ?? 0} tokens · ${Math.round((latestTrace.value.latencyMs ?? 0) / 1000)}s`,
      status: "success",
      createdAt: latestTrace.value.createdAt
    });
  }

  return timeline;
};

const sliceChunk = (chunk: string) => {
  const pieces: string[] = [];
  const normalized = chunk.replace(/\s+/g, " ").trim();
  if (!normalized) {
    return pieces;
  }

  const segments = normalized.split(/(?<=[，。！？；：])/).filter(Boolean);
  for (const segment of segments) {
    if (segment.length <= 16) {
      pieces.push(segment);
      continue;
    }

    for (let index = 0; index < segment.length; index += 12) {
      pieces.push(segment.slice(index, index + 12));
    }
  }
  return pieces;
};

const updatePendingAssistantMessage = (messageId: number, content: string, streaming = true) => {
  messages.value = messages.value.map((item) =>
    item.id === messageId
      ? {
          ...item,
          pending: false,
          streaming,
          content
        }
      : item
  );
};

const flushChunkQueue = async (messageId: number, onUpdate: (value: string) => void) => {
  if (chunkFlushRunning.value) {
    return;
  }

  chunkFlushRunning.value = true;
  try {
    let content = messages.value.find((item) => item.id === messageId)?.content ?? "";
    while (contentChunkQueue.value.length) {
      const next = contentChunkQueue.value.shift();
      if (!next) {
        continue;
      }
      content += next;
      onUpdate(content);
      updatePendingAssistantMessage(messageId, content, true);
      await wait(next.length <= 4 ? 28 : next.length <= 10 ? 48 : 72);
    }
  } finally {
    chunkFlushRunning.value = false;
  }
};

const enqueueContentChunk = (chunk: string, messageId: number, onUpdate: (value: string) => void) => {
  contentChunkQueue.value.push(...sliceChunk(chunk));
  void flushChunkQueue(messageId, onUpdate);
};

const waitForChunkQueueDrain = async (messageId: number, onUpdate: (value: string) => void) => {
  while (contentChunkQueue.value.length || chunkFlushRunning.value) {
    await flushChunkQueue(messageId, onUpdate);
    if (contentChunkQueue.value.length || chunkFlushRunning.value) {
      await wait(20);
    }
  }
};

const startThinkingStages = () => {
  stopThinkingStages();
  thinkingStageIndex.value = 0;
  thinkingTimer.value = window.setInterval(() => {
    thinkingStageIndex.value = (thinkingStageIndex.value + 1) % thinkingStages.value.length;
  }, 1100);
};

const stopThinkingStages = () => {
  if (thinkingTimer.value !== null) {
    window.clearInterval(thinkingTimer.value);
    thinkingTimer.value = null;
  }
};

const toggleToolCall = (id: number) => {
  if (expandedToolCallIds.value.includes(id)) {
    expandedToolCallIds.value = expandedToolCallIds.value.filter((item) => item !== id);
    return;
  }
  expandedToolCallIds.value = [...expandedToolCallIds.value, id];
};

const formatJsonPreview = (value: unknown) => {
  if (!value) {
    return "暂无";
  }

  if (typeof value === "string") {
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  }

  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
};

const parseStructuredPayload = (value: unknown): AgentStructuredResponse | null => {
  if (!value) {
    return null;
  }

  if (typeof value === "string") {
    try {
      return JSON.parse(value) as AgentStructuredResponse;
    } catch {
      return null;
    }
  }

  if (typeof value === "object") {
    return value as AgentStructuredResponse;
  }

  return null;
};

const normalizeStructuredMessage = (message: AgentMessage): AgentMessage => ({
  ...message,
  structuredPayload: parseStructuredPayload(message.structuredPayload)
});

const buildStructuredDraft = (message: AgentMessageView) => {
  const structured = message.structuredPayload;
  if (!structured) {
    return message.content;
  }

  const sections = [
    `摘要：${structured.summary}`,
    structured.observations.length ? `观察：\n- ${structured.observations.join("\n- ")}` : "",
    structured.risks.length ? `风险提醒：\n- ${structured.risks.join("\n- ")}` : "",
    structured.suggestions.length ? `建议：\n- ${structured.suggestions.join("\n- ")}` : ""
  ].filter(Boolean);

  return sections.join("\n\n");
};

const appendToDraft = (message: AgentMessageView) => {
  const block = buildStructuredDraft(message).trim();
  if (!block) {
    return;
  }
  outputDraft.value = outputDraft.value.trim()
    ? `${outputDraft.value.trim()}\n\n${block}`
    : block;
  ElMessage.success("已插入草稿区");
};

const replaceDraft = (message: AgentMessageView) => {
  outputDraft.value = buildStructuredDraft(message).trim();
  ElMessage.success("已生成新的草稿");
};

const retryLastPrompt = async () => {
  if (!retryPrompt.value.trim()) {
    ElMessage.warning("当前没有可重试的问题");
    return;
  }
  await sendMessage(retryPrompt.value);
};

const copyDraft = async () => {
  if (!outputDraft.value.trim()) {
    ElMessage.warning("当前草稿区还没有内容");
    return;
  }

  try {
    await navigator.clipboard.writeText(outputDraft.value);
    ElMessage.success("草稿已复制");
  } catch {
    ElMessage.error("复制失败，请手动复制");
  }
};

const saveDraftNote = async () => {
  if (!anchorType.value || !anchorId.value || !outputDraft.value.trim()) {
    ElMessage.warning("当前草稿区还没有可保存的内容");
    return;
  }

  const timeLabel = new Date().toLocaleString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).replace(/\//g, "-");

  const titlePrefix = anchorType.value === "patient" ? "智能体随访记录" : "智能体处方摘要";
  const note = await agentService.saveNote(
    activeSession.value?.id ?? null,
    anchorType.value,
    anchorId.value,
    draftNoteType.value,
    `${titlePrefix} ${timeLabel}`,
    outputDraft.value
  );
  notes.value = [note, ...notes.value.filter((item) => item.id !== note.id)];
  ElMessage.success("草稿已保存进系统");
};

const removeSavedNote = async (note: AgentNote) => {
  try {
    await ElMessageBox.confirm(`确认删除记录「${note.title}」吗？`, "删除智能体记录", { type: "warning" });
    await agentService.deleteNote(note.id);
    notes.value = notes.value.filter((item) => item.id !== note.id);
    ElMessage.success("记录已删除");
  } catch {
    // noop
  }
};

const renameSavedNote = async (note: AgentNote) => {
  try {
    const { value } = await ElMessageBox.prompt("请输入新的记录标题", "重命名智能体记录", {
      inputValue: note.title,
      inputPlaceholder: "请输入标题"
    });
    const updated = await agentService.updateNoteTitle(note.id, value);
    notes.value = notes.value.map((item) => (item.id === note.id ? updated : item));
    ElMessage.success("标题已更新");
  } catch {
    // noop
  }
};

const deleteSession = async (session: AgentSessionSummary) => {
  try {
    await ElMessageBox.confirm(`确认删除会话「${session.title}」吗？`, "删除会话", { type: "warning" });
    await agentService.deleteSession(session.id);
    if (activeSession.value?.id === session.id) {
      activeSession.value = null;
      contextSummary.value = null;
      messages.value = [];
      toolCalls.value = [];
    }
    await loadSessions();
    ElMessage.success("会话已删除");
  } catch {
    // noop
  }
};

watch([anchorType, anchorId], () => {
  void loadSessions();
}, { immediate: true });

watch(sessionIdFromRoute, (value) => {
  if (value && value !== activeSession.value?.id) {
    void hydrateSession(value);
  }
});

onMounted(() => {
  if (!route.query.anchorType && route.query.from === "patient") {
    void router.replace("/patients");
  }
});

onUnmounted(() => {
  stopThinkingStages();
});
</script>

<template>
  <AppShell>
    <div class="agent-page" :class="{ 'agent-page-empty': emptyState }">
      <template v-if="pageReady">
        <aside class="agent-session-pane" v-loading="loading">
          <SectionCard title="会话列表" subtitle="围绕当前患者或处方持续追问" inline-subtitle>
            <template #extra>
              <el-button type="primary" plain @click="startNewSession">新建会话</el-button>
            </template>

            <div class="agent-session-list">
              <button
                v-for="session in sessions"
                :key="session.id"
                type="button"
                class="agent-session-item"
                :class="{ active: activeSession?.id === session.id }"
                @click="openSession(session)"
              >
                <div class="agent-session-main">
                  <strong>{{ session.title }}</strong>
                  <span>{{ session.lastAssistantSummary || "等待智能体回答" }}</span>
                </div>
                <div class="agent-session-meta">
                  <small>{{ session.lastMessageAt || session.createdAt }}</small>
                  <el-button link type="danger" @click.stop="deleteSession(session)">删除</el-button>
                </div>
              </button>
            </div>
          </SectionCard>
        </aside>

        <section class="agent-chat-pane">
          <SectionCard
            :title="contextSummary?.title || placeholderTitle"
            subtitle="基于当前上下文进行问答、分析与总结"
            inline-subtitle
          >
            <template #extra>
              <div class="agent-anchor-chip">{{ anchorType === "patient" ? "患者上下文" : "处方上下文" }}</div>
            </template>

            <div v-loading="sessionLoading" class="agent-chat-body">
              <div class="agent-quick-prompts">
                <button
                  v-for="prompt in quickPrompts"
                  :key="prompt"
                  type="button"
                  class="agent-quick-prompt"
                  @click="sendMessage(prompt)"
                >
                  {{ prompt }}
                </button>
              </div>

              <div class="agent-message-list">
                <div v-if="messages.length === 0" class="agent-chat-empty">
                  <strong>当前会话还没有消息</strong>
                  <span>你可以直接提问，或点击上面的快捷问题开始。</span>
                </div>

                <div
                  v-for="message in messages"
                  :key="message.id"
                  class="agent-message"
                  :class="message.role === 'user' ? 'user' : 'assistant'"
                >
                  <div class="agent-message-role">{{ message.role === "user" ? "你" : "智能体" }}</div>
                  <div class="agent-message-bubble">
                    <div v-if="message.pending" class="agent-thinking">
                      <span class="agent-thinking-dot" />
                      <span class="agent-thinking-dot" />
                      <span class="agent-thinking-dot" />
                      <strong>{{ currentThinkingStage }}</strong>
                    </div>
                    <p v-else>
                      {{ message.content }}
                      <span v-if="message.streaming && streamingCursorId === message.id" class="agent-stream-cursor" />
                    </p>
                    <div v-if="message.structuredPayload && message.structuredVisible" class="agent-structured-block">
                      <div class="agent-structured-actions">
                        <el-button size="small" @click="appendToDraft(message)">插入草稿</el-button>
                        <el-button size="small" type="primary" plain @click="replaceDraft(message)">替换草稿</el-button>
                      </div>
                      <div class="agent-structured-card">
                        <span>摘要</span>
                        <strong>{{ message.structuredPayload.summary }}</strong>
                      </div>
                      <div v-if="message.structuredPayload.observations.length" class="agent-structured-card">
                        <span>观察</span>
                        <ul>
                          <li v-for="item in message.structuredPayload.observations" :key="item">{{ item }}</li>
                        </ul>
                      </div>
                      <div v-if="message.structuredPayload.risks.length" class="agent-structured-card warning">
                        <span>风险提醒</span>
                        <ul>
                          <li v-for="item in message.structuredPayload.risks" :key="item">{{ item }}</li>
                        </ul>
                      </div>
                      <div v-if="message.structuredPayload.suggestions.length" class="agent-structured-card">
                        <span>建议</span>
                        <ul>
                          <li v-for="item in message.structuredPayload.suggestions" :key="item">{{ item }}</li>
                        </ul>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <div class="agent-composer">
                <el-input
                  v-model="promptInput"
                  type="textarea"
                  :rows="3"
                  resize="none"
                  placeholder="请输入你想让智能体分析的问题"
                  @keydown.enter.prevent="!$event.shiftKey && sendMessage()"
                />
              <div class="agent-composer-actions">
                <span>Enter 发送，Shift + Enter 换行</span>
                <div class="agent-composer-buttons">
                  <el-button v-if="retryPrompt" plain @click="retryLastPrompt">重试上次问题</el-button>
                  <el-button type="primary" :loading="sending" @click="sendMessage()">发送</el-button>
                </div>
              </div>
              </div>
            </div>
          </SectionCard>
        </section>

        <aside class="agent-context-pane">
          <SectionCard title="上下文摘要" subtitle="当前智能体会话绑定的数据锚点">
            <div v-if="contextSummary" class="agent-context-grid">
              <div><span>对象名称</span><strong>{{ contextSummary.title }}</strong></div>
              <div v-if="contextSummary.prescriptionNo"><span>处方编号</span><strong class="mono">{{ contextSummary.prescriptionNo }}</strong></div>
              <div v-if="contextSummary.dateLabel"><span>日期</span><strong>{{ contextSummary.dateLabel }}</strong></div>
              <div v-if="contextSummary.diagnosis"><span>诊断</span><strong>{{ contextSummary.diagnosis }}</strong></div>
              <div v-if="contextSummary.doseCount"><span>剂数</span><strong>{{ contextSummary.doseCount }}剂</strong></div>
              <div v-if="contextSummary.prescriptionCount !== null && contextSummary.prescriptionCount !== undefined">
                <span>历史处方</span><strong>{{ contextSummary.prescriptionCount }} 张</strong>
              </div>
            </div>
            <div v-if="contextSummary?.herbNames.length" class="agent-herb-tags">
              <span v-for="name in contextSummary.herbNames" :key="name">{{ name }}</span>
            </div>
          </SectionCard>

          <SectionCard :title="draftTitle" subtitle="将智能体结论沉淀成可复用的业务草稿">
            <div class="agent-draft-pane">
              <el-input
                v-model="outputDraft"
                type="textarea"
                :rows="10"
                resize="none"
                placeholder="点击左侧回答中的“插入草稿”或“替换草稿”，在这里沉淀结果。"
              />
              <div class="agent-draft-actions">
                <el-button @click="outputDraft = ''">清空草稿</el-button>
                <div class="agent-draft-action-group">
                  <el-button plain @click="copyDraft">复制草稿</el-button>
                  <el-button type="primary" @click="saveDraftNote">{{ draftSaveLabel }}</el-button>
                </div>
              </div>
              <div v-if="notes.length" class="agent-note-list">
                <div class="agent-note-list-head">
                  <strong>最近保存记录</strong>
                  <span>{{ notes.length }} 条</span>
                </div>
                <div
                  v-for="note in notes"
                  :key="note.id"
                  class="agent-note-item"
                >
                  <div class="agent-note-meta">
                    <div class="agent-note-meta-main">
                      <strong>{{ note.title }}</strong>
                      <span>{{ note.createdAt }}</span>
                    </div>
                    <div class="agent-note-actions">
                      <el-button link @click="renameSavedNote(note)">重命名</el-button>
                      <el-button link type="danger" @click="removeSavedNote(note)">删除</el-button>
                    </div>
                  </div>
                  <p>{{ note.content }}</p>
                </div>
              </div>
            </div>
          </SectionCard>

          <SectionCard title="工具轨迹" subtitle="最近一次会话调用的工具">
            <div v-if="latestTrace" class="agent-trace-banner">
              <span>{{ latestTraceMode || "模型轨迹" }}</span>
              <strong>{{ latestTrace.modelName || "未记录模型" }}</strong>
              <small>
                {{ latestTrace.totalTokens ?? 0 }} tokens
                <template v-if="latestTrace.latencyMs"> · {{ Math.round(latestTrace.latencyMs / 1000) }}s</template>
              </small>
            </div>
            <div v-if="displayTimeline.length" class="agent-timeline">
              <div
                v-for="item in displayTimeline"
                :key="item.id"
                class="agent-timeline-item"
                :class="item.status"
              >
                <div class="agent-timeline-dot" />
                <div class="agent-timeline-body">
                  <strong>{{ item.label }}</strong>
                  <span v-if="item.detail">{{ item.detail }}</span>
                  <small v-if="item.createdAt">{{ item.createdAt }}</small>
                </div>
              </div>
            </div>
            <div v-if="displayToolCalls.length" class="agent-tool-list">
              <div
                v-for="call in displayToolCalls"
                :key="call.id"
                class="agent-tool-item"
                :class="{
                  running: call.status === 'running',
                  success: call.status === 'success',
                  error: call.status === 'error'
                }"
              >
                <div class="agent-tool-line" />
                <div class="agent-tool-dot" />
                <div class="agent-tool-head" @click="toggleToolCall(call.id)">
                  <div>
                    <strong>{{ call.toolLabel || call.toolName }}</strong>
                    <span>{{ call.createdAt }}</span>
                  </div>
                  <small>{{ visibleExpandedToolCallIds.includes(call.id) ? "收起" : "展开" }}</small>
                </div>
                <small>{{ call.status === "running" ? "执行中" : call.status }}<template v-if="call.latencyMs"> · {{ call.latencyMs }}ms</template></small>
                <div v-if="visibleExpandedToolCallIds.includes(call.id)" class="agent-tool-detail">
                  <div>
                    <span>输入</span>
                    <pre>{{ formatJsonPreview(call.inputJson) }}</pre>
                  </div>
                  <div>
                    <span>输出</span>
                    <pre>{{ formatJsonPreview(call.outputJson) }}</pre>
                  </div>
                </div>
              </div>
            </div>
            <div v-else class="agent-side-empty">本轮还没有工具调用记录</div>
          </SectionCard>
        </aside>
      </template>

      <div v-else class="agent-empty-wrap">
        <SectionCard title="智能体工作台" subtitle="请从患者详情页或处方详情页进入，以自动带入上下文">
          <div class="agent-entry-actions">
            <el-button @click="router.push('/patients')">进入患者管理</el-button>
            <el-button type="primary" @click="router.push('/prescriptions')">进入处方列表</el-button>
          </div>
        </SectionCard>
      </div>
    </div>
  </AppShell>
</template>
