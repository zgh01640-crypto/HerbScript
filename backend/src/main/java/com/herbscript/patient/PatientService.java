package com.herbscript.patient;

import com.herbscript.patient.dto.PatientDraftRequest;
import com.herbscript.patient.dto.PatientDetailResponse;
import com.herbscript.patient.dto.PatientMergeRequest;
import com.herbscript.patient.dto.PatientMatchCandidateResponse;
import com.herbscript.patient.dto.PatientMatchRequest;
import com.herbscript.patient.dto.PatientSummaryResponse;
import com.herbscript.prescription.dto.PrescriptionSummaryResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PatientService {

    private final JdbcTemplate jdbcTemplate;

    public PatientService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PatientMatchCandidateResponse> match(PatientMatchRequest request) {
        List<PatientMatchCandidateResponse> candidates = jdbcTemplate.query(
                """
                SELECT p.id, p.patient_no, p.name, p.gender, p.age, p.phone,
                       COUNT(pr.id) AS prescription_count,
                       DATE_FORMAT(MAX(pr.prescription_date), '%Y-%m-%d') AS last_prescription_date
                FROM patient p
                LEFT JOIN prescription pr ON pr.patient_id = p.id AND pr.deleted = 0
                WHERE p.deleted = 0 AND p.name = ?
                GROUP BY p.id, p.patient_no, p.name, p.gender, p.age, p.phone
                """,
                (rs, rowNum) -> toCandidate(rs, request),
                request.name().trim()
        );

        return candidates.stream()
                .filter(candidate -> candidate.matchScore() > 0)
                .sorted(Comparator
                        .comparing(PatientMatchCandidateResponse::matchScore).reversed()
                .thenComparing(PatientMatchCandidateResponse::prescriptionCount, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public List<PatientSummaryResponse> list(String keyword) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.patient_no, p.name, p.gender, p.age, p.phone,
                       COUNT(pr.id) AS prescription_count,
                       DATE_FORMAT(MAX(pr.prescription_date), '%Y-%m-%d') AS last_prescription_date
                FROM patient p
                LEFT JOIN prescription pr ON pr.patient_id = p.id AND pr.deleted = 0
                WHERE p.deleted = 0
                """);
        List<Object> args = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (p.patient_no LIKE ? OR p.name LIKE ? OR p.phone LIKE ?)");
            String normalized = "%" + keyword.trim() + "%";
            args.add(normalized);
            args.add(normalized);
            args.add(normalized);
        }

        sql.append("""
                 GROUP BY p.id, p.patient_no, p.name, p.gender, p.age, p.phone
                 ORDER BY last_prescription_date DESC, p.updated_at DESC, p.id DESC
                """);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> toSummary(rs), args.toArray());
    }

    public PatientDetailResponse getDetail(Long patientId, List<PrescriptionSummaryResponse> prescriptions) {
        List<PatientDetailResponse> results = jdbcTemplate.query(
                """
                SELECT p.id, p.patient_no, p.name, p.gender, p.age, p.phone, p.remark,
                       COUNT(pr.id) AS prescription_count,
                       DATE_FORMAT(MAX(pr.prescription_date), '%Y-%m-%d') AS last_prescription_date
                FROM patient p
                LEFT JOIN prescription pr ON pr.patient_id = p.id AND pr.deleted = 0
                WHERE p.deleted = 0 AND p.id = ?
                GROUP BY p.id, p.patient_no, p.name, p.gender, p.age, p.phone, p.remark
                """,
                (rs, rowNum) -> new PatientDetailResponse(
                        rs.getLong("id"),
                        rs.getString("patient_no"),
                        rs.getString("name"),
                        rs.getString("gender"),
                        rs.getInt("age"),
                        rs.getString("phone"),
                        rs.getString("remark"),
                        rs.getInt("prescription_count"),
                        rs.getString("last_prescription_date"),
                        prescriptions
                ),
                patientId
        );

        if (results.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "患者不存在");
        }

        return results.get(0);
    }

    public void mergePatients(Long targetPatientId, PatientMergeRequest request) {
        Long sourcePatientId = request.sourcePatientId();
        if (targetPatientId.equals(sourcePatientId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能将患者合并到自身");
        }

        PatientProfile target = getPatientProfile(targetPatientId);
        PatientProfile source = getPatientProfile(sourcePatientId);

        jdbcTemplate.update(
                "UPDATE prescription SET patient_id = ? WHERE deleted = 0 AND patient_id = ?",
                targetPatientId,
                sourcePatientId
        );

        String mergedPhone = hasText(target.phone()) ? target.phone() : source.phone();
        String mergedRemark = mergeRemark(target.remark(), source);

        jdbcTemplate.update(
                """
                UPDATE patient
                SET phone = ?, remark = ?
                WHERE id = ? AND deleted = 0
                """,
                mergedPhone,
                mergedRemark,
                targetPatientId
        );

        jdbcTemplate.update(
                """
                UPDATE patient
                SET deleted = 1,
                    remark = ?
                WHERE id = ? AND deleted = 0
                """,
                buildArchivedRemark(source, target),
                sourcePatientId
        );

        jdbcTemplate.update(
                """
                INSERT INTO operation_log
                (operator_id, operator_name, module_name, operation_type, target_type, target_id, detail)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                1L,
                "系统管理员",
                "patient",
                "merge",
                "patient",
                targetPatientId,
                "合并患者主档：" + source.name() + "(" + source.patientNo() + ") -> " + target.name() + "(" + target.patientNo() + ")"
        );
    }

    public Long resolvePatientId(Long patientId, PatientDraftRequest patientDraft, String fallbackName, String fallbackGender, Integer fallbackAge) {
        if (patientId != null) {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM patient WHERE id = ? AND deleted = 0",
                    Integer.class,
                    patientId
            );
            if (exists == null || exists == 0) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "所选患者不存在");
            }
            return patientId;
        }

        PatientDraftRequest resolvedDraft = patientDraft != null
                ? patientDraft
                : new PatientDraftRequest(fallbackName, fallbackGender, fallbackAge, null, null);

        return createPatient(resolvedDraft);
    }

    public Long createPatient(PatientDraftRequest request) {
        String patientNo = nextPatientNo();
        jdbcTemplate.update(
                """
                INSERT INTO patient (patient_no, name, gender, age, phone, remark)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                patientNo,
                request.name(),
                request.gender(),
                request.age(),
                request.phone(),
                request.remark()
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    public void ensurePatientBackfill() {
        List<Long> prescriptionIds = jdbcTemplate.query(
                """
                SELECT id
                FROM prescription
                WHERE deleted = 0 AND patient_id IS NULL
                ORDER BY id ASC
                """,
                (rs, rowNum) -> rs.getLong("id")
        );

        for (Long prescriptionId : prescriptionIds) {
            PatientSnapshot snapshot = jdbcTemplate.queryForObject(
                    """
                    SELECT patient_name, gender, age
                    FROM prescription
                    WHERE id = ?
                    """,
                    (rs, rowNum) -> new PatientSnapshot(
                            rs.getString("patient_name"),
                            rs.getString("gender"),
                            rs.getInt("age")
                    ),
                    prescriptionId
            );

            if (snapshot == null) {
                continue;
            }

            List<PatientMatchCandidateResponse> matches = match(new PatientMatchRequest(
                    snapshot.name(),
                    snapshot.gender(),
                    snapshot.age(),
                    null
            ));

            Long resolvedPatientId;
            if (!matches.isEmpty() && "high".equals(matches.get(0).matchLevel())) {
                resolvedPatientId = matches.get(0).id();
            } else {
                resolvedPatientId = createPatient(new PatientDraftRequest(
                        snapshot.name(),
                        snapshot.gender(),
                        snapshot.age(),
                        null,
                        "由历史处方回填生成"
                ));
            }

            jdbcTemplate.update("UPDATE prescription SET patient_id = ? WHERE id = ?", resolvedPatientId, prescriptionId);
        }
    }

    private PatientMatchCandidateResponse toCandidate(ResultSet rs, PatientMatchRequest request) throws SQLException {
        int ageDiff = Math.abs(rs.getInt("age") - request.age());
        boolean genderMatched = rs.getString("gender").equals(request.gender());
        boolean phoneMatched = request.phone() != null
                && !request.phone().isBlank()
                && request.phone().equals(rs.getString("phone"));

        double score = 0D;
        String level = "low";

        if (phoneMatched && genderMatched && ageDiff <= 1) {
            score = 0.99;
            level = "high";
        } else if (genderMatched && ageDiff <= 1) {
            score = 0.95;
            level = "high";
        } else if (genderMatched && ageDiff <= 3) {
            score = 0.82;
            level = "medium";
        } else if (genderMatched || ageDiff <= 3) {
            score = 0.64;
            level = "low";
        }

        return new PatientMatchCandidateResponse(
                rs.getLong("id"),
                rs.getString("patient_no"),
                rs.getString("name"),
                rs.getString("gender"),
                rs.getInt("age"),
                rs.getString("phone"),
                level,
                score,
                rs.getInt("prescription_count"),
                rs.getString("last_prescription_date")
        );
    }

    private PatientSummaryResponse toSummary(ResultSet rs) throws SQLException {
        return new PatientSummaryResponse(
                rs.getLong("id"),
                rs.getString("patient_no"),
                rs.getString("name"),
                rs.getString("gender"),
                rs.getInt("age"),
                rs.getString("phone"),
                rs.getInt("prescription_count"),
                rs.getString("last_prescription_date")
        );
    }

    private PatientProfile getPatientProfile(Long patientId) {
        List<PatientProfile> results = jdbcTemplate.query(
                """
                SELECT id, patient_no, name, gender, age, phone, remark
                FROM patient
                WHERE id = ? AND deleted = 0
                """,
                (rs, rowNum) -> new PatientProfile(
                        rs.getLong("id"),
                        rs.getString("patient_no"),
                        rs.getString("name"),
                        rs.getString("gender"),
                        rs.getInt("age"),
                        rs.getString("phone"),
                        rs.getString("remark")
                ),
                patientId
        );

        if (results.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "待合并患者不存在");
        }

        return results.get(0);
    }

    private String mergeRemark(String targetRemark, PatientProfile source) {
        String sourceRemark = source.remark();
        if (!hasText(targetRemark) && !hasText(sourceRemark)) {
            return null;
        }
        if (!hasText(targetRemark)) {
            return sourceRemark;
        }
        if (!hasText(sourceRemark)) {
            return targetRemark;
        }
        return targetRemark + "；合并来源备注：" + sourceRemark;
    }

    private String buildArchivedRemark(PatientProfile source, PatientProfile target) {
        String base = "已合并至患者主档 " + target.name() + "(" + target.patientNo() + ")";
        if (!hasText(source.remark())) {
            return base;
        }
        return source.remark() + "；" + base;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String nextPatientNo() {
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM patient WHERE patient_no LIKE ?",
                Integer.class,
                "PT" + datePart + "%"
        );
        int sequence = (count == null ? 0 : count) + 1;
        return "PT" + datePart + String.format("%04d", sequence);
    }

    private record PatientProfile(Long id, String patientNo, String name, String gender, Integer age, String phone, String remark) {
    }

    private record PatientSnapshot(String name, String gender, Integer age) {
    }
}
