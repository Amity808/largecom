# 04 — Data Model & Migrations

## Conventions

- PK `id UUID DEFAULT gen_random_uuid()`.
- `created_at`, `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`.
- Money is `BIGINT` kobo, column names end in `_kobo`.
- Every tenant table has `tenant_id UUID NOT NULL REFERENCES tenants(id)` plus an index starting with `tenant_id`.
- Soft delete (`deleted_at`) only where history matters: products, customers. Everything else is hard-deleted.
- Enum-like values are `TEXT` with a `CHECK` constraint, which is easier to extend than Postgres `ENUM`.
- JSON columns are `JSONB`.
- Slugs are unique **per tenant**: `UNIQUE (tenant_id, slug)`.

Column names are `snake_case` in SQL and `camelCase` in Java/JSON. Hibernate's default naming strategy does the mapping.

## Migrations, by week

Files live in `largecom/src/main/resources/db/migration/`. One migration per table group. **Never edit one after it has run.**

| Version | File | Week |
|:--|:--|:--|
| V1 | `V1__extensions.sql` | 1 |
| V2 | `V2__tenants_users_memberships.sql` | 2 |
| V3 | `V3__refresh_tokens.sql` | 2 |
| V4 | `V4__tenant_counters_idempotency.sql` | 2 |
| V5 | `V5__store_themes.sql` | 3 |
| V6 | `V6__categories_products_variants.sql` | 4 |
| V7 | `V7__media_files.sql` | 4 |
| V8 | `V8__locations_inventory.sql` | 4 (tables) / 5 (screens) |
| V9 | `V9__customers.sql` | 5 |
| V10 | `V10__orders.sql` | 6 |
| V11 | `V11__pos.sql` | 6 |
| V12 | `V12__portfolio_packages_bookings.sql` | 7 |
| V13 | `V13__storefront_contact_newsletter.sql` | 8 |
| V14 | `V14__custom_domains.sql` | 8 |
| V15 | `V15__carts_payments.sql` | 9 |
| V16 | `V16__wallet_ledger.sql` | 9 |
| V17 | `V17__expenses.sql` | 10 |
| V18 | `V18__deliveries.sql` | 10 |
| V19 | `V19__campaigns.sql` | 10 |
| V20 | `V20__team_invites.sql` | 10 |
| V21 | `V21__storefront_events.sql` | 11 |
| V22 | `V22__rls_policies.sql` | 12 |
| V23 | `V23__app_role_grants.sql` | 12 |

## Tables

### V1 — extensions
```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pg_trgm;    -- fuzzy search later
CREATE EXTENSION IF NOT EXISTS citext;     -- case-insensitive email
```

### V2 — tenants, users, memberships
```sql
CREATE TABLE tenants (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  slug TEXT NOT NULL UNIQUE,                       -- acme → acme.punta.shop
  business_name TEXT NOT NULL,                     -- the ONE store name
  business_mode TEXT NOT NULL DEFAULT 'ecommerce' CHECK (business_mode IN ('ecommerce','pos','portfolio')),
  has_onboarded BOOLEAN NOT NULL DEFAULT false,
  currency TEXT NOT NULL DEFAULT 'NGN',
  timezone TEXT NOT NULL DEFAULT 'Africa/Lagos',
  settings JSONB NOT NULL DEFAULT '{}',            -- tagline, supportEmail, supportPhone, vatPercent, notifyOnNewOrder, autoFulfillDigital
  is_active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email CITEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  full_name TEXT,
  last_login_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE memberships (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role TEXT NOT NULL CHECK (role IN ('OWNER','MANAGER','STAFF')),
  status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','PENDING','DISABLED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (tenant_id, user_id)
);
```
Reserved slugs (reject at registration): `www, app, api, admin, store, mail, help, support, static, cdn`.

### V3 — refresh_tokens
`id, user_id, tenant_id, token_hash TEXT UNIQUE, expires_at, revoked_at, replaced_by UUID, created_at`.
On reuse of a revoked token, revoke the whole family (token theft detection).

### V4 — tenant_counters, idempotency_keys
```sql
CREATE TABLE tenant_counters (tenant_id UUID REFERENCES tenants(id), name TEXT, value BIGINT NOT NULL, PRIMARY KEY (tenant_id, name));
CREATE TABLE idempotency_keys (
  tenant_id UUID NOT NULL, key TEXT NOT NULL, request_hash TEXT NOT NULL,
  response_status INT, response_body JSONB, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (tenant_id, key)
);
```
At registration, insert a counter `('order', 1000)`.

