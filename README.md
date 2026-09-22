# Real-Time E-Commerce Order Orchestration & Fulfillment Platform

Built for High-Concurrency Flash Sales and Safe Inventory Reservation.

## Project Team (Triumph Squad)
- **Garimalla Kiran Sai** (Team Leader)
- **Maram Akash** (Member)
- **Manoj Kumar** (Member)
- **Durga Vamsi Krishnam Raju Inampudi** (Member)

---

## 1. Core Problem & Engineering Challenge
During high-demand flash sales (e.g. 500 customers hitting "Order" simultaneously for a product with only 100 units across warehouses), conventional `read-then-write` logic results in race conditions where multiple requests read stale stock counts, leading to negative inventory and overselling.

### System Guarantees:
1. **Zero Overselling**: Stock can never be sold more than available.
2. **Never Negative**: Inventory counters freeze strictly at `0` under extreme concurrent bursts.
3. **Multi-Warehouse Routing**: Real-time allocation across Chennai, Bangalore, Hyderabad, and Mumbai warehouses.
4. **Resilient Bounded Retries**: Temporary payment timeouts retry up to 3 times before routing to DLQ.
5. **Dead Letter Queue (DLQ)**: Quarantines unresolvable orders for administrator inspection and 1-click re-driving.
6. **Live Operations Dashboard**: Instant visibility into stock levels, thread pool workers, and order life cycles.

---

## 2. Technical Architecture

```
[ Customer / Flash Sale Burst ]
              │
              ▼
    [ React Operations UI ]
              │ (REST / WebSockets)
              ▼
   [ Spring Boot Backend ]
              │
              ├── Custom Java ThreadPoolTaskExecutor (10 core, 25 max, 500 queue)
              │
              ├── Inventory Reservation Service
              │    └── PESSIMISTIC_WRITE Row Locking + Atomic Conditional Decrements
              │
              ├── Payment Service Simulation (Success, Timeout Retry, Outright Failure)
              │
              ├── Bounded Retry Engine (Max 3 attempts with exponential backoff)
              │
              ├── Dead Letter Queue (DLQ) Repository
              │
              └── Order Lifecycle State Machine & Audit Timeline Logs
```

---

## 3. Order Lifecycle States
- `CREATED` → `INVENTORY_RESERVED` → `PAYMENT_PROCESSING` → `PAYMENT_CONFIRMED` → `ORDER_CONFIRMED` → `PACKED` → `SHIPPED` → `DELIVERED`
- Failure & Reverse States: `OUT_OF_STOCK`, `PAYMENT_FAILED`, `DLQ`, `CANCELLED`

---

## 4. Running the Platform Locally

### Prerequisites
- Java 17 or higher (`java -version`)
- MySQL 8.0 running locally on port 3306
- Node.js 18+ and npm (`node -v`)

### Database Setup
1. Ensure your local MySQL service is running.
2. By default, the application connects to:
   - URL: `jdbc:mysql://localhost:3306/order_platform?createDatabaseIfNotExist=true`
   - Username: `root`
   - Password: `root` (Change in `backend/src/main/resources/application.properties` if different)

### Step 1: Start Backend
```powershell
cd D:\TriumpSquad\backend
# Using tools/apache-maven or system mvn:
mvn spring-boot:run
```
The server will start at `http://localhost:8080`. Initial inventory (100 units across 4 warehouses) is seeded automatically.

### Step 2: Start Frontend Dashboard
```powershell
cd D:\TriumpSquad\frontend
npm run dev
```
Open `http://localhost:5173` in your browser.

---

## 5. Demonstrating Concurrency to Judges
1. Open the dashboard at `http://localhost:5173`.
2. Look at the **Multi-Warehouse Inventory Hub**: Total available = 100 units (25 in Chennai, 25 in Bangalore, 25 in Hyderabad, 25 in Mumbai).
3. In the **High-Concurrency Flash Sale Simulator**, select **200 Orders** and click **Launch 200 Concurrent Orders**.
4. Observe the results live:
   - Exactly **100 orders** are confirmed and fulfilled.
   - Remaining **100 orders** safely transition to **OUT_OF_STOCK**.
   - Available inventory across all 4 warehouses drops to exactly **0** and **NEVER goes negative**.
   - Simulated payment timeouts demonstrate bounded 3-retries and route quarantined items to the **Dead Letter Queue (DLQ)**.
