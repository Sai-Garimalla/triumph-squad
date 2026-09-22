package com.forge.orders;

import com.forge.orders.model.Inventory;
import com.forge.orders.repository.InventoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;

@SpringBootApplication
public class OrdersApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrdersApplication.class, args);
    }

    /**
     * Seeds initial warehouse inventory on startup if empty.
     */
    @Bean
    public CommandLineRunner initDatabase(InventoryRepository inventoryRepository) {
        return args -> {
            if (inventoryRepository.count() == 0) {
                String sku = "PROD-PHONE";
                String name = "Flagship Smartphone 5G";
                double price = 29999.0;

                // 25 units per warehouse = 100 units total across 4 warehouses
                Inventory chennai = new Inventory(sku, name, "Chennai", 25, price);
                Inventory bangalore = new Inventory(sku, name, "Bangalore", 25, price);
                Inventory hyderabad = new Inventory(sku, name, "Hyderabad", 25, price);
                Inventory mumbai = new Inventory(sku, name, "Mumbai", 25, price);

                inventoryRepository.saveAll(List.of(chennai, bangalore, hyderabad, mumbai));
                System.out.println(">> Database initialized with 100 units across 4 warehouses (Chennai, Bangalore, Hyderabad, Mumbai).");
            }
        };
    }
}
