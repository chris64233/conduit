package com.conduit.hazard;

import com.conduit.inspection.InspectionTask;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "hazards")
public class Hazard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_task_id", nullable = false)
    private InspectionTask inspectionTask;

    @Column(nullable = false, length = 1024)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private HazardLevel level;

    @Column(nullable = false, length = 64)
    private String reporter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private HazardStatus status;

    @Column(length = 64)
    private String assignee;

    @Column(length = 1024)
    private String resolution;

    protected Hazard() {
    }

    public Hazard(InspectionTask inspectionTask, String description, HazardLevel level, String reporter) {
        this.inspectionTask = inspectionTask;
        this.description = description;
        this.level = level;
        this.reporter = reporter;
        this.status = HazardStatus.OPEN;
    }

    public Long getId() {
        return id;
    }

    public InspectionTask getInspectionTask() {
        return inspectionTask;
    }

    public String getDescription() {
        return description;
    }

    public HazardLevel getLevel() {
        return level;
    }

    public String getReporter() {
        return reporter;
    }

    public HazardStatus getStatus() {
        return status;
    }

    public String getAssignee() {
        return assignee;
    }

    public String getResolution() {
        return resolution;
    }

    public void startRectification(String assignee) {
        this.status = HazardStatus.RECTIFYING;
        this.assignee = assignee;
    }

    public void resolve(String resolution) {
        this.status = HazardStatus.RESOLVED;
        this.resolution = resolution;
    }
}
