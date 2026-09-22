package com.conduit.emergency;

import com.conduit.emergency.dto.BurstEventCreateRequest;
import com.conduit.emergency.dto.BurstEventResponse;
import com.conduit.emergency.dto.BurstEventTransitionRequest;
import com.conduit.emergency.dto.ReassignmentRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/burst-events")
public class BurstEventController {

    private final BurstEventService service;

    public BurstEventController(BurstEventService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<BurstEventResponse> create(@Valid @RequestBody BurstEventCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping("/{id}")
    public BurstEventResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping("/{id}/transitions")
    public BurstEventResponse transition(@PathVariable Long id,
                                         @Valid @RequestBody BurstEventTransitionRequest request) {
        return service.transition(id, request);
    }

    @PostMapping("/{id}/reassignment")
    public BurstEventResponse reassign(@PathVariable Long id,
                                       @Valid @RequestBody ReassignmentRequest request) {
        return service.reassign(id, request);
    }
}
