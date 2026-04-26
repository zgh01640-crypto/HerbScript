export type PrescriptionStatus = "draft" | "pending_review" | "verified" | "archived";
export type EntryMode = "manual" | "ai_recognition";

export interface PrescriptionItem {
  id: number;
  sortNo: number;
  herbName: string;
  rawHerbName?: string;
  dosage: number;
  unit: string;
  specialInstruction?: string;
  confidence?: number;
  effectHint?: string;
}

export interface OperationLogEntry {
  id: number;
  time: string;
  content: string;
}

export interface RecognitionDraft {
  taskId: number;
  providerName: string;
  imageUrl: string;
  rawText?: string;
  warnings: string[];
  lowConfidenceFields: string[];
}

export interface RecognitionConfirmPayload {
  patientName: string;
  gender: string;
  age: number;
  department?: string;
  diagnosis?: string;
  doseCount: number;
  prescriptionDate: string;
  doctorName?: string;
  usageMethod?: string;
  remark?: string;
  items: PrescriptionItemInput[];
}

export interface PrescriptionRecord {
  id: number;
  prescriptionNo: string;
  patientName: string;
  gender: string;
  age: number;
  department: string;
  diagnosis: string;
  doseCount: number;
  prescriptionDate: string;
  doctorName: string;
  usageMethod: string;
  entryMode: EntryMode;
  status: PrescriptionStatus;
  createdByName: string;
  createdAt: string;
  sourceModel?: string;
  sourceImageUrl?: string;
  items: PrescriptionItem[];
  logs: OperationLogEntry[];
  recognitionDraft?: RecognitionDraft;
}

export interface DashboardSummary {
  todayNewCount: number;
  pendingReviewCount: number;
  verifiedWeekCount: number;
  recognitionSuccessRate: number;
  recentPrescriptions: PrescriptionRecord[];
}

export interface PrescriptionFilters {
  keyword: string;
  entryMode: "" | EntryMode;
  status: "" | PrescriptionStatus;
}

export interface PrescriptionItemInput {
  sortNo: number;
  herbName: string;
  rawHerbName?: string;
  dosage: number;
  unit: string;
  specialInstruction?: string;
}

export interface PrescriptionSavePayload {
  hospitalName?: string;
  prescriptionType?: string;
  patientName: string;
  gender: string;
  age: number;
  department?: string;
  diagnosis?: string;
  doseCount: number;
  prescriptionDate: string;
  doctorName?: string;
  usageMethod?: string;
  remark?: string;
  items: PrescriptionItemInput[];
}
