package com.conduit.emergency.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ResourceTransferRequestPayload(
        @NotNull(message = "sourceEventId 不能为空")
        Long sourceEventId,

        @NotNull(message = "targetEventId 不能为空")
        Long targetEventId,

        @NotEmpty(message = "resourceCodes 不能为空")
        List<
                @NotBlank(message = "resourceCodes 中的编号不能为空")
                @Size(max = 64, message = "资源编号长度不能超过 64")
                String> resourceCodes,

        @NotBlank(message = "requestNo 不能为空")
        @Size(max = 64, message = "requestNo 长度不能超过 64")
        String requestNo,

        @NotBlank(message = "operator 不能为空")
        @Size(max = 64, message = "operator 长度不能超过 64")
        String operator,

        @NotBlank(message = "reason 不能为空")
        @Size(max = 1024, message = "reason 长度不能超过 1024")
        String reason
) {
}
