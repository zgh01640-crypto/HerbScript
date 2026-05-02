<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import AppShell from "../components/AppShell.vue";
import SectionCard from "../components/SectionCard.vue";
import StatusPill from "../components/StatusPill.vue";
import { useAsyncState } from "../composables/useAsyncState";
import { agentService } from "../services/agentService";
import { prescriptionService } from "../services/prescriptionService";
import type { AgentNote } from "../types/agent";
import type { PatientDetail, PatientSummary, PrescriptionRecord } from "../types/prescription";

const route = useRoute();
const router = useRouter();
const patientId = computed(() => Number(route.params.id));
const { data, loading, run } = useAsyncState<PatientDetail | undefined>();
const compareLeftId = ref<number | null>(null);
const compareRightId = ref<number | null>(null);
const compareLoading = ref(false);
const compareLeft = ref<PrescriptionRecord | null>(null);
const compareRight = ref<PrescriptionRecord | null>(null);
const trendLoading = ref(false);
const trendRecords = ref<PrescriptionRecord[]>([]);
const mergeKeyword = ref("");
const mergeCandidates = ref<PatientSummary[]>([]);
const mergeLoading = ref(false);
const merging = ref(false);
const followupNotes = ref<AgentNote[]>([]);
const followupGenerating = ref(false);

onMounted(() => {
  void loadDetail();
});

const loadDetail = async () => {
  await run(async () => {
    const detail = await prescriptionService.getPatientDetail(patientId.value);
    compareLeftId.value = detail.prescriptions[0]?.id ?? null;
    compareRightId.value = detail.prescriptions[1]?.id ?? detail.prescriptions[0]?.id ?? null;
    return detail;
  });
  followupNotes.value = (await agentService.listNotes("patient", patientId.value))
    .filter((item) => item.noteType === "followup_note");
};

const compareOptions = computed(() => data.value?.prescriptions ?? []);
const comparePairsReady = computed(() => Boolean(compareLeftId.value && compareRightId.value));
const recentDiagnoses = computed(() =>
  trendRecords.value
    .filter((record) => record.diagnosis)
    .slice(0, 3)
    .map((record) => ({
      id: record.id,
      diagnosis: record.diagnosis,
      date: record.prescriptionDate
    }))
);
const commonHerbs = computed(() => {
  const herbCounter = new Map<string, number>();

  trendRecords.value.forEach((record) => {
    record.items.forEach((item) => {
      herbCounter.set(item.herbName, (herbCounter.get(item.herbName) ?? 0) + 1);
    });
  });

  return Array.from(herbCounter.entries())
    .sort((a, b) => b[1] - a[1])
    .slice(0, 6)
    .map(([name, count]) => ({ name, count }));
});
const doseTrend = computed(() =>
  trendRecords.value.slice(0, 4).map((record) => ({
    id: record.id,
    date: record.prescriptionDate,
    doseCount: record.doseCount
  }))
);
const trendInsight = computed(() => {
  const doses = doseTrend.value;
  if (doses.length < 2) {
    return "当前处方样本较少，后续录入更多历史处方后可观察剂数变化趋势。";
  }

  const newest = doses[0].doseCount;
  const oldest = doses[doses.length - 1].doseCount;
  if (newest > oldest) {
    return `最近一次处方剂数较早期增加 ${newest - oldest} 剂，治疗节奏有加量趋势。`;
  }
  if (newest < oldest) {
    return `最近一次处方剂数较早期减少 ${oldest - newest} 剂，治疗节奏有收敛趋势。`;
  }
  return "近几次处方剂数整体平稳，治疗节奏变化不大。";
});
const filteredMergeCandidates = computed(() =>
  mergeCandidates.value.filter((candidate) => candidate.id !== patientId.value)
);
const compareFieldRows = computed(() => {
  const left = compareLeft.value;
  const right = compareRight.value;

  if (!left || !right) {
    return [];
  }

  return [
    { label: "处方日期", left: left.prescriptionDate, right: right.prescriptionDate },
    { label: "临床诊断", left: left.diagnosis || "-", right: right.diagnosis || "-" },
    { label: "剂数", left: `${left.doseCount}剂`, right: `${right.doseCount}剂` },
    { label: "医师信息", left: left.doctorName || "-", right: right.doctorName || "-" },
    { label: "服用说明", left: left.usageMethod || "-", right: right.usageMethod || "-" },
    { label: "录入方式", left: left.entryMode === "manual" ? "手动录入" : "AI识别", right: right.entryMode === "manual" ? "手动录入" : "AI识别" },
    { label: "当前状态", left: left.status, right: right.status }
  ].map((row) => ({
    ...row,
    changed: row.left !== row.right
  }));
});

