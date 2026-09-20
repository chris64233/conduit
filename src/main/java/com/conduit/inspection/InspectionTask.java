package com.conduit.inspection;

import com.conduit.pipesegment.PipeSegment;
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

import java.time.OffsetDateTime;

@Entity
@Table(name = "inspection_tasks")
public class InspectionTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pipe_segment_id", nullable = false)
    private PipeSegment pipeSegment;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(nullable = false, length = 64)
    private String inspector;

    @Column(nullable = false)
    private OffsetDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InspectionStatus status;

    @Column(length = 1024)
    private String result;

    protected InspectionTask() {
    }

    public InspectionTask(PipeSegment pipeSegment, String title, String inspector,
                          OffsetDateTime scheduledAt, InspectionStatus status) {
        this.pipeSegment = pipeSegment;
        this.title = title;
        this.inspector = inspector;
        this.scheduledAt = scheduledAt;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public PipeSegment getPipeSegment() {
        return pipeSegment;
    }

    public String getTitle() {
        return title;
    }

    public String getInspector() {
        return inspector;
    }

    public OffsetDateTime getScheduledAt() {
        return scheduledAt;
    }

    public InspectionStatus getStatus() {
        return status;
    }

    public String getResult() {
        return result;
    }

    public void start() {
        this.status = InspectionStatus.IN_PROGRESS;
    }

    public void complete(String result) {
        this.status = InspectionStatus.COMPLETED;
        this.result = result;
    }
}
