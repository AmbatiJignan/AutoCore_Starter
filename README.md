# AutoCore — Automobile Spare Parts Inventory & Business Management System

Full Stack Development II (FSD-II) Academic Laboratory Project  
**Tech Stack:** Java 17 · Apache Tomcat 9 (`javax.servlet`) · JSP · JDBC · Oracle Database 11g XE · Maven WAR · jBCrypt

---

## 1. System Overview & Architecture

AutoCore is a comprehensive, database-driven enterprise web application inspired by the day-to-day operations of an automobile spare-parts shop and warehouse. It delivers an end-to-end business management workflow including inventory catalogue management, supplier vendor directory, procurement orders with ACID goods receipt, counter sales and tax billing, audited stock movements ledger, and analytical financial reports.

### Architectural Layers (MVC Pattern)
- **Presentation Layer (View):** JSP views located securely under `WEB-INF/views/`:
  - `dashboard.jsp` — Real-time operational dashboard with valuation metrics, sales, purchases, movements, and reorder alerts.
  - `products.jsp` — Full inventory catalogue with category filters, stock filters, pagination, and manual adjustments.
  - `suppliers.jsp` — Supplier vendor directory, contact records, and order history.
  - `purchases.jsp`, `purchase-create.jsp`, `purchase-detail.jsp` — Purchase order creation, multi-part lines, and ACID goods receipt.
  - `sales.jsp`, `sale-create.jsp`, `invoice.jsp` — Counter sales, live stock validation, tax invoice rendering, and print stylesheet.
  - `movements.jsp` — Complete stock movement ledger tracking all receipts, sales, returns, and manual adjustments.
  - `reports.jsp` — Business intelligence analytics (valuation by category, sales summaries, best-sellers, supplier spend, and CSV export).
  - `users.jsp` — Administrator-only staff account management, role assignment, and password resets.
  - `login.jsp`, `error.jsp` — Secure authentication and friendly error pages.
- **Controller Layer (Servlets):**
  - `DashboardServlet` (`/dashboard`), `ProductServlet` (`/products`), `SupplierServlet` (`/suppliers`), `PurchaseServlet` (`/purchases`), `SaleServlet` (`/sales`), `StockMovementServlet` (`/movements`), `ReportServlet` (`/reports`), `UserServlet` (`/users`), `LoginServlet` (`/login`), `LogoutServlet` (`/logout`).
- **Security & Filter Layer:**
  - `AuthFilter` (`/*`) enforcing server-side session checks, role-based access control (blocking staff from `/users` with HTTP 403), anti-caching headers, and defensive security headers (`X-Content-Type-Options: nosniff`, `X-Frame-Options: SAMEORIGIN`, `X-XSS-Protection: 1; mode=block`).
  - `CsrfUtil` validating synchronizer tokens across all state-changing operations.
  - `LoginThrottler` blocking brute-force attacks after 5 consecutive failed attempts.
- **Business & Persistence Layer (DAO):**
  - `ProductDAO`, `SupplierDAO`, `PurchaseOrderDAO`, `SaleDAO`, `StockMovementDAO`, `ReportDAO`, `UserDAO` executing parameterized SQL statements via JDBC.
- **Database Schema (Oracle 11g XE):**
  - `AUTOCORE_USERS`, `AUTOCORE_PRODUCTS`, `AUTOCORE_SUPPLIERS`, `AUTOCORE_PURCHASE_ORDERS`, `AUTOCORE_PO_ITEMS`, `AUTOCORE_SALES`, `AUTOCORE_SALE_ITEMS`, `AUTOCORE_STOCK_MOVEMENTS`.
  - Sequences: `AUTOCORE_USER_SEQ`, `AUTOCORE_PRODUCT_SEQ`, `AUTOCORE_SUPPLIER_SEQ`, `AUTOCORE_PO_SEQ`, `AUTOCORE_PO_ITEM_SEQ`, `AUTOCORE_SALE_SEQ`, `AUTOCORE_SALE_ITEM_SEQ`, `AUTOCORE_MOVEMENT_SEQ`.

---

## 2. Database Setup & Migration

### Initial Setup
Connect to Oracle Database 11g XE as user `AUTOCORE`:
- **Schema Script:** `database/schema.sql` (Creates `AUTOCORE_PRODUCTS` and `AUTOCORE_USERS`).

### Phase 2 Incremental Migration
Run `database/phase2_migration.sql` against the existing `AUTOCORE` schema:
```sql
-- In SQL*Plus or Oracle SQL Developer connected as AUTOCORE:
@database/phase2_migration.sql
```
This script incrementally creates the 6 missing business tables, 6 sequences, seeds verified suppliers (Bosch, Mann+Hummel, Valeo), and generates initial opening stock movements without altering existing users or product IDs.

