<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter, useRoute } from "vue-router";
import { recognitionUploadState } from "../composables/useRecognitionUploadState";
import { authService } from "../services/authService";
import { prescriptionService } from "../services/prescriptionService";

const router = useRouter();
const route = useRoute();
const currentUser = computed(() => authService.getStoredUser());
const pendingReviewCount = ref<number>(0);
const isRecognitionPage = computed(() => route.path === "/recognition");
const isRecognitionUploading = computed(() => recognitionUploadState.status === "uploading");

const menuItems = [
  { label: "概览", path: "/dashboard", badge: "概览" },
  { label: "患者管理", path: "/patients", badge: "档案" },
  { label: "处方识别", path: "/recognition", badge: "AI" },
  { label: "处方列表", path: "/prescriptions", badge: "核心" },
  { label: "药材字典", path: "/herbs", badge: "标准" },
  { label: "用户管理", path: "/users", badge: "权限" },
  { label: "模型配置", path: "/model-config", badge: "设置" }
];

const pageTitle = computed(() => {
  if (route.path.startsWith("/prescriptions/")) {
    return "处方详情";
  }

  if (route.path.startsWith("/patients/")) {
    return "患者详情";
  }

  const current = menuItems.find((item) => item.path === route.path);
  return current?.label ?? "HerbScript";
});

const logout = () => {
  authService.logout();
  void router.push("/login");
};

onMounted(async () => {
  try {
    const summary = await prescriptionService.getDashboardSummary();
    pendingReviewCount.value = summary.pendingReviewCount;
  } catch {
    pendingReviewCount.value = 0;
  }
});
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand-block">
        <div class="brand-code">HS</div>
        <div>
          <div class="brand-title">HerbScript</div>
          <div class="brand-subtitle">本草处方系统</div>
        </div>
      </div>

      <nav class="nav-list">
        <RouterLink
          v-for="item in menuItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: route.path === item.path }"
        >
          <span>{{ item.label }}</span>
          <small>{{ item.badge }}</small>
        </RouterLink>

        <div class="sidebar-footnote compact online">
          <span class="sidebar-online-dot" />
          <div>
            <div>Vision Model</div>
            <strong>doubao-seed-2-0-pro</strong>
          </div>
        </div>
      </nav>
    </aside>

    <div class="main-panel">
      <header class="topbar">
        <div>
          <div class="eyebrow">
            <span>TCM Prescription Platform</span>
            <span v-if="isRecognitionPage" class="eyebrow-model">Vision Model · doubao-seed-2-0-pro</span>
          </div>
          <h1>{{ pageTitle }}</h1>
        </div>
        <div class="topbar-meta">
          <button
            v-if="isRecognitionUploading"
            class="recognition-global-indicator"
            type="button"
            @click="router.push('/recognition')"
          >
            <span class="recognition-global-dot" />
            <strong>识别进行中</strong>
            <small>{{ recognitionUploadState.fileName || "处理中" }}</small>
          </button>
          <div class="meta-card">
            <span>今日待校对</span>
            <strong>{{ pendingReviewCount }}</strong>
          </div>
          <button class="avatar-pill avatar-button" type="button" @click="logout">
            {{ currentUser?.realName ?? "管理员" }}
          </button>
        </div>
      </header>

      <main class="content-slot">
        <slot />
      </main>
    </div>
  </div>
</template>
