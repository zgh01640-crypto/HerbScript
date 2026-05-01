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
  patientId?: number;
  patientDraft?: PatientDraftInput;
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
  patientId?: number;
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

export interface PrescriptionSummary {
  id: number;
  prescriptionNo: string;
  patientName: string;
  gender: string;
  age: number;
  prescriptionDate: string;
  doseCount: number;
  entryMode: EntryMode;
  status: PrescriptionStatus;
  createdByName: string;
  createdAt: string;
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
  patientId?: number;
  patientDraft?: PatientDraftInput;
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

export interface PatientDraftInput {
  name: string;
  gender: string;
  age: number;
  phone?: string;
  remark?: string;
}

export interface PatientMatchCandidate {
  id: number;
  patientNo: string;
  name: string;
  gender: string;
  age: number;
  phone?: string;
  matchLevel: "high" | "medium" | "low";
  matchScore: number;
  prescriptionCount: number;
  lastPrescriptionDate?: string;
}

export interface PatientMatchPayload {
  name: string;
  gender: string;
  age: number;
  phone?: string;
}

export interface PatientSummary {
  id: number;
  patientNo: string;
  name: string;
  gender: string;
  age: number;
  phone?: string;
  prescriptionCount: number;
  lastPrescriptionDate?: string;
}

export interface PatientDetail extends PatientSummary {
  remark?: string;
  prescriptions: PrescriptionSummary[];
}
