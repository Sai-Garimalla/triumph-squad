package com.forge.orders.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class WebSocketNotifier {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketNotifier(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void notifyOrderUpdate(Object orderData) {
        try {
            messagingTemplate.convertAndSend("/topic/orders", orderData);
        } catch (Exception ignored) {
        }
    }

    public void notifyInventoryUpdate(Object inventoryData) {
        try {
            messagingTemplate.convertAndSend("/topic/inventory", inventoryData);
        } catch (Exception ignored) {
        }
    }

    public void notifyMetricsUpdate(Object metricsData) {
        try {
            messagingTemplate.convertAndSend("/topic/metrics", metricsData);
        } catch (Exception ignored) {
        }
    }

    public void notifyEvent(String eventType, String message) {
        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("type", eventType);
            payload.put("message", message);
            messagingTemplate.convertAndSend("/topic/events", payload);
        } catch (Exception ignored) {
        }
    }
}
