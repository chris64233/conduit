package com.conduit.emergency.dto;

import com.conduit.emergency.EmergencyResource;
import com.conduit.emergency.ResourceStatus;
import com.conduit.emergency.ResourceType;

public record EmergencyResourceResponse(
        Long id,
        String code,
        String name,
        ResourceType type,
        ResourceStatus status
) {

    public static EmergencyResourceResponse from(EmergencyResource resource) {
        return new EmergencyResourceResponse(
                resource.getId(),
                resource.getCode(),
                resource.getName(),
                resource.getType(),
                resource.getStatus()
        );
    }
}
