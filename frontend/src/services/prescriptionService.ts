import { http } from "./http";
import { dashboardSummary, prescriptions } from "../mocks/prescriptions";
import type {
  DashboardSummary,
  PrescriptionFilters,
  RecognitionConfirmPayload,
  PrescriptionRecord,
  PrescriptionSavePayload
} from "../types/prescription";

const wait = async (ms = 180) => new Promise((resolve) => setTimeout(resolve, ms));
const useMock = import.meta.env.VITE_USE_MOCK !== "false";

type ApiResponse<T> = {
  code: number;
  message: string;
  data: T;
};

type CreatePrescriptionResponse = {
  id: number;
  prescriptionNo: string;
};

const includesKeyword = (record: PrescriptionRecord, keyword: string) => {
  const normalized = keyword.trim().toLowerCase();
  if (!normalized) return true;

  return [record.prescriptionNo, record.patientName, record.doctorName, record.diagnosis]
    .join(" ")
    .toLowerCase()
    .includes(normalized);
};

export const prescriptionService = {
  async getDashboardSummary(): Promise<DashboardSummary> {
    if (!useMock) {
      const response = await http.get<ApiResponse<DashboardSummary>>("/api/dashboard/summary");
      return response.data;
    }

    await wait();
    return dashboardSummary;
  },

  async getPrescriptionList(filters: PrescriptionFilters): Promise<PrescriptionRecord[]> {
    if (!useMock) {
      const response = await http.get<ApiResponse<PrescriptionRecord[]>>("/api/prescriptions", {
        keyword: filters.keyword,
        entryMode: filters.entryMode,
        status: filters.status
      });
      return response.data;
    }

    await wait();
    return prescriptions.filter((record) => {
      const keywordMatched = includesKeyword(record, filters.keyword);
      const modeMatched = !filters.entryMode || record.entryMode === filters.entryMode;
      const statusMatched = !filters.status || record.status === filters.status;
      return keywordMatched && modeMatched && statusMatched;
    });
  },

  async getPrescriptionDetail(id: number): Promise<PrescriptionRecord | undefined> {
    if (!useMock) {
      const response = await http.get<ApiResponse<PrescriptionRecord>>(`/api/prescriptions/${id}`);
      return response.data;
    }

    await wait();
    return prescriptions.find((record) => record.id === id);
  },

  async getRecognitionDraft(taskId?: number): Promise<PrescriptionRecord | undefined> {
    if (!useMock) {
      const path = taskId ? `/api/recognitions/${taskId}` : "/api/recognitions/draft";
      const response = await http.get<ApiResponse<PrescriptionRecord | null>>(path);
      return response.data ?? undefined;
    }

    await wait();
    if (taskId) {
      return prescriptions.find((record) => record.recognitionDraft?.taskId === taskId);
    }

    return prescriptions.find((record) => record.status === "pending_review" && record.recognitionDraft);
  },

  async createPrescription(payload: PrescriptionSavePayload): Promise<CreatePrescriptionResponse> {
    const response = await http.post<ApiResponse<CreatePrescriptionResponse>>("/api/prescriptions", payload);
    return response.data;
  },

  async updatePrescription(id: number, payload: PrescriptionSavePayload): Promise<CreatePrescriptionResponse> {
    const response = await http.put<ApiResponse<CreatePrescriptionResponse>>(`/api/prescriptions/${id}`, payload);
    return response.data;
  },

  async deletePrescription(id: number): Promise<void> {
    await http.delete<ApiResponse<null>>(`/api/prescriptions/${id}`);
  },

  async confirmRecognitionDraft(taskId: number, payload: RecognitionConfirmPayload): Promise<CreatePrescriptionResponse> {
    const response = await http.post<ApiResponse<CreatePrescriptionResponse>>(
      `/api/recognitions/${taskId}/confirm`,
      payload
    );
    return response.data;
  },

  async uploadRecognitionImage(file: File): Promise<PrescriptionRecord> {
    const formData = new FormData();
    formData.append("file", file);
    const response = await http.postForm<ApiResponse<PrescriptionRecord>>("/api/recognitions/upload", formData);
    return response.data;
  }
};
