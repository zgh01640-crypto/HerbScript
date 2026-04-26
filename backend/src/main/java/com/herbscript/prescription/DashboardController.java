package com.herbscript.prescription;

import com.herbscript.common.ApiResponse;
import com.herbscript.prescription.dto.DashboardSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final PrescriptionQueryService prescriptionService;

    public DashboardController(PrescriptionQueryService prescriptionService) {
        this.prescriptionService = prescriptionService;
    }

    @GetMapping("/summary")
    public ApiResponse<DashboardSummaryResponse> summary() {
        return ApiResponse.success(prescriptionService.getDashboardSummary());
    }
}
