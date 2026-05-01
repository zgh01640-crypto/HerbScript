<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import AppShell from "../components/AppShell.vue";
import SectionCard from "../components/SectionCard.vue";
import { useAsyncState } from "../composables/useAsyncState";
import { prescriptionService } from "../services/prescriptionService";
import type { PatientSummary } from "../types/prescription";

const router = useRouter();
const keyword = ref("");
const { data, loading, run } = useAsyncState<PatientSummary[]>();

const loadPatients = async () => {
  await run(() => prescriptionService.getPatientList(keyword.value));
};

const rows = computed(() => data.value ?? []);

onMounted(() => {
  void loadPatients();
});
</script>

<template>
  <AppShell>
    <SectionCard title="患者管理" subtitle="按患者主档查看累计处方与最近就诊记录">
      <div class="filter-grid patient-filter-grid">
        <el-input v-model="keyword" placeholder="患者编号 / 姓名 / 电话" @keyup.enter="loadPatients" />
        <div class="action-strip">
          <el-button @click="loadPatients">查询</el-button>
        </div>
      </div>

      <div v-loading="loading" class="table-shell">
        <table class="data-table">
          <thead>
            <tr>
              <th>患者编号</th>
              <th>患者姓名</th>
              <th>性别</th>
              <th>年龄</th>
              <th>累计处方</th>
              <th>最近处方</th>
              <th>电话</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.id">
              <td class="mono">{{ row.patientNo }}</td>
              <td>{{ row.name }}</td>
              <td>{{ row.gender }}</td>
              <td>{{ row.age }}</td>
              <td>{{ row.prescriptionCount }}</td>
              <td>{{ row.lastPrescriptionDate || "-" }}</td>
              <td>{{ row.phone || "-" }}</td>
              <td>
                <div class="table-actions">
                  <el-button text @click="router.push(`/patients/${row.id}`)">详情</el-button>
                  <el-button text @click="router.push({ path: '/prescriptions', query: { keyword: row.name } })">查看处方</el-button>
                </div>
              </td>
            </tr>
            <tr v-if="rows.length === 0">
              <td colspan="8" class="empty-cell">暂无患者主档，请先录入或识别处方后自动沉淀</td>
            </tr>
          </tbody>
        </table>
      </div>
    </SectionCard>
  </AppShell>
</template>
