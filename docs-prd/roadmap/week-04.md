# Week 4 — Catalog: Categories, Products, Variants, Images

**Goal:** `/dashboard/products/**` runs on the API. Images are real files, not data URLs. Stock lives in `inventory_levels` from day one.

**Depends on:** Week 2 (tenancy), Week 3 (onboarding seeder hook).

## Day 1 — Schema
- [ ] `V6__categories_products_variants.sql`, `V7__media_files.sql`, `V8__locations_inventory.sql` (tables only; the inventory endpoints come in Week 5).
- [ ] Entities: `Category`, `Product` (`@OneToMany variants`, `tags` as `String[]` / `List<String>` with `@JdbcTypeCode(SqlTypes.ARRAY)`), `ProductVariant` (`attributes` as a JSON `Map<String,String>`), `Location`, `InventoryLevel`, `InventoryMovement`. All extend `TenantScopedEntity`.
- [ ] Registration now creates the default "Main Store" location. Resolve the Week 2 TODO and add a backfill migration for tenants that already exist:
  ```sql
  INSERT INTO locations (tenant_id, name, is_default)
  SELECT id, 'Main Store', true FROM tenants t
  WHERE NOT EXISTS (SELECT 1 FROM locations l WHERE l.tenant_id = t.id);
  ```
  This only inserts; it never deletes.

## Day 2 — Categories + product create/read
- [ ] `POST /categories` is idempotent by slug (matches `addCategory`). Also `GET`, `PATCH`, `DELETE`.
- [ ] `ProductService.create(input)`:
  - unique slug per tenant (`-2` suffix)
  - validate `priceKobo ≥ 0`, `compareAtKobo` ≥ price if set
  - `categoryId` must belong to the tenant (`@TenantId` makes a foreign one "not found")
  - insert an `inventory_levels` row at the default location with `on_hand = input.stock`, plus an `initial` movement
- [ ] `ProductResponse.stock` = `SUM(on_hand - reserved)` across locations. Load it in one query for list pages (`GROUP BY product_id`), not N+1.

## Day 3 — Update / delete / toggles / search
- [ ] `PUT /products/{id}`: regenerate the slug only if the name changed. **Ignore `stock`** (stock changes go through inventory).
- [ ] `PATCH /products/{id}` for `isActive` / `isFeatured`.
- [ ] `DELETE` is a soft delete. The slug becomes free again (partial unique index).
- [ ] `GET /products?search=&status=&categoryId=`: search on name, SKU or tag (`ILIKE` for now; `pg_trgm` later).
- [ ] Variants: `POST/PATCH/DELETE /products/{id}/variants/{vid}`.

## Day 4 — Image uploads
- [ ] `FileStorage` interface `store(tenantId, InputStream, contentType) → StoredFile(key, url)`.
- [ ] `LocalFileStorage`:
  - writes to `./uploads/{tenantId}/{uuid}.{ext}`, outside `src/`, and add `uploads/` to `.gitignore`
  - served at `/files/**` by a `ResourceHandler`
- [ ] `POST /uploads` (multipart):
  - check the **magic bytes**, not just the content-type
  - ≤ 5 MB
  - JPEG / PNG / WebP only
  - set `spring.servlet.multipart.max-file-size=5MB`
- [ ] Record a row in `media_files`.
- [ ] `S3FileStorage` for Cloudflare R2 can wait until Week 12. Selected via `@ConditionalOnProperty(storage.provider=s3)`.

## Day 5 — Onboarding seeding + frontend switch
- [ ] `CatalogOnboardingSeeder`: creates the template's categories and products, with each product's `stock` as initial inventory.
- [ ] Tests:
  - CRUD
  - slug collision
  - a foreign `categoryId` → 404
  - cross-tenant product access → 404
  - an upload with a fake extension → 400
- [ ] `/web`:
  - `product-store.ts` → API (keep the action names; make them async)
  - `ImageUploader` posts the file to `/uploads` and uses the returned URL (keep the client-side resize; it saves bandwidth)
  - `CategorySelect` → `POST /categories`
  - Remove the `seedProducts()` usage and the `persist`.

## Done when
- [ ] Product created on a laptop → visible on a phone after login.
- [ ] Onboarding with "Bold Market" creates the electronics catalog on the server.
- [ ] No `data:image/...` strings in the DB: `SELECT count(*) FROM products WHERE image_url LIKE 'data:%'` = 0.