const compareHerbRows = computed(() => {
  const left = compareLeft.value;
  const right = compareRight.value;

  if (!left || !right) {
    return [];
  }

  const herbNames = Array.from(
    new Set([
      ...left.items.map((item) => item.herbName),
      ...right.items.map((item) => item.herbName)
    ])
  );

  return herbNames.map((name) => {
    const leftItem = left.items.find((item) => item.herbName === name);
    const rightItem = right.items.find((item) => item.herbName === name);

    const leftValue = leftItem ? `${leftItem.dosage}${leftItem.unit}` : "-";
    const rightValue = rightItem ? `${rightItem.dosage}${rightItem.unit}` : "-";

    return {
      herbName: name,
      left: leftValue,
      right: rightValue,
      changed: leftValue !== rightValue
    };
  });
});

const compareSummary = computed(() => {
  const left = compareLeft.value;
  const right = compareRight.value;

  if (!left || !right) {
    return null;
  }

  const leftMap = new Map(left.items.map((item) => [item.herbName, `${item.dosage}${item.unit}`]));
  const rightMap = new Map(right.items.map((item) => [item.herbName, `${item.dosage}${item.unit}`]));

  const addedHerbs = Array.from(rightMap.keys()).filter((name) => !leftMap.has(name));
  const removedHerbs = Array.from(leftMap.keys()).filter((name) => !rightMap.has(name));
  const changedDosages = Array.from(leftMap.keys()).filter((name) => rightMap.has(name) && leftMap.get(name) !== rightMap.get(name));
  const changedFields = compareFieldRows.value.filter((row) => row.changed).map((row) => row.label);

  return {
    addedHerbs,
    removedHerbs,
    changedDosages,
    changedFields
  };
});

const loadCompareDetails = async () => {
  if (!compareLeftId.value || !compareRightId.value) {
    compareLeft.value = null;
    compareRight.value = null;
    return;
  }

  compareLoading.value = true;
  try {
    const [left, right] = await Promise.all([
      prescriptionService.getPrescriptionDetail(compareLeftId.value),
      prescriptionService.getPrescriptionDetail(compareRightId.value)
    ]);
    compareLeft.value = left ?? null;
    compareRight.value = right ?? null;
  } finally {
    compareLoading.value = false;
  }
};

const loadTrendDetails = async () => {
  if (!data.value?.prescriptions.length) {
    trendRecords.value = [];
    return;
  }

  trendLoading.value = true;
  try {
    const details = await Promise.all(
      data.value.prescriptions.slice(0, 6).map((item) => prescriptionService.getPrescriptionDetail(item.id))
    );
    trendRecords.value = details.filter((item): item is PrescriptionRecord => Boolean(item));
  } finally {
    trendLoading.value = false;
  }
};

watch(comparePairsReady, (ready) => {
  if (ready) {
    void loadCompareDetails();
  }
}, { immediate: true });

watch([compareLeftId, compareRightId], () => {
  if (comparePairsReady.value) {
    void loadCompareDetails();
  }
});

watch(data, (value) => {
  if (value?.prescriptions.length) {
    void loadTrendDetails();
  } else {
    trendRecords.value = [];
  }
}, { immediate: true });

const searchMergeCandidates = async () => {
  mergeLoading.value = true;
  try {
    mergeCandidates.value = await prescriptionService.getPatientList(mergeKeyword.value || data.value?.name || "");
  } finally {
    mergeLoading.value = false;
  }
};

