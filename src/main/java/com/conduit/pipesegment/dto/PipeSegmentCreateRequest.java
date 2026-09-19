package com.conduit.pipesegment.dto;

import com.conduit.pipesegment.PipeSegmentStatus;
import com.conduit.pipesegment.UtilityType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PipeSegmentCreateRequest(
        @NotBlank(message = "code 不能为空")
        @Size(max = 64, message = "code 长度不能超过 64")
        String code,

        @NotBlank(message = "name 不能为空")
        @Size(max = 128, message = "name 长度不能超过 128")
        String name,

        @NotNull(message = "utilityType 不能为空")
        UtilityType utilityType,

        @NotBlank(message = "material 不能为空")
        @Size(max = 64, message = "material 长度不能超过 64")
        String material,

        @NotNull(message = "diameterMm 不能为空")
        @Positive(message = "diameterMm 必须大于 0")
        Integer diameterMm,

        @NotNull(message = "startLongitude 不能为空")
        @DecimalMin(value = "-180.0", message = "经度范围为 -180 至 180")
        @DecimalMax(value = "180.0", message = "经度范围为 -180 至 180")
        Double startLongitude,

        @NotNull(message = "startLatitude 不能为空")
        @DecimalMin(value = "-90.0", message = "纬度范围为 -90 至 90")
        @DecimalMax(value = "90.0", message = "纬度范围为 -90 至 90")
        Double startLatitude,

        @NotNull(message = "endLongitude 不能为空")
        @DecimalMin(value = "-180.0", message = "经度范围为 -180 至 180")
        @DecimalMax(value = "180.0", message = "经度范围为 -180 至 180")
        Double endLongitude,

        @NotNull(message = "endLatitude 不能为空")
        @DecimalMin(value = "-90.0", message = "纬度范围为 -90 至 90")
        @DecimalMax(value = "90.0", message = "纬度范围为 -90 至 90")
        Double endLatitude,

        @NotNull(message = "status 不能为空")
        PipeSegmentStatus status
) {
}
