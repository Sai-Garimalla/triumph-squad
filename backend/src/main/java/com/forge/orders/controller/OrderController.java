package com.forge.orders.controller;

import com.forge.orders.model.Order;
import com.forge.orders.model.OrderAuditLog;
import com.forge.orders.model.OrderStatus;
import com.forge.orders.repository.DeadLetterQueueRepository;
import com.forge.orders.repository.OrderAuditLogRepository;
import com.forge.orders.repository.OrderRepository;
import com.forge.orders.service.OrderOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrderController {

    private final OrderOrchestrator orderOrchestrator;
    private final OrderRepository orderRepository;
    private final OrderAuditLogRepository auditLogRepository;
    private final DeadLetterQueueRepository dlqRepository;

    public OrderController(
            OrderOrchestrator orderOrchestrator,
            OrderRepository orderRepository,
            OrderAuditLogRepository auditLogRepository,
            DeadLetterQueueRepository dlqRepository) {
        this.orderOrchestrator = orderOrchestrator;
        this.orderRepository = orderRepository;
        this.auditLogRepository = auditLogRepository;
        this.dlqRepository = dlqRepository;
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody Order orderRequest, @RequestParam(defaultValue = "false") boolean forceFailure) {
        if (orderRequest.getOrderNumber() == null || orderRequest.getOrderNumber().isEmpty()) {
            orderRequest.setOrderNumber("ORD-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000));
        }
        Order placed = orderOrchestrator.placeOrder(orderRequest, forceFailure);
        return ResponseEntity.ok(placed);
    }

    @GetMapping
    public ResponseEntity<List<Order>> getRecentOrders() {
        return ResponseEntity.ok(orderRepository.findTop50ByOrderByCreatedAtDesc());
    }

    @GetMapping("/{id}/timeline")
    public ResponseEntity<List<OrderAuditLog>> getOrderTimeline(@PathVariable Long id) {
        return ResponseEntity.ok(auditLogRepository.findByOrderIdOrderByTimestampAsc(id));
    }

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        long total = orderRepository.count();
        long confirmed = orderRepository.countByStatus(OrderStatus.ORDER_CONFIRMED)
                + orderRepository.countByStatus(OrderStatus.PACKED)
                + orderRepository.countByStatus(OrderStatus.SHIPPED);
        long outOfStock = orderRepository.countByStatus(OrderStatus.OUT_OF_STOCK);
        long dlq = dlqRepository.countByStatus("UNRESOLVED");
        long failed = orderRepository.countByStatus(OrderStatus.PAYMENT_FAILED)
                + orderRepository.countByStatus(OrderStatus.PROCESSING_FAILED);
        long processing = total - (confirmed + outOfStock + dlq + failed);

        metrics.put("totalOrders", total);
        metrics.put("confirmedOrders", confirmed);
        metrics.put("outOfStockOrders", outOfStock);
        metrics.put("dlqOrders", dlq);
        metrics.put("failedOrders", failed);
        metrics.put("processingOrders", Math.max(0, processing));

        return ResponseEntity.ok(metrics);
    }

    /**
     * Flash Sale Simulator Endpoint:
     * Fires concurrent orders simultaneously using a CountDownLatch to hit the system in parallel.
     */
    @PostMapping("/simulate-burst")
    public ResponseEntity<Map<String, Object>> simulateBurst(
            @RequestParam(defaultValue = "50") int totalOrders,
            @RequestParam(defaultValue = "PROD-PHONE") String productSku,
            @RequestParam(defaultValue = "1") int quantityPerOrder) {

        ExecutorService clientSimulator = Executors.newFixedThreadPool(Math.min(totalOrders, 50));
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger submittedCount = new AtomicInteger(0);

        String[] cities = {"Chennai", "Bangalore", "Hyderabad", "Mumbai"};
        String[] customerNames = {"Kiran", "Akash", "Manoj", "Raju", "Priya", "Rahul", "Ananya", "Suresh", "Vikram", "Sneha"};

        for (int i = 0; i < totalOrders; i++) {
            final int index = i;
            clientSimulator.submit(() -> {
                try {
                    latch.await(); // Hold all threads until released simultaneously
                    String orderNum = "FLASH-" + System.currentTimeMillis() + "-" + index;
                    String customer = customerNames[index % customerNames.length] + " " + (index + 1);
                    String city = cities[index % cities.length];

                    Order burstOrder = new Order(
                            orderNum,
                            customer,
                            city,
                            productSku,
                            quantityPerOrder,
                            29999.0 * quantityPerOrder
                    );
                    orderOrchestrator.placeOrder(burstOrder, false);
                    submittedCount.incrementAndGet();
                } catch (Exception ignored) {
                }
            });
        }

        // Release all threads simultaneously for true concurrency spike
        latch.countDown();
        clientSimulator.shutdown();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "TRIGGERED");
        response.put("message", "Simulated " + totalOrders + " concurrent orders spike");
        response.put("ordersDispatched", totalOrders);
        return ResponseEntity.ok(response);
    }
}