const mergePatient = async (sourcePatient: PatientSummary) => {
  try {
    await ElMessageBox.confirm(
      `确认将患者 ${sourcePatient.name}（${sourcePatient.patientNo}）的历史处方合并到当前主档吗？`,
      "确认合并患者",
      { type: "warning" }
    );
    merging.value = true;
    await prescriptionService.mergePatient(patientId.value, sourcePatient.id);
    ElMessage.success("患者主档已合并");
    mergeCandidates.value = [];
    mergeKeyword.value = "";
    await loadDetail();
  } catch {
    // keep current state when cancelled or failed
  } finally {
    merging.value = false;
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

const generateFollowupNote = async () => {
  if (!data.value) {
    return;
  }

  followupGenerating.value = true;
  try {
    const sessions = await agentService.listSessions("patient", patientId.value);
    const session = sessions[0] ?? await agentService.createSession("patient", patientId.value, `${data.value.name}病例分析`);
    const response = await agentService.chat(
      session.id,
      "patient",
      patientId.value,
      "请生成一段适合随访沟通的总结"
    );
    const content = response.structured
      ? buildAgentDraft(
          response.structured.summary,
          response.structured.observations,
          response.structured.risks,
          response.structured.suggestions
        )
      : response.message.content;
    const title = `智能体随访记录 ${new Date().toLocaleString("zh-CN", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit"
    }).replace(/\//g, "-")}`;
    const note = await agentService.saveNote(
      session.id,
      "patient",
      patientId.value,
      "followup_note",
      title,
      content
    );
    followupNotes.value = [note, ...followupNotes.value];
    ElMessage.success("智能体随访记录已生成并保存");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "生成随访记录失败");
  } finally {
    followupGenerating.value = false;
  }
};

const removeFollowupNote = async (note: AgentNote) => {
  try {
    await ElMessageBox.confirm(`确认删除记录「${note.title}」吗？`, "删除智能体记录", { type: "warning" });
    await agentService.deleteNote(note.id);
    followupNotes.value = followupNotes.value.filter((item) => item.id !== note.id);
    ElMessage.success("记录已删除");
  } catch {
    // noop
  }
};

const renameFollowupNote = async (note: AgentNote) => {
  try {
    const { value } = await ElMessageBox.prompt("请输入新的记录标题", "重命名智能体记录", {
      inputValue: note.title,
      inputPlaceholder: "请输入标题"
    });
    const updated = await agentService.updateNoteTitle(note.id, value);
    followupNotes.value = followupNotes.value.map((item) => (item.id === note.id ? updated : item));
    ElMessage.success("标题已更新");
  } catch {
    // noop
  }
};
</script>

<template>
  <AppShell>
    <div v-loading="loading" class="detail-grid">
      <div class="detail-summary-bar">
        <div class="detail-summary-item">
          <span>患者编号</span>
          <strong class="mono">{{ data?.patientNo ?? "-" }}</strong>
        </div>
        <div class="detail-summary-item">
          <span>患者姓名</span>
          <strong>{{ data?.name ?? "-" }}</strong>
        </div>
        <div class="detail-summary-item">
          <span>累计处方</span>
          <strong>{{ data?.prescriptionCount ?? 0 }} 张</strong>
        </div>
        <div class="detail-summary-item">
          <span>最近处方</span>
          <strong>{{ data?.lastPrescriptionDate ?? "-" }}</strong>
        </div>
        <div class="action-strip">
          <el-button
            type="primary"
            @click="router.push(`/agent?anchorType=patient&anchorId=${patientId}&title=${encodeURIComponent((data?.name ?? '患者') + '病例分析')}`)"
          >
            问智能体
          </el-button>
        </div>
      </div>

      <div class="workbench-grid">
      <SectionCard title="患者主档" subtitle="保存患者基础信息与近似匹配主档">
        <div class="info-grid">
          <div><span>患者姓名</span><strong>{{ data?.name ?? "-" }}</strong></div>
            <div><span>性别</span><strong>{{ data?.gender ?? "-" }}</strong></div>
            <div><span>年龄</span><strong>{{ data?.age ?? "-" }}</strong></div>
            <div><span>联系电话</span><strong>{{ data?.phone || "-" }}</strong></div>
            <div class="patient-remark-box"><span>备注</span><strong>{{ data?.remark || "当前暂无补充备注" }}</strong></div>
        </div>
      </SectionCard>

      <SectionCard title="智能体随访记录" subtitle="沉淀智能体生成的随访总结与沟通草稿">
        <template #extra>
          <el-button type="primary" plain :loading="followupGenerating" @click="generateFollowupNote">一键生成随访记录</el-button>
        </template>
        <div v-if="followupNotes.length" class="agent-note-list">
          <div
            v-for="note in followupNotes"
            :key="note.id"
            class="agent-note-item"
          >
            <div class="agent-note-meta">
              <div class="agent-note-meta-main">
                <strong>{{ note.title }}</strong>
                <span>{{ note.createdAt }}</span>
              </div>
              <div class="agent-note-actions">
                <el-button link @click="renameFollowupNote(note)">重命名</el-button>
                <el-button link type="danger" @click="removeFollowupNote(note)">删除</el-button>
              </div>
            </div>
            <p>{{ note.content }}</p>
          </div>
        </div>
        <div v-else class="detail-image-empty">
          当前患者暂无智能体随访记录
        </div>
      </SectionCard>

      <SectionCard title="历史处方时间线" subtitle="点击处方可进入详情继续对比原图与结构化结果">
          <div v-if="(data?.prescriptions.length ?? 0) > 0" class="record-list">
            <button
              v-for="item in data?.prescriptions ?? []"
              :key="item.id"
              type="button"
              class="record-row record-button"
              @click="router.push(`/prescriptions/${item.id}`)"
            >
              <div>
                <strong>{{ item.prescriptionDate }} · {{ item.prescriptionNo }}</strong>
                <span>{{ item.doseCount }}剂 · {{ item.entryMode === "manual" ? "手动录入" : "AI识别" }} · {{ item.createdByName }}</span>
              </div>
              <StatusPill :status="item.status" />
            </button>
          </div>
          <div v-else class="detail-image-empty">
            当前患者暂无处方记录
          </div>
        </SectionCard>
      </div>

      <SectionCard title="患者趋势概览" subtitle="基于近期处方自动总结诊断、常用药味与剂数变化">
        <div v-loading="trendLoading" class="trend-grid">
          <div class="trend-card emphasized">
            <span>诊断演变</span>
            <strong>{{ recentDiagnoses.length }} 次记录</strong>
            <div v-if="recentDiagnoses.length > 0" class="trend-list">
              <button
                v-for="item in recentDiagnoses"
                :key="item.id"
                type="button"
                class="trend-list-item"
                @click="router.push(`/prescriptions/${item.id}`)"
              >
                <strong>{{ item.date }}</strong>
                <span>{{ item.diagnosis }}</span>
              </button>
            </div>
            <p v-else>当前还没有足够的诊断记录用于总结趋势。</p>
          </div>

          <div class="trend-card">
            <span>常用药味</span>
            <strong>{{ commonHerbs.length }} 味高频药材</strong>
            <div v-if="commonHerbs.length > 0" class="trend-chip-list">
              <span v-for="item in commonHerbs" :key="item.name" class="trend-chip">{{ item.name }} · {{ item.count }}次</span>
            </div>
            <p v-else>当前没有足够的药味数据。</p>
          </div>

          <div class="trend-card">
            <span>剂数趋势</span>
            <strong>{{ doseTrend.length }} 次对照</strong>
            <div v-if="doseTrend.length > 0" class="dose-trend-list">
              <div v-for="item in doseTrend" :key="item.id" class="dose-trend-row">
                <span>{{ item.date }}</span>
                <strong>{{ item.doseCount }}剂</strong>
              </div>
            </div>
            <p>{{ trendInsight }}</p>
          </div>
        </div>
      </SectionCard>

      <SectionCard title="患者合并治理" subtitle="当同一患者被拆成多个主档时，可将疑似重复主档合并到当前患者">
        <div class="compare-toolbar">
          <el-input v-model="mergeKeyword" placeholder="输入疑似重复患者姓名 / 编号 / 电话" @keyup.enter="searchMergeCandidates" />
          <span class="compare-toolbar-label">合并到当前主档</span>
          <div class="action-strip">
            <el-button :loading="mergeLoading" @click="searchMergeCandidates">搜索候选</el-button>
          </div>
        </div>

        <div v-if="filteredMergeCandidates.length > 0" class="patient-candidate-list">
          <div
            v-for="candidate in filteredMergeCandidates"
            :key="candidate.id"
            class="patient-candidate merge-candidate"
          >
            <div class="patient-candidate-body">
              <div class="patient-candidate-main">
                <strong>{{ candidate.name }} / {{ candidate.gender }} / {{ candidate.age }}岁</strong>
                <span>{{ candidate.patientNo }} · 历史处方 {{ candidate.prescriptionCount }} 张 · 最近处方 {{ candidate.lastPrescriptionDate || "-" }}</span>
              </div>
              <div class="patient-candidate-side">
                <el-button type="danger" plain :loading="merging" @click="mergePatient(candidate)">合并到当前主档</el-button>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="detail-image-empty">
          搜索疑似重复患者后，可将对方历史处方整体合并到当前患者主档。
        </div>
      </SectionCard>

      <SectionCard title="处方对比视图" subtitle="选择两张历史处方，直接比较关键字段与药味差异">
        <div class="compare-toolbar">
          <el-select v-model="compareLeftId" placeholder="选择左侧处方">
            <el-option
              v-for="item in compareOptions"
              :key="`left-${item.id}`"
              :label="`${item.prescriptionDate} · ${item.prescriptionNo}`"
              :value="item.id"
            />
          </el-select>
          <span class="compare-toolbar-label">对比</span>
          <el-select v-model="compareRightId" placeholder="选择右侧处方">
            <el-option
              v-for="item in compareOptions"
              :key="`right-${item.id}`"
              :label="`${item.prescriptionDate} · ${item.prescriptionNo}`"
              :value="item.id"
            />
          </el-select>
        </div>

        <div v-loading="compareLoading" class="compare-workbench">
          <div v-if="compareLeft && compareRight && compareSummary" class="compare-summary-grid">
            <div class="compare-summary-card emphasized">
              <span>字段变化</span>
              <strong>{{ compareSummary.changedFields.length }} 项</strong>
              <p>
                {{ compareSummary.changedFields.length > 0 ? compareSummary.changedFields.join("、") : "关键字段暂无变化" }}
              </p>
            </div>
            <div class="compare-summary-card">
              <span>新增药味</span>
              <strong>{{ compareSummary.addedHerbs.length }} 味</strong>
              <p>{{ compareSummary.addedHerbs.length > 0 ? compareSummary.addedHerbs.join("、") : "本次无新增药味" }}</p>
            </div>
            <div class="compare-summary-card">
              <span>减少药味</span>
              <strong>{{ compareSummary.removedHerbs.length }} 味</strong>
              <p>{{ compareSummary.removedHerbs.length > 0 ? compareSummary.removedHerbs.join("、") : "本次无减少药味" }}</p>
            </div>
            <div class="compare-summary-card">
              <span>剂量变化</span>
              <strong>{{ compareSummary.changedDosages.length }} 味</strong>
              <p>{{ compareSummary.changedDosages.length > 0 ? compareSummary.changedDosages.join("、") : "本次无剂量调整" }}</p>
            </div>
          </div>

          <div v-if="compareLeft && compareRight" class="compare-grid">
            <div class="compare-card">
              <div class="compare-card-head">
                <strong>{{ compareLeft.prescriptionDate }}</strong>
                <span class="mono">{{ compareLeft.prescriptionNo }}</span>
              </div>
              <div class="compare-card-meta">
                <span>{{ compareLeft.entryMode === "manual" ? "手动录入" : "AI识别" }}</span>
                <StatusPill :status="compareLeft.status" />
              </div>
            </div>

            <div class="compare-card">
              <div class="compare-card-head">
                <strong>{{ compareRight.prescriptionDate }}</strong>
                <span class="mono">{{ compareRight.prescriptionNo }}</span>
              </div>
              <div class="compare-card-meta">
                <span>{{ compareRight.entryMode === "manual" ? "手动录入" : "AI识别" }}</span>
                <StatusPill :status="compareRight.status" />
              </div>
            </div>
          </div>

          <div v-if="compareLeft && compareRight" class="compare-table-shell">
            <table class="data-table compare-table">
              <thead>
                <tr>
                  <th>字段</th>
                  <th>{{ compareLeft.prescriptionNo }}</th>
                  <th>{{ compareRight.prescriptionNo }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in compareFieldRows" :key="row.label" :class="{ 'compare-row-changed': row.changed }">
                  <td>{{ row.label }}</td>
                  <td>{{ row.left }}</td>
                  <td>{{ row.right }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div v-if="compareLeft && compareRight" class="compare-table-shell">
            <table class="data-table compare-table">
              <thead>
                <tr>
                  <th>药味</th>
                  <th>{{ compareLeft.prescriptionNo }}</th>
                  <th>{{ compareRight.prescriptionNo }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in compareHerbRows" :key="row.herbName" :class="{ 'compare-row-changed': row.changed }">
                  <td>{{ row.herbName }}</td>
                  <td>{{ row.left }}</td>
                  <td>{{ row.right }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div v-if="!compareLeft || !compareRight" class="detail-image-empty">
            至少需要两张处方记录才能进行对比
          </div>
        </div>
      </SectionCard>
    </div>
  </AppShell>
</template>
