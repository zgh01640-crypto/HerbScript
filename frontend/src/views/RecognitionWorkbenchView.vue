<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import AppShell from "../components/AppShell.vue";
import ConfidenceBadge from "../components/ConfidenceBadge.vue";
import SectionCard from "../components/SectionCard.vue";
import { recognitionUploadController, recognitionUploadState } from "../composables/useRecognitionUploadState";
import { useAsyncState } from "../composables/useAsyncState";
import { prescriptionService } from "../services/prescriptionService";
import { resolveApiBaseUrl } from "../utils/runtime";
import type { PatientMatchCandidate, PrescriptionItemInput, PrescriptionRecord } from "../types/prescription";

type WorkbenchForm = {
  patientName: string;
  gender: string;
  age: string;
  department: string;
  diagnosis: string;
  doseCount: string;
  prescriptionDate: string;
  usageMethod: string;
  doctorName: string;
  remark: string;
};

const router = useRouter();
const { data, loading, run } = useAsyncState<PrescriptionRecord | undefined>();
const submitting = reactive({ confirm: false, upload: false });
const errorMessage = reactive({ value: "" });
const apiBaseUrl = resolveApiBaseUrl();
const imageUploadInput = ref<HTMLInputElement | null>(null);
const dragActive = ref(false);
const matching = ref(false);
const detailPanelExpanded = ref(false);
const matchedPatients = ref<PatientMatchCandidate[]>([]);
const selectedPatientId = ref<number | null>(null);
const patientChoiceMode = ref<"new" | "existing">("new");
const streamedRecognitionJson = ref("");
let streamTimer: number | null = null;
const form = reactive<WorkbenchForm>({
  patientName: "",
  gender: "",
  age: "",
  department: "",
  diagnosis: "",
  doseCount: "",
  prescriptionDate: "",
  usageMethod: "",
  doctorName: "",
  remark: ""
});
const editableItems = reactive<PrescriptionItemInput[]>([]);
const hiddenWarnings = new Set<string>();
const mockFallbackMessage = "当前为 Doubao 本地 mock 草稿，待接真实模型响应。";

const items = computed(() => editableItems);
const warnings = computed(() =>
  (data.value?.recognitionDraft?.warnings ?? []).filter((warning) => !hiddenWarnings.has(warning))
);
const isMockFallback = computed(() => (data.value?.recognitionDraft?.warnings ?? []).includes(mockFallbackMessage));
const lowConfidenceFields = computed(() => new Set(data.value?.recognitionDraft?.lowConfidenceFields ?? []));
const recommendedPatient = computed(() => matchedPatients.value[0]);
const matchedHistoryCount = computed(() => matchedPatients.value.reduce((total, candidate) => total + candidate.prescriptionCount, 0));
const selectedPatient = computed(() => matchedPatients.value.find((candidate) => candidate.id === selectedPatientId.value) ?? null);
const matchLevelLabelMap: Record<PatientMatchCandidate["matchLevel"], string> = {
  high: "高置信",
  medium: "中等置信",
  low: "弱匹配"
};
const previewImageUrl = computed(() => {
  if (recognitionUploadState.previewUrl) {
    return recognitionUploadState.previewUrl;
  }

  const sourceImageUrl = data.value?.sourceImageUrl;

  if (!sourceImageUrl) {
    return "";
  }

  if (sourceImageUrl.startsWith("http://") || sourceImageUrl.startsWith("https://")) {
    return sourceImageUrl;
  }

  return `${apiBaseUrl}${sourceImageUrl.startsWith("/") ? sourceImageUrl : `/${sourceImageUrl}`}`;
});
const recognitionJsonSource = computed(() => {
  const rawText = data.value?.recognitionDraft?.rawText?.trim();
  if (rawText) {
    return rawText;
  }

  if (!data.value) {
    return "";
  }

  return JSON.stringify(
    {
      patientName: form.patientName,
      gender: form.gender,
      age: form.age,
      department: form.department,
      diagnosis: form.diagnosis,
      doseCount: form.doseCount,
      prescriptionDate: form.prescriptionDate,
      doctorName: form.doctorName,
      usageMethod: form.usageMethod,
      warnings: warnings.value,
      lowConfidenceFields: Array.from(lowConfidenceFields.value),
      items: editableItems.map((item, index) => ({
        sortNo: index + 1,
        herbName: item.herbName,
        rawHerbName: item.rawHerbName,
        dosage: item.dosage,
        unit: item.unit,
        specialInstruction: item.specialInstruction
      }))
    },
    null,
    2
  );
});
const hasRecognitionJson = computed(() => Boolean(recognitionJsonSource.value));
const formSummary = computed(() => {
  const parts = [
    form.patientName ? `${form.patientName}` : "未填姓名",
    form.gender || "未填性别",
    form.age ? `${form.age}岁` : "未填年龄",
    form.diagnosis || "未填诊断",
    form.doseCount ? `${form.doseCount}剂` : "未填剂数",
    form.prescriptionDate || "未填日期"
  ];

  return parts.join(" / ");
});

