package com.conduit.inspection;

import com.conduit.inspection.dto.InspectionTaskCreateRequest;
import com.conduit.inspection.dto.InspectionTaskResponse;
import com.conduit.inspection.dto.InspectionTaskTransitionRequest;
import com.conduit.inspection.exception.InspectionTaskNotFoundException;
import com.conduit.inspection.exception.InvalidStateTransitionException;
import com.conduit.pipesegment.PipeSegment;
import com.conduit.pipesegment.PipeSegmentRepository;
import com.conduit.pipesegment.exception.InvalidRequestException;
import com.conduit.pipesegment.exception.PipeSegmentNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionTaskService {

    private static final Sort SORT_BY_SCHEDULED_AT =
            Sort.by(Sort.Direction.ASC, "scheduledAt").and(Sort.by(Sort.Direction.ASC, "id"));

    private final InspectionTaskRepository taskRepository;
    private final PipeSegmentRepository pipeSegmentRepository;

    public InspectionTaskService(InspectionTaskRepository taskRepository,
                                 PipeSegmentRepository pipeSegmentRepository) {
        this.taskRepository = taskRepository;
        this.pipeSegmentRepository = pipeSegmentRepository;
    }

    @Transactional
    public InspectionTaskResponse create(InspectionTaskCreateRequest request) {
        PipeSegment segment = pipeSegmentRepository.findById(request.pipeSegmentId())
                .orElseThrow(() -> new PipeSegmentNotFoundException(request.pipeSegmentId()));
        InspectionTask task = new InspectionTask(
                segment,
                request.title().trim(),
                request.inspector().trim(),
                request.scheduledAt(),
                InspectionStatus.PENDING
        );
        return InspectionTaskResponse.from(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public InspectionTaskResponse getById(Long id) {
        return taskRepository.findById(id)
                .map(InspectionTaskResponse::from)
                .orElseThrow(() -> new InspectionTaskNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<InspectionTaskResponse> list(String inspector, InspectionStatus status, int page, int size) {
        Specification<InspectionTask> spec = Specification
                .where(InspectionTaskSpecifications.hasInspector(inspector))
                .and(InspectionTaskSpecifications.hasStatus(status));
        Pageable pageable = PageRequest.of(page, size, SORT_BY_SCHEDULED_AT);
        return taskRepository.findAll(spec, pageable).map(InspectionTaskResponse::from);
    }

    @Transactional
    public InspectionTaskResponse transition(Long id, InspectionTaskTransitionRequest request) {
        InspectionTask task = taskRepository.findById(id)
                .orElseThrow(() -> new InspectionTaskNotFoundException(id));
        InspectionStatus current = task.getStatus();
        InspectionStatus target = request.targetStatus();

        if (current == InspectionStatus.PENDING && target == InspectionStatus.IN_PROGRESS) {
            task.start();
        } else if (current == InspectionStatus.IN_PROGRESS && target == InspectionStatus.COMPLETED) {
            String result = request.result();
            if (result == null || result.isBlank()) {
                throw new InvalidRequestException("任务完成时巡检结果不能为空");
            }
            task.complete(result.trim());
        } else {
            throw new InvalidStateTransitionException(current, target);
        }
        return InspectionTaskResponse.from(taskRepository.save(task));
    }
}
