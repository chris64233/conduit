package com.conduit.emergency;

import com.conduit.pipesegment.PipeSegment;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private BurstEventLevel level;

    @Column(nullable = false, length = 64)
    private String reporter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BurstEventStatus status;

    @Column(length = 1024)
    private String resolution;

    @ManyToMany
    @JoinTable(name = "burst_event_resources",
            joinColumns = @JoinColumn(name = "burst_event_id"),
            inverseJoinColumns = @JoinColumn(name = "resource_id"))
    @OrderBy("id")
    private List<EmergencyResource> resources = new ArrayList<>();

    @OneToMany(mappedBy = "burstEvent", cascade = CascadeType.ALL)
    @OrderBy("operatedAt ASC, id ASC")
    private List<ReassignmentRecord> reassignments = new ArrayList<>();

    protected BurstEvent() {
    }

    public BurstEvent(PipeSegment pipeSegment, String description, BurstEventLevel level, String reporter) {
        this.pipeSegment = pipeSegment;
        this.description = description;
        this.level = level;
        this.reporter = reporter;
        this.status = BurstEventStatus.REPORTED;
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

    public BurstEventLevel getLevel() {
        return level;
    }

    public String getReporter() {
        return reporter;
    }

    public BurstEventStatus getStatus() {
        return status;
    }

    public String getResolution() {
        return resolution;
    }

    public List<EmergencyResource> getResources() {
        return resources;
    }

    public List<ReassignmentRecord> getReassignments() {
        return reassignments;
    }

    public void dispatch(List<EmergencyResource> dispatchedResources) {
        this.status = BurstEventStatus.DISPATCHED;
        for (EmergencyResource resource : dispatchedResources) {
            resource.markBusy();
            this.resources.add(resource);
        }
    }

    public void reassign(List<EmergencyResource> targetResources, String operator, String reason) {
        List<Long> previousResourceIds = this.resources.stream()
                .map(EmergencyResource::getId)
                .toList();
        Set<Long> targetIds = new HashSet<>();
        for (EmergencyResource resource : targetResources) {
            targetIds.add(resource.getId());
        }
        for (EmergencyResource resource : this.resources) {
            if (!targetIds.contains(resource.getId())) {
                resource.markAvailable();
            }
        }
        Set<Long> currentIds = new HashSet<>(previousResourceIds);
        for (EmergencyResource resource : targetResources) {
            if (!currentIds.contains(resource.getId())) {
                resource.markBusy();
            }
        }
        this.resources.clear();
        this.resources.addAll(targetResources);
        List<Long> newResourceIds = targetResources.stream()
                .map(EmergencyResource::getId)
                .toList();
        this.reassignments.add(new ReassignmentRecord(this, operator, reason,
                previousResourceIds, newResourceIds));
    }

    public void resolve(String resolution) {
        this.status = BurstEventStatus.RESOLVED;
        this.resolution = resolution;
        for (EmergencyResource resource : this.resources) {
            resource.markAvailable();
        }
    }

    public boolean containsResource(EmergencyResource resource) {
        for (EmergencyResource current : this.resources) {
            if (current.getId().equals(resource.getId())) {
                return true;
            }
        }
        return false;
    }

    public void transferResourcesOut(List<EmergencyResource> transferredResources) {
        this.resources.removeAll(transferredResources);
    }

    public void transferResourcesIn(List<EmergencyResource> transferredResources) {
        for (EmergencyResource resource : transferredResources) {
            resource.markBusy();
            this.resources.add(resource);
        }
    }
}