const hydrateDraft = (record: PrescriptionRecord) => {
  form.patientName = record.patientName;
  form.gender = record.gender;
  form.age = String(record.age);
  form.department = record.department;
  form.diagnosis = record.diagnosis;
  form.doseCount = String(record.doseCount);
  form.prescriptionDate = record.prescriptionDate;
  form.usageMethod = record.usageMethod;
  form.doctorName = record.doctorName;
  form.remark = "";
  editableItems.splice(0, editableItems.length, ...record.items.map((item, index) => ({
    sortNo: index + 1,
    herbName: item.herbName,
    rawHerbName: item.rawHerbName,
    dosage: item.dosage,
    unit: item.unit,
    specialInstruction: item.specialInstruction
  })));
};

const resetPatientMatching = () => {
  matchedPatients.value = [];
  selectedPatientId.value = null;
  patientChoiceMode.value = "new";
};

const resetWorkbench = () => {
  data.value = undefined;
  resetPatientMatching();
  editableItems.splice(0, editableItems.length);
  form.patientName = "";
  form.gender = "";
  form.age = "";
  form.department = "";
  form.diagnosis = "";
  form.doseCount = "";
  form.prescriptionDate = "";
  form.usageMethod = "";
  form.doctorName = "";
  form.remark = "";
};

const syncFromRecognitionState = () => {
  if (recognitionUploadState.record) {
    data.value = recognitionUploadState.record;
    hydrateDraft(recognitionUploadState.record);
    return;
  }

  if (recognitionUploadState.status === "uploading") {
    data.value = undefined;
    resetPatientMatching();
    editableItems.splice(0, editableItems.length);
    return;
  }

  if (recognitionUploadState.status === "error") {
    errorMessage.value = recognitionUploadState.errorMessage;
  }
};

const addItem = () => {
  editableItems.push({
    sortNo: editableItems.length + 1,
    herbName: "",
    dosage: 10,
    unit: "g",
    specialInstruction: ""
  });
};

const runPatientMatch = async (silent = false) => {
  if (!form.patientName || !form.gender || !form.age) {
    if (!silent) {
      ElMessage.warning("请先填写患者姓名、性别和年龄");
    }
    return;
  }

  matching.value = true;
  try {
    matchedPatients.value = await prescriptionService.matchPatients({
      name: form.patientName,
      gender: form.gender,
      age: Number(form.age)
    });
    if (matchedPatients.value.length > 0) {
      patientChoiceMode.value = "existing";
      selectedPatientId.value = matchedPatients.value[0].id;
      if (!silent) {
        ElMessage.success(`已匹配到 ${matchedPatients.value.length} 个疑似患者档案`);
      }
    } else {
      patientChoiceMode.value = "new";
      selectedPatientId.value = null;
      if (!silent) {
        ElMessage.info("未匹配到近似患者，将按新患者处理");
      }
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "患者匹配失败";
  } finally {
    matching.value = false;
  }
};

const updateItemDosage = (index: number, value: string) => {
  const normalized = value.trim();
  editableItems[index].dosage = normalized === "" ? 0 : Number(normalized);
};

const removeItem = (index: number) => {
  editableItems.splice(index, 1);
  editableItems.forEach((item, idx) => {
    item.sortNo = idx + 1;
  });
};

const confirmDraft = async () => {
  if (!data.value?.recognitionDraft?.taskId) {
    errorMessage.value = "未找到识别任务";
    return;
  }

  submitting.confirm = true;
  errorMessage.value = "";

  try {
    const result = await prescriptionService.confirmRecognitionDraft(data.value.recognitionDraft.taskId, {
      patientId: patientChoiceMode.value === "existing" ? selectedPatientId.value ?? undefined : undefined,
      patientDraft: patientChoiceMode.value === "new"
        ? {
            name: form.patientName,
            gender: form.gender,
            age: Number(form.age)
          }
        : undefined,
      patientName: form.patientName,
      gender: form.gender,
      age: Number(form.age),
      department: form.department,
      diagnosis: form.diagnosis,
      doseCount: Number(form.doseCount),
      prescriptionDate: form.prescriptionDate,
      doctorName: form.doctorName,
      usageMethod: form.usageMethod,
      remark: form.remark,
      items: editableItems.map((item, index) => ({
        ...item,
        sortNo: index + 1
      }))
    });
    recognitionUploadController.clear();
    resetWorkbench();
    await router.push(`/prescriptions/${result.id}`);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "确认入库失败";
  } finally {
    submitting.confirm = false;
  }
};

const uploadImage = async (file: File) => {
  submitting.upload = true;
  errorMessage.value = "";

  try {
    const record = await recognitionUploadController.upload(file);
    data.value = record;
    hydrateDraft(record);
    void runPatientMatch(true);
  } catch (error) {
    const message = error instanceof Error ? error.message : "上传识别失败";
    if (message.includes("InvalidSubscription") || message.includes("未开通有效订阅")) {
      errorMessage.value = "模型识别失败：当前账号未开通有效订阅，请先检查模型配置或开通对应套餐。";
    } else if (message.includes("API Key 无效") || message.includes("无访问权限")) {
      errorMessage.value = "模型识别失败：API Key 无效或当前账号无访问权限。";
    } else if (message.includes("接口地址或路径无效")) {
      errorMessage.value = "模型识别失败：模型接口地址或路径配置不正确。";
    } else {
      errorMessage.value = message;
    }
  } finally {
    submitting.upload = false;
  }
};

const openImagePicker = () => {
  imageUploadInput.value?.click();
};

const handleImageInputChange = (event: Event) => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) {
    return;
  }
  void uploadImage(file);
  input.value = "";
};

