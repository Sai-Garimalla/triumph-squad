package com.forge.orders.service;

import com.forge.orders.model.Inventory;
import com.forge.orders.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final WebSocketNotifier webSocketNotifier;

    public InventoryService(InventoryRepository inventoryRepository, WebSocketNotifier webSocketNotifier) {
        this.inventoryRepository = inventoryRepository;
        this.webSocketNotifier = webSocketNotifier;
    }

    /**
     * Warehouse preference mapping based on customer location.
     */
    private List<String> getWarehousePriority(String destinationCity) {
        if (destinationCity == null) {
            return Arrays.asList("Chennai", "Bangalore", "Hyderabad", "Mumbai");
        }
        String city = destinationCity.trim().toLowerCase();
        switch (city) {
            case "chennai":
                return Arrays.asList("Chennai", "Bangalore", "Hyderabad", "Mumbai");
            case "bangalore":
            case "bengaluru":
                return Arrays.asList("Bangalore", "Chennai", "Hyderabad", "Mumbai");
            case "hyderabad":
                return Arrays.asList("Hyderabad", "Bangalore", "Chennai", "Mumbai");
            case "mumbai":
                return Arrays.asList("Mumbai", "Hyderabad", "Bangalore", "Chennai");
            default:
                return Arrays.asList("Bangalore", "Chennai", "Hyderabad", "Mumbai");
        }
    }

    /**
     * Safely reserves stock across candidate warehouses.
     * Uses row-level locking and atomic conditional decrement (WHERE available >= qty).
     * Prevents negative inventory and eliminates race conditions.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String attemptStockReservation(String productSku, int quantity, String destinationCity) {
        List<String> candidateWarehouses = getWarehousePriority(destinationCity);

        for (String warehouse : candidateWarehouses) {
            Optional<Inventory> optInventory = inventoryRepository.findWithLock(productSku, warehouse);
            if (optInventory.isPresent()) {
                Inventory inv = optInventory.get();
                if (inv.getAvailableQuantity() >= quantity) {
                    // Attempt atomic decrement
                    int updatedRows = inventoryRepository.reserveStockAtomic(inv.getId(), quantity);
                    if (updatedRows > 0) {
                        webSocketNotifier.notifyInventoryUpdate(getAllInventory());
                        return warehouse;
                    }
                }
            }
        }
        return null; // Out of stock across all warehouses
    }

    /**
     * Releases reserved stock back to available status if payment fails or order is cancelled.
     */
    @Transactional
    public void releaseReservation(String productSku, String warehouse, int quantity) {
        if (warehouse == null) return;
        Optional<Inventory> optInventory = inventoryRepository.findWithLock(productSku, warehouse);
        if (optInventory.isPresent()) {
            inventoryRepository.releaseReservationAtomic(optInventory.get().getId(), quantity);
            webSocketNotifier.notifyInventoryUpdate(getAllInventory());
        }
    }

    /**
     * Finalizes inventory sale from reserved to sold upon payment confirmation.
     */
    @Transactional
    public void finalizeSale(String productSku, String warehouse, int quantity) {
        if (warehouse == null) return;
        Optional<Inventory> optInventory = inventoryRepository.findWithLock(productSku, warehouse);
        if (optInventory.isPresent()) {
            inventoryRepository.confirmSaleAtomic(optInventory.get().getId(), quantity);
            webSocketNotifier.notifyInventoryUpdate(getAllInventory());
        }
    }

    public List<Inventory> getAllInventory() {
        return inventoryRepository.findAll();
    }

    public List<Inventory> getInventoryBySku(String sku) {
        return inventoryRepository.findByProductSku(sku);
    }
}
