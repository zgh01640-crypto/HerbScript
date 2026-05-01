package com.herbscript.recognition.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.herbscript.prescription.dto.PrescriptionItemSaveRequest;
import com.herbscript.recognition.config.RecognitionProperties;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Component;

@Component
public class DoubaoVisionRecognitionProvider implements RecognitionProvider {

    private static final Pattern CHINESE_DATE_PATTERN = Pattern.compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})日");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("(\\d+)");
    private static final Pattern AGE_FROM_TEXT_PATTERN = Pattern.compile("(\\d{1,3})\\s*岁");
    private static final Pattern DOSE_FROM_TEXT_PATTERN = Pattern.compile("(\\d{1,2})\\s*(?:剂|付|帖)");

    private final RecognitionProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public DoubaoVisionRecognitionProvider(RecognitionProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getDoubaoBaseUrl())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String providerName() {
        return properties.getDoubaoModel();
    }

    @Override
    public RecognitionDraftData recognize(Path imagePath) {
        String fileName = imagePath.getFileName().toString().toLowerCase();

        if (properties.getDoubaoApiKey() == null || properties.getDoubaoApiKey().isBlank()) {
            return mockDraft(fileName);
        }

        try {
            String imageDataUrl = toDataUrl(imagePath);
            String response = restClient.post()
                    .uri(properties.getDoubaoChatPath())
                    .header("Authorization", "Bearer " + properties.getDoubaoApiKey())
                    .body(buildRequestBody(imageDataUrl))
                    .retrieve()
                    .body(String.class);
            return parseResponse(response);
        } catch (Exception ex) {
            if (properties.isFallbackToMockOnError()) {
                return mockDraft(fileName);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Doubao 识别调用失败");
        }
    }

    private String toDataUrl(Path imagePath) throws IOException {
        byte[] bytes = Files.readAllBytes(imagePath);
        String mimeType = Files.probeContentType(imagePath);
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = "image/png";
        }
        return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private Map<String, Object> buildRequestBody(String imageDataUrl) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", properties.getDoubaoModel());
        body.put("temperature", 0.1);
        body.put("messages", List.of(
                Map.of(
                        "role", "system",
                        "content", """
                                你是中医处方结构化助手。请识别处方图片并严格返回 JSON。
                                返回格式必须包含：
                                patientName, gender, age, department, diagnosis, doseCount,
                                prescriptionDate, doctorName, usageMethod, warnings, lowConfidenceFields, items, rawText。
                                items 为数组，每项包含：sortNo, herbName, rawHerbName, dosage, unit, specialInstruction。
                                age 和 doseCount 必须返回纯数字，不要带“岁”“剂”等单位。
                                age 只能返回患者年龄，不要误取剂量或日期中的数字。
                                doseCount 只能返回处方剂数，不要误取年龄或药味剂量。
                                不要返回 markdown，不要返回额外解释。
                                """
                ),
                Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "text", "text", "请识别这张中医处方图片并输出指定 JSON。"),
                                Map.of("type", "image_url", "image_url", Map.of("url", imageDataUrl))
                        )
                )
        ));
        return body;
    }

    private RecognitionDraftData parseResponse(String response) throws IOException {
        JsonNode root = objectMapper.readTree(response);
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        if (contentNode.isMissingNode() || contentNode.isNull()) {
            throw new IllegalArgumentException("Doubao 返回为空");
        }

        String content = contentNode.isTextual() ? contentNode.asText() : contentNode.toString();
        String normalizedContent = stripMarkdownCodeFence(content);
        JsonNode draftNode = objectMapper.readTree(normalizedContent);
        String sourceText = draftNode.path("rawText").asText(content);
        List<PrescriptionItemSaveRequest> items = objectMapper.readerForListOf(PrescriptionItemSaveRequest.class)
                .readValue(draftNode.path("items"));

        return new RecognitionDraftData(
                draftNode.path("patientName").asText(""),
                draftNode.path("gender").asText(""),
                resolveAge(draftNode, sourceText),
                draftNode.path("department").asText(""),
                draftNode.path("diagnosis").asText(""),
                resolveDoseCount(draftNode, sourceText),
                normalizeDate(draftNode.path("prescriptionDate").asText(LocalDate.now().toString())),
                draftNode.path("doctorName").asText(""),
                draftNode.path("usageMethod").asText(""),
                toStringList(draftNode.path("warnings")),
                toStringList(draftNode.path("lowConfidenceFields")),
                items,
                normalizedContent
        );
    }

    private List<String> toStringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
                .map(JsonNode::asText)
                .toList();
    }

    private String stripMarkdownCodeFence(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed.trim();
    }

    private int parseIntegerField(JsonNode node, int defaultValue) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return defaultValue;
        }

        if (node.isInt() || node.isLong()) {
            return node.asInt(defaultValue);
        }

        String raw = node.asText("").trim();
        if (raw.isBlank()) {
            return defaultValue;
        }

        Matcher matcher = INTEGER_PATTERN.matcher(raw);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        Integer chineseNumber = parseChineseNumber(raw);
        return chineseNumber == null ? defaultValue : chineseNumber;
    }

    private Integer parseChineseNumber(String raw) {
        String normalized = raw.replace("岁", "")
                .replace("剂", "")
                .replace("付", "")
                .replace("帖", "")
                .trim();

        Map<Character, Integer> digits = Map.ofEntries(
                Map.entry('零', 0),
                Map.entry('一', 1),
                Map.entry('二', 2),
                Map.entry('两', 2),
                Map.entry('三', 3),
                Map.entry('四', 4),
                Map.entry('五', 5),
                Map.entry('六', 6),
                Map.entry('七', 7),
                Map.entry('八', 8),
                Map.entry('九', 9)
        );

        if (normalized.length() == 1 && digits.containsKey(normalized.charAt(0))) {
            return digits.get(normalized.charAt(0));
        }

        if ("十".equals(normalized)) {
            return 10;
        }

        if (normalized.contains("十")) {
            String[] parts = normalized.split("十", -1);
            int tens = parts[0].isBlank() ? 1 : digits.getOrDefault(parts[0].charAt(0), 0);
            int units = parts.length > 1 && !parts[1].isBlank() ? digits.getOrDefault(parts[1].charAt(0), 0) : 0;
            return tens * 10 + units;
        }

        return null;
    }

    private int resolveAge(JsonNode draftNode, String rawText) {
        int age = parseIntegerField(draftNode.path("age"), 0);
        if (age > 0 && age <= 120) {
            return age;
        }

        age = parseIntegerField(draftNode.path("patientAge"), 0);
        if (age > 0 && age <= 120) {
            return age;
        }

        Matcher matcher = AGE_FROM_TEXT_PATTERN.matcher(rawText == null ? "" : rawText);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        return 0;
    }

    private int resolveDoseCount(JsonNode draftNode, String rawText) {
        int doseCount = parseIntegerField(draftNode.path("doseCount"), 0);
        if (doseCount >= 1 && doseCount <= 30) {
            return doseCount;
        }

        doseCount = parseIntegerField(draftNode.path("doses"), 0);
        if (doseCount >= 1 && doseCount <= 30) {
            return doseCount;
        }

        Matcher matcher = DOSE_FROM_TEXT_PATTERN.matcher(rawText == null ? "" : rawText);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        return 1;
    }

    private String normalizeDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return LocalDate.now().toString();
        }

        String trimmed = rawDate.trim();
        Matcher matcher = CHINESE_DATE_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            int year = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            int day = Integer.parseInt(matcher.group(3));
            return LocalDate.of(year, month, day).toString();
        }

        String normalized = trimmed.replace('/', '-').replace('.', '-');
        try {
            return LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE).toString();
        } catch (DateTimeParseException ignored) {
            return LocalDate.now().toString();
        }
    }

    private RecognitionDraftData mockDraft(String fileName) {
        if (fileName.contains("liver") || fileName.contains("3")) {
            return new RecognitionDraftData(
                    "刘海峰",
                    "男",
                    39,
                    "肝胆科",
                    "肝郁气滞",
                    6,
                    LocalDate.now().minusDays(1).toString(),
                    "孙医生",
                    "煎服，分早晚两次",
                    List.of("当前为 Doubao 本地 mock 草稿，待接真实模型响应。"),
                    List.of("diagnosis"),
                    List.of(
                            new PrescriptionItemSaveRequest(1, "柴胡", "柴胡", new BigDecimal("10"), "g", null),
                            new PrescriptionItemSaveRequest(2, "香附", "香附", new BigDecimal("9"), "g", null),
                            new PrescriptionItemSaveRequest(3, "白芍", "白芍", new BigDecimal("12"), "g", null)
                    ),
                    "Mock draft for liver prescription image."
            );
        }

        return new RecognitionDraftData(
                "王秀兰",
                "女",
                58,
                "内科",
                "脾胃虚弱",
                7,
                LocalDate.now().toString(),
                "李医生",
                "水煎服，每日一剂",
                List.of("当前为 Doubao 本地 mock 草稿，待接真实模型响应。", "服用说明识别置信度较低"),
                List.of("diagnosis", "usageMethod"),
                List.of(
                        new PrescriptionItemSaveRequest(1, "黄芪", "黄芪", new BigDecimal("15"), "g", null),
                        new PrescriptionItemSaveRequest(2, "党参", "党参", new BigDecimal("12"), "g", null),
                        new PrescriptionItemSaveRequest(3, "白术", "白术", new BigDecimal("10"), "g", "麸炒"),
                        new PrescriptionItemSaveRequest(4, "炙甘草", "炙甘草", new BigDecimal("6"), "g", null)
                ),
                "Mock draft for spleen prescription image."
        );
    }
}
