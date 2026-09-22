package com.forge.orders.repository;

import com.forge.orders.model.DeadLetterQueueItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeadLetterQueueRepository extends JpaRepository<DeadLetterQueueItem, Long> {
    List<DeadLetterQueueItem> findTop50ByOrderByFailedAtDesc();
    long countByStatus(String status);
}
