package com.conduit.pipesegment;

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
@Table(
        name = "pipe_segments",
        uniqueConstraints = @UniqueConstraint(name = "uk_pipe_segments_code_normalized", columnNames = "code_normalized")
)
public class PipeSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(name = "code_normalized", nullable = false, length = 64)
    private String codeNormalized;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UtilityType utilityType;

    @Column(nullable = false, length = 64)
    private String material;

    @Column(nullable = false)
    private Integer diameterMm;

    @Column(nullable = false)
    private Double startLongitude;

    @Column(nullable = false)
    private Double startLatitude;

    @Column(nullable = false)
    private Double endLongitude;

    @Column(nullable = false)
    private Double endLatitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PipeSegmentStatus status;

    protected PipeSegment() {
    }

    public PipeSegment(String code, String codeNormalized, String name, UtilityType utilityType,
                       String material, Integer diameterMm,
                       Double startLongitude, Double startLatitude,
                       Double endLongitude, Double endLatitude,
                       PipeSegmentStatus status) {
        this.code = code;
        this.codeNormalized = codeNormalized;
        this.name = name;
        this.utilityType = utilityType;
        this.material = material;
        this.diameterMm = diameterMm;
        this.startLongitude = startLongitude;
        this.startLatitude = startLatitude;
        this.endLongitude = endLongitude;
        this.endLatitude = endLatitude;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getCodeNormalized() {
        return codeNormalized;
    }

    public String getName() {
        return name;
    }

    public UtilityType getUtilityType() {
        return utilityType;
    }

    public String getMaterial() {
        return material;
    }

    public Integer getDiameterMm() {
        return diameterMm;
    }

    public Double getStartLongitude() {
        return startLongitude;
    }

    public Double getStartLatitude() {
        return startLatitude;
    }

    public Double getEndLongitude() {
        return endLongitude;
    }

    public Double getEndLatitude() {
        return endLatitude;
    }

    public PipeSegmentStatus getStatus() {
        return status;
    }
}
