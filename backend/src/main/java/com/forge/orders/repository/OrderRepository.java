package com.forge.orders.repository;

import com.forge.orders.model.Order;
import com.forge.orders.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderNumber(String orderNumber);
    List<Order> findTop50ByOrderByCreatedAtDesc();
    long countByStatus(OrderStatus status);
}
