package com.conduit.hazard.dto;

import com.conduit.hazard.HazardLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HazardCreateRequest(
        @NotNull(message = "inspectionTaskId 不能为空")
        Long inspectionTaskId,

        @NotBlank(message = "description 不能为空")
        @Size(max = 1024, message = "description 长度不能超过 1024")
        String description,

        @NotNull(message = "level 不能为空")
        HazardLevel level,

        @NotBlank(message = "reporter 不能为空")
        @Size(max = 64, message = "reporter 长度不能超过 64")
        String reporter
) {
}
