package com.conduit.emergency;

import com.conduit.emergency.dto.EmergencyResourceCreateRequest;
import com.conduit.emergency.dto.EmergencyResourceResponse;
import com.conduit.emergency.exception.DuplicateResourceCodeException;
import com.conduit.emergency.exception.EmergencyResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class EmergencyResourceService {

    private final EmergencyResourceRepository repository;

    public EmergencyResourceService(EmergencyResourceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public EmergencyResourceResponse create(EmergencyResourceCreateRequest request) {
        String code = request.code().trim();
        EmergencyResource resource = new EmergencyResource(
                code,
                code.toUpperCase(Locale.ROOT),
                request.name().trim(),
                request.type()
        );
        if (repository.existsByCodeKey(resource.getCodeKey())) {
            throw new DuplicateResourceCodeException(code);
        }
        try {
            return EmergencyResourceResponse.from(repository.saveAndFlush(resource));
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceCodeException(code);
        }
    }

    @Transactional(readOnly = true)
    public EmergencyResourceResponse getById(Long id) {
        return repository.findById(id)
                .map(EmergencyResourceResponse::from)
                .orElseThrow(() -> new EmergencyResourceNotFoundException(id));
    }
}
