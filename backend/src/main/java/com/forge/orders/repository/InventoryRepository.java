package com.forge.orders.repository;

import com.forge.orders.model.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    List<Inventory> findByProductSku(String productSku);

    // Row-level lock for concurrency safety during warehouse stock inspection
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.productSku = :sku AND i.warehouseName = :warehouse")
    Optional<Inventory> findWithLock(@Param("sku") String sku, @Param("warehouse") String warehouse);

    // Atomic conditional decrement to guarantee inventory never goes negative
    @Modifying
    @Query("UPDATE Inventory i SET i.availableQuantity = i.availableQuantity - :qty, i.reservedQuantity = i.reservedQuantity + :qty " +
           "WHERE i.id = :id AND i.availableQuantity >= :qty")
    int reserveStockAtomic(@Param("id") Long id, @Param("qty") int qty);

    // Release reservation back to available (e.g. on payment failure or cancellation)
    @Modifying
    @Query("UPDATE Inventory i SET i.availableQuantity = i.availableQuantity + :qty, i.reservedQuantity = i.reservedQuantity - :qty " +
           "WHERE i.id = :id AND i.reservedQuantity >= :qty")
    int releaseReservationAtomic(@Param("id") Long id, @Param("qty") int qty);

    // Confirm sale (move from reserved to sold)
    @Modifying
    @Query("UPDATE Inventory i SET i.reservedQuantity = i.reservedQuantity - :qty, i.soldQuantity = i.soldQuantity + :qty " +
           "WHERE i.id = :id AND i.reservedQuantity >= :qty")
    int confirmSaleAtomic(@Param("id") Long id, @Param("qty") int qty);
}
