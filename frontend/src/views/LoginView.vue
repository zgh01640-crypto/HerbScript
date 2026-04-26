<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { authService } from "../services/authService";

const router = useRouter();
const loading = ref(false);
const errorMessage = ref("");
const form = reactive({
  username: "admin",
  password: "123456"
});

const submit = async () => {
  loading.value = true;
  errorMessage.value = "";

  try {
    await authService.login({
      username: form.username,
      password: form.password
    });
    await router.push("/dashboard");
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "登录失败";
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <div class="login-page">
    <section class="login-hero">
      <div class="hero-badge">AI Assisted TCM Workflow</div>
      <h1>HerbScript</h1>
      <h2>把纸方变成可检索、可校对、可追溯的数据资产</h2>
      <p>
        面向中医门诊、中医馆与药房录入场景，支持处方图片直接交给
        `doubao-seed-2-0-pro` 识别，再由人工完成最终确认。
      </p>

      <div class="hero-grid">
        <div class="hero-panel">
          <span>识别模型</span>
          <strong>doubao-seed-2-0-pro</strong>
        </div>
        <div class="hero-panel">
          <span>录入模式</span>
          <strong>AI 识别 + 人工校对</strong>
        </div>
        <div class="hero-panel">
          <span>追溯机制</span>
          <strong>原图 + 原始响应 + 最终结果</strong>
        </div>
      </div>
    </section>

    <section class="login-card">
      <div class="login-card-head">
        <span>系统登录</span>
        <strong>本草处方系统</strong>
      </div>

      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-form-item>
          <el-button
            class="primary-action"
            type="primary"
            size="large"
            style="width: 100%"
            :loading="loading"
            @click="submit"
          >
            进入工作台
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-tip">
        当前默认使用后端真实登录接口，演示账号可直接使用 `admin / 123456`。
      </div>
      <p v-if="errorMessage" class="login-error">{{ errorMessage }}</p>
    </section>
  </div>
</template>
