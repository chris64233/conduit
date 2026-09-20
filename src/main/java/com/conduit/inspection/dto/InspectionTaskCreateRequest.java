package com.conduit.inspection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record InspectionTaskCreateRequest(
        @NotNull(message = "pipeSegmentId 不能为空")
        Long pipeSegmentId,

        @NotBlank(message = "title 不能为空")
        @Size(max = 128, message = "title 长度不能超过 128")
        String title,

        @NotBlank(message = "inspector 不能为空")
        @Size(max = 64, message = "inspector 长度不能超过 64")
        String inspector,

        @NotNull(message = "scheduledAt 不能为空")
        OffsetDateTime scheduledAt
) {
}
