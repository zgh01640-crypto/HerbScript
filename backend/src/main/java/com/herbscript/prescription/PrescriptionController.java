package com.herbscript.prescription;

import com.herbscript.common.ApiResponse;
import com.herbscript.prescription.dto.PrescriptionCreateResponse;
import com.herbscript.prescription.dto.PrescriptionDetailResponse;
import com.herbscript.prescription.dto.PrescriptionQuery;
import com.herbscript.prescription.dto.PrescriptionSaveRequest;
import com.herbscript.prescription.dto.PrescriptionSummaryResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/prescriptions")
public class PrescriptionController {

    private final PrescriptionQueryService prescriptionService;
    private final PrescriptionCommandService prescriptionCommandService;

    public PrescriptionController(
            PrescriptionQueryService prescriptionService,
            PrescriptionCommandService prescriptionCommandService
    ) {
        this.prescriptionService = prescriptionService;
        this.prescriptionCommandService = prescriptionCommandService;
    }

    @GetMapping
    public ApiResponse<List<PrescriptionSummaryResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String entryMode,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.success(prescriptionService.list(new PrescriptionQuery(keyword, entryMode, status)));
    }

    @PostMapping
    public ApiResponse<PrescriptionCreateResponse> create(@Valid @RequestBody PrescriptionSaveRequest request) {
        return ApiResponse.success(prescriptionCommandService.createManualPrescription(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<PrescriptionCreateResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody PrescriptionSaveRequest request
    ) {
        return ApiResponse.success(prescriptionCommandService.updatePrescription(id, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<PrescriptionDetailResponse> detail(@PathVariable Long id) {
        return prescriptionService.getDetail(id)
                .map(ApiResponse::success)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "处方不存在"));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        prescriptionCommandService.deletePrescription(id);
        return ApiResponse.success(null);
    }
}
