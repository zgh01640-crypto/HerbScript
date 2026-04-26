<script setup lang="ts">
import { onMounted } from "vue";
import { useRouter } from "vue-router";
import AppShell from "../components/AppShell.vue";
import SectionCard from "../components/SectionCard.vue";
import StatCard from "../components/StatCard.vue";
import { useAsyncState } from "../composables/useAsyncState";
import { prescriptionService } from "../services/prescriptionService";
import type { DashboardSummary } from "../types/prescription";

const router = useRouter();
const { data, loading, run } = useAsyncState<DashboardSummary>();

onMounted(() => {
  void run(() => prescriptionService.getDashboardSummary());
});
</script>

<template>
  <AppShell>
    <div v-loading="loading" class="dashboard-grid">
      <StatCard label="今日新增" :value="String(data?.todayNewCount ?? 0)" trend="+3 较昨日" />
      <StatCard label="待人工校对" :value="String(data?.pendingReviewCount ?? 0)" trend="优先处理低置信处方" />
      <StatCard
        label="本周已完成"
        :value="String(data?.verifiedWeekCount ?? 0)"
        :trend="`识别成功率 ${Math.round((data?.recognitionSuccessRate ?? 0) * 100)}%`"
      />
    </div>

    <div class="two-column">
      <SectionCard title="近期处方记录" subtitle="优先关注 AI 识别后待确认的数据">
        <div class="record-list">
          <button
            v-for="item in data?.recentPrescriptions ?? []"
            :key="item.prescriptionNo"
            class="record-row record-button"
            type="button"
            @click="router.push(`/prescriptions/${item.id}`)"
          >
            <div>
              <strong>{{ item.prescriptionNo }}</strong>
              <span>{{ item.patientName }}</span>
            </div>
            <div>{{ item.entryMode === "ai_recognition" ? "AI识别" : "手动录入" }}</div>
            <div>{{ item.status === "pending_review" ? "待校对" : item.status === "verified" ? "已校对" : "已归档" }}</div>
          </button>
        </div>
      </SectionCard>

      <SectionCard title="工作建议" subtitle="把最影响闭环效率的动作放到首页">
        <div class="quick-grid">
          <button class="quick-card quick-button" type="button" @click="router.push('/recognition')">
            <strong>开始图片识别</strong>
            <p>上传新处方，交给模型生成结构化草稿。</p>
          </button>
          <button class="quick-card quick-button" type="button" @click="router.push('/prescriptions')">
            <strong>处理低置信字段</strong>
            <p>优先修正诊断、剂量、特殊煎服说明。</p>
          </button>
          <div class="quick-card">
            <strong>维护药材字典</strong>
            <p>增强模型输出与标准药材名的映射一致性。</p>
          </div>
        </div>
      </SectionCard>
    </div>
  </AppShell>
</template>
