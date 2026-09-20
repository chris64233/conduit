package com.conduit.inspection;

import com.conduit.inspection.dto.InspectionTaskCreateRequest;
import com.conduit.inspection.dto.InspectionTaskResponse;
import com.conduit.inspection.dto.InspectionTaskStatusUpdateRequest;
import com.conduit.pipesegment.exception.InvalidRequestException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inspection-tasks")
public class InspectionTaskController {

    private static final int MAX_PAGE_SIZE = 100;

    private final InspectionTaskService service;

    public InspectionTaskController(InspectionTaskService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<InspectionTaskResponse> create(@Valid @RequestBody InspectionTaskCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping("/{id}")
    public InspectionTaskResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @GetMapping
    public Page<InspectionTaskResponse> list(
            @RequestParam(required = false) String inspector,
            @RequestParam(required = false) InspectionTaskStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0) {
            throw new InvalidRequestException("page 不能小于 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("size 必须在 1 至 " + MAX_PAGE_SIZE + " 之间");
        }
        return service.list(inspector, status, page, size);
    }

    @PatchMapping("/{id}/status")
    public InspectionTaskResponse updateStatus(@PathVariable Long id,
                                               @Valid @RequestBody InspectionTaskStatusUpdateRequest request) {
        return service.updateStatus(id, request);
    }
}
