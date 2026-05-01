export interface ModelConfigProfile {
  id: number;
  profileName: string;
  provider: string;
  doubaoBaseUrl: string;
  doubaoModel: string;
  doubaoChatPath: string;
  fallbackToMockOnError: boolean;
  apiKeyConfigured: boolean;
  maskedApiKey: string;
  active: boolean;
  online: boolean;
  updatedAt: string;
}

export interface ModelConfigPage {
  activeProfileId?: number;
  profiles: ModelConfigProfile[];
}

export interface ModelConfigSavePayload {
  profileId?: number;
  profileName: string;
  provider: string;
  doubaoBaseUrl: string;
  doubaoModel: string;
  doubaoChatPath: string;
  fallbackToMockOnError: boolean;
  doubaoApiKey?: string;
  clearApiKey?: boolean;
  activate?: boolean;
}
