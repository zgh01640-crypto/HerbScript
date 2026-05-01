<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import AppShell from "../components/AppShell.vue";
import SectionCard from "../components/SectionCard.vue";
import StatusPill from "../components/StatusPill.vue";
import { useAsyncState } from "../composables/useAsyncState";
import { prescriptionService } from "../services/prescriptionService";
import type { PrescriptionRecord, PrescriptionSummary } from "../types/prescription";

const route = useRoute();
const router = useRouter();
const prescriptionId = computed(() => Number(route.params.id));
const { data, loading, run } = useAsyncState<PrescriptionRecord | undefined>();
const patientHistory = ref<PrescriptionSummary[]>([]);
const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? "http://localhost:8081";
const imagePreviewVisible = ref(false);
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

onMounted(() => {
  void run(async () => {
    const detail = await prescriptionService.getPrescriptionDetail(prescriptionId.value);
    if (detail?.patientId) {
      patientHistory.value = (await prescriptionService.getPatientPrescriptions(detail.patientId))
        .filter((item) => item.id !== detail.id);
    } else {
      patientHistory.value = [];
    }
    return detail;
  });
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

const openImagePreview = () => {
  if (previewImageUrl.value) {
    imagePreviewVisible.value = true;
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

      <el-dialog v-model="imagePreviewVisible" title="处方原图" width="56%" top="2vh">
        <div class="preview-dialog-body">
          <img v-if="previewImageUrl" :src="previewImageUrl" alt="处方原图放大预览" class="preview-dialog-image" />
        </div>
      </el-dialog>
    </div>
  </AppShell>
</template>