### V5 — store_themes
```sql
CREATE TABLE store_themes (
  tenant_id UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
  template_id TEXT,
  document JSONB NOT NULL,          -- {global, header, footer, about, contact, shop, sections, pages, navLinks}
  version BIGINT NOT NULL DEFAULT 0, -- @Version optimistic lock
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### V6 — catalog
```sql
CREATE TABLE categories (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
  name TEXT NOT NULL, slug TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (tenant_id, slug)
);

CREATE TABLE products (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
  category_id UUID REFERENCES categories(id) ON DELETE SET NULL,
  name TEXT NOT NULL, slug TEXT NOT NULL, description TEXT NOT NULL DEFAULT '',
  price_kobo BIGINT NOT NULL CHECK (price_kobo >= 0),
  compare_at_kobo BIGINT CHECK (compare_at_kobo >= 0), cost_kobo BIGINT CHECK (cost_kobo >= 0),
  sku TEXT NOT NULL DEFAULT '', barcode TEXT NOT NULL DEFAULT '',
  is_active BOOLEAN NOT NULL DEFAULT true, is_featured BOOLEAN NOT NULL DEFAULT false,
  tags TEXT[] NOT NULL DEFAULT '{}', emoji TEXT NOT NULL DEFAULT '', swatch TEXT NOT NULL DEFAULT '#e5e7eb',
  image_url TEXT,
  deleted_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_products_slug ON products(tenant_id, slug) WHERE deleted_at IS NULL;
CREATE INDEX ix_products_list ON products(tenant_id, is_active, created_at DESC) WHERE deleted_at IS NULL;

CREATE TABLE product_variants (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
  product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  name TEXT NOT NULL, sku TEXT NOT NULL DEFAULT '',
  price_kobo BIGINT,                         -- NULL = use product price
  attributes JSONB NOT NULL DEFAULT '{}',    -- {"size":"L","color":"Red"}
  is_active BOOLEAN NOT NULL DEFAULT true, position INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```
`stock` is **not** a column here. See V8.

### V7 — media_files
`id, tenant_id, storage_key, public_url, content_type, size_bytes, created_by, created_at`. Used to clean up orphaned files later.

### V8 — locations, inventory
```sql
CREATE TABLE locations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
  name TEXT NOT NULL, is_default BOOLEAN NOT NULL DEFAULT false, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_locations_default ON locations(tenant_id) WHERE is_default;

CREATE TABLE inventory_levels (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
  product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  variant_id UUID REFERENCES product_variants(id) ON DELETE CASCADE,
  location_id UUID NOT NULL REFERENCES locations(id),
  on_hand INT NOT NULL DEFAULT 0 CHECK (on_hand >= 0),
  reserved INT NOT NULL DEFAULT 0 CHECK (reserved >= 0),
  reorder_point INT NOT NULL DEFAULT 5,
  unit_cost_kobo BIGINT,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_inventory ON inventory_levels(tenant_id, product_id, COALESCE(variant_id, '00000000-0000-0000-0000-000000000000'::uuid), location_id);

CREATE TABLE inventory_movements (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
  product_id UUID NOT NULL, variant_id UUID, location_id UUID NOT NULL,
  delta INT NOT NULL,
  reason TEXT NOT NULL CHECK (reason IN ('initial','restock','adjustment','sale','pos_sale','cancel','reserve','release','transfer_in','transfer_out')),
  reference_id UUID,              -- order id etc.
  created_by UUID, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```
Registration creates the default location "Main Store". Creating a product with `stock: n` creates its level row with `on_hand = n` and an `initial` movement.

### V9 — customers
```sql
CREATE TABLE customers (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
  name TEXT NOT NULL, email CITEXT, phone TEXT,
  customer_group TEXT NOT NULL DEFAULT 'New' CHECK (customer_group IN ('VIP','Returning','New','Wholesale')),
  status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active','inactive')),
  marketing_consent BOOLEAN NOT NULL DEFAULT false,
  total_orders INT NOT NULL DEFAULT 0,            -- maintained by OrderService in the same tx
  total_spent_kobo BIGINT NOT NULL DEFAULT 0,
  last_order_at TIMESTAMPTZ,
  deleted_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_customers_email ON customers(tenant_id, email) WHERE email IS NOT NULL AND deleted_at IS NULL;
CREATE UNIQUE INDEX ux_customers_phone ON customers(tenant_id, phone) WHERE phone IS NOT NULL AND deleted_at IS NULL;
```

### V10 — orders
```sql
CREATE TABLE orders (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
  order_number TEXT NOT NULL,                      -- ORD-1001
  customer_id UUID REFERENCES customers(id),
  customer_name TEXT NOT NULL, customer_email TEXT, customer_phone TEXT,   -- snapshot
  shipping_address TEXT NOT NULL DEFAULT '',
  channel TEXT NOT NULL CHECK (channel IN ('web','whatsapp','instagram','manual','pos')),
  payment_status TEXT NOT NULL CHECK (payment_status IN ('paid','unpaid','refunded')),
  payment_method TEXT CHECK (payment_method IN ('cash','card','transfer')),
  fulfillment_status TEXT NOT NULL DEFAULT 'pending'
     CHECK (fulfillment_status IN ('pending','processing','shipped','delivered','cancelled')),
  subtotal_kobo BIGINT NOT NULL, discount_kobo BIGINT NOT NULL DEFAULT 0,
  shipping_kobo BIGINT NOT NULL DEFAULT 0, tax_kobo BIGINT NOT NULL DEFAULT 0, total_kobo BIGINT NOT NULL,
  register_session_id UUID,                        -- set for POS (FK added in V11)
  cashier_id UUID REFERENCES users(id),
  notes TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (tenant_id, order_number)
);
CREATE INDEX ix_orders_list ON orders(tenant_id, created_at DESC);

CREATE TABLE order_items (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
  order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  product_id UUID REFERENCES products(id), variant_id UUID REFERENCES product_variants(id),
  name TEXT NOT NULL,                              -- snapshot "Classic Tee – L / Red"
  sku TEXT, quantity INT NOT NULL CHECK (quantity > 0),
  unit_price_kobo BIGINT NOT NULL, line_total_kobo BIGINT NOT NULL
);
```

### V11 — POS
```sql
CREATE TABLE register_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
  cashier_id UUID NOT NULL REFERENCES users(id), location_id UUID REFERENCES locations(id),
  status TEXT NOT NULL CHECK (status IN ('open','closed')),
  opened_at TIMESTAMPTZ NOT NULL DEFAULT now(), closed_at TIMESTAMPTZ,
  opening_float_kobo BIGINT NOT NULL,
  cash_sales_kobo BIGINT NOT NULL DEFAULT 0, card_sales_kobo BIGINT NOT NULL DEFAULT 0,
  transfer_sales_kobo BIGINT NOT NULL DEFAULT 0, transaction_count INT NOT NULL DEFAULT 0,
  actual_cash_kobo BIGINT
);
CREATE UNIQUE INDEX ux_one_open_register ON register_sessions(tenant_id, cashier_id) WHERE status = 'open';
ALTER TABLE orders ADD CONSTRAINT fk_orders_register FOREIGN KEY (register_session_id) REFERENCES register_sessions(id);

CREATE TABLE pos_settings (
  tenant_id UUID PRIMARY KEY REFERENCES tenants(id),
  show_store_name BOOLEAN NOT NULL DEFAULT true, show_store_phone BOOLEAN NOT NULL DEFAULT true,
  footer_message TEXT NOT NULL DEFAULT 'Thank you for shopping with us!',
  auto_print BOOLEAN NOT NULL DEFAULT false,
  default_payment_method TEXT NOT NULL DEFAULT 'cash',
  enable_vat BOOLEAN NOT NULL DEFAULT false,
  enable_multi_register BOOLEAN NOT NULL DEFAULT false
);
```
`POSSaleRecord` is a view over orders where `channel = 'pos'`. `POSDailySummary` is a `GROUP BY date` query. Neither has its own table.

### V12 — portfolio mode
```sql
CREATE TABLE portfolio_items (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
  title TEXT NOT NULL, slug TEXT NOT NULL, category TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '', image_url TEXT NOT NULL,
  is_featured BOOLEAN NOT NULL DEFAULT false, position INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE (tenant_id, slug)
);

CREATE TABLE service_packages (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
  name TEXT NOT NULL, price_kobo BIGINT NOT NULL, description TEXT NOT NULL DEFAULT '',
  features TEXT[] NOT NULL DEFAULT '{}', delivery_timeframe TEXT NOT NULL DEFAULT '',
  photos_included INT NOT NULL DEFAULT 0,
  is_popular BOOLEAN NOT NULL DEFAULT false, is_active BOOLEAN NOT NULL DEFAULT true,
  sort_order INT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE booking_inquiries (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
  client_name TEXT NOT NULL, email CITEXT NOT NULL, phone TEXT,
  event_type TEXT NOT NULL CHECK (event_type IN ('Wedding','Portrait','Event','Commercial','Family','Editorial','Other')),
  preferred_date DATE, package_id UUID REFERENCES service_packages(id) ON DELETE SET NULL,
  message TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL DEFAULT 'new' CHECK (status IN ('new','contacted','confirmed','declined')),
  source TEXT NOT NULL DEFAULT 'storefront' CHECK (source IN ('storefront','dashboard')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### V13 — storefront inbound
- `contact_messages(id, tenant_id, name, email, message, ip_hash, created_at, read_at)`
- `newsletter_subscribers(id, tenant_id, email, created_at, unsubscribed_at, UNIQUE(tenant_id, email))`

### V14 — custom_domains
`id, tenant_id, hostname TEXT UNIQUE, verification_token, status ('pending','verified','failed'), last_checked_at, verified_at, created_at`.

### V15 — carts, payments
- `carts(id, tenant_id, token_hash UNIQUE, customer_email, expires_at, created_at, updated_at)`
- `cart_items(id, tenant_id, cart_id, product_id, variant_id, quantity)`
- `payments(id, tenant_id, order_id, provider 'paystack', reference UNIQUE, amount_kobo, status ('initialized','success','failed','abandoned'), raw_event JSONB, paid_at, created_at)`
- `webhook_events(provider, event_id, received_at, PRIMARY KEY(provider, event_id))`: the dedupe table

### V16 — wallet
```sql
CREATE TABLE ledger_entries (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(), tenant_id UUID NOT NULL REFERENCES tenants(id),
  reference TEXT NOT NULL,
  type TEXT NOT NULL CHECK (type IN ('order_settlement','payout','addon_fee','topup','refund','fee')),
  description TEXT NOT NULL, amount_kobo BIGINT NOT NULL,      -- signed
  status TEXT NOT NULL CHECK (status IN ('pending','completed','failed')),
  available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  source_id UUID,                                              -- payment / payout id
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (tenant_id, reference)
);
```
- `bank_accounts(id, tenant_id, bank_name, bank_code, account_number_enc, account_last4, account_name, is_default, verified_at)`
- `payout_requests(id, tenant_id, bank_account_id, amount_kobo, status ('requested','processing','paid','failed'), requested_by, created_at, processed_at)`

### V17–V20
- `expenses(id, tenant_id, expense_date DATE, description, category CHECK (...5 values), vendor, amount_kobo, created_by, created_at)`
- `deliveries(id, tenant_id, order_id NOT NULL, courier, tracking_code, address, estimated_arrival DATE, status CHECK ('pending','in_transit','delivered','action_required'), created_at, updated_at)`
- `campaigns(id, tenant_id, name, channel CHECK ('whatsapp','sms','email'), audience JSONB, message_body TEXT NOT NULL, status CHECK ('draft','scheduled','sending','sent','failed'), scheduled_at, sent_at, sent_count, delivered_count, open_count, click_count, created_at)`, plus `campaign_recipients(campaign_id, customer_id, status, provider_message_id, opened_at, clicked_at)`
- `team_invites(id, tenant_id, email, name, role, token_hash, expires_at, accepted_at, invited_by, created_at)`

### V21 — storefront_events
`(id, tenant_id, type ('page_view','product_view','add_to_cart','checkout_started'), path, session_hash, created_at)`. Index `(tenant_id, created_at)`. Feeds the conversion rate.

## Entity relationships (core)

```
tenants 1─* memberships *─1 users
tenants 1─1 store_themes, pos_settings
tenants 1─* categories 1─* products 1─* product_variants
products/variants 1─* inventory_levels *─1 locations
customers 1─* orders 1─* order_items *─1 products
register_sessions 1─* orders (channel = pos)
orders 1─* payments, 1─* deliveries
service_packages 1─* booking_inquiries
tenants 1─* ledger_entries, payout_requests, bank_accounts
```
