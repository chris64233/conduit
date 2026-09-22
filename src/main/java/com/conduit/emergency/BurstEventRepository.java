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

public interface BurstEventRepository extends JpaRepository<BurstEvent, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT event FROM BurstEvent event WHERE event.id IN :ids ORDER BY event.id ASC")
    List<BurstEvent> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);
}
