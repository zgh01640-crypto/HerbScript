<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import AppShell from "../components/AppShell.vue";
import SectionCard from "../components/SectionCard.vue";
import { useAsyncState } from "../composables/useAsyncState";
import { modelConfigService } from "../services/modelConfigService";
import type { ModelConfigPage, ModelConfigProfile } from "../types/model-config";

const { data, loading, run } = useAsyncState<ModelConfigPage>();
const saving = ref(false);
const switching = ref<number | null>(null);
const selectedProfileId = ref<number | null>(null);
const form = reactive({
  profileId: undefined as number | undefined,
  profileName: "",
  provider: "",
  doubaoBaseUrl: "",
  doubaoModel: "",
  doubaoChatPath: "",
  fallbackToMockOnError: true,
  doubaoApiKey: "",
  clearApiKey: false,
  activate: true
});

const profiles = computed(() => data.value?.profiles ?? []);
const currentProfile = computed(() => profiles.value.find((profile) => profile.id === selectedProfileId.value) ?? null);
const activeProfile = computed(() => profiles.value.find((profile) => profile.active) ?? null);
const onlineCount = computed(() => profiles.value.filter((profile) => profile.online).length);

const apiKeyStatus = computed(() => {
  if (form.clearApiKey) {
    return "将清空";
  }
  if (currentProfile.value?.apiKeyConfigured) {
    return currentProfile.value.maskedApiKey || "已配置";
  }
  return "未配置";
});

const syncForm = (profile?: ModelConfigProfile | null) => {
  form.profileId = profile?.id;
  form.profileName = profile?.profileName ?? "";
  form.provider = profile?.provider ?? "doubao-seed-2-0-pro";
  form.doubaoBaseUrl = profile?.doubaoBaseUrl ?? "";
  form.doubaoModel = profile?.doubaoModel ?? "";
  form.doubaoChatPath = profile?.doubaoChatPath ?? "";
  form.fallbackToMockOnError = profile?.fallbackToMockOnError ?? true;
  form.doubaoApiKey = "";
  form.clearApiKey = false;
  form.activate = profile?.active ?? true;
};

const pickProfile = (profile: ModelConfigProfile) => {
  selectedProfileId.value = profile.id;
  syncForm(profile);
};

const startNewProfile = () => {
  selectedProfileId.value = null;
  syncForm(null);
  form.profileName = `模型档案 ${profiles.value.length + 1}`;
};

const loadConfig = async () => {
  await run(async () => {
    const page = await modelConfigService.getConfig();
    const profile = page.profiles.find((item) => item.active) ?? page.profiles[0] ?? null;
    selectedProfileId.value = profile?.id ?? null;
    syncForm(profile);
    return page;
  });
};

const saveConfig = async () => {
  saving.value = true;
  try {
    const page = await modelConfigService.saveConfig({
      profileId: form.profileId,
      profileName: form.profileName,
      provider: form.provider,
      doubaoBaseUrl: form.doubaoBaseUrl,
      doubaoModel: form.doubaoModel,
      doubaoChatPath: form.doubaoChatPath,
      fallbackToMockOnError: form.fallbackToMockOnError,
      doubaoApiKey: form.doubaoApiKey.trim() || undefined,
      clearApiKey: form.clearApiKey,
      activate: form.activate
    });
    data.value = page;
    const profile = page.profiles.find((item) => item.profileName === form.profileName) ?? page.profiles.find((item) => item.active) ?? page.profiles[0];
    if (profile) {
      pickProfile(profile);
    }
    ElMessage.success("模型档案已保存");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "模型档案保存失败");
  } finally {
    saving.value = false;
  }
};

const activateProfile = async (profile: ModelConfigProfile) => {
  switching.value = profile.id;
  try {
    const page = await modelConfigService.activateConfig(profile.id);
    data.value = page;
    const next = page.profiles.find((item) => item.id === profile.id) ?? null;
    if (next) {
      pickProfile(next);
    }
    ElMessage.success(`已切换到 ${profile.profileName}`);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "模型切换失败");
  } finally {
    switching.value = null;
  }
};

