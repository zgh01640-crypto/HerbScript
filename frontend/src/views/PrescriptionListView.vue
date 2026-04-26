<script setup lang="ts">
import { computed, onMounted, reactive } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import AppShell from "../components/AppShell.vue";
import SectionCard from "../components/SectionCard.vue";
import StatusPill from "../components/StatusPill.vue";
import { useAsyncState } from "../composables/useAsyncState";
import { prescriptionService } from "../services/prescriptionService";
import type { PrescriptionFilters, PrescriptionRecord } from "../types/prescription";

const router = useRouter();
const filters = reactive<PrescriptionFilters>({
  keyword: "",
  entryMode: "",
  status: ""
});

const { data, loading, run } = useAsyncState<PrescriptionRecord[]>();

const loadList = async () => {
  await run(() => prescriptionService.getPrescriptionList(filters));
};

const resetFilters = async () => {
  filters.keyword = "";
  filters.entryMode = "";
  filters.status = "";
  await loadList();
};

const rows = computed(() => data.value ?? []);

onMounted(() => {
  void loadList();
});

const removePrescription = async (id: number) => {
  try {
    await ElMessageBox.confirm("删除后将不再显示在列表中，是否继续？", "确认删除", {
      type: "warning"
    });
    await prescriptionService.deletePrescription(id);
    ElMessage.success("处方已删除");
    await loadList();
  } catch {
    // User cancelled or request failed; keep UI unchanged.
  }
};
</script>

<template>
  <AppShell>
    <SectionCard title="处方列表" subtitle="按患者、处方号、日期和状态快速定位记录">
      <template #extra>
        <div class="action-strip">
          <el-button @click="router.push('/prescriptions/new')">手动新增</el-button>
          <el-button type="primary" @click="router.push('/recognition')">图片识别录入</el-button>
        </div>
      </template>

      <div class="filter-grid">
        <el-input v-model="filters.keyword" placeholder="处方号 / 患者姓名 / 诊断" />
        <el-select v-model="filters.entryMode" clearable placeholder="录入方式">
          <el-option label="AI识别" value="ai_recognition" />
          <el-option label="手动录入" value="manual" />
        </el-select>
        <el-select v-model="filters.status" clearable placeholder="校对状态">
          <el-option label="待校对" value="pending_review" />
          <el-option label="已校对" value="verified" />
          <el-option label="已归档" value="archived" />
        </el-select>
        <div class="action-strip">
          <el-button @click="loadList">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </div>
      </div>

      <div v-loading="loading" class="table-shell">
        <table class="data-table">
          <thead>
            <tr>
              <th>患者</th>
              <th>性别</th>
              <th>年龄</th>
              <th>日期</th>
              <th>录入方式</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.id">
              <td>{{ row.patientName }}</td>
              <td>{{ row.gender }}</td>
              <td>{{ row.age }}</td>
              <td>{{ row.prescriptionDate }}</td>
              <td>{{ row.entryMode === "ai_recognition" ? "AI识别" : "手动录入" }}</td>
              <td><StatusPill :status="row.status" /></td>
              <td>
                <div class="table-actions">
                  <el-button text @click="router.push(`/prescriptions/${row.id}`)">详情</el-button>
                  <el-button text @click="router.push(`/prescriptions/${row.id}/edit`)">编辑</el-button>
                  <el-button text type="danger" @click="removePrescription(row.id)">删除</el-button>
                </div>
              </td>
            </tr>
            <tr v-if="rows.length === 0">
              <td colspan="7" class="empty-cell">暂无符合条件的处方记录</td>
            </tr>
          </tbody>
        </table>
      </div>
    </SectionCard>
  </AppShell>
</template>
