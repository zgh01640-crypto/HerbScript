<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import AppShell from "../components/AppShell.vue";
import SectionCard from "../components/SectionCard.vue";
import StatusPill from "../components/StatusPill.vue";
import { recognitionUploadController } from "../composables/useRecognitionUploadState";
import { useAsyncState } from "../composables/useAsyncState";
import { agentService } from "../services/agentService";
import { prescriptionService } from "../services/prescriptionService";
import { resolveApiBaseUrl } from "../utils/runtime";
import type { AgentNote } from "../types/agent";
import type { PrescriptionItemInput, PrescriptionRecord, PrescriptionSummary } from "../types/prescription";

const route = useRoute();
const router = useRouter();
const prescriptionId = computed(() => Number(route.params.id));
const { data, loading, run } = useAsyncState<PrescriptionRecord | undefined>();
const patientHistory = ref<PrescriptionSummary[]>([]);
const apiBaseUrl = resolveApiBaseUrl();
const imagePreviewVisible = ref(false);
const previewSaving = ref(false);
const previewEditableItems = ref<PrescriptionItemInput[]>([]);
const summaryNotes = ref<AgentNote[]>([]);
const expandedSummaryNoteIds = ref<number[]>([]);
const summaryNoteFilter = ref<"all" | "pinned">("all");
const summaryGenerating = ref(false);

const normalizeConfidence = (value?: string | null) => {
  const normalized = (value || "").toLowerCase();
  return normalized === "high" || normalized === "medium" || normalized === "low" ? normalized : "";
};

const confidenceLabel = (value?: string | null) => {
  const normalized = normalizeConfidence(value);
  if (normalized === "high") return "高";
  if (normalized === "low") return "低";
  if (normalized === "medium") return "中";
  return "";
};

const parseRemainingUncertainties = (value?: string | null) => {
  if (!value) {
    return [];
  }
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed.map(String) : [];
  } catch {
    return [];
  }
};

const sortAgentNotes = (items: AgentNote[]) =>
  [...items].sort((left, right) => {
    if (left.pinned !== right.pinned) {
      return left.pinned ? -1 : 1;
    }
    return right.createdAt.localeCompare(left.createdAt);
  });
const visibleSummaryNotes = computed(() =>
  summaryNoteFilter.value === "pinned"
    ? summaryNotes.value.filter((item) => item.pinned)
    : summaryNotes.value
);
const patientHistoryCount = computed(() => patientHistory.value.length + (data.value?.patientId ? 1 : 0));
const latestPrescriptionDate = computed(() => {
  const dates = [
    data.value?.prescriptionDate,
    ...patientHistory.value.map((item) => item.prescriptionDate)
  ].filter(Boolean) as string[];

  return dates.sort((a, b) => b.localeCompare(a))[0] ?? "";
});
const previewImageUrl = computed(() => {
  const sourceImageUrl = data.value?.sourceImageUrl;

  if (!sourceImageUrl) {
    return "";
  }

  if (sourceImageUrl.startsWith("http://") || sourceImageUrl.startsWith("https://")) {
    return sourceImageUrl;
  }

  return `${apiBaseUrl}${sourceImageUrl.startsWith("/") ? sourceImageUrl : `/${sourceImageUrl}`}`;
});

const syncPreviewItems = () => {
  previewEditableItems.value = (data.value?.items ?? []).map((item, index) => ({
    sortNo: index + 1,
    herbName: item.herbName,
    rawHerbName: item.rawHerbName,
    dosage: item.dosage,
    unit: item.unit,
    specialInstruction: item.specialInstruction
  }));
};

const loadDetail = async () => {
  await run(async () => {
    const detail = await prescriptionService.getPrescriptionDetail(prescriptionId.value);
    if (detail?.patientId) {
      patientHistory.value = (await prescriptionService.getPatientPrescriptions(detail.patientId))
        .filter((item) => item.id !== detail.id);
    } else {
      patientHistory.value = [];
    }
    return detail;
  });
  syncPreviewItems();
  summaryNotes.value = sortAgentNotes(
    (await agentService.listNotes("prescription", prescriptionId.value))
      .filter((item) => item.noteType === "prescription_summary")
  );
  expandedSummaryNoteIds.value = [];
};