onMounted(() => {
  void loadConfig();
});
</script>

<template>
  <AppShell>
    <SectionCard title="模型配置管理" subtitle="保存多套模型档案，并随时切换当前识别模型">
      <div class="patient-stats-strip">
        <div class="patient-stat-card">
          <span>当前模型</span>
          <strong>{{ activeProfile?.doubaoModel ?? "-" }}</strong>
        </div>
        <div class="patient-stat-card">
          <span>在线档案</span>
          <strong>{{ onlineCount }} 套</strong>
        </div>
        <div class="patient-stat-card">
          <span>配置档案</span>
          <strong>{{ profiles.length }} 套</strong>
        </div>
      </div>

      <div class="workbench-grid model-config-layout">
        <SectionCard title="模型档案" subtitle="保存多套配置并一键切换">
          <template #extra>
            <el-button @click="startNewProfile">新增档案</el-button>
          </template>

          <div v-loading="loading" class="patient-candidate-list">
            <button
              v-for="profile in profiles"
              :key="profile.id"
              type="button"
              class="patient-candidate model-profile-card"
              :class="{ selected: selectedProfileId === profile.id }"
              @click="pickProfile(profile)"
            >
              <div class="patient-candidate-body">
                <div class="patient-candidate-main">
                  <strong>{{ profile.profileName }}</strong>
                  <span>{{ profile.doubaoModel }} · {{ profile.provider }}</span>
                </div>
                <div class="patient-candidate-side">
                  <span class="patient-match-badge" :class="profile.online ? 'high' : 'low'">
                    {{ profile.active ? "当前生效" : profile.online ? "可用" : "待配置" }}
                  </span>
                  <small>{{ profile.updatedAt }}</small>
                </div>
              </div>
              <div class="model-profile-actions">
                <span>{{ profile.maskedApiKey || "未配置 Key" }}</span>
                <el-button
                  v-if="!profile.active"
                  size="small"
                  :loading="switching === profile.id"
                  @click.stop="activateProfile(profile)"
                >
                  设为当前
                </el-button>
              </div>
            </button>

            <div v-if="profiles.length === 0" class="detail-image-empty">
              当前还没有模型档案，请先新增一套配置。
            </div>
          </div>
        </SectionCard>

        <SectionCard title="档案编辑" subtitle="可保存为新档案，也可修改当前档案">
          <div v-loading="loading" class="form-grid model-config-grid">
            <div class="field-box">
              <label>档案名称</label>
              <el-input v-model="form.profileName" />
            </div>
            <div class="field-box">
              <label>提供方</label>
              <el-input v-model="form.provider" />
            </div>
            <div class="field-box">
              <label>模型名称</label>
              <el-input v-model="form.doubaoModel" />
            </div>
            <div class="field-box">
              <label>Chat Path</label>
              <el-input v-model="form.doubaoChatPath" />
            </div>
            <div class="field-box patient-remark-box">
              <label>Base URL</label>
              <el-input v-model="form.doubaoBaseUrl" />
            </div>
            <div class="field-box">
              <label>回退策略</label>
              <el-switch
                v-model="form.fallbackToMockOnError"
                inline-prompt
                active-text="允许回退"
                inactive-text="严格失败"
              />
            </div>
            <div class="field-box">
              <label>保存后生效</label>
              <el-switch
                v-model="form.activate"
                inline-prompt
                active-text="立即启用"
                inactive-text="仅保存"
              />
            </div>
            <div class="field-box patient-remark-box">
              <label>更新 API Key</label>
              <el-input v-model="form.doubaoApiKey" type="password" show-password placeholder="留空则保持当前配置" />
              <p class="login-tip compact-tip">当前状态：{{ apiKeyStatus }}</p>
              <el-checkbox v-model="form.clearApiKey">清空现有 API Key</el-checkbox>
            </div>
          </div>

          <div class="action-strip">
            <el-button @click="loadConfig">重新读取</el-button>
            <el-button type="primary" :loading="saving" @click="saveConfig">保存档案</el-button>
          </div>
        </SectionCard>
      </div>
    </SectionCard>
  </AppShell>
</template>
