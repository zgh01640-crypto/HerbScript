package com.herbscript.recognition;

import com.herbscript.modelconfig.ModelConfigService;
import com.herbscript.modelconfig.ModelRuntimeConfig;
import com.herbscript.prescription.PrescriptionQueryService;
import com.herbscript.prescription.dto.PrescriptionDetailResponse;
import com.herbscript.prescription.dto.PrescriptionItemSaveRequest;
import com.herbscript.recognition.provider.RecognitionDraftData;
import com.herbscript.recognition.provider.RecognitionProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecognitionUploadService {

    private final ModelConfigService modelConfigService;
    private final RecognitionProvider recognitionProvider;
    private final JdbcTemplate jdbcTemplate;
    private final PrescriptionQueryService prescriptionQueryService;

    public RecognitionUploadService(
            ModelConfigService modelConfigService,
            RecognitionProvider recognitionProvider,
            JdbcTemplate jdbcTemplate,
            PrescriptionQueryService prescriptionQueryService
    ) {
        this.modelConfigService = modelConfigService;
        this.recognitionProvider = recognitionProvider;
        this.jdbcTemplate = jdbcTemplate;
        this.prescriptionQueryService = prescriptionQueryService;
    }

    @Transactional
    public PrescriptionDetailResponse uploadAndRecognize(MultipartFile file) {
        validateFile(file);

        Path savedPath = storeFile(file);
        String publicImageUrl = toPublicImageUrl(savedPath);
        RecognitionDraftData draft = recognitionProvider.recognize(savedPath);

        Long taskId = createRecognitionTask(publicImageUrl, draft);
        createRecognitionFields(taskId, draft);
        Long prescriptionId = createDraftPrescription(taskId, publicImageUrl, draft);
        createDraftItems(prescriptionId, draft.items());
        createRecognitionLog(prescriptionId, draft.warnings());

        return prescriptionQueryService.getDetail(prescriptionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "识别草稿生成失败"));
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上传文件不能为空");
        }

        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件名无效");
        }

        String normalized = filename.toLowerCase();
        if (!normalized.endsWith(".png") && !normalized.endsWith(".jpg") && !normalized.endsWith(".jpeg")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 JPG / PNG 图片");
        }
    }

    private Path storeFile(MultipartFile file) {
        try {
            ModelRuntimeConfig runtimeConfig = modelConfigService.getRuntimeConfig();
            Path baseDir = Path.of(runtimeConfig.uploadDir(), LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE));
            Files.createDirectories(baseDir);

            String originalName = file.getOriginalFilename() == null ? "prescription.png" : file.getOriginalFilename();
            String extension = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf('.')) : ".png";
            Path target = baseDir.resolve(UUID.randomUUID() + extension);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return target.toAbsolutePath();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "保存图片失败");
        }
    }

    private String toPublicImageUrl(Path savedPath) {
        String normalized = savedPath.toString().replace("\\", "/");
        int uploadsIndex = normalized.indexOf("/uploads/");
        if (uploadsIndex >= 0) {
            return normalized.substring(uploadsIndex);
        }
        return "/uploads/" + savedPath.getFileName();
    }

    private Long createRecognitionTask(String imageUrl, RecognitionDraftData draft) {
        jdbcTemplate.update("""
                INSERT INTO recognition_task
                (image_url, provider_name, status, raw_text, warning_message, created_by, started_at, finished_at)
                VALUES (?, ?, 'success', ?, ?, 1, NOW(), NOW())
                """,
                imageUrl,
                recognitionProvider.providerName(),
                draft.rawText(),
                String.join("|", draft.warnings())
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void createRecognitionFields(Long taskId, RecognitionDraftData draft) {
        saveRecognitionField(taskId, "patientName", "患者姓名", draft.patientName(), confidenceFor("patientName", draft));
        saveRecognitionField(taskId, "gender", "性别", draft.gender(), confidenceFor("gender", draft));
        saveRecognitionField(taskId, "diagnosis", "临床诊断", draft.diagnosis(), confidenceFor("diagnosis", draft));
        saveRecognitionField(taskId, "doseCount", "剂数", String.valueOf(draft.doseCount()), confidenceFor("doseCount", draft));
        saveRecognitionField(taskId, "usageMethod", "服用说明", draft.usageMethod(), confidenceFor("usageMethod", draft));
        saveRecognitionField(taskId, "doctorName", "医师信息", draft.doctorName(), confidenceFor("doctorName", draft));
    }

    private void saveRecognitionField(Long taskId, String key, String label, String value, double confidence) {
        jdbcTemplate.update("""
                INSERT INTO recognition_field
                (task_id, field_key, field_label, raw_value, corrected_value, confidence, confirm_status)
                VALUES (?, ?, ?, ?, ?, ?, 'pending')
                """,
                taskId,
                key,
                label,
                value,
                value,
                confidence
        );
    }

    private double confidenceFor(String field, RecognitionDraftData draft) {
        return draft.lowConfidenceFields().contains(field) ? 0.78 : 0.96;
    }

    private Long createDraftPrescription(Long taskId, String imageUrl, RecognitionDraftData draft) {
        String prescriptionNo = nextDraftPrescriptionNo();
        jdbcTemplate.update("""
                INSERT INTO prescription
                (prescription_no, patient_name, gender, age, department, diagnosis, dose_count, prescription_date,
                 doctor_name, usage_method, entry_mode, status, source_image_url, source_task_id, raw_recognition_text,
                 created_by, updated_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ai_recognition', 'pending_review', ?, ?, ?, 1, 1, NOW(), NOW())
                """,
                prescriptionNo,
                draft.patientName(),
                draft.gender(),
                draft.age(),
                draft.department(),
                draft.diagnosis(),
                draft.doseCount(),
                draft.prescriptionDate(),
                draft.doctorName(),
                draft.usageMethod(),
                imageUrl,
                taskId,
                draft.rawText()
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void createDraftItems(Long prescriptionId, List<PrescriptionItemSaveRequest> items) {
        int index = 1;
        for (PrescriptionItemSaveRequest item : items) {
            jdbcTemplate.update("""
                    INSERT INTO prescription_item
                    (prescription_id, sort_no, herb_name, raw_herb_name, dosage, unit, special_instruction, confidence, confirm_status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'pending')
                    """,
                    prescriptionId,
                    item.sortNo() != null ? item.sortNo() : index,
                    item.herbName(),
                    item.rawHerbName(),
                    item.dosage(),
                    item.unit(),
                    item.specialInstruction(),
                    0.95
            );
            index++;
        }
    }

    private void createRecognitionLog(Long prescriptionId, List<String> warnings) {
        String detail = warnings.isEmpty()
                ? "上传处方图片并生成识别草稿。"
                : "上传处方图片并生成识别草稿。提示：" + String.join("；", warnings);
        jdbcTemplate.update("""
                INSERT INTO operation_log
                (operator_id, operator_name, module_name, operation_type, target_type, target_id, detail, created_at)
                VALUES (1, '系统管理员', 'recognition', 'upload', 'prescription', ?, ?, ?)
                """,
                prescriptionId,
                detail,
                LocalDateTime.now()
        );
    }

    private String nextDraftPrescriptionNo() {
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM prescription WHERE prescription_no LIKE ?",
                Integer.class,
                "HS" + datePart + "%"
        );
        int sequence = (count == null ? 0 : count) + 1;
        return "HS" + datePart + String.format("%04d", sequence);
    }
}