### JDBC Configuration
The connection utility `com.autocore.util.DBConnection` automatically uses local defaults:
- **JDBC URL:** `jdbc:oracle:thin:@localhost:1521:XE`
- **Username:** `AUTOCORE`
- **Password:** `Jonathan`

Override via JVM system properties if needed:
`-Dautocore.db.url="jdbc:oracle:thin:@localhost:1521:XE" -Dautocore.db.user="AUTOCORE" -Dautocore.db.password="your_password"`

---

## 3. Running & Deploying in Eclipse IDE & Tomcat 9

### Step 1: Import Project into Eclipse
1. Open Eclipse IDE for Enterprise Java and Web Developers.
2. Select **File → Import... → Maven → Existing Maven Projects**.
3. Select `AutoCore_Starter` and click **Finish**.
4. Right-click the project → **Maven → Update Project...** (Alt+F5).

### Step 2: Configure Tomcat 9 Server Runtime
1. In Eclipse bottom panel under **Servers**, right-click → **New → Server**.
2. Select **Apache → Tomcat v9.0 Server**, JRE: **Java SE 17** (`jdk-17.0.19`).
3. Right-click server → **Add and Remove...** → Add `AutoCore`.

### Step 3: Access the Application
Start the Tomcat server and navigate to:
```
http://localhost:8081/AutoCore/
```
Unauthenticated requests will automatically redirect to `http://localhost:8081/AutoCore/login`.

> [!NOTE]
> **Port 8081 vs 8080 (Oracle XE XML DB Conflict):**
> Oracle 11g XE occupies port `8080` by default for its embedded XML DB (`TNSLSNR.exe`), which challenges with HTTP 401 `Basic realm="XDB"`. Tomcat 9 is therefore configured to listen on port **`8081`**. Always use port **8081**.

---

## 4. Key Business Modules

### Module 1: Enhanced Operational Dashboard
- Real-time inventory metrics: Total parts count, total units, low-stock reorder alerts, and out-of-stock items.
- Dual valuation models: Total Purchase-Cost Valuation vs Total Selling-Price Valuation, and potential gross profit margin.
- Sales revenue & purchase expenditures directly queried from Oracle.
- Live tables for Critical Stock Attention, Recent Stock Movements, and Recent Sales.

### Module 2: Advanced Inventory Management
- Catalogue management: Part number, product name, brand, vehicle model, category, purchase price, selling price, stock quantity, and minimum stock threshold.
- Multi-field search across part number, name, brand, model, and category.
- Dropdown filters for category and stock status (`LOW`, `OUT`, `HEALTHY`).
- Server-side 3-tier pagination (`ROWNUM` compatible with Oracle 11g).
- Manual stock adjustment with mandatory reason and ledger tracking.
- Delete protection: Prevents deleting parts that exist in historical purchase orders or sales invoices.

### Module 3: Supplier Management
- Complete vendor directory: Supplier name, contact person, phone, email, business address, and tax ID / GSTIN.
- Active / Inactive status toggle.
- Purchase order history per supplier (`/suppliers?action=view&id=...`).
- Deletion protection: Deleting a supplier with recorded purchase orders is blocked; deactivation is enforced.

### Module 4: Purchasing & Goods Receipt
- Interactive purchase order builder with dynamic spare-part lines, quantities, and unit purchase costs.
- Calculates line totals and grand total dynamically on the client and validates server-side.
- Stock is **NOT** added when the order is placed (`ORDERED` status).
- **ACID Goods Receipt (`receiveOrder`):** Locks rows, increments stock, writes `PURCHASE_RECEIPT` movement records, and marks PO as `RECEIVED` in a single database transaction. Duplicate receipt is strictly prohibited.
- Purchase order detail tracking with printable PO view.

### Module 5: Sales & Billing
- Counter sales / POS checkout with customer name, phone, and dynamic item rows.
- Validates quantities against live available inventory; sales cannot cause stock to drop below zero.
- Generates a unique sequential invoice number (`INV-2026-XXXX`).
- Atomic checkout decrements physical stock and logs `SALE` movements.
- Professional, printable customer tax invoice layout (`/sales?action=invoice&id=...`) with print stylesheet (`@media print`).
- Order cancellation / customer return handling: Restores items to inventory and logs `SALE_CANCEL` movements.

### Module 6: Stock Movement Ledger
- Historical audit log recording every inventory change event:
  - `OPENING_STOCK`, `PURCHASE_RECEIPT`, `SALE`, `SALE_CANCEL`, `MANUAL_ADJUSTMENT`.
  - Captures product, quantity change, previous stock, resulting balance, transaction reference ID, reason, user, and exact timestamp.
