package com.herbscript.modelconfig;

import com.herbscript.modelconfig.dto.ModelConfigPageResponse;
import com.herbscript.modelconfig.dto.ModelConfigProfileResponse;
import com.herbscript.modelconfig.dto.ModelConfigSaveRequest;
import com.herbscript.modelconfig.dto.ModelConfigTestRequest;
import com.herbscript.modelconfig.dto.ModelConfigTestResponse;
import com.herbscript.recognition.config.RecognitionProperties;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class ModelConfigService {

    private static final String ACTIVE_PROFILE_KEY = "recognition.activeProfileId";

    private final JdbcTemplate jdbcTemplate;
    private final RecognitionProperties properties;

    public ModelConfigService(JdbcTemplate jdbcTemplate, RecognitionProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public ModelRuntimeConfig getRuntimeConfig() {
        Long activeProfileId = getActiveProfileId();
        if (activeProfileId != null) {
            ModelRuntimeConfig config = jdbcTemplate.query(
                    """
                    SELECT provider, doubao_base_url, doubao_model, doubao_chat_path, doubao_api_key, fallback_to_mock_on_error
                    FROM model_profile
                    WHERE id = ? AND deleted = 0
                    """,
                    ps -> ps.setLong(1, activeProfileId),
                    rs -> rs.next() ? new ModelRuntimeConfig(
                            rs.getString("provider"),
                            rs.getString("doubao_base_url"),
                            rs.getString("doubao_model"),
                            rs.getString("doubao_chat_path"),
                            rs.getString("doubao_api_key"),
                            rs.getBoolean("fallback_to_mock_on_error"),
                            properties.getUploadDir()
                    ) : null
            );
            if (config != null) {
                return config;
            }
        }

        return new ModelRuntimeConfig(
                properties.getProvider(),
                properties.getDoubaoBaseUrl(),
                properties.getDoubaoModel(),
                properties.getDoubaoChatPath(),
                properties.getDoubaoApiKey(),
                properties.isFallbackToMockOnError(),
                properties.getUploadDir()
        );
    }

    public ModelConfigPageResponse getConfigPage() {
        Long activeProfileId = getActiveProfileId();
        return new ModelConfigPageResponse(activeProfileId, listProfiles(activeProfileId));
    }

    public ModelConfigTestResponse testConfig(ModelConfigTestRequest request) {
        ResolvedModelConfig resolved = resolveTestConfig(request);
        ModelRuntimeConfig config = resolved.config();
        if (config.doubaoApiKey() == null || config.doubaoApiKey().isBlank()) {
            return new ModelConfigTestResponse(
                    false,
                    "missing_api_key",
                    "未配置 API Key，无法完成连通性检测",
                    0,
                    null,
                    config.provider(),
                    config.doubaoModel(),
                    config.doubaoBaseUrl(),
                    resolved.activeProfileUsed()
            );
        }

        long startedAt = System.currentTimeMillis();
        try {
            RestClient restClient = RestClient.builder()
                    .baseUrl(config.doubaoBaseUrl())
                    .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .build();
            String response = restClient.post()
                    .uri(config.doubaoChatPath())
                    .header("Authorization", "Bearer " + config.doubaoApiKey())
                    .body(buildTestRequestBody(config))
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                return new ModelConfigTestResponse(
                        false,
                        "empty_response",
                        "模型服务可达，但返回内容为空",
                        System.currentTimeMillis() - startedAt,
                        200,
                        config.provider(),
                        config.doubaoModel(),
                        config.doubaoBaseUrl(),
                        resolved.activeProfileUsed()
                );
            }

            return new ModelConfigTestResponse(
                    true,
                    "online",
                    "模型连接成功，已收到有效响应",
                    System.currentTimeMillis() - startedAt,
                    200,
                    config.provider(),
                    config.doubaoModel(),
                    config.doubaoBaseUrl(),
                    resolved.activeProfileUsed()
            );
        } catch (RestClientResponseException ex) {
            return new ModelConfigTestResponse(
                    false,
                    mapHttpStatus(ex.getStatusCode().value()),
                    shorten(ex.getResponseBodyAsString(), "模型返回错误状态"),
                    System.currentTimeMillis() - startedAt,
                    ex.getStatusCode().value(),
                    config.provider(),
                    config.doubaoModel(),
                    config.doubaoBaseUrl(),
                    resolved.activeProfileUsed()
            );
        } catch (Exception ex) {
            return new ModelConfigTestResponse(
                    false,
                    "network_error",
                    shorten(ex.getMessage(), "模型连接失败"),
                    System.currentTimeMillis() - startedAt,
                    null,
                    config.provider(),
                    config.doubaoModel(),
                    config.doubaoBaseUrl(),
                    resolved.activeProfileUsed()
            );
        }
    }

    @Transactional
    public ModelConfigPageResponse saveProfile(ModelConfigSaveRequest request) {
        Long profileId = request.profileId();
        Long existingProfileId = profileId;
        String existingApiKey = existingProfileId == null ? "" : jdbcTemplate.query(
                "SELECT doubao_api_key FROM model_profile WHERE id = ? AND deleted = 0",
                ps -> ps.setLong(1, existingProfileId),
                rs -> rs.next() ? rs.getString(1) : null
        );

        String apiKey = resolveApiKey(request, existingApiKey);
        boolean fallback = Boolean.TRUE.equals(request.fallbackToMockOnError());
        boolean activate = Boolean.TRUE.equals(request.activate());

        if (profileId == null) {
            jdbcTemplate.update(
                    """
                    INSERT INTO model_profile
                    (profile_name, provider, doubao_base_url, doubao_model, doubao_chat_path, doubao_api_key, fallback_to_mock_on_error)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    request.profileName().trim(),
                    request.provider().trim(),
                    request.doubaoBaseUrl().trim(),
                    request.doubaoModel().trim(),
                    request.doubaoChatPath().trim(),
                    apiKey,
                    fallback
            );
            profileId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        } else {
            int updated = jdbcTemplate.update(
                    """
                    UPDATE model_profile
                    SET profile_name = ?, provider = ?, doubao_base_url = ?, doubao_model = ?, doubao_chat_path = ?,
                        doubao_api_key = ?, fallback_to_mock_on_error = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND deleted = 0
                    """,
                    request.profileName().trim(),
                    request.provider().trim(),
                    request.doubaoBaseUrl().trim(),
                    request.doubaoModel().trim(),
                    request.doubaoChatPath().trim(),
                    apiKey,
                    fallback,
                    profileId
            );
            if (updated == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模型配置档案不存在");
            }
        }

        if (activate) {
            activateProfile(profileId);
        }

        return getConfigPage();
    }

    @Transactional
    public ModelConfigPageResponse activateProfile(Long profileId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM model_profile WHERE id = ? AND deleted = 0",
                Integer.class,
                profileId
        );
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模型配置档案不存在");
        }

        upsertActiveProfileId(profileId);
        jdbcTemplate.update("UPDATE model_profile SET is_active = 0 WHERE deleted = 0");
        jdbcTemplate.update("UPDATE model_profile SET is_active = 1 WHERE id = ? AND deleted = 0", profileId);
        return getConfigPage();
    }

    private List<ModelConfigProfileResponse> listProfiles(Long activeProfileId) {
        return jdbcTemplate.query(
                """
                SELECT id, profile_name, provider, doubao_base_url, doubao_model, doubao_chat_path,
                       doubao_api_key, fallback_to_mock_on_error, is_active, updated_at
                FROM model_profile
                WHERE deleted = 0
                ORDER BY is_active DESC, updated_at DESC, id DESC
                """,
                (rs, rowNum) -> toProfileResponse(rs, activeProfileId)
        );
    }

    private ModelConfigProfileResponse toProfileResponse(ResultSet rs, Long activeProfileId) throws SQLException {
        String apiKey = rs.getString("doubao_api_key");
        boolean active = activeProfileId != null && activeProfileId.equals(rs.getLong("id"));
        return new ModelConfigProfileResponse(
                rs.getLong("id"),
                rs.getString("profile_name"),
                rs.getString("provider"),
                rs.getString("doubao_base_url"),
                rs.getString("doubao_model"),
                rs.getString("doubao_chat_path"),
                rs.getBoolean("fallback_to_mock_on_error"),
                apiKey != null && !apiKey.isBlank(),
                maskApiKey(apiKey),
                active,
                apiKey != null && !apiKey.isBlank(),
                rs.getTimestamp("updated_at").toLocalDateTime().toString().replace('T', ' ')
        );
    }

    private Long getActiveProfileId() {
        String value = jdbcTemplate.query(
                "SELECT setting_value FROM system_setting WHERE setting_key = ? AND deleted = 0",
                ps -> ps.setString(1, ACTIVE_PROFILE_KEY),
                rs -> rs.next() ? rs.getString(1) : null
        );
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.valueOf(value);
    }

    private void upsertActiveProfileId(Long profileId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system_setting WHERE setting_key = ?",
                Integer.class,
                ACTIVE_PROFILE_KEY
        );
        if (count != null && count > 0) {
            jdbcTemplate.update(
                    "UPDATE system_setting SET setting_value = ?, updated_at = CURRENT_TIMESTAMP, deleted = 0 WHERE setting_key = ?",
                    String.valueOf(profileId),
                    ACTIVE_PROFILE_KEY
            );
            return;
        }

        jdbcTemplate.update(
                "INSERT INTO system_setting (setting_key, setting_value, setting_group, remark) VALUES (?, ?, 'recognition', ?)",
                ACTIVE_PROFILE_KEY,
                String.valueOf(profileId),
                "当前生效模型档案"
        );
    }

    private String resolveApiKey(ModelConfigSaveRequest request, String existingApiKey) {
        if (Boolean.TRUE.equals(request.clearApiKey())) {
            return "";
        }
        if (request.doubaoApiKey() != null && !request.doubaoApiKey().isBlank()) {
            return request.doubaoApiKey().trim();
        }
        return existingApiKey == null ? "" : existingApiKey;
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        if (apiKey.length() <= 8) {
            return "已配置";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    private ResolvedModelConfig resolveTestConfig(ModelConfigTestRequest request) {
        if (request != null && request.profileId() != null) {
            Long profileId = request.profileId();
            ModelRuntimeConfig config = jdbcTemplate.query(
                    """
                    SELECT provider, doubao_base_url, doubao_model, doubao_chat_path, doubao_api_key, fallback_to_mock_on_error
                    FROM model_profile
                    WHERE id = ? AND deleted = 0
                    """,
                    ps -> ps.setLong(1, profileId),
                    rs -> rs.next() ? new ModelRuntimeConfig(
                            rs.getString("provider"),
                            rs.getString("doubao_base_url"),
                            rs.getString("doubao_model"),
                            rs.getString("doubao_chat_path"),
                            rs.getString("doubao_api_key"),
                            rs.getBoolean("fallback_to_mock_on_error"),
                            properties.getUploadDir()
                    ) : null
            );
            if (config != null) {
                return new ResolvedModelConfig(config, true);
            }
        }

        if (request != null && hasAdhocConfig(request)) {
            return new ResolvedModelConfig(
                    new ModelRuntimeConfig(
                            trimOrDefault(request.provider(), properties.getProvider()),
                            trimOrDefault(request.doubaoBaseUrl(), properties.getDoubaoBaseUrl()),
                            trimOrDefault(request.doubaoModel(), properties.getDoubaoModel()),
                            trimOrDefault(request.doubaoChatPath(), properties.getDoubaoChatPath()),
                            request.doubaoApiKey() == null ? "" : request.doubaoApiKey().trim(),
                            Boolean.TRUE.equals(request.fallbackToMockOnError()),
                            properties.getUploadDir()
                    ),
                    false
            );
        }

        return new ResolvedModelConfig(getRuntimeConfig(), true);
    }

    private boolean hasAdhocConfig(ModelConfigTestRequest request) {
        return !isBlank(request.provider())
                || !isBlank(request.doubaoBaseUrl())
                || !isBlank(request.doubaoModel())
                || !isBlank(request.doubaoChatPath())
                || !isBlank(request.doubaoApiKey());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimOrDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private Map<String, Object> buildTestRequestBody(ModelRuntimeConfig config) {
        return Map.of(
                "model", config.doubaoModel(),
                "temperature", 0,
                "messages", List.of(
                        Map.of("role", "system", "content", "你是模型连通性检测助手。"),
                        Map.of("role", "user", "content", "请只回复 OK")
                )
        );
    }

    private String mapHttpStatus(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return "auth_failed";
        }
        if (statusCode == 404) {
            return "invalid_path";
        }
        if (statusCode == 429) {
            return "rate_limited";
        }
        if (statusCode >= 500) {
            return "upstream_error";
        }
        return "http_error";
    }

    private String shorten(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > 160 ? normalized.substring(0, 160) + "..." : normalized;
    }

    private record ResolvedModelConfig(ModelRuntimeConfig config, boolean activeProfileUsed) {
    }
}
