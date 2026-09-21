package com.conduit.emergency.dto;

import com.conduit.emergency.BurstStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BurstEventTransitionRequest(
        @NotNull(message = "targetStatus 不能为空")
        BurstStatus targetStatus,

        List<Long> resourceIds,

        @Size(max = 1024, message = "resolution 长度不能超过 1024")
        String resolution
) {
}