onMounted(() => {
  void loadDetail();
});

const removePrescription = async () => {
  try {
    await ElMessageBox.confirm("删除后该处方将从列表中隐藏，是否继续？", "确认删除", {
      type: "warning"
    });
    await prescriptionService.deletePrescription(prescriptionId.value);
    ElMessage.success("处方已删除");
    await router.push("/prescriptions");
  } catch {
    // User cancelled or request failed; keep UI unchanged.
  }
};

const goToRecognition = async () => {
  recognitionUploadController.clear();
  await router.push("/recognition");
};

const openImagePreview = () => {
  if (previewImageUrl.value) {
    syncPreviewItems();
    imagePreviewVisible.value = true;
  }
};

const updatePreviewDosage = (index: number, value: string) => {
  previewEditableItems.value[index].dosage = value.trim() === "" ? 0 : Number(value);
};

const savePreviewItems = async () => {
  if (!data.value) {
    return;
  }

  previewSaving.value = true;
  try {
    await prescriptionService.updatePrescription(data.value.id, {
      patientId: data.value.patientId,
      patientName: data.value.patientName,
      gender: data.value.gender,
      age: data.value.age,
      department: data.value.department,
      diagnosis: data.value.diagnosis,
      doseCount: data.value.doseCount,
      prescriptionDate: data.value.prescriptionDate,
      doctorName: data.value.doctorName,
      usageMethod: data.value.usageMethod,
      items: previewEditableItems.value.map((item, index) => ({
        ...item,
        sortNo: index + 1
      }))
    });
    ElMessage.success("药味修改已保存");
    await loadDetail();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "保存药味失败");
  } finally {
    previewSaving.value = false;
  }
};

const buildAgentDraft = (summary: string, observations: string[], risks: string[], suggestions: string[]) => {
  return [
    `摘要：${summary}`,
    observations.length ? `观察：\n- ${observations.join("\n- ")}` : "",
    risks.length ? `风险提醒：\n- ${risks.join("\n- ")}` : "",
    suggestions.length ? `建议：\n- ${suggestions.join("\n- ")}` : ""
  ].filter(Boolean).join("\n\n");
};

const generatePrescriptionSummary = async () => {
  if (!data.value) {
    return;
  }

  summaryGenerating.value = true;
  try {
    const sessions = await agentService.listSessions("prescription", prescriptionId.value);
    const session = sessions[0] ?? await agentService.createSession("prescription", prescriptionId.value, `${data.value.prescriptionNo}分析`);
    const response = await agentService.chat(
      session.id,
      "prescription",
      prescriptionId.value,
      "请生成适合归档的结构化处方总结"
    );
    const content = response.structured
      ? buildAgentDraft(
          response.structured.summary,
          response.structured.observations,
          response.structured.risks,
          response.structured.suggestions
        )
      : response.message.content;
    const title = `智能体处方摘要 ${new Date().toLocaleString("zh-CN", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit"
    }).replace(/\//g, "-")}`;
    const note = await agentService.saveNote(
      session.id,
      "prescription",
      prescriptionId.value,
      "prescription_summary",
      title,
      content,
      {
        answerConfidence: response.structured?.answerConfidence ?? null,
        remainingUncertainties: response.structured?.remainingUncertainties ?? []
      }
    );
    summaryNotes.value = sortAgentNotes([note, ...summaryNotes.value]);
    expandedSummaryNoteIds.value = [note.id, ...expandedSummaryNoteIds.value];
    ElMessage.success("智能体处方摘要已生成并保存");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "生成处方摘要失败");
  } finally {
    summaryGenerating.value = false;
  }
};

const removeSummaryNote = async (note: AgentNote) => {
  try {
    await ElMessageBox.confirm(`确认删除记录「${note.title}」吗？`, "删除智能体记录", { type: "warning" });
    await agentService.deleteNote(note.id);
    summaryNotes.value = summaryNotes.value.filter((item) => item.id !== note.id);
    expandedSummaryNoteIds.value = expandedSummaryNoteIds.value.filter((id) => id !== note.id);
    ElMessage.success("记录已删除");
  } catch {
    // noop
  }
};

