package com.herbscript.prescription;

import com.herbscript.prescription.dto.DashboardSummaryResponse;
import com.herbscript.prescription.dto.OperationLogResponse;
import com.herbscript.prescription.dto.PrescriptionDetailResponse;
import com.herbscript.prescription.dto.PrescriptionItemResponse;
import com.herbscript.prescription.dto.PrescriptionQuery;
import com.herbscript.prescription.dto.PrescriptionSummaryResponse;
import com.herbscript.recognition.dto.RecognitionDraftResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class PrescriptionQueryService {

    private static final RowMapper<PrescriptionSummaryResponse> SUMMARY_ROW_MAPPER = (rs, rowNum) ->
            new PrescriptionSummaryResponse(
                    rs.getLong("id"),
                    rs.getString("prescription_no"),
                    rs.getString("patient_name"),
                    rs.getString("gender"),
                    rs.getInt("age"),
                    rs.getString("prescription_date"),
                    rs.getInt("dose_count"),
                    rs.getString("entry_mode"),
                    rs.getString("status"),
                    rs.getString("created_by_name"),
                    rs.getString("created_at")
            );

    private final JdbcTemplate jdbcTemplate;

    public PrescriptionQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DashboardSummaryResponse getDashboardSummary() {
        Integer todayNewCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM prescription WHERE deleted = 0 AND prescription_date = CURRENT_DATE",
                Integer.class
        );
        Integer pendingReviewCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM prescription WHERE deleted = 0 AND status = 'pending_review'",
                Integer.class
        );
        Integer verifiedWeekCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM prescription WHERE deleted = 0 AND status = 'verified' " +
                        "AND prescription_date >= DATE_SUB(CURRENT_DATE, INTERVAL 7 DAY)",
                Integer.class
        );

        List<PrescriptionSummaryResponse> recent = jdbcTemplate.query(
                """
                SELECT p.id, p.prescription_no, p.patient_name, p.gender, p.age,
                       DATE_FORMAT(p.prescription_date, '%Y-%m-%d') AS prescription_date,
                       p.dose_count, p.entry_mode, p.status,
                       u.real_name AS created_by_name,
                       DATE_FORMAT(p.created_at, '%Y-%m-%d %H:%i:%s') AS created_at
                FROM prescription p
                LEFT JOIN sys_user u ON u.id = p.created_by
                WHERE p.deleted = 0
                ORDER BY p.created_at DESC
                LIMIT 10
                """,
                SUMMARY_ROW_MAPPER
        );

        return new DashboardSummaryResponse(
                nullToZero(todayNewCount),
                nullToZero(pendingReviewCount),
                nullToZero(verifiedWeekCount),
                calculateRecognitionSuccessRate(),
                recent
        );
    }

    public List<PrescriptionSummaryResponse> list(PrescriptionQuery query) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.prescription_no, p.patient_name, p.gender, p.age,
                       DATE_FORMAT(p.prescription_date, '%Y-%m-%d') AS prescription_date,
                       p.dose_count, p.entry_mode, p.status,
                       u.real_name AS created_by_name,
                       DATE_FORMAT(p.created_at, '%Y-%m-%d %H:%i:%s') AS created_at
                FROM prescription p
                LEFT JOIN sys_user u ON u.id = p.created_by
                WHERE p.deleted = 0
                """);

        List<Object> args = new ArrayList<>();

        if (query.keyword() != null && !query.keyword().isBlank()) {
            sql.append(" AND (p.prescription_no LIKE ? OR p.patient_name LIKE ? OR p.doctor_name LIKE ? OR p.diagnosis LIKE ?)");
            String keyword = "%" + query.keyword().trim() + "%";
            args.add(keyword);
            args.add(keyword);
            args.add(keyword);
            args.add(keyword);
        }

        if (query.entryMode() != null && !query.entryMode().isBlank()) {
            sql.append(" AND p.entry_mode = ?");
            args.add(query.entryMode());
        }

        if (query.status() != null && !query.status().isBlank()) {
            sql.append(" AND p.status = ?");
            args.add(query.status());
        }

        sql.append(" ORDER BY p.created_at DESC");

        return jdbcTemplate.query(sql.toString(), SUMMARY_ROW_MAPPER, args.toArray());
    }

    public List<PrescriptionSummaryResponse> listByPatientId(Long patientId) {
        return jdbcTemplate.query(
                """
                SELECT p.id, p.prescription_no, p.patient_name, p.gender, p.age,
                       DATE_FORMAT(p.prescription_date, '%Y-%m-%d') AS prescription_date,
                       p.dose_count, p.entry_mode, p.status,
                       u.real_name AS created_by_name,
                       DATE_FORMAT(p.created_at, '%Y-%m-%d %H:%i:%s') AS created_at
                FROM prescription p
                LEFT JOIN sys_user u ON u.id = p.created_by
                WHERE p.deleted = 0 AND p.patient_id = ?
                ORDER BY p.prescription_date DESC, p.created_at DESC
                """,
                SUMMARY_ROW_MAPPER,
                patientId
        );
    }

    public Optional<PrescriptionDetailResponse> getDetail(Long id) {
        List<PrescriptionDetailResponse> results = jdbcTemplate.query(
                """
                SELECT p.id, p.prescription_no, p.patient_name, p.gender, p.age, p.department,
                       p.diagnosis, p.dose_count,
                       DATE_FORMAT(p.prescription_date, '%Y-%m-%d') AS prescription_date,
                       p.doctor_name, p.usage_method, p.entry_mode, p.status,
                       u.real_name AS created_by_name,
                       DATE_FORMAT(p.created_at, '%Y-%m-%d %H:%i:%s') AS created_at,
                       p.patient_id,
                       rt.provider_name AS source_model,
                       p.source_image_url,
                       p.source_task_id
                FROM prescription p
                LEFT JOIN sys_user u ON u.id = p.created_by
                LEFT JOIN recognition_task rt ON rt.id = p.source_task_id
                WHERE p.deleted = 0 AND p.id = ?
                """,
                (rs, rowNum) -> toDetail(rs),
                id
        );

        return results.stream().findFirst();
    }

    public Optional<PrescriptionDetailResponse> getRecognitionDraft(Long taskId) {
        String sql = """
                SELECT p.id, p.prescription_no, p.patient_name, p.gender, p.age, p.department,
                       p.diagnosis, p.dose_count,
                       DATE_FORMAT(p.prescription_date, '%Y-%m-%d') AS prescription_date,
                       p.doctor_name, p.usage_method, p.entry_mode, p.status,
                       u.real_name AS created_by_name,
                       DATE_FORMAT(p.created_at, '%Y-%m-%d %H:%i:%s') AS created_at,
                       p.patient_id,
                       rt.provider_name AS source_model,
                       p.source_image_url,
                       p.source_task_id
                FROM prescription p
                LEFT JOIN sys_user u ON u.id = p.created_by
                LEFT JOIN recognition_task rt ON rt.id = p.source_task_id
                WHERE p.deleted = 0 AND p.status = 'pending_review'
                """;

        List<PrescriptionDetailResponse> results;
        if (taskId == null) {
            results = jdbcTemplate.query(sql + " ORDER BY p.created_at DESC LIMIT 1", (rs, rowNum) -> toDetail(rs));
        } else {
            results = jdbcTemplate.query(sql + " AND p.source_task_id = ?", (rs, rowNum) -> toDetail(rs), taskId);
        }

        return results.stream().findFirst();
    }

    private PrescriptionDetailResponse toDetail(ResultSet rs) throws SQLException {
        Long prescriptionId = rs.getLong("id");
        Long taskId = rs.getLong("source_task_id");
        if (rs.wasNull()) {
            taskId = null;
        }

        return new PrescriptionDetailResponse(
                prescriptionId,
                rs.getString("prescription_no"),
                rs.getObject("patient_id") == null ? null : rs.getLong("patient_id"),
                rs.getString("patient_name"),
                rs.getString("gender"),
                rs.getInt("age"),
                rs.getString("department"),
                rs.getString("diagnosis"),
                rs.getInt("dose_count"),
                rs.getString("prescription_date"),
                rs.getString("doctor_name"),
                rs.getString("usage_method"),
                rs.getString("entry_mode"),
                rs.getString("status"),
                rs.getString("created_by_name"),
                rs.getString("created_at"),
                rs.getString("source_model"),
                rs.getString("source_image_url"),
                findItems(prescriptionId),
                findLogs(prescriptionId),
                taskId == null ? null : findRecognitionDraft(taskId)
        );
    }

    private List<PrescriptionItemResponse> findItems(Long prescriptionId) {
        return jdbcTemplate.query(
                """
                SELECT id, sort_no, herb_name, raw_herb_name, dosage, unit,
                       special_instruction, confidence
                FROM prescription_item
                WHERE prescription_id = ?
                ORDER BY sort_no ASC
                """,
                (rs, rowNum) -> new PrescriptionItemResponse(
                        rs.getLong("id"),
                        rs.getInt("sort_no"),
                        rs.getString("herb_name"),
                        rs.getString("raw_herb_name"),
                        rs.getBigDecimal("dosage"),
                        rs.getString("unit"),
                        rs.getString("special_instruction"),
                        rs.getObject("confidence") == null ? null : rs.getDouble("confidence"),
                        null
                ),
                prescriptionId
        );
    }

    private List<OperationLogResponse> findLogs(Long prescriptionId) {
        return jdbcTemplate.query(
                """
                SELECT id,
                       DATE_FORMAT(created_at, '%Y-%m-%d %H:%i') AS created_time,
                       detail
                FROM operation_log
                WHERE target_type = 'prescription' AND target_id = ?
                ORDER BY created_at ASC
                """,
                (rs, rowNum) -> new OperationLogResponse(
                        rs.getLong("id"),
                        rs.getString("created_time"),
                        rs.getString("detail")
                ),
                prescriptionId
        );
    }

    private RecognitionDraftResponse findRecognitionDraft(Long taskId) {
        String warningMessage = jdbcTemplate.queryForObject(
                "SELECT warning_message FROM recognition_task WHERE id = ?",
                String.class,
                taskId
        );
        String rawText = jdbcTemplate.queryForObject(
                "SELECT raw_text FROM recognition_task WHERE id = ?",
                String.class,
                taskId
        );
        List<String> lowConfidenceFields = jdbcTemplate.query(
                "SELECT field_key FROM recognition_field WHERE task_id = ? AND confidence < 0.85 ORDER BY id ASC",
                (rs, rowNum) -> rs.getString("field_key"),
                taskId
        );
        List<String> warnings = warningMessage == null || warningMessage.isBlank()
                ? List.of()
                : List.of(warningMessage.split("\\|"));

        return jdbcTemplate.queryForObject(
                """
                SELECT id, provider_name, image_url
                FROM recognition_task
                WHERE id = ?
                """,
                (rs, rowNum) -> new RecognitionDraftResponse(
                        rs.getLong("id"),
                        rs.getString("provider_name"),
                        rs.getString("image_url"),
                        rawText,
                        warnings,
                        lowConfidenceFields
                ),
                taskId
        );
    }

    private double calculateRecognitionSuccessRate() {
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM prescription WHERE deleted = 0 AND entry_mode = 'ai_recognition'",
                Integer.class
        );
        Integer success = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM prescription WHERE deleted = 0 AND entry_mode = 'ai_recognition' AND status IN ('pending_review', 'verified', 'archived')",
                Integer.class
        );
        if (total == null || total == 0 || success == null) {
            return 0D;
        }
        return Math.round(success * 100.0 / total) / 100.0;
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
