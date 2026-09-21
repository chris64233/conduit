package com.conduit.hazard;

import com.conduit.hazard.dto.HazardCreateRequest;
import com.conduit.hazard.dto.HazardResponse;
import com.conduit.hazard.dto.HazardTransitionRequest;
import com.conduit.pipesegment.exception.InvalidRequestException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hazards")
public class HazardController {

    private static final int MAX_PAGE_SIZE = 100;

    private final HazardService service;

    public HazardController(HazardService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<HazardResponse> report(@Valid @RequestBody HazardCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.report(request));
    }

    @GetMapping("/{id}")
    public HazardResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @GetMapping
    public Page<HazardResponse> list(
            @RequestParam(required = false) HazardLevel level,
            @RequestParam(required = false) HazardStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0) {
            throw new InvalidRequestException("page 不能小于 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("size 必须在 1 至 " + MAX_PAGE_SIZE + " 之间");
        }
        return service.list(level, status, page, size);
    }

    @PostMapping("/{id}/transitions")
    public HazardResponse transition(@PathVariable Long id,
                                     @Valid @RequestBody HazardTransitionRequest request) {
        return service.transition(id, request);
    }
}