const toggleSummaryNote = (noteId: number) => {
  expandedSummaryNoteIds.value = expandedSummaryNoteIds.value.includes(noteId)
    ? expandedSummaryNoteIds.value.filter((id) => id !== noteId)
    : [...expandedSummaryNoteIds.value, noteId];
};

const renameSummaryNote = async (note: AgentNote) => {
  try {
    const { value } = await ElMessageBox.prompt("请输入新的记录标题", "重命名智能体记录", {
      inputValue: note.title,
      inputPlaceholder: "请输入标题"
    });
    const updated = await agentService.updateNoteTitle(note.id, value);
    summaryNotes.value = sortAgentNotes(summaryNotes.value.map((item) => (item.id === note.id ? updated : item)));
    ElMessage.success("标题已更新");
  } catch {
    // noop
  }
};

const editSummaryNoteContent = async (note: AgentNote) => {
  try {
    const { value } = await ElMessageBox.prompt("请编辑处方摘要正文", "编辑智能体记录", {
      inputValue: note.content,
      inputType: "textarea",
      inputPlaceholder: "请输入处方摘要正文"
    });
    const updated = await agentService.updateNoteContent(note.id, value);
    summaryNotes.value = sortAgentNotes(summaryNotes.value.map((item) => (item.id === note.id ? updated : item)));
    expandedSummaryNoteIds.value = expandedSummaryNoteIds.value.includes(note.id) ? expandedSummaryNoteIds.value : [note.id, ...expandedSummaryNoteIds.value];
    ElMessage.success("正文已更新");
  } catch {
    // noop
  }
};

const toggleSummaryNotePinned = async (note: AgentNote) => {
  try {
    const updated = await agentService.updateNotePinned(note.id, !note.pinned);
    summaryNotes.value = sortAgentNotes(summaryNotes.value.map((item) => (item.id === note.id ? updated : item)));
    ElMessage.success(updated.pinned ? "记录已置顶" : "已取消置顶");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "更新置顶状态失败");
  }
};

const copySummaryNoteContent = async (note: AgentNote) => {
  try {
    await navigator.clipboard.writeText(note.content);
    ElMessage.success("正文已复制");
  } catch {
    ElMessage.error("复制失败，请手动复制");
  }
};
</script>

