package com.forge.orders.controller;

import com.forge.orders.model.Inventory;
import com.forge.orders.repository.InventoryRepository;
import com.forge.orders.service.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@CrossOrigin(origins = "*")
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryRepository inventoryRepository;

    public InventoryController(InventoryService inventoryService, InventoryRepository inventoryRepository) {
        this.inventoryService = inventoryService;
        this.inventoryRepository = inventoryRepository;
    }

    @GetMapping
    public ResponseEntity<List<Inventory>> getInventory() {
        return ResponseEntity.ok(inventoryService.getAllInventory());
    }

    /**
     * Resets inventory to clean initial state for demonstration purposes.
     */
    @PostMapping("/reset")
    public ResponseEntity<List<Inventory>> resetInventory(@RequestParam(defaultValue = "10") int stockPerWarehouse) {
        inventoryRepository.deleteAll();

        String sku = "PROD-PHONE";
        String name = "Flagship Smartphone 5G";
        double price = 29999.0;

        Inventory chennai = new Inventory(sku, name, "Chennai", stockPerWarehouse, price);
        Inventory bangalore = new Inventory(sku, name, "Bangalore", stockPerWarehouse, price);
        Inventory hyderabad = new Inventory(sku, name, "Hyderabad", stockPerWarehouse, price);
        Inventory mumbai = new Inventory(sku, name, "Mumbai", stockPerWarehouse, price);

        inventoryRepository.saveAll(List.of(chennai, bangalore, hyderabad, mumbai));

        return ResponseEntity.ok(inventoryService.getAllInventory());
    }
}