- Manual stock adjustments enforce role authorization, mandatory reasons, and negative stock prevention.

### Module 7: Reports & Analytics
- Multi-tab business intelligence:
  - **Inventory Valuation:** Category breakdown of units, purchase valuation, selling valuation, and expected gross margin.
  - **Stock Health & Reorder:** Critical list of all low-stock and out-of-stock parts.
  - **Sales Performance:** Total completed sales, total revenue, average sale value, and transaction listing with date range filters.
  - **Purchase Summary:** Total purchase expenditures, fulfillment counts, and orders with date range filters.
  - **Best-Selling Parts:** Top parts ranked by units sold and revenue from actual recorded sales.
  - **Supplier Spend:** Vendor procurement spend analysis.
- One-click CSV Export for valuation and sales reports (`Content-Type: text/csv`).
- Clean print-friendly report stylesheets.

### Module 8: Role-Based Access Control (RBAC)
- **ADMIN:** Unrestricted access to all modules, including user administration (`/users`), role assignment, staff deactivation, password reset, and product deletion.
- **STAFF:** Access to daily operations (Dashboard, Inventory, Suppliers, Purchases, Sales, Stock Movements, Reports). Blocked from user administration (HTTP 403 Forbidden) and product deletion.

---

## 5. Lab Examination Demonstration Sequence

Follow this sequence to demonstrate all AutoCore features during your FSD-II examination:

1. **Sign In (`/AutoCore/login`):**
   - Sign in as administrator (`admin` / `Jonathan`).
   - Observe session establishment, session ID rotation, and CSRF token generation.
2. **Operational Dashboard (`/AutoCore/dashboard`):**
   - Review live metrics: Total parts, stock units, reorder alerts, purchase valuation vs selling valuation, and potential gross profit.
3. **Inventory Management (`/AutoCore/products`):**
   - Demonstrate multi-field search and category/stock filtering.
   - Click **+ Add Spare Part** to register a new part (e.g. `SP-4001`, `Spark Plug`, `NGK`, `Honda City`, `Ignition`, Purchase: `150`, Selling: `250`, Stock: `10`, Min: `4`).
   - Notice initial opening stock movement logged in the ledger.
   - Click **⚖ Stock Adjustment** on any item to demonstrate manual adjustment with an audited reason.
4. **Supplier Management (`/AutoCore/suppliers`):**
   - Review existing suppliers (Bosch, Mann+Hummel, Valeo).
   - Click **+ Register Supplier** to add a new distributor (e.g. `Denso India`).
   - Click **History** on Bosch to demonstrate historical purchase order tracking.
5. **Purchasing & Goods Receipt (`/AutoCore/purchases`):**
   - Click **+ Create Purchase Order**.
   - Select supplier, add multiple spare-part lines, observe live total calculation, and submit.
   - In the PO details view, verify status is **ORDERED** and inventory stock has **not** changed yet.
   - Click **📦 Receive Goods & Update Stock**.
   - Verify status transitions to **RECEIVED** and physical stock quantities are incremented in Oracle.
   - Click the button again to prove duplicate receipt prevention is enforced.
6. **Sales & Billing (`/AutoCore/sales`):**
   - Click **+ New Counter Sale**.
   - Enter customer name and phone.
   - Add spare parts; verify that attempting to sell more than available stock is prevented.
   - Submit the sale; verify invoice number generation (`INV-2026-XXXX`).
   - Review the printable **Tax Invoice** view.
   - Click **🖨 Print Invoice** to showcase the print stylesheet.
7. **Stock Movement Ledger (`/AutoCore/movements`):**
   - Review the audit trail. Show that every purchase receipt, sale, and adjustment appears with timestamp, before/after balances, and user name.
   - Filter by event type (`PURCHASE_RECEIPT`, `SALE`, etc.) and date range.
8. **Reports & Analytics (`/AutoCore/reports`):**
   - Demonstrate the **Inventory Valuation** tab showing category margin breakdown.
   - Show the **Best-Selling Parts** tab ranked by real recorded sales revenue.
   - Click **📥 Export CSV** to download `autocore_valuation_report.csv`.
9. **Role-Based Access Control (`/AutoCore/users`):**
   - As `admin`, navigate to `/users` and create a `STAFF` account (e.g. `staff1` / `Staff@123`).
   - Sign out, then sign in as `staff1`.
   - Show that `staff1` can perform daily inventory, sales billing, and purchasing, but the **User Accounts** sidebar link is hidden.
   - Attempt direct navigation to `/AutoCore/users` as `staff1` to prove the server returns **HTTP 403 Forbidden**.
