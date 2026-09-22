package com.forge.orders.service;

import com.forge.orders.model.*;
import com.forge.orders.repository.DeadLetterQueueRepository;
import com.forge.orders.repository.OrderAuditLogRepository;
import com.forge.orders.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;

@Service
public class OrderOrchestrator {

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final DeadLetterQueueRepository dlqRepository;
    private final OrderAuditLogRepository auditLogRepository;
    private final WebSocketNotifier webSocketNotifier;
    private final Executor executor;

    @Value("${app.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${app.retry.delay-ms:250}")
    private long retryDelayMs;

    public OrderOrchestrator(
            OrderRepository orderRepository,
            InventoryService inventoryService,
            PaymentService paymentService,
            DeadLetterQueueRepository dlqRepository,
            OrderAuditLogRepository auditLogRepository,
            WebSocketNotifier webSocketNotifier,
            @Qualifier("orderProcessingExecutor") Executor executor) {
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.dlqRepository = dlqRepository;
        this.auditLogRepository = auditLogRepository;
        this.webSocketNotifier = webSocketNotifier;
        this.executor = executor;
    }

    /**
     * Entry point: creates the order record and submits processing to the thread pool.
     */
    public Order placeOrder(Order order, boolean forcePaymentFailure) {
        order.setStatus(OrderStatus.CREATED);
        Order savedOrder = orderRepository.save(order);
        recordAudit(savedOrder.getId(), savedOrder.getOrderNumber(), "ORDER_CREATED", "Order placed by " + savedOrder.getCustomerName());
        webSocketNotifier.notifyOrderUpdate(savedOrder);

        // Submit to thread pool for concurrent processing
        executor.execute(() -> processOrderLifecycle(savedOrder.getId(), forcePaymentFailure));

        return savedOrder;
    }

