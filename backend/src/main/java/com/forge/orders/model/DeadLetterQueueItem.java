package com.forge.orders.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "dead_letter_queue")
public class DeadLetterQueueItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;
    private String orderNumber;
    private String productSku;
    private int quantity;

    @Column(length = 1000)
    private String failureReason;

    private int retryCount;
    private LocalDateTime failedAt;
    private String status; // UNRESOLVED, REPROCESSED

    public DeadLetterQueueItem() {
    }

    public DeadLetterQueueItem(Long orderId, String orderNumber, String productSku, int quantity, String failureReason, int retryCount) {
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.productSku = productSku;
        this.quantity = quantity;
        this.failureReason = failureReason;
        this.retryCount = retryCount;
        this.failedAt = LocalDateTime.now();
        this.status = "UNRESOLVED";
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getProductSku() {
        return productSku;
    }

    public void setProductSku(String productSku) {
        this.productSku = productSku;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public LocalDateTime getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(LocalDateTime failedAt) {
        this.failedAt = failedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
