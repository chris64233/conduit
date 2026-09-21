package com.conduit.emergency.dto;

import com.conduit.emergency.ResourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EmergencyResourceCreateRequest(
        @NotBlank(message = "code 不能为空")
        @Size(max = 64, message = "code 长度不能超过 64")
        String code,

        @NotBlank(message = "name 不能为空")
        @Size(max = 128, message = "name 长度不能超过 128")
        String name,

        @NotNull(message = "type 不能为空")
        ResourceType type
) {
}
