package com.conduit.inspection.dto;

import com.conduit.inspection.InspectionStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InspectionTaskTransitionRequest(
        @NotNull(message = "targetStatus 不能为空")
        InspectionStatus targetStatus,

        @Size(max = 1024, message = "result 长度不能超过 1024")
        String result
) {
}
