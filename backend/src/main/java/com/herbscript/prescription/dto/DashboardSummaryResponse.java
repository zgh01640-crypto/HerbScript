package com.herbscript.prescription.dto;

import java.util.List;

public record DashboardSummaryResponse(
        Integer todayNewCount,
        Integer pendingReviewCount,
        Integer verifiedWeekCount,
        Double recognitionSuccessRate,
        List<PrescriptionSummaryResponse> recentPrescriptions
) {
}
