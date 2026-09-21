package com.conduit.emergency;

import com.conduit.emergency.dto.EmergencyResourceCreateRequest;
import com.conduit.emergency.dto.EmergencyResourceResponse;
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
@RequestMapping("/api/emergency-resources")
public class EmergencyResourceController {

    private final EmergencyResourceService service;

    public EmergencyResourceController(EmergencyResourceService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<EmergencyResourceResponse> create(
            @Valid @RequestBody EmergencyResourceCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping("/{id}")
    public EmergencyResourceResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }
}
