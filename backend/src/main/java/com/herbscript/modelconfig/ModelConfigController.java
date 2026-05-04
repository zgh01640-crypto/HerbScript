package com.herbscript.modelconfig;

import com.herbscript.common.ApiResponse;
import com.herbscript.modelconfig.dto.ModelConfigActivateRequest;
import com.herbscript.modelconfig.dto.ModelConfigPageResponse;
import com.herbscript.modelconfig.dto.ModelConfigSaveRequest;
import com.herbscript.modelconfig.dto.ModelConfigTestRequest;
import com.herbscript.modelconfig.dto.ModelConfigTestResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/model-config")
public class ModelConfigController {

    private final ModelConfigService modelConfigService;

    public ModelConfigController(ModelConfigService modelConfigService) {
        this.modelConfigService = modelConfigService;
    }

    @GetMapping
    public ApiResponse<ModelConfigPageResponse> getConfig() {
        return ApiResponse.success(modelConfigService.getConfigPage());
    }

    @PutMapping
    public ApiResponse<ModelConfigPageResponse> updateConfig(@Valid @RequestBody ModelConfigSaveRequest request) {
        return ApiResponse.success(modelConfigService.saveProfile(request));
    }

    @PostMapping("/activate")
    public ApiResponse<ModelConfigPageResponse> activateConfig(@Valid @RequestBody ModelConfigActivateRequest request) {
        return ApiResponse.success(modelConfigService.activateProfile(request.profileId()));
    }

    @PostMapping("/test")
    public ApiResponse<ModelConfigTestResponse> testConfig(@RequestBody ModelConfigTestRequest request) {
        return ApiResponse.success(modelConfigService.testConfig(request));
    }
}
