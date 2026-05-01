import { reactive } from "vue";
import { prescriptionService } from "../services/prescriptionService";
import type { PrescriptionRecord } from "../types/prescription";

type RecognitionUploadStatus = "idle" | "uploading" | "success" | "error";

type RecognitionUploadState = {
  status: RecognitionUploadStatus;
  fileName: string;
  previewUrl: string;
  startedAt: number | null;
  errorMessage: string;
  record?: PrescriptionRecord;
};

const state = reactive<RecognitionUploadState>({
  status: "idle",
  fileName: "",
  previewUrl: "",
  startedAt: null,
  errorMessage: "",
  record: undefined
});

let currentRequestId = 0;

const revokePreviewUrl = () => {
  if (state.previewUrl) {
    URL.revokeObjectURL(state.previewUrl);
    state.previewUrl = "";
  }
};

const clear = () => {
  revokePreviewUrl();
  state.status = "idle";
  state.fileName = "";
  state.startedAt = null;
  state.errorMessage = "";
  state.record = undefined;
};

const upload = async (file: File) => {
  const requestId = ++currentRequestId;

  revokePreviewUrl();
  state.status = "uploading";
  state.fileName = file.name;
  state.previewUrl = URL.createObjectURL(file);
  state.startedAt = Date.now();
  state.errorMessage = "";
  state.record = undefined;

  try {
    const record = await prescriptionService.uploadRecognitionImage(file);
    if (requestId !== currentRequestId) {
      return record;
    }

    revokePreviewUrl();
    state.status = "success";
    state.record = record;
    state.errorMessage = "";
    return record;
  } catch (error) {
    if (requestId !== currentRequestId) {
      throw error;
    }

    state.status = "error";
    state.errorMessage = error instanceof Error ? error.message : "上传识别失败";
    throw error;
  }
};

export const recognitionUploadState = state;
export const recognitionUploadController = {
  clear,
  upload
};