    /**
     * Order processing lifecycle executed concurrently in worker threads.
     */
    private void processOrderLifecycle(Long orderId, boolean forcePaymentFailure) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) return;

        try {
            // 1. Inventory Check & Safe Reservation
            String allocatedWarehouse = inventoryService.attemptStockReservation(
                    order.getProductSku(),
                    order.getQuantity(),
                    order.getDestinationCity()
            );

            if (allocatedWarehouse == null) {
                // Insufficient stock across all warehouses
                order.setStatus(OrderStatus.OUT_OF_STOCK);
                order.setFailureReason("Insufficient stock across all fulfillment warehouses");
                orderRepository.save(order);
                recordAudit(order.getId(), order.getOrderNumber(), "OUT_OF_STOCK", "Product out of stock across warehouses");
                webSocketNotifier.notifyOrderUpdate(order);
                return;
            }

            // Inventory reserved successfully
            order.setAllocatedWarehouse(allocatedWarehouse);
            order.setStatus(OrderStatus.INVENTORY_RESERVED);
            orderRepository.save(order);
            recordAudit(order.getId(), order.getOrderNumber(), "INVENTORY_RESERVED", "Reserved at " + allocatedWarehouse + " warehouse");
            webSocketNotifier.notifyOrderUpdate(order);

            // 2. Payment Processing with Bounded Retry Mechanism
            order.setStatus(OrderStatus.PAYMENT_PROCESSING);
            orderRepository.save(order);
            recordAudit(order.getId(), order.getOrderNumber(), "PAYMENT_PROCESSING", "Initiating payment gateway interaction");
            webSocketNotifier.notifyOrderUpdate(order);

            int attempts = 0;
            PaymentService.PaymentResult paymentResult = PaymentService.PaymentResult.TRANSIENT_TIMEOUT;

            while (attempts < maxRetryAttempts) {
                attempts++;
                paymentResult = paymentService.processPayment(
                        order.getOrderNumber(),
                        order.getTotalAmount(),
                        attempts,
                        forcePaymentFailure
                );

                if (paymentResult == PaymentService.PaymentResult.SUCCESS) {
                    break;
                } else if (paymentResult == PaymentService.PaymentResult.TRANSIENT_TIMEOUT) {
                    order.setRetryCount(attempts);
                    orderRepository.save(order);
                    recordAudit(order.getId(), order.getOrderNumber(), "PAYMENT_RETRY", "Attempt " + attempts + " timed out, retrying...");
                    webSocketNotifier.notifyOrderUpdate(order);

                    if (attempts < maxRetryAttempts) {
                        try {
                            Thread.sleep(retryDelayMs);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                } else {
                    // Outright rejection
                    break;
                }
            }

            // 3. Evaluate Outcome
            if (paymentResult == PaymentService.PaymentResult.SUCCESS) {
                // Confirm inventory sale
                inventoryService.finalizeSale(order.getProductSku(), order.getAllocatedWarehouse(), order.getQuantity());

                order.setStatus(OrderStatus.PAYMENT_CONFIRMED);
                orderRepository.save(order);
                recordAudit(order.getId(), order.getOrderNumber(), "PAYMENT_CONFIRMED", "Payment successfully processed");
                webSocketNotifier.notifyOrderUpdate(order);

                // Progress through warehouse fulfillment steps
                progressFulfillment(order);

            } else if (paymentResult == PaymentService.PaymentResult.TRANSIENT_TIMEOUT) {
                // Max retries exhausted -> Route to Dead Letter Queue (DLQ)
                inventoryService.releaseReservation(order.getProductSku(), order.getAllocatedWarehouse(), order.getQuantity());

                order.setStatus(OrderStatus.DLQ);
                order.setFailureReason("Exceeded maximum retry attempts (" + maxRetryAttempts + ") due to gateway timeout");
                orderRepository.save(order);

                DeadLetterQueueItem dlqItem = new DeadLetterQueueItem(
                        order.getId(),
                        order.getOrderNumber(),
                        order.getProductSku(),
                        order.getQuantity(),
                        order.getFailureReason(),
                        order.getRetryCount()
                );
                dlqRepository.save(dlqItem);

                recordAudit(order.getId(), order.getOrderNumber(), "DLQ_ROUTED", "Order sent to DLQ: " + order.getFailureReason());
                webSocketNotifier.notifyOrderUpdate(order);

            } else {
                // Outright payment failure
                inventoryService.releaseReservation(order.getProductSku(), order.getAllocatedWarehouse(), order.getQuantity());

                order.setStatus(OrderStatus.PAYMENT_FAILED);
                order.setFailureReason("Card authorization declined by issuer");
                orderRepository.save(order);
                recordAudit(order.getId(), order.getOrderNumber(), "PAYMENT_FAILED", "Payment rejected");
                webSocketNotifier.notifyOrderUpdate(order);
            }

        } catch (Exception e) {
            // General exception recovery
            order.setStatus(OrderStatus.PROCESSING_FAILED);
            order.setFailureReason(e.getMessage());
            orderRepository.save(order);
            recordAudit(order.getId(), order.getOrderNumber(), "PROCESSING_FAILED", e.getMessage());
            webSocketNotifier.notifyOrderUpdate(order);
        }
    }

    private void progressFulfillment(Order order) {
        try {
            order.setStatus(OrderStatus.ORDER_CONFIRMED);
            orderRepository.save(order);
            recordAudit(order.getId(), order.getOrderNumber(), "ORDER_CONFIRMED", "Order confirmed for dispatch");
            webSocketNotifier.notifyOrderUpdate(order);

            Thread.sleep(150);
            order.setStatus(OrderStatus.PACKED);
            orderRepository.save(order);
            recordAudit(order.getId(), order.getOrderNumber(), "PACKED", "Packed at " + order.getAllocatedWarehouse());
            webSocketNotifier.notifyOrderUpdate(order);

            Thread.sleep(150);
            order.setStatus(OrderStatus.SHIPPED);
            orderRepository.save(order);
            recordAudit(order.getId(), order.getOrderNumber(), "SHIPPED", "Handed over to logistics carrier");
            webSocketNotifier.notifyOrderUpdate(order);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void recordAudit(Long orderId, String orderNumber, String stage, String details) {
        try {
            OrderAuditLog log = new OrderAuditLog(orderId, orderNumber, stage, details);
            auditLogRepository.save(log);
        } catch (Exception ignored) {
        }
    }

    /**
     * Reprocesses an order from the Dead Letter Queue.
     */
    public boolean reprocessDlqOrder(Long dlqId) {
        DeadLetterQueueItem dlqItem = dlqRepository.findById(dlqId).orElse(null);
        if (dlqItem == null || "REPROCESSED".equalsIgnoreCase(dlqItem.getStatus())) {
            return false;
        }

        dlqItem.setStatus("REPROCESSED");
        dlqRepository.save(dlqItem);

        Order order = orderRepository.findById(dlqItem.getOrderId()).orElse(null);
        if (order != null) {
            order.setStatus(OrderStatus.CREATED);
            order.setRetryCount(0);
            order.setFailureReason(null);
            orderRepository.save(order);
            recordAudit(order.getId(), order.getOrderNumber(), "DLQ_REPROCESSED", "Manually re-queued by administrator");
            webSocketNotifier.notifyOrderUpdate(order);

            executor.execute(() -> processOrderLifecycle(order.getId(), false));
            return true;
        }
        return false;
    }
}
