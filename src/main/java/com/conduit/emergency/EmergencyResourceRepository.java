package com.conduit.emergency;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmergencyResourceRepository extends JpaRepository<EmergencyResource, Long> {

    boolean existsByCodeKey(String codeKey);

    Optional<EmergencyResource> findByCodeKey(String codeKey);
}
