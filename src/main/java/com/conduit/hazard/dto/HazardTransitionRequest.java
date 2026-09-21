package com.conduit.hazard.dto;

import com.conduit.hazard.HazardStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HazardTransitionRequest(
        @NotNull(message = "targetStatus 不能为空")
        HazardStatus targetStatus,

        @Size(max = 64, message = "assignee 长度不能超过 64")
        String assignee,

        @Size(max = 1024, message = "resolution 长度不能超过 1024")
        String resolution
) {
}
