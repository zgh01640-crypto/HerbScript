package com.herbscript.recognition.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "herbscript.recognition")
public class RecognitionProperties {

    private String provider = "doubao-seed-2-0-pro";
    private String uploadDir = "./uploads/prescriptions";
    private String doubaoBaseUrl = "https://ark.cn-beijing.volces.com/api/coding/v3";
    private String doubaoModel = "doubao-seed-2-0-pro";
    private String doubaoChatPath = "/chat/completions";
    private String doubaoApiKey = "";
    private boolean fallbackToMockOnError = true;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }

    public String getDoubaoBaseUrl() {
        return doubaoBaseUrl;
    }

    public void setDoubaoBaseUrl(String doubaoBaseUrl) {
        this.doubaoBaseUrl = doubaoBaseUrl;
    }

    public String getDoubaoModel() {
        return doubaoModel;
    }

    public void setDoubaoModel(String doubaoModel) {
        this.doubaoModel = doubaoModel;
    }

    public String getDoubaoChatPath() {
        return doubaoChatPath;
    }

    public void setDoubaoChatPath(String doubaoChatPath) {
        this.doubaoChatPath = doubaoChatPath;
    }

    public String getDoubaoApiKey() {
        return doubaoApiKey;
    }

    public void setDoubaoApiKey(String doubaoApiKey) {
        this.doubaoApiKey = doubaoApiKey;
    }

    public boolean isFallbackToMockOnError() {
        return fallbackToMockOnError;
    }

    public void setFallbackToMockOnError(boolean fallbackToMockOnError) {
        this.fallbackToMockOnError = fallbackToMockOnError;
    }
}
