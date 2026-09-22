package com.conduit.emergency.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BurstEventReassignRequest(
        @NotEmpty(message = "resourceIds 至少选择一个应急资源")
        List<Long> resourceIds,

        @NotBlank(message = "operator 不能为空")
        @Size(max = 64, message = "operator 长度不能超过 64")
        String operator,

        @NotBlank(message = "reason 不能为空")
        @Size(max = 1024, message = "reason 长度不能超过 1024")
        String reason
) {
}
