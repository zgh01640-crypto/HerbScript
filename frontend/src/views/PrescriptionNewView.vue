<script setup lang="ts">
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { useRouter } from "vue-router";
import AppShell from "../components/AppShell.vue";
import SectionCard from "../components/SectionCard.vue";
import { prescriptionService } from "../services/prescriptionService";
import type { PatientMatchCandidate, PrescriptionItemInput, PrescriptionSavePayload } from "../types/prescription";

const router = useRouter();
const saving = ref(false);
const errorMessage = ref("");
const matching = ref(false);
const matchedPatients = ref<PatientMatchCandidate[]>([]);
const selectedPatientId = ref<number | null>(null);
const patientChoiceMode = ref<"new" | "existing">("new");

const form = reactive<PrescriptionSavePayload>({
  hospitalName: "本草中医馆",
  prescriptionType: "中药饮片",
  patientName: "",
  gender: "",
  age: 30,
  department: "",
  diagnosis: "",
  doseCount: 7,
  prescriptionDate: new Date().toISOString().slice(0, 10),
  doctorName: "",
  usageMethod: "水煎服，每日一剂",
  remark: "",
  items: [
    {
      sortNo: 1,
      herbName: "",
      dosage: 10,
      unit: "g",
      specialInstruction: ""
    }
  ]
});

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

const runPatientMatch = async () => {
  if (!form.patientName || !form.gender || !form.age) {
    ElMessage.warning("请先填写患者姓名、性别和年龄");
    return;
  }

  matching.value = true;
  try {
    matchedPatients.value = await prescriptionService.matchPatients({
      name: form.patientName,
      gender: form.gender,
      age: form.age
    });
    if (matchedPatients.value.length > 0) {
      patientChoiceMode.value = "existing";
      selectedPatientId.value = matchedPatients.value[0].id;
    } else {
      patientChoiceMode.value = "new";
      selectedPatientId.value = null;
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "患者匹配失败";
  } finally {
    matching.value = false;
  }
};

const submit = async () => {
  saving.value = true;
  errorMessage.value = "";

  try {
    const payload: PrescriptionSavePayload = {
      ...form,
      patientId: patientChoiceMode.value === "existing" ? selectedPatientId.value ?? undefined : undefined,
      patientDraft: patientChoiceMode.value === "new"
        ? {
            name: form.patientName,
            gender: form.gender,
            age: form.age
          }
        : undefined,
      items: form.items.map((item: PrescriptionItemInput, index) => ({
        ...item,
        sortNo: index + 1
      }))
    };
    const result = await prescriptionService.createPrescription(payload);
    await router.push(`/prescriptions/${result.id}`);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "保存失败";
  } finally {
    saving.value = false;
  }
};
</script>

<template>
  <AppShell>
    <SectionCard title="手动新增处方" subtitle="直接录入处方基础信息和药味明细，保存后进入详情页">
      <div class="form-grid">
        <div class="field-box">
          <label>患者姓名</label>
          <el-input v-model="form.patientName" placeholder="请输入患者姓名" />
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
        <div class="field-box field-box-action">
          <label>患者匹配</label>
          <el-button :loading="matching" @click="runPatientMatch">匹配已有患者</el-button>
        </div>
        <div class="field-box">
          <label>科室</label>
          <el-input v-model="form.department" placeholder="例如：内科" />
        </div>
        <div class="field-box">
          <label>临床诊断</label>
          <el-input v-model="form.diagnosis" placeholder="请输入临床诊断" />
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
          <el-input v-model="form.doctorName" placeholder="请输入医师姓名" />
        </div>
        <div class="field-box">
          <label>服用说明</label>
          <el-input v-model="form.usageMethod" placeholder="例如：水煎服，每日一剂" />
        </div>
        <div class="field-box">
          <label>备注</label>
          <el-input v-model="form.remark" placeholder="可填写补充说明" />
        </div>
      </div>

      <div class="patient-match-panel">
        <div class="patient-match-head">
          <strong>患者确认</strong>
          <p>同一患者可关联多张处方，系统先给出近似匹配候选，再由人工确认。</p>
        </div>
        <el-radio-group v-model="patientChoiceMode">
          <el-radio-button label="new">新建患者档案</el-radio-button>
          <el-radio-button label="existing" :disabled="matchedPatients.length === 0">关联已有患者</el-radio-button>
        </el-radio-group>

        <div v-if="matchedPatients.length > 0" class="patient-candidate-list">
          <label
            v-for="candidate in matchedPatients"
            :key="candidate.id"
            class="patient-candidate"
            :class="{ selected: selectedPatientId === candidate.id && patientChoiceMode === 'existing' }"
          >
            <input v-model="selectedPatientId" type="radio" :value="candidate.id" :disabled="patientChoiceMode !== 'existing'" />
            <div>
              <strong>{{ candidate.name }} / {{ candidate.gender }} / {{ candidate.age }}岁</strong>
              <span>{{ candidate.patientNo }} · {{ candidate.matchLevel }} 匹配 · 历史处方 {{ candidate.prescriptionCount }} 张</span>
            </div>
          </label>
        </div>
        <p v-else class="login-tip">当前没有匹配到近似患者，保存时将创建新的患者主档。</p>
      </div>

      <div class="items-head">
        <div>
          <strong>药味明细</strong>
          <p>至少填写一味药材，支持动态新增与删除</p>
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
              <td><el-input v-model="item.herbName" placeholder="如：黄芪" /></td>
              <td><el-input-number v-model="item.dosage" :min="0.01" :step="1" style="width: 100%" /></td>
              <td><el-input v-model="item.unit" placeholder="g" /></td>
              <td><el-input v-model="item.specialInstruction" placeholder="如：麸炒" /></td>
              <td>
                <el-button text type="danger" :disabled="form.items.length === 1" @click="removeItem(index)">
                  删除
                </el-button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="action-strip submit-strip">
        <el-button @click="router.push('/prescriptions')">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存处方</el-button>
      </div>

      <p v-if="errorMessage" class="login-error">{{ errorMessage }}</p>
    </SectionCard>
  </AppShell>
</template>
