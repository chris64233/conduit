package com.conduit.emergency;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmergencyResourceRepository extends JpaRepository<EmergencyResource, Long> {

    boolean existsByCodeKey(String codeKey);
}
