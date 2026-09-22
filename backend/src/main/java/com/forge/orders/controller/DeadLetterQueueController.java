package com.forge.orders.controller;

import com.forge.orders.model.DeadLetterQueueItem;
import com.forge.orders.repository.DeadLetterQueueRepository;
import com.forge.orders.service.OrderOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dlq")
@CrossOrigin(origins = "*")
public class DeadLetterQueueController {

    private final DeadLetterQueueRepository dlqRepository;
    private final OrderOrchestrator orderOrchestrator;

    public DeadLetterQueueController(DeadLetterQueueRepository dlqRepository, OrderOrchestrator orderOrchestrator) {
        this.dlqRepository = dlqRepository;
        this.orderOrchestrator = orderOrchestrator;
    }

    @GetMapping
    public ResponseEntity<List<DeadLetterQueueItem>> getDlqItems() {
        return ResponseEntity.ok(dlqRepository.findTop50ByOrderByFailedAtDesc());
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<Map<String, Object>> retryDlqItem(@PathVariable Long id) {
        boolean success = orderOrchestrator.reprocessDlqOrder(id);
        if (success) {
            return ResponseEntity.ok(Map.of("status", "REQUEUED", "message", "Order successfully re-queued for processing"));
        } else {
            return ResponseEntity.badRequest().body(Map.of("status", "FAILED", "message", "Unable to re-queue DLQ order"));
        }
    }
}
