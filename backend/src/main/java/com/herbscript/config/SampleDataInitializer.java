package com.herbscript.config;

import com.herbscript.patient.PatientService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SampleDataInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final PatientService patientService;

    public SampleDataInitializer(JdbcTemplate jdbcTemplate, PatientService patientService) {
        this.jdbcTemplate = jdbcTemplate;
        this.patientService = patientService;
    }

    @Override
    public void run(String... args) {
        ensurePatientSchema();
        jdbcTemplate.update("UPDATE sys_user SET real_name = ? WHERE id = 1", "系统管理员");
        patientService.ensurePatientBackfill();

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM prescription", Integer.class);
        if (count != null && count > 0) {
            return;
        }

        jdbcTemplate.update("""
                INSERT INTO patient (id, patient_no, name, gender, age, remark)
                VALUES
                (1, 'PT202604210001', '王秀兰', '女', 58, '示例患者'),
                (2, 'PT202604210002', '赵明德', '男', 43, '示例患者'),
                (3, 'PT202604210003', '刘海峰', '男', 39, '示例患者')
                """);

        jdbcTemplate.update("""
                INSERT INTO recognition_task
                (id, image_url, provider_name, status, warning_message, created_by)
                VALUES
                (2001, '/mock/prescriptions/1.png', 'doubao-seed-2-0-pro', 'success', '服用说明识别置信度较低|白术特殊说明疑似为“麸炒”', 1),
                (2002, '/mock/prescriptions/3.png', 'doubao-seed-2-0-pro', 'success', '', 1)
                """);

        jdbcTemplate.update("""
                INSERT INTO prescription
                (id, prescription_no, patient_id, patient_name, gender, age, department, diagnosis, dose_count, prescription_date,
                 doctor_name, usage_method, entry_mode, status, source_image_url, source_task_id, created_by, created_at, updated_at)
                VALUES
                (1, 'HS202604210012', 1, '王秀兰', '女', 58, '内科', '脾胃虚弱', 7, '2026-04-21',
                 '李医生', '水煎服，每日一剂', 'ai_recognition', 'pending_review', '/mock/prescriptions/1.png', 2001, 1, '2026-04-21 10:22:00', '2026-04-21 10:22:00'),
                (2, 'HS202604210011', 2, '赵明德', '男', 43, '脾胃病科', '食少乏力', 5, '2026-04-21',
                 '陈医生', '温水煎服', 'manual', 'verified', NULL, NULL, 1, '2026-04-21 09:46:00', '2026-04-21 09:46:00'),
                (3, 'HS202604210010', 3, '刘海峰', '男', 39, '肝胆科', '肝郁气滞', 6, '2026-04-20',
                 '孙医生', '煎服，分早晚两次', 'ai_recognition', 'archived', '/mock/prescriptions/3.png', 2002, 1, '2026-04-20 16:31:00', '2026-04-20 16:31:00')
                """);

        jdbcTemplate.update("""
                INSERT INTO prescription_item
                (id, prescription_id, sort_no, herb_name, raw_herb_name, dosage, unit, special_instruction, confidence, confirm_status)
                VALUES
                (101, 1, 1, '黄芪', '黄芪', 15.00, 'g', NULL, 0.9800, 'confirmed'),
                (102, 1, 2, '党参', '党参', 12.00, 'g', NULL, 0.9400, 'confirmed'),
                (103, 1, 3, '白术', '白术', 10.00, 'g', '麸炒', 0.8300, 'corrected'),
                (104, 1, 4, '炙甘草', '炙甘草', 6.00, 'g', NULL, 0.7200, 'confirmed'),
                (201, 2, 1, '山药', NULL, 20.00, 'g', NULL, NULL, 'confirmed'),
                (202, 2, 2, '茯苓', NULL, 12.00, 'g', NULL, NULL, 'confirmed'),
                (203, 2, 3, '陈皮', NULL, 6.00, 'g', NULL, NULL, 'confirmed'),
                (301, 3, 1, '柴胡', '柴胡', 10.00, 'g', NULL, 0.9600, 'confirmed'),
                (302, 3, 2, '香附', '香附', 9.00, 'g', NULL, 0.9100, 'confirmed'),
                (303, 3, 3, '白芍', '白芍', 12.00, 'g', NULL, 0.9300, 'confirmed')
                """);

        jdbcTemplate.update("""
                INSERT INTO recognition_field
                (id, task_id, field_key, field_label, raw_value, corrected_value, confidence, confirm_status, confirmed_by, confirmed_at)
                VALUES
                (1, 2001, 'diagnosis', '临床诊断', '脾胃虚弱', '脾胃虚弱', 0.8200, 'corrected', 1, NOW()),
                (2, 2001, 'usageMethod', '服用说明', '水煎服，每日一剂', '水煎服，每日一剂', 0.7900, 'corrected', 1, NOW()),
                (3, 2001, 'patientName', '患者姓名', '王秀兰', '王秀兰', 0.9800, 'confirmed', 1, NOW())
                """);

        jdbcTemplate.update("""
                INSERT INTO operation_log
                (id, operator_id, operator_name, module_name, operation_type, target_type, target_id, detail, created_at)
                VALUES
                (1, 1, '录入员王芳', 'prescription', 'recognition', 'prescription', 1, '模型生成识别草稿，标记 2 个低置信字段。', '2026-04-21 10:22:00'),
                (2, 1, '录入员王芳', 'prescription', 'review', 'prescription', 1, '录入员开始人工校对处方字段与药味明细。', '2026-04-21 10:24:00'),
                (3, 1, '录入员陈雨', 'prescription', 'create', 'prescription', 2, '录入员手动新增处方并保存。', '2026-04-21 09:46:00'),
                (4, 1, '录入员陈雨', 'prescription', 'verify', 'prescription', 2, '医师完成内容复核。', '2026-04-21 09:48:00'),
                (5, 1, '录入员王芳', 'prescription', 'recognition', 'prescription', 3, '模型生成识别草稿。', '2026-04-20 16:22:00'),
                (6, 1, '录入员王芳', 'prescription', 'confirm', 'prescription', 3, '录入员完成校对并确认入库。', '2026-04-20 16:27:00'),
                (7, 1, '系统管理员', 'prescription', 'archive', 'prescription', 3, '管理员复核并归档处方。', '2026-04-20 16:31:00')
                """);
    }

    private void ensurePatientSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS patient (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  patient_no VARCHAR(64) NOT NULL,
                  name VARCHAR(64) NOT NULL,
                  gender VARCHAR(16) NOT NULL,
                  age INT NOT NULL,
                  phone VARCHAR(32) NULL,
                  remark VARCHAR(255) NULL,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  deleted TINYINT NOT NULL DEFAULT 0,
                  UNIQUE KEY uk_patient_no (patient_no),
                  KEY idx_patient_name (name),
                  KEY idx_patient_phone (phone)
                )
                """);

        Integer patientIdColumnCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'prescription'
                  AND COLUMN_NAME = 'patient_id'
                """,
                Integer.class
        );

        if (patientIdColumnCount == null || patientIdColumnCount == 0) {
            jdbcTemplate.execute("ALTER TABLE prescription ADD COLUMN patient_id BIGINT NULL AFTER prescription_type");
        }
    }
}