<template>
  <AppShell>
    <div v-loading="loading" class="detail-grid">
      <div class="detail-summary-bar">
        <div class="detail-summary-item">
          <span>处方编号</span>
          <strong class="mono">{{ data?.prescriptionNo ?? "-" }}</strong>
        </div>
        <div class="detail-summary-item">
          <span>录入方式</span>
          <strong>{{ data?.entryMode === "manual" ? "手动录入" : "AI识别" }}</strong>
        </div>
        <div class="detail-summary-item">
          <span>当前状态</span>
          <StatusPill :status="data?.status ?? 'draft'" />
        </div>
        <div class="detail-summary-item">
          <span>来源模型</span>
          <strong>{{ data?.sourceModel ?? "人工录入" }}</strong>
        </div>
        <div class="action-strip">
          <el-button
            type="primary"
            @click="router.push(`/agent?anchorType=prescription&anchorId=${prescriptionId}&title=${encodeURIComponent((data?.prescriptionNo ?? '处方') + '分析')}`)"
          >
            分析处方
          </el-button>
          <el-button @click="goToRecognition">处方识别</el-button>
          <el-button @click="router.push(`/prescriptions/${prescriptionId}/edit`)">编辑处方</el-button>
          <el-button type="danger" @click="removePrescription">删除处方</el-button>
        </div>
      </div>

      <div class="workbench-grid">
        <SectionCard title="处方原图" subtitle="点击图片可放大查看">
          <div class="detail-image-stage">
            <div v-if="previewImageUrl" class="detail-image-shell image-clickable" @click="openImagePreview">
              <img :src="previewImageUrl" alt="处方原图" class="detail-image" />
            </div>
            <div v-else class="detail-image-empty">
              当前处方没有关联原图
            </div>
          </div>
        </SectionCard>

        <SectionCard title="结构化结果" subtitle="保留基础字段与药味明细，便于对照原图核验">
          <div class="info-grid">
            <div><span>患者姓名</span><strong>{{ data?.patientName ?? "-" }}</strong></div>
            <div><span>性别</span><strong>{{ data?.gender ?? "-" }}</strong></div>
            <div><span>年龄</span><strong>{{ data?.age ?? "-" }}</strong></div>
            <div><span>科室</span><strong>{{ data?.department ?? "-" }}</strong></div>
            <div><span>临床诊断</span><strong>{{ data?.diagnosis ?? "-" }}</strong></div>
            <div><span>处方日期</span><strong>{{ data?.prescriptionDate ?? "-" }}</strong></div>
            <div><span>剂数</span><strong>{{ data?.doseCount ?? "-" }}</strong></div>
            <div><span>医师信息</span><strong>{{ data?.doctorName ?? "-" }}</strong></div>
            <div><span>服用说明</span><strong>{{ data?.usageMethod ?? "-" }}</strong></div>
            <div><span>录入人员</span><strong>{{ data?.createdByName ?? "-" }}</strong></div>
          </div>

          <div class="items-head compact-head">
            <div>
              <strong>药味明细</strong>
              <p>按处方顺序展示结构化识别结果</p>
            </div>
          </div>

          <div class="record-list">
            <div v-for="item in data?.items ?? []" :key="item.id" class="record-row">
              <div>
                <strong>{{ item.herbName }}</strong>
                <span>{{ item.specialInstruction || item.effectHint || "标准药味" }}</span>
              </div>
              <div>{{ item.dosage }}{{ item.unit }}</div>
            </div>
          </div>
        </SectionCard>
      </div>

      <SectionCard title="操作记录" subtitle="保留识别、校对、编辑全程留痕">
        <div class="timeline-list">
          <div v-for="log in data?.logs ?? []" :key="log.id" class="timeline-row">
            <strong>{{ log.time }}</strong>
            <span>{{ log.content }}</span>
          </div>
        </div>
      </SectionCard>

      <SectionCard title="智能体处方摘要" subtitle="沉淀智能体生成的处方分析与归档草稿">
        <template #extra>
          <el-button type="primary" plain :loading="summaryGenerating" @click="generatePrescriptionSummary">一键生成处方摘要</el-button>
        </template>
        <div v-if="summaryNotes.length" class="agent-note-list">
          <div class="agent-note-list-head">
            <div class="agent-note-list-summary">
              <strong>记录列表</strong>
              <span>{{ visibleSummaryNotes.length }} / {{ summaryNotes.length }} 条</span>
            </div>
            <div class="agent-note-filter">
              <button type="button" :class="{ active: summaryNoteFilter === 'all' }" @click="summaryNoteFilter = 'all'">全部</button>
              <button type="button" :class="{ active: summaryNoteFilter === 'pinned' }" @click="summaryNoteFilter = 'pinned'">仅置顶</button>
            </div>
          </div>
          <div
            v-for="note in visibleSummaryNotes"
            :key="note.id"
            class="agent-note-item"
            :class="{ expanded: expandedSummaryNoteIds.includes(note.id) }"
          >
            <div class="agent-note-head" @click="toggleSummaryNote(note.id)">
              <div class="agent-note-meta-main">
                <strong>
                  <span v-if="note.pinned" class="agent-note-pin-badge">置顶</span>
                  {{ note.title }}
                </strong>
                <span>{{ note.createdAt }}</span>
              </div>
              <div class="agent-note-actions">
                <el-button link type="success" @click.stop="toggleSummaryNotePinned(note)">{{ note.pinned ? "取消置顶" : "置顶" }}</el-button>
                <small class="agent-note-toggle">{{ expandedSummaryNoteIds.includes(note.id) ? "收起" : "展开" }}</small>
                <el-button link @click.stop="copySummaryNoteContent(note)">复制正文</el-button>
                <el-button link @click.stop="editSummaryNoteContent(note)">编辑正文</el-button>
                <el-button link @click.stop="renameSummaryNote(note)">重命名</el-button>
                <el-button link type="danger" @click.stop="removeSummaryNote(note)">删除</el-button>
              </div>
            </div>
            <div v-if="expandedSummaryNoteIds.includes(note.id)" class="agent-note-body">
              <div v-if="confidenceLabel(note.answerConfidence)" class="agent-note-confidence-row">
                <span>结论把握度</span>
                <strong class="agent-confidence-value" :class="normalizeConfidence(note.answerConfidence)">
                  {{ confidenceLabel(note.answerConfidence) }}
                </strong>
              </div>
              <div v-if="parseRemainingUncertainties(note.remainingUncertaintiesJson).length" class="agent-note-uncertainty-box">
                <span>剩余不确定性</span>
                <ul>
                  <li v-for="item in parseRemainingUncertainties(note.remainingUncertaintiesJson)" :key="item">{{ item }}</li>
                </ul>
              </div>
              <p>{{ note.content }}</p>
            </div>
          </div>
          <div v-if="!visibleSummaryNotes.length" class="detail-image-empty">
            当前筛选条件下暂无记录
          </div>
        </div>
        <div v-else class="detail-image-empty">
          当前处方暂无智能体处方摘要
        </div>
      </SectionCard>

      <SectionCard title="患者历史处方" subtitle="同一患者的既往处方记录">
        <div v-if="data?.patientId" class="history-overview">
          <div class="history-overview-item">
            <span>所属患者</span>
            <strong>{{ data.patientName }}</strong>
          </div>
          <div class="history-overview-item">
            <span>累计处方</span>
            <strong>{{ patientHistoryCount }} 张</strong>
          </div>
          <div v-if="latestPrescriptionDate" class="history-overview-item">
            <span>最近处方</span>
            <strong>{{ latestPrescriptionDate }}</strong>
          </div>
        </div>
        <div v-if="patientHistory.length > 0" class="record-list">
          <button
            v-for="item in patientHistory"
            :key="item.id"
            type="button"
            class="record-row record-button"
            @click="router.push(`/prescriptions/${item.id}`)"
          >
            <div>
              <strong>{{ item.prescriptionDate }} · {{ item.patientName }}</strong>
              <span>{{ item.prescriptionNo }} · {{ item.doseCount }}剂 · {{ item.entryMode === "manual" ? "手动录入" : "AI识别" }}</span>
            </div>
            <div class="history-record-side">
              <small>{{ item.createdAt }}</small>
              <StatusPill :status="item.status" />
            </div>
          </button>
        </div>
        <div v-else class="detail-image-empty">
          当前患者暂无其他历史处方
        </div>
      </SectionCard>

      <el-dialog v-model="imagePreviewVisible" title="处方原图对照" width="82%" top="1vh">
        <div class="preview-compare-layout">
          <div class="preview-dialog-body">
            <img v-if="previewImageUrl" :src="previewImageUrl" alt="处方原图放大预览" class="preview-dialog-image" />
          </div>
          <div class="preview-compare-panel">
            <div class="preview-compare-head compact">
              <div class="preview-compare-title">
                <small>药味对照编辑</small>
                <span>患者姓名</span>
                <strong>{{ data?.patientName ?? "-" }}</strong>
              </div>
              <el-button type="primary" class="preview-save-button" :loading="previewSaving" @click="savePreviewItems">保存药味</el-button>
            </div>

            <div class="preview-compare-summary">
              <span>共 {{ previewEditableItems.length }} 味药</span>
              <span>左侧原图，右侧逐味校对</span>
            </div>

            <div class="preview-compare-list">
              <div v-for="(item, index) in previewEditableItems" :key="`${item.sortNo}-${index}`" class="preview-compare-row editable">
                <div class="preview-compare-index">{{ index + 1 }}</div>
                <div class="preview-compare-main">
                  <el-input v-model="item.herbName" />
                </div>
                <div class="preview-compare-editors">
                  <el-input
                    :model-value="String(item.dosage ?? '')"
                    placeholder="剂量"
                    inputmode="decimal"
                    @update:model-value="updatePreviewDosage(index, $event)"
                  />
                  <el-input v-model="item.unit" placeholder="单位" />
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-dialog>
    </div>
  </AppShell>
</template>