const handleDragOver = (event: DragEvent) => {
  event.preventDefault();
  dragActive.value = true;
};

const handleDragLeave = (event: DragEvent) => {
  event.preventDefault();
  dragActive.value = false;
};

const handleDrop = (event: DragEvent) => {
  event.preventDefault();
  dragActive.value = false;
  const file = event.dataTransfer?.files?.[0];
  if (!file) {
    return;
  }
  void uploadImage(file);
};

const copyRecognitionJson = async () => {
  if (!recognitionJsonSource.value) {
    ElMessage.warning("当前没有可复制的识别结果");
    return;
  }

  await navigator.clipboard.writeText(recognitionJsonSource.value);
  ElMessage.success("识别 JSON 已复制");
};

onMounted(() => {
  if (recognitionUploadState.status === "idle") {
    resetWorkbench();
  }
  syncFromRecognitionState();
});

onBeforeUnmount(() => {
  if (streamTimer !== null) {
    window.clearInterval(streamTimer);
  }
});

watch(
  recognitionJsonSource,
  (value) => {
    if (streamTimer !== null) {
      window.clearInterval(streamTimer);
      streamTimer = null;
    }

    if (!value) {
      streamedRecognitionJson.value = "";
      return;
    }

    streamedRecognitionJson.value = "";
    let index = 0;
    streamTimer = window.setInterval(() => {
      index += 8;
      streamedRecognitionJson.value = value.slice(0, index);
      if (index >= value.length && streamTimer !== null) {
        window.clearInterval(streamTimer);
        streamTimer = null;
      }
    }, 18);
  },
  { immediate: true }
);

watch(
  () => recognitionUploadState.status,
  () => {
    submitting.upload = recognitionUploadState.status === "uploading";
    syncFromRecognitionState();
  },
  { immediate: true }
);

watch(
  () => recognitionUploadState.record,
  (record) => {
    if (record) {
      data.value = record;
      hydrateDraft(record);
      void runPatientMatch(true);
    }
  }
);
</script>

