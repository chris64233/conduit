package com.conduit.emergency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "emergency_resources", uniqueConstraints = {
        @UniqueConstraint(name = "uk_emergency_resources_code_key", columnNames = "code_key")
})
public class EmergencyResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(name = "code_key", nullable = false, length = 64)
    private String codeKey;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ResourceType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ResourceStatus status;

    protected EmergencyResource() {
    }

    public EmergencyResource(String code, String codeKey, String name, ResourceType type) {
        this.code = code;
        this.codeKey = codeKey;
        this.name = name;
        this.type = type;
        this.status = ResourceStatus.AVAILABLE;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getCodeKey() {
        return codeKey;
    }

    public String getName() {
        return name;
    }

    public ResourceType getType() {
        return type;
    }

    public ResourceStatus getStatus() {
        return status;
    }

    public void markBusy() {
        this.status = ResourceStatus.BUSY;
    }

    public void markAvailable() {
        this.status = ResourceStatus.AVAILABLE;
    }
}
