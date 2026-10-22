# PharmTech Application Business Flow

## 1. Purpose

PharmTech supports day-to-day pharmacy operations: maintaining medicine stock, creating customer bills, scanning prescriptions for in-stock medicine matches, and reviewing sales. The application is a React web client backed by a Spring Boot REST API and a relational database.

## 2. People and entry points

| Actor | How they enter | Main workflow |
|---|---|---|
| Administrator | Signs in with an `ADMIN` account and is routed to `/admin/dashboard` | Reviews operational summaries; manages medicines, sales records, and staff accounts |
| Biller | Signs in with a `BILLER` account and is routed to `/biller/dashboard` | Finds or scans medicines, prepares a cart, collects customer and payment details, and finalizes a bill |

Unauthenticated visitors are sent to the login page. The browser stores the returned JWT, role, and username in local storage; subsequent API requests include the JWT. Signing out removes these values.

## 3. Main operating cycle

```text
Admin maintains catalog and staff
              |
              v
Biller looks up medicines in available stock
              |
              v
Cart + customer details + payment mode
              |
              v
Backend validates stock, records bill, and deducts stock
              |
              v
PDF invoice for customer       Admin reviews sales ledger
              |                              |
              +---- informs replenishment ---+
```

## 4. Detailed workflows

### A. Sign-in and routing

1. A staff member enters a username and password on the login page.
2. The frontend sends the credentials to `POST /api/auth/login`.
3. The backend authenticates the credentials and returns a JWT and role.
4. The frontend stores the token, role, and username, then routes an administrator to the admin dashboard or a biller to the billing dashboard.
5. Protected frontend routes redirect unauthenticated users to login and redirect users with the wrong role to `/`.
6. If an authenticated API call receives an unauthorized/forbidden response, the API client clears the token and redirects to login (except for a failed login request).

### B. Catalog and staff setup (administrator)

1. The administrator opens **Inventory Stock** and reviews the medicine catalog.
2. They can search the list, export it to PDF, add a product, edit its details, or delete it. Catalog fields include name, manufacturer, unit price, stock quantity, and expiry date.
3. Changes are sent to the medicine API and saved in the database. The biller fetches this catalog for point-of-sale use.
4. In **Staff Management**, the administrator can onboard an account with a username, password, and `BILLER` or `ADMIN` role; edit an account; or revoke access.
5. The dashboard summarizes recorded sales, product count, low-stock count (fewer than 10 units), and estimated inventory value (unit price × stock quantity).

On an empty database, the backend seeds an `admin` and `biller` account plus sample medicines. These initial accounts are development/bootstrap behavior, not a staff onboarding step.

### C. Prescription-assisted billing (biller)

1. The billing page loads the medicine catalog and displays only items with positive stock.
2. The biller can search by medicine name and add an item to the cart. Repeated additions increase the quantity; quantity controls prevent values below one or above the stock currently shown in the client.
3. Alternatively, the biller uploads a prescription image using **Scan Rx**. The frontend compresses the image and sends it to `POST /api/ocr/extract`.
4. The backend sends the image to Gemini for medicine-name extraction, then compares extracted text against in-stock catalog names using exact/normalized and fuzzy matching.
5. Matching products are shown for review. The biller can add all matches to the cart or cancel; scanning does not create a bill or determine dosage/quantity.
6. The biller enters the customer name (required to finalize), optionally enters a phone number, and chooses `CASH`, `UPI`, or `CARD`.
7. The UI shows line subtotals and calculates GST as 9% CGST + 9% SGST (18% total). The biller may download a PDF preview from the cart or choose **Finalize Bill & Print**.
8. Finalization sends customer details, payment mode, medicine IDs, and quantities to `POST /api/bills`.
9. In a database transaction, the backend looks up the biller from the authenticated identity, reloads each medicine and its current unit price, checks stock, deducts quantities, calculates an 18% GST-inclusive total, and saves the bill and its line items. If any item is missing or has insufficient stock, the transaction fails rather than completing a partial sale.
10. On success, the frontend downloads the invoice PDF, clears the cart and customer fields, and refreshes medicine stock.

The backend is authoritative for item prices and the saved total. The PDF is generated in the browser using the current cart data and fixed invoice details.

### D. Sales review and reporting (administrator)

1. The dashboard and **Accounting Ledger** load saved bills from `GET /api/bills`.
2. The administrator can search bills by customer, phone, bill ID, payment mode, or date, and can narrow results to a calendar date.
3. The sales screen shows the bill identifier, customer/contact, date, payment mode, and total; reports can be exported to PDF.
4. The dashboard's sales total is the sum of saved bill totals. Stock deductions from each successful bill are reflected in the inventory view and subsequent biller catalog fetch.

## 5. Business records

| Record | Business meaning | Relationships |
|---|---|---|
| User | Staff login and role | A bill records the biller who created it |
| Medicine | Sellable catalog item and current inventory | Bill items refer to medicines |
| Bill | Completed sale, customer details, payment mode, date, and GST-inclusive total | Has one biller and multiple bill items |
| Bill item | Medicine, quantity, and unit price captured for a sale | Belongs to a bill and references a medicine |
| Pharmacy settings | Pharmacy identity/contact/tax configuration | Settings API exists; it is not currently part of the visible admin workflow |

## 6. Important implementation notes

- The current React application exposes login, admin dashboard, and biller billing routes. Staff registration is presented as an administrator action in the staff screen.
- API access is authenticated by default. The user-management API explicitly requires the `ADMIN` role; the medicine, bill, and OCR controller methods do not add role-specific backend restrictions. The frontend's role-based routes should therefore not be treated as a substitute for server-side authorization.
- The billing UI and billing service currently use a fixed 18% GST calculation. Although a pharmacy settings API and tax-rate field exist, those settings are not wired into the current billing screen or calculation.
- Prescription OCR suggests catalog matches only. The biller must review the match and explicitly add items; no prescription, dosage, or clinical validation record is stored by this workflow.
- The README describes OCR.space, but the current `OcrService` implementation calls Gemini. OCR therefore requires a configured `gemini.api.key`.

## 7. Code landmarks

- Frontend route protection and role routing: `frontend/PharmTech/src/App.jsx`
- Session state and token persistence: `frontend/PharmTech/src/context/AuthContext.jsx`
- API requests and token interceptor: `frontend/PharmTech/src/api/api.js`
- Login screen: `frontend/PharmTech/src/pages/LoginPage.jsx`
- Administrator workflows: `frontend/PharmTech/src/pages/AdminMedicines.jsx`
- Biller, cart, OCR flow, totals, and invoice: `frontend/PharmTech/src/pages/BillingPage.jsx`
- API endpoints: `backend/pharmtech/src/main/java/com/pharmtech/controller/`
- Billing validation, stock deduction, and total calculation: `backend/pharmtech/src/main/java/com/pharmtech/service/BillingService.java`
- OCR extraction: `backend/pharmtech/src/main/java/com/pharmtech/service/OcrService.java`
- Catalog matching: `backend/pharmtech/src/main/java/com/pharmtech/service/MedicineService.java`
- Authentication and bootstrap data: `backend/pharmtech/src/main/java/com/pharmtech/service/AuthService.java`, `backend/pharmtech/src/main/java/com/pharmtech/config/DataLoader.java`
