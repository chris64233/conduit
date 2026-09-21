package com.conduit.emergency;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmergencyResourceRepository extends JpaRepository<EmergencyResource, Long> {

    boolean existsByCodeKey(String codeKey);

    List<EmergencyResource> findByAssignedEventId(Long eventId);
}
