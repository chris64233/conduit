package com.conduit.emergency;

import com.conduit.emergency.dto.ResourceTransferRecordResponse;
import com.conduit.emergency.dto.ResourceTransferRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resource-transfers")
public class ResourceTransferController {

    private final ResourceTransferService service;

    public ResourceTransferController(ResourceTransferService service) {
        this.service = service;
    }

    @PostMapping
    public ResourceTransferRecordResponse transfer(@Valid @RequestBody ResourceTransferRequest request) {
        return service.transfer(request);
    }
}
