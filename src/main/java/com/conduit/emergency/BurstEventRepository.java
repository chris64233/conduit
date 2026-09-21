package com.conduit.emergency;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BurstEventRepository extends JpaRepository<BurstEvent, Long> {
}
