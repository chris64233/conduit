package com.conduit.emergency.dto;

import com.conduit.emergency.BurstEventStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BurstEventTransitionRequest(
        @NotNull(message = "targetStatus 不能为空")
        BurstEventStatus targetStatus,

        List<Long> resourceIds,

        @Size(max = 1024, message = "resolution 长度不能超过 1024")
        String resolution
) {
}
