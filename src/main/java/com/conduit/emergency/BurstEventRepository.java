package com.conduit.emergency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;

public interface BurstEventRepository extends JpaRepository<BurstEvent, Long> {

    @Query("select event from BurstEvent event where event.id in :ids order by event.id asc")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<BurstEvent> findAllByIdInIdOrderForUpdate(@Param("ids") List<Long> ids);
}
