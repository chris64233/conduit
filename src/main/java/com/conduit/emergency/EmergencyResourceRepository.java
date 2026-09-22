package com.conduit.emergency;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmergencyResourceRepository extends JpaRepository<EmergencyResource, Long> {

    boolean existsByCodeKey(String codeKey);

    Optional<EmergencyResource> findByCodeKey(String codeKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT resource FROM EmergencyResource resource WHERE resource.codeKey IN :codeKeys "
            + "ORDER BY resource.id ASC")
    List<EmergencyResource> findByCodeKeyInForUpdate(@Param("codeKeys") Collection<String> codeKeys);
}
