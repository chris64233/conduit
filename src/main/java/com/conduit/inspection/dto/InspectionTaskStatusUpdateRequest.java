package com.conduit.inspection.dto;

import com.conduit.inspection.InspectionTaskStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InspectionTaskStatusUpdateRequest(
        @NotNull(message = "status 不能为空")
        InspectionTaskStatus status,

        @Size(max = 1000, message = "result 长度不能超过 1000")
        String result
) {
}
