package com.conduit.emergency.dto;

import com.conduit.emergency.BurstEventLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BurstEventCreateRequest(
        @NotNull(message = "pipeSegmentId 不能为空")
        Long pipeSegmentId,

        @NotBlank(message = "description 不能为空")
        @Size(max = 1024, message = "description 长度不能超过 1024")
        String description,

        @NotNull(message = "level 不能为空")
        BurstEventLevel level,

        @NotBlank(message = "reporter 不能为空")
        @Size(max = 64, message = "reporter 长度不能超过 64")
        String reporter
) {
}
