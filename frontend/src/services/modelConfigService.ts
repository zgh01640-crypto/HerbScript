import { http } from "./http";
import type {
  ModelConfigPage,
  ModelConfigSavePayload,
  ModelConfigTestPayload,
  ModelConfigTestResult
} from "../types/model-config";

type ApiResponse<T> = {
  code: number;
  message: string;
  data: T;
};

export const modelConfigService = {
  async getConfig(): Promise<ModelConfigPage> {
    const response = await http.get<ApiResponse<ModelConfigPage>>("/api/model-config");
    return response.data;
  },

  async saveConfig(payload: ModelConfigSavePayload): Promise<ModelConfigPage> {
    const response = await http.put<ApiResponse<ModelConfigPage>>("/api/model-config", payload);
    return response.data;
  },

  async activateConfig(profileId: number): Promise<ModelConfigPage> {
    const response = await http.post<ApiResponse<ModelConfigPage>>("/api/model-config/activate", { profileId });
    return response.data;
  },

  async testConfig(payload: ModelConfigTestPayload): Promise<ModelConfigTestResult> {
    const response = await http.post<ApiResponse<ModelConfigTestResult>>("/api/model-config/test", payload);
    return response.data;
  }
};
