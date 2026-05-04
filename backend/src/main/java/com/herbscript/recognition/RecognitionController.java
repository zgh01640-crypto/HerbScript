package com.herbscript.recognition;

import com.herbscript.common.ApiResponse;
import com.herbscript.prescription.PrescriptionCommandService;
import com.herbscript.prescription.PrescriptionQueryService;
import com.herbscript.prescription.dto.PrescriptionCreateResponse;
import com.herbscript.prescription.dto.PrescriptionDetailResponse;
import com.herbscript.recognition.dto.RecognitionConfirmRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/recognitions")
public class RecognitionController {

    private final PrescriptionQueryService prescriptionService;
    private final PrescriptionCommandService prescriptionCommandService;
    private final RecognitionUploadService recognitionUploadService;

    public RecognitionController(
            PrescriptionQueryService prescriptionService,
            PrescriptionCommandService prescriptionCommandService,
            RecognitionUploadService recognitionUploadService
    ) {
        this.prescriptionService = prescriptionService;
        this.prescriptionCommandService = prescriptionCommandService;
        this.recognitionUploadService = recognitionUploadService;
    }

    @GetMapping("/draft")
    public ApiResponse<PrescriptionDetailResponse> draft(@RequestParam(required = false) Long taskId) {
        return ApiResponse.success(prescriptionService.getRecognitionDraft(taskId).orElse(null));
    }

    @GetMapping("/{taskId:\\d+}")
    public ApiResponse<PrescriptionDetailResponse> detail(@PathVariable Long taskId) {
        return prescriptionService.getRecognitionDraft(taskId)
                .map(ApiResponse::success)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "识别任务不存在"));
    }

    @PostMapping("/upload")
    public ApiResponse<PrescriptionDetailResponse> upload(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success(recognitionUploadService.uploadAndRecognize(file));
    }

    @PostMapping("/{taskId:\\d+}/confirm")
    public ApiResponse<PrescriptionCreateResponse> confirm(
            @PathVariable Long taskId,
            @Valid @RequestBody RecognitionConfirmRequest request
    ) {
        return ApiResponse.success(prescriptionCommandService.confirmRecognitionDraft(taskId, request));
    }
}
