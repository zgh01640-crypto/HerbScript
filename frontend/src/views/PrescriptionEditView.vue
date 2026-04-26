<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import AppShell from "../components/AppShell.vue";
import SectionCard from "../components/SectionCard.vue";
import { prescriptionService } from "../services/prescriptionService";
import type { PrescriptionItemInput, PrescriptionRecord, PrescriptionSavePayload } from "../types/prescription";

const route = useRoute();
const router = useRouter();
const prescriptionId = Number(route.params.id);
const loading = ref(false);
const saving = ref(false);
const errorMessage = ref("");
const record = ref<PrescriptionRecord | undefined>();
const imagePreviewVisible = ref(false);
const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? "http://localhost:8081";

const form = reactive<PrescriptionSavePayload>({
  hospitalName: "",
  prescriptionType: "",
  patientName: "",
  gender: "",
  age: 0,
  department: "",
  diagnosis: "",
  doseCount: 1,
  prescriptionDate: "",
  doctorName: "",
  usageMethod: "",
  remark: "",
  items: []
});

const hydrate = (record: PrescriptionRecord) => {
  form.hospitalName = "";
  form.prescriptionType = record.entryMode === "manual" ? "手动录入处方" : "识别校对处方";
  form.patientName = record.patientName;
  form.gender = record.gender;
  form.age = record.age;
  form.department = record.department;
  form.diagnosis = record.diagnosis;
  form.doseCount = record.doseCount;
  form.prescriptionDate = record.prescriptionDate;
  form.doctorName = record.doctorName;
  form.usageMethod = record.usageMethod;
  form.remark = "";
  form.items.splice(
    0,
    form.items.length,
    ...record.items.map<PrescriptionItemInput>((item, index) => ({
      sortNo: index + 1,
      herbName: item.herbName,
      rawHerbName: item.rawHerbName,
      dosage: item.dosage,
      unit: item.unit,
      specialInstruction: item.specialInstruction
    }))
  );
};

const previewImageUrl = computed(() => {
  const sourceImageUrl = record.value?.sourceImageUrl;

  if (!sourceImageUrl) {
    return "";
  }

  if (sourceImageUrl.startsWith("http://") || sourceImageUrl.startsWith("https://")) {
    return sourceImageUrl;
  }

  return `${apiBaseUrl}${sourceImageUrl.startsWith("/") ? sourceImageUrl : `/${sourceImageUrl}`}`;
});

const loadDetail = async () => {
  loading.value = true;
  errorMessage.value = "";
  try {
    const detail = await prescriptionService.getPrescriptionDetail(prescriptionId);
    if (!detail) {
      throw new Error("处方不存在");
    }
    record.value = detail;
    hydrate(detail);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "加载失败";
  } finally {
    loading.value = false;
  }
};

const addItem = () => {
  form.items.push({
    sortNo: form.items.length + 1,
    herbName: "",
    dosage: 10,
    unit: "g",
    specialInstruction: ""
  });
};

const removeItem = (index: number) => {
  form.items.splice(index, 1);
  form.items.forEach((item, idx) => {
    item.sortNo = idx + 1;
  });
};

const submit = async () => {
  saving.value = true;
  errorMessage.value = "";
  try {
    const payload: PrescriptionSavePayload = {
      ...form,
      items: form.items.map((item, index) => ({ ...item, sortNo: index + 1 }))
    };
    const result = await prescriptionService.updatePrescription(prescriptionId, payload);
    await router.push(`/prescriptions/${result.id}`);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "保存失败";
  } finally {
    saving.value = false;
  }
};

const openImagePreview = () => {
  if (previewImageUrl.value) {
    imagePreviewVisible.value = true;
  }
};

onMounted(() => {
  void loadDetail();
});
</script>

<template>
  <AppShell>
    <div v-loading="loading" class="detail-grid">
      <div class="detail-summary-bar">
        <div class="detail-summary-item">
          <span>编辑对象</span>
          <strong class="mono">{{ record?.prescriptionNo ?? "-" }}</strong>
        </div>
        <div class="detail-summary-item">
          <span>录入方式</span>
          <strong>{{ record?.entryMode === "manual" ? "手动录入" : "AI识别" }}</strong>
        </div>
        <div class="action-strip">
          <el-button @click="router.push(`/prescriptions/${prescriptionId}`)">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submit">保存修改</el-button>
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

        <SectionCard title="编辑处方" subtitle="修改基础信息，保存后返回详情页">
          <div class="form-grid">
            <div class="field-box">
              <label>患者姓名</label>
              <el-input v-model="form.patientName" />
            </div>
            <div class="field-box">
              <label>性别</label>
              <el-select v-model="form.gender" placeholder="请选择性别">
                <el-option label="男" value="男" />
                <el-option label="女" value="女" />
              </el-select>
            </div>
            <div class="field-box">
              <label>年龄</label>
              <el-input-number v-model="form.age" :min="0" :max="120" style="width: 100%" />
            </div>
            <div class="field-box">
              <label>科室</label>
              <el-input v-model="form.department" />
            </div>
            <div class="field-box">
              <label>临床诊断</label>
              <el-input v-model="form.diagnosis" />
            </div>
            <div class="field-box">
              <label>剂数</label>
              <el-input-number v-model="form.doseCount" :min="1" :max="99" style="width: 100%" />
            </div>
            <div class="field-box">
              <label>处方日期</label>
              <el-input v-model="form.prescriptionDate" type="date" />
            </div>
            <div class="field-box">
              <label>医师信息</label>
              <el-input v-model="form.doctorName" />
            </div>
            <div class="field-box">
              <label>服用说明</label>
              <el-input v-model="form.usageMethod" />
            </div>
            <div class="field-box">
              <label>备注</label>
              <el-input v-model="form.remark" />
            </div>
          </div>

          <div class="items-head">
            <div>
              <strong>药味明细</strong>
              <p>支持编辑、增加和删除药味</p>
            </div>
            <el-button @click="addItem">新增药味</el-button>
          </div>

          <div class="table-shell">
            <table class="data-table compact">
              <thead>
                <tr>
                  <th>#</th>
                  <th>药材名</th>
                  <th>剂量</th>
                  <th>单位</th>
                  <th>特殊说明</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(item, index) in form.items" :key="`${item.sortNo}-${index}`">
                  <td>{{ index + 1 }}</td>
                  <td><el-input v-model="item.herbName" /></td>
                  <td><el-input-number v-model="item.dosage" :min="0.01" :step="1" style="width: 100%" /></td>
                  <td><el-input v-model="item.unit" /></td>
                  <td><el-input v-model="item.specialInstruction" /></td>
                  <td>
                    <el-button text type="danger" :disabled="form.items.length === 1" @click="removeItem(index)">删除</el-button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </SectionCard>
      </div>

      <el-dialog v-model="imagePreviewVisible" title="处方原图" width="56%" top="2vh">
        <div class="preview-dialog-body">
          <img v-if="previewImageUrl" :src="previewImageUrl" alt="处方原图放大预览" class="preview-dialog-image" />
        </div>
      </el-dialog>

      <p v-if="errorMessage" class="login-error">{{ errorMessage }}</p>
    </div>
  </AppShell>
</template>
