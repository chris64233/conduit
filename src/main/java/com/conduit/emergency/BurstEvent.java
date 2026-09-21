package com.conduit.emergency;

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

@Entity
@Table(name = "burst_events")
public class BurstEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pipe_segment_id", nullable = false)
    private PipeSegment pipeSegment;

    @Column(nullable = false, length = 1024)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BurstLevel level;

    @Column(nullable = false, length = 64)
    private String reporter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BurstStatus status;

    @Column(length = 1024)
    private String resolution;

    protected BurstEvent() {
    }

    public BurstEvent(PipeSegment pipeSegment, String description, BurstLevel level, String reporter) {
        this.pipeSegment = pipeSegment;
        this.description = description;
        this.level = level;
        this.reporter = reporter;
        this.status = BurstStatus.REPORTED;
    }

    public Long getId() {
        return id;
    }

    public PipeSegment getPipeSegment() {
        return pipeSegment;
    }

    public String getDescription() {
        return description;
    }

    public BurstLevel getLevel() {
        return level;
    }

    public String getReporter() {
        return reporter;
    }

    public BurstStatus getStatus() {
        return status;
    }

    public String getResolution() {
        return resolution;
    }

    public void dispatch() {
        this.status = BurstStatus.DISPATCHED;
    }

    public void resolve(String resolution) {
        this.status = BurstStatus.RESOLVED;
        this.resolution = resolution;
    }
}
