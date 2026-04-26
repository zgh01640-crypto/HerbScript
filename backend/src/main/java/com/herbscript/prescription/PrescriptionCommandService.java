package com.herbscript.prescription;

import com.herbscript.prescription.dto.PrescriptionCreateResponse;
import com.herbscript.prescription.dto.PrescriptionItemSaveRequest;
import com.herbscript.prescription.dto.PrescriptionSaveRequest;
import com.herbscript.recognition.dto.RecognitionConfirmRequest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PrescriptionCommandService {

    private final JdbcTemplate jdbcTemplate;

    public PrescriptionCommandService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public PrescriptionCreateResponse createManualPrescription(PrescriptionSaveRequest request) {
        String prescriptionNo = nextPrescriptionNo();
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO prescription
                    (prescription_no, hospital_name, prescription_type, patient_name, gender, age,
                     department, diagnosis, dose_count, prescription_date, doctor_name, usage_method,
                     remark, entry_mode, status, created_by, updated_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'manual', 'verified', 1, 1)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, prescriptionNo);
            ps.setString(2, request.hospitalName());
            ps.setString(3, request.prescriptionType());
            ps.setString(4, request.patientName());
            ps.setString(5, request.gender());
            ps.setInt(6, request.age());
            ps.setString(7, request.department());
            ps.setString(8, request.diagnosis());
            ps.setInt(9, request.doseCount());
            ps.setString(10, request.prescriptionDate());
            ps.setString(11, request.doctorName());
            ps.setString(12, request.usageMethod());
            ps.setString(13, request.remark());
            return ps;
        }, keyHolder);

        Long prescriptionId = keyHolder.getKey() == null ? null : keyHolder.getKey().longValue();
        if (prescriptionId == null) {
            throw new IllegalStateException("处方创建失败");
        }

        int index = 1;
        for (PrescriptionItemSaveRequest item : request.items()) {
            jdbcTemplate.update("""
                    INSERT INTO prescription_item
                    (prescription_id, sort_no, herb_name, raw_herb_name, dosage, unit, special_instruction, confirm_status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'confirmed')
                    """,
                    prescriptionId,
                    item.sortNo() != null ? item.sortNo() : index,
                    item.herbName(),
                    item.rawHerbName(),
                    item.dosage(),
                    item.unit(),
                    item.specialInstruction()
            );
            index++;
        }

        jdbcTemplate.update("""
                INSERT INTO operation_log
                (operator_id, operator_name, module_name, operation_type, target_type, target_id, detail, created_at)
                VALUES (1, '系统管理员', 'prescription', 'create', 'prescription', ?, ?, ?)
                """,
                prescriptionId,
                "手动新增处方并保存。",
                LocalDateTime.now()
        );

        return new PrescriptionCreateResponse(prescriptionId, prescriptionNo);
    }

    @Transactional
    public PrescriptionCreateResponse confirmRecognitionDraft(Long taskId, RecognitionConfirmRequest request) {
        Long prescriptionId = jdbcTemplate.query(
                """
                SELECT id
                FROM prescription
                WHERE source_task_id = ? AND deleted = 0
                ORDER BY id DESC
                LIMIT 1
                """,
                (rs, rowNum) -> rs.getLong("id"),
                taskId
        ).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("识别草稿不存在"));

        jdbcTemplate.update("""
                UPDATE prescription
                SET patient_name = ?, gender = ?, age = ?, department = ?, diagnosis = ?,
                    dose_count = ?, prescription_date = ?, doctor_name = ?, usage_method = ?,
                    remark = ?, status = 'verified', updated_by = 1, updated_at = NOW(),
                    verified_by = 1, verified_at = NOW()
                WHERE id = ?
                """,
                request.patientName(),
                request.gender(),
                request.age(),
                request.department(),
                request.diagnosis(),
                request.doseCount(),
                request.prescriptionDate(),
                request.doctorName(),
                request.usageMethod(),
                request.remark(),
                prescriptionId
        );

        jdbcTemplate.update("DELETE FROM prescription_item WHERE prescription_id = ?", prescriptionId);

        int index = 1;
        for (PrescriptionItemSaveRequest item : request.items()) {
            jdbcTemplate.update("""
                    INSERT INTO prescription_item
                    (prescription_id, sort_no, herb_name, raw_herb_name, dosage, unit, special_instruction, confirm_status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'corrected')
                    """,
                    prescriptionId,
                    item.sortNo() != null ? item.sortNo() : index,
                    item.herbName(),
                    item.rawHerbName(),
                    item.dosage(),
                    item.unit(),
                    item.specialInstruction()
            );
            index++;
        }

        jdbcTemplate.update("""
                UPDATE recognition_field
                SET corrected_value = CASE
                    WHEN field_key = 'patientName' THEN ?
                    WHEN field_key = 'gender' THEN ?
                    WHEN field_key = 'diagnosis' THEN ?
                    WHEN field_key = 'doseCount' THEN ?
                    WHEN field_key = 'usageMethod' THEN ?
                    WHEN field_key = 'doctorName' THEN ?
                    ELSE corrected_value
                END,
                confirm_status = 'corrected',
                confirmed_by = 1,
                confirmed_at = NOW(),
                updated_at = NOW()
                WHERE task_id = ?
                """,
                request.patientName(),
                request.gender(),
                request.diagnosis(),
                String.valueOf(request.doseCount()),
                request.usageMethod(),
                request.doctorName(),
                taskId
        );

        jdbcTemplate.update("""
                INSERT INTO operation_log
                (operator_id, operator_name, module_name, operation_type, target_type, target_id, detail, created_at)
                VALUES (1, '系统管理员', 'recognition', 'confirm', 'prescription', ?, ?, ?)
                """,
                prescriptionId,
                "识别草稿人工校对完成并确认入库。",
                LocalDateTime.now()
        );

        String prescriptionNo = jdbcTemplate.queryForObject(
                "SELECT prescription_no FROM prescription WHERE id = ?",
                String.class,
                prescriptionId
        );

        return new PrescriptionCreateResponse(prescriptionId, prescriptionNo);
    }

    @Transactional
    public PrescriptionCreateResponse updatePrescription(Long id, PrescriptionSaveRequest request) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM prescription WHERE id = ? AND deleted = 0",
                Integer.class,
                id
        );
        if (exists == null || exists == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "处方不存在");
        }

        jdbcTemplate.update("""
                UPDATE prescription
                SET hospital_name = ?, prescription_type = ?, patient_name = ?, gender = ?, age = ?,
                    department = ?, diagnosis = ?, dose_count = ?, prescription_date = ?, doctor_name = ?,
                    usage_method = ?, remark = ?, updated_by = 1, updated_at = NOW()
                WHERE id = ?
                """,
                request.hospitalName(),
                request.prescriptionType(),
                request.patientName(),
                request.gender(),
                request.age(),
                request.department(),
                request.diagnosis(),
                request.doseCount(),
                request.prescriptionDate(),
                request.doctorName(),
                request.usageMethod(),
                request.remark(),
                id
        );

        jdbcTemplate.update("DELETE FROM prescription_item WHERE prescription_id = ?", id);
        int index = 1;
        for (PrescriptionItemSaveRequest item : request.items()) {
            jdbcTemplate.update("""
                    INSERT INTO prescription_item
                    (prescription_id, sort_no, herb_name, raw_herb_name, dosage, unit, special_instruction, confirm_status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'confirmed')
                    """,
                    id,
                    item.sortNo() != null ? item.sortNo() : index,
                    item.herbName(),
                    item.rawHerbName(),
                    item.dosage(),
                    item.unit(),
                    item.specialInstruction()
            );
            index++;
        }

        jdbcTemplate.update("""
                INSERT INTO operation_log
                (operator_id, operator_name, module_name, operation_type, target_type, target_id, detail, created_at)
                VALUES (1, '系统管理员', 'prescription', 'update', 'prescription', ?, ?, ?)
                """,
                id,
                "编辑处方并保存更新。",
                LocalDateTime.now()
        );

        String prescriptionNo = jdbcTemplate.queryForObject(
                "SELECT prescription_no FROM prescription WHERE id = ?",
                String.class,
                id
        );
        return new PrescriptionCreateResponse(id, prescriptionNo);
    }

    @Transactional
    public void deletePrescription(Long id) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM prescription WHERE id = ? AND deleted = 0",
                Integer.class,
                id
        );
        if (exists == null || exists == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "处方不存在");
        }

        jdbcTemplate.update(
                "UPDATE prescription SET deleted = 1, updated_by = 1, updated_at = NOW() WHERE id = ?",
                id
        );

        jdbcTemplate.update("""
                INSERT INTO operation_log
                (operator_id, operator_name, module_name, operation_type, target_type, target_id, detail, created_at)
                VALUES (1, '系统管理员', 'prescription', 'delete', 'prescription', ?, ?, ?)
                """,
                id,
                "删除处方记录。",
                LocalDateTime.now()
        );
    }

    private String nextPrescriptionNo() {
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
