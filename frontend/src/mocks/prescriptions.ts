import type { DashboardSummary, PrescriptionRecord } from "../types/prescription";

export const prescriptions: PrescriptionRecord[] = [
  {
    id: 1,
    prescriptionNo: "HS202604210012",
    patientName: "王秀兰",
    gender: "女",
    age: 58,
    department: "内科",
    diagnosis: "脾胃虚弱",
    doseCount: 7,
    prescriptionDate: "2026-04-21",
    doctorName: "李医生",
    usageMethod: "水煎服，每日一剂",
    entryMode: "ai_recognition",
    status: "pending_review",
    createdByName: "录入员王芳",
    createdAt: "2026-04-21 10:22:00",
    sourceModel: "doubao-seed-2-0-pro",
    sourceImageUrl: "/mock/prescriptions/1.png",
    items: [
      { id: 101, sortNo: 1, herbName: "黄芪", rawHerbName: "黄芪", dosage: 15, unit: "g", confidence: 0.98, effectHint: "补气固表" },
      { id: 102, sortNo: 2, herbName: "党参", rawHerbName: "党参", dosage: 12, unit: "g", confidence: 0.94, effectHint: "健脾益肺" },
      { id: 103, sortNo: 3, herbName: "白术", rawHerbName: "白术", dosage: 10, unit: "g", specialInstruction: "麸炒", confidence: 0.83, effectHint: "燥湿健脾" },
      { id: 104, sortNo: 4, herbName: "炙甘草", rawHerbName: "炙甘草", dosage: 6, unit: "g", confidence: 0.72, effectHint: "调和诸药" }
    ],
    logs: [
      { id: 1, time: "2026-04-21 10:22", content: "模型生成识别草稿，标记 2 个低置信字段。" },
      { id: 2, time: "2026-04-21 10:24", content: "录入员开始人工校对处方字段与药味明细。" }
    ],
    recognitionDraft: {
      taskId: 2001,
      providerName: "doubao-seed-2-0-pro",
      imageUrl: "/mock/prescriptions/1.png",
      warnings: ["服用说明识别置信度较低", "白术特殊说明疑似为“麸炒”"],
      lowConfidenceFields: ["diagnosis", "usageMethod"]
    }
  },
  {
    id: 2,
    prescriptionNo: "HS202604210011",
    patientName: "赵明德",
    gender: "男",
    age: 43,
    department: "脾胃病科",
    diagnosis: "食少乏力",
    doseCount: 5,
    prescriptionDate: "2026-04-21",
    doctorName: "陈医生",
    usageMethod: "温水煎服",
    entryMode: "manual",
    status: "verified",
    createdByName: "录入员陈雨",
    createdAt: "2026-04-21 09:46:00",
    items: [
      { id: 201, sortNo: 1, herbName: "山药", dosage: 20, unit: "g", effectHint: "补脾养胃" },
      { id: 202, sortNo: 2, herbName: "茯苓", dosage: 12, unit: "g", effectHint: "健脾渗湿" },
      { id: 203, sortNo: 3, herbName: "陈皮", dosage: 6, unit: "g", effectHint: "理气和中" }
    ],
    logs: [
      { id: 3, time: "2026-04-21 09:46", content: "录入员手动新增处方并保存。" },
      { id: 4, time: "2026-04-21 09:48", content: "医师完成内容复核。" }
    ]
  },
  {
    id: 3,
    prescriptionNo: "HS202604210010",
    patientName: "刘海峰",
    gender: "男",
    age: 39,
    department: "肝胆科",
    diagnosis: "肝郁气滞",
    doseCount: 6,
    prescriptionDate: "2026-04-20",
    doctorName: "孙医生",
    usageMethod: "煎服，分早晚两次",
    entryMode: "ai_recognition",
    status: "archived",
    createdByName: "录入员王芳",
    createdAt: "2026-04-20 16:31:00",
    sourceModel: "doubao-seed-2-0-pro",
    items: [
      { id: 301, sortNo: 1, herbName: "柴胡", dosage: 10, unit: "g", confidence: 0.96, effectHint: "疏肝解郁" },
      { id: 302, sortNo: 2, herbName: "香附", dosage: 9, unit: "g", confidence: 0.91, effectHint: "理气解郁" },
      { id: 303, sortNo: 3, herbName: "白芍", dosage: 12, unit: "g", confidence: 0.93, effectHint: "养血柔肝" }
    ],
    logs: [
      { id: 5, time: "2026-04-20 16:22", content: "模型生成识别草稿。" },
      { id: 6, time: "2026-04-20 16:27", content: "录入员完成校对并确认入库。" },
      { id: 7, time: "2026-04-20 16:31", content: "管理员复核并归档处方。" }
    ],
    recognitionDraft: {
      taskId: 2002,
      providerName: "doubao-seed-2-0-pro",
      imageUrl: "/mock/prescriptions/3.png",
      warnings: [],
      lowConfidenceFields: []
    }
  }
];

export const dashboardSummary: DashboardSummary = {
  todayNewCount: 12,
  pendingReviewCount: 8,
  verifiedWeekCount: 126,
  recognitionSuccessRate: 0.91,
  recentPrescriptions: prescriptions
};