<template>
  <AppShell>
    <div v-loading="loading" class="recognition-page">
      <div v-if="warnings.length" class="warning-stack">
        <div v-for="warning in warnings" :key="warning" class="warning-chip">
          {{ warning }}
        </div>
      </div>

      <div class="workbench-grid">
        <SectionCard title="处方原图" subtitle="点击或拖拽图片到该区域，即可开始处方识别" inline-subtitle>
          <template #extra>
            <span class="recognition-card-badge">AI 识别入口</span>
          </template>
          <input
            ref="imageUploadInput"
            class="upload-input"
            type="file"
            accept=".png,.jpg,.jpeg"
            @change="handleImageInputChange"
          />
          <div
            class="image-stage image-upload-stage"
            :class="{ 'drag-active': dragActive, 'empty-stage': !previewImageUrl }"
            @click="openImagePicker"
            @dragover="handleDragOver"
            @dragleave="handleDragLeave"
            @drop="handleDrop"
          >
            <div v-if="submitting.upload" class="recognition-image-status active">
              <span>正在上传图片并调用 AI 识别，请等待草稿生成</span>
              <span class="status-dots" aria-hidden="true">
                <i />
                <i />
                <i />
              </span>
            </div>
            <div v-if="previewImageUrl" class="image-preview-shell">
              <img :src="previewImageUrl" alt="处方原图" class="recognition-image" />
            </div>
            <div v-else class="image-paper">
              <div class="image-upload-hint">
                <strong>点击选择处方图片</strong>
                <span>或将 JPG / PNG 图片拖拽到这里，系统会立即开始识别。</span>
              </div>
            </div>
          </div>
          <div v-if="data?.sourceImageUrl" class="image-meta">
            <span>图片地址</span>
            <strong class="mono">{{ data.sourceImageUrl }}</strong>
          </div>
          <div v-if="isMockFallback" class="recognition-fallback-note">
            当前结果来自本地回退草稿，不是实时模型返回。
          </div>
          <div class="json-stream-panel">
            <div class="json-stream-head">
              <span>模型识别 JSON</span>
              <div class="json-stream-actions">
                <strong>{{ data?.recognitionDraft?.providerName ?? "doubao-seed-2-0-pro" }}</strong>
                <el-button text @click="copyRecognitionJson">复制</el-button>
              </div>
            </div>
            <pre v-if="streamedRecognitionJson" class="json-stream-content">{{ streamedRecognitionJson }}</pre>
            <div v-else-if="!hasRecognitionJson" class="json-stream-empty">
              识别结果将在上传图片并生成草稿后显示在这里。
            </div>
          </div>
        </SectionCard>

        <SectionCard title="结构化结果" subtitle="低置信字段已高亮，保存前请逐项确认" inline-subtitle>
          <template #extra>
            <span class="recognition-card-badge muted">人工校对面板</span>
          </template>
          <div class="recognition-summary-bar" :class="{ expanded: detailPanelExpanded }">
            <div class="recognition-summary-text">
              <strong>校对摘要</strong>
              <span>{{ formSummary }}</span>
            </div>
            <el-button text @click="detailPanelExpanded = !detailPanelExpanded">
              {{ detailPanelExpanded ? "收起信息" : "展开信息" }}
            </el-button>
          </div>

          <div v-show="detailPanelExpanded" class="form-grid">
            <div class="field-box" :class="{ 'low-confidence': lowConfidenceFields.has('patientName') }">
              <label>患者姓名</label>
              <span v-if="lowConfidenceFields.has('patientName')" class="field-alert">低置信</span>
              <el-input v-model="form.patientName" />
            </div>
            <div class="field-box" :class="{ 'low-confidence': lowConfidenceFields.has('gender') }">
              <label>性别</label>
              <span v-if="lowConfidenceFields.has('gender')" class="field-alert">低置信</span>
              <el-input v-model="form.gender" />
            </div>
            <div class="field-box">
              <label>年龄</label>
              <el-input v-model="form.age" />
            </div>
            <div class="field-box field-box-action">
              <label>患者匹配</label>
              <el-button :loading="matching" @click="runPatientMatch">匹配已有患者</el-button>
            </div>
            <div class="field-box">
              <label>科室</label>
              <el-input v-model="form.department" />
            </div>
            <div class="field-box" :class="{ 'low-confidence': lowConfidenceFields.has('diagnosis') }">
              <label>临床诊断</label>
              <span v-if="lowConfidenceFields.has('diagnosis')" class="field-alert">低置信</span>
              <el-input v-model="form.diagnosis" />
            </div>
            <div class="field-box" :class="{ 'low-confidence': lowConfidenceFields.has('doseCount') }">
              <label>剂数</label>
              <span v-if="lowConfidenceFields.has('doseCount')" class="field-alert">低置信</span>
              <el-input v-model="form.doseCount" />
            </div>
            <div class="field-box">
              <label>处方日期</label>
              <el-input v-model="form.prescriptionDate" type="date" />
            </div>
            <div class="field-box" :class="{ 'low-confidence': lowConfidenceFields.has('usageMethod') }">
              <label>服用说明</label>
              <span v-if="lowConfidenceFields.has('usageMethod')" class="field-alert">低置信</span>
              <el-input v-model="form.usageMethod" />
            </div>
            <div class="field-box" :class="{ 'low-confidence': lowConfidenceFields.has('doctorName') }">
              <label>医师信息</label>
              <span v-if="lowConfidenceFields.has('doctorName')" class="field-alert">低置信</span>
              <el-input v-model="form.doctorName" />
            </div>
            <div class="field-box">
              <label>备注</label>
              <el-input v-model="form.remark" />
            </div>
          </div>

          <div v-show="detailPanelExpanded" class="patient-match-panel">
            <div class="patient-match-head">
              <strong>患者确认</strong>
              <p>系统会先根据姓名、性别和年龄推荐近似患者，再由你确认是否复用已有患者档案。</p>
            </div>
            <div v-if="matchedPatients.length > 0" class="match-result-banner">
              <strong>已自动匹配 {{ matchedPatients.length }} 个疑似患者档案。</strong>
              <span v-if="recommendedPatient">
                推荐关联 {{ recommendedPatient.name }}，该患者已有 {{ recommendedPatient.prescriptionCount }} 张处方
                <template v-if="recommendedPatient.lastPrescriptionDate">，最近一次为 {{ recommendedPatient.lastPrescriptionDate }}</template>。
              </span>
            </div>
            <el-radio-group v-model="patientChoiceMode">
              <el-radio-button label="new">新建患者档案</el-radio-button>
              <el-radio-button label="existing" :disabled="matchedPatients.length === 0">关联已有患者</el-radio-button>
            </el-radio-group>

            <div v-if="matchedPatients.length > 0" class="patient-match-overview">
              <div class="patient-match-stat">
                <span>疑似患者</span>
                <strong>{{ matchedPatients.length }} 个</strong>
              </div>
              <div class="patient-match-stat">
                <span>历史处方</span>
                <strong>{{ matchedHistoryCount }} 张</strong>
              </div>
              <div v-if="selectedPatient" class="patient-match-stat emphasized">
                <span>当前选择</span>
                <strong>{{ selectedPatient.name }} · {{ matchLevelLabelMap[selectedPatient.matchLevel] }}</strong>
              </div>
            </div>

            <div v-if="matchedPatients.length > 0" class="patient-candidate-list">
              <label
                v-for="candidate in matchedPatients"
                :key="candidate.id"
                class="patient-candidate"
                :class="{ selected: selectedPatientId === candidate.id && patientChoiceMode === 'existing' }"
              >
                <input v-model="selectedPatientId" type="radio" :value="candidate.id" :disabled="patientChoiceMode !== 'existing'" />
                <div class="patient-candidate-body">
                  <div class="patient-candidate-main">
                    <strong>{{ candidate.name }} / {{ candidate.gender }} / {{ candidate.age }}岁</strong>
                    <span>{{ candidate.patientNo }} · {{ matchLevelLabelMap[candidate.matchLevel] }} · 历史处方 {{ candidate.prescriptionCount }} 张</span>
                  </div>
                  <div class="patient-candidate-side">
                    <span class="patient-match-badge" :class="candidate.matchLevel">{{ matchLevelLabelMap[candidate.matchLevel] }}</span>
                    <small v-if="candidate.lastPrescriptionDate">最近处方 {{ candidate.lastPrescriptionDate }}</small>
                  </div>
                </div>
              </label>
            </div>
            <p v-else class="login-tip">当前没有匹配到近似患者，确认入库时会自动创建新的患者主档。</p>
          </div>

          <div class="items-head">
            <div>
              <strong>药味明细</strong>
              <p>支持新增、删除、修改和顺序调整</p>
            </div>
            <el-button @click="addItem">新增药味</el-button>
          </div>

          <div class="table-shell">
            <table class="data-table compact recognition-items-table">
              <colgroup>
                <col style="width: 48px" />
                <col style="min-width: 52px" />
                <col style="width: 72px" />
                <col style="width: 68px" />
                <col style="width: 52px" />
                <col style="width: 64px" />
              </colgroup>
              <thead>
                <tr>
                  <th>#</th>
                  <th>药材名</th>
                  <th>剂量</th>
                  <th>单位</th>
                  <th>置信度</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(item, index) in items" :key="`${item.sortNo}-${index}`">
                  <td>{{ index + 1 }}</td>
                  <td><el-input v-model="item.herbName" /></td>
                  <td>
                    <el-input
                      :model-value="String(item.dosage ?? '')"
                      inputmode="decimal"
                      @update:model-value="updateItemDosage(index, $event)"
                    />
                  </td>
                  <td><el-input v-model="item.unit" /></td>
                  <td>
                    <ConfidenceBadge :value="data?.items[index]?.confidence ?? 1" compact />
                  </td>
                  <td>
                    <el-button text type="danger" :disabled="items.length === 1" @click="removeItem(index)">
                      删除
                    </el-button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="action-strip">
            <el-button>保存草稿</el-button>
            <el-button type="primary" :loading="submitting.confirm" @click="confirmDraft">确认入库</el-button>
          </div>
          <p v-if="errorMessage.value" class="login-error">{{ errorMessage.value }}</p>
        </SectionCard>
      </div>
    </div>
  </AppShell>
</template>
