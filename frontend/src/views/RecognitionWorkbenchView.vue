<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { useRouter } from "vue-router";
import AppShell from "../components/AppShell.vue";
import ConfidenceBadge from "../components/ConfidenceBadge.vue";
import RecognitionUploader from "../components/RecognitionUploader.vue";
import SectionCard from "../components/SectionCard.vue";
import { useAsyncState } from "../composables/useAsyncState";
import { prescriptionService } from "../services/prescriptionService";
import type { PrescriptionItemInput, PrescriptionRecord } from "../types/prescription";

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
const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? "http://localhost:8081";
const localPreviewUrl = ref("");
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
const hiddenWarnings = new Set([
  "当前为 Doubao 本地 mock 草稿，待接真实模型响应。"
]);

const items = computed(() => editableItems);
const warnings = computed(() =>
  (data.value?.recognitionDraft?.warnings ?? []).filter((warning) => !hiddenWarnings.has(warning))
);
const lowConfidenceFields = computed(() => new Set(data.value?.recognitionDraft?.lowConfidenceFields ?? []));
const previewImageUrl = computed(() => {
  if (localPreviewUrl.value) {
    return localPreviewUrl.value;
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
const recognitionJsonSource = computed(() => data.value?.recognitionDraft?.rawText ?? "");

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

const loadDraft = async () => {
  await run(() => prescriptionService.getRecognitionDraft());

  if (data.value) {
    hydrateDraft(data.value);
  } else {
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
  if (localPreviewUrl.value) {
    URL.revokeObjectURL(localPreviewUrl.value);
  }
  localPreviewUrl.value = URL.createObjectURL(file);

  try {
    const record = await prescriptionService.uploadRecognitionImage(file);
    data.value = record;
    hydrateDraft(record);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "上传识别失败";
  } finally {
    if (!errorMessage.value && localPreviewUrl.value) {
      URL.revokeObjectURL(localPreviewUrl.value);
      localPreviewUrl.value = "";
    }
    submitting.upload = false;
  }
};

onMounted(() => {
  void loadDraft();
});

onBeforeUnmount(() => {
  if (localPreviewUrl.value) {
    URL.revokeObjectURL(localPreviewUrl.value);
  }
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
</script>

<template>
  <AppShell>
    <div v-loading="loading" class="recognition-page">
      <div class="recognition-topbar">
        <div class="recognition-alert">
          <span class="recognition-alert-label">当前工作台</span>
          <div class="recognition-alert-main">
            <strong>{{ data?.recognitionDraft?.providerName ?? "doubao-seed-2-0-pro" }}</strong>
            <span class="recognition-alert-chip">低置信字段 {{ lowConfidenceFields.size }}</span>
          </div>
          <p>先核对左侧原图，再确认入库。</p>
        </div>

        <div class="recognition-upload-panel">
          <RecognitionUploader @uploaded="uploadImage" />
        </div>
      </div>
      <div v-if="submitting.upload" class="recognition-status-banner active">
        正在上传图片并调用 AI 识别，请等待草稿生成...
      </div>
      <p v-if="!loading && !data" class="login-tip">当前没有待校对草稿，请先上传一张处方图片。</p>

      <div v-if="warnings.length" class="warning-stack">
        <div v-for="warning in warnings" :key="warning" class="warning-chip">
          {{ warning }}
        </div>
      </div>

      <div class="workbench-grid">
        <SectionCard title="处方原图" subtitle="支持缩放、复核和逐项比对">
          <div class="image-stage">
            <div v-if="previewImageUrl" class="image-preview-shell">
              <img :src="previewImageUrl" alt="处方原图" class="recognition-image" />
            </div>
            <div v-else class="image-paper">
              <div class="paper-watermark">Original Prescription</div>
              <div class="paper-lines">
                <span />
                <span />
                <span />
                <span />
                <span />
              </div>
            </div>
          </div>
          <div v-if="data?.sourceImageUrl" class="image-meta">
            <span>图片地址</span>
            <strong class="mono">{{ data.sourceImageUrl }}</strong>
          </div>
          <div v-if="streamedRecognitionJson" class="json-stream-panel">
            <div class="json-stream-head">
              <span>模型识别 JSON</span>
              <strong>{{ data?.recognitionDraft?.providerName ?? "doubao-seed-2-0-pro" }}</strong>
            </div>
            <pre class="json-stream-content">{{ streamedRecognitionJson }}</pre>
          </div>
        </SectionCard>

        <SectionCard title="结构化结果" subtitle="低置信字段已高亮，保存前请逐项确认">
          <div class="form-grid">
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
