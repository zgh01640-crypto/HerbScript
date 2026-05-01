package com.herbscript.patient;

import com.herbscript.common.ApiResponse;
import com.herbscript.prescription.PrescriptionQueryService;
import com.herbscript.prescription.dto.PrescriptionSummaryResponse;
import com.herbscript.patient.dto.PatientDetailResponse;
import com.herbscript.patient.dto.PatientMergeRequest;
import com.herbscript.patient.dto.PatientMatchCandidateResponse;
import com.herbscript.patient.dto.PatientMatchRequest;
import com.herbscript.patient.dto.PatientSummaryResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService patientService;
    private final PrescriptionQueryService prescriptionQueryService;

    public PatientController(PatientService patientService, PrescriptionQueryService prescriptionQueryService) {
        this.patientService = patientService;
        this.prescriptionQueryService = prescriptionQueryService;
    }

    @PostMapping("/match")
    public ApiResponse<List<PatientMatchCandidateResponse>> match(@Valid @RequestBody PatientMatchRequest request) {
        return ApiResponse.success(patientService.match(request));
    }

    @GetMapping
    public ApiResponse<List<PatientSummaryResponse>> list(@RequestParam(required = false) String keyword) {
        return ApiResponse.success(patientService.list(keyword));
    }

    @GetMapping("/{patientId}")
    public ApiResponse<PatientDetailResponse> detail(@PathVariable Long patientId) {
        List<PrescriptionSummaryResponse> prescriptions = prescriptionQueryService.listByPatientId(patientId);
        return ApiResponse.success(patientService.getDetail(patientId, prescriptions));
    }

    @PostMapping("/{patientId}/merge")
    public ApiResponse<Void> merge(@PathVariable Long patientId, @Valid @RequestBody PatientMergeRequest request) {
        patientService.mergePatients(patientId, request);
        return ApiResponse.success(null);
    }

    @GetMapping("/{patientId}/prescriptions")
    public ApiResponse<List<PrescriptionSummaryResponse>> prescriptions(@PathVariable Long patientId) {
        return ApiResponse.success(prescriptionQueryService.listByPatientId(patientId));
    }
}
