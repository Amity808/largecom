# Week 3 — Store Settings, Theme, Templates, Onboarding

**Goal:** Onboarding, the theme editor and the Settings page save to the server. Store name, VAT and contact details have one source of truth.

**Depends on:** Week 2. **Note:** onboarding seeds products, so finish Days 1–3 now. The catalog seeding part of `POST /onboarding` ships in Week 4 (Day 5).

## Day 1 — Settings
- [ ] `StoreSettings` record ↔ `tenants.settings` jsonb (plus `business_name`).
- [ ] `GET /settings`, `PUT /settings`, `PUT /settings/business-mode`.
- [ ] `PUT /settings` with a new `storeName` also updates `store_themes.document.global.storeName` **in the same transaction**.
- [ ] Validation: valid email, phone ≤ 20 chars, `vatPercent` 0–100.

## Day 2 — Theme storage
- [ ] `V5__store_themes.sql`.
- [ ] Java records mirroring `web/src/lib/theme/schema.ts`:
  - `GlobalThemeSettings`, `HeaderSettings`, `FooterSettings`, `AboutPageSettings`, `ContactPageSettings`, `ShopPageSettings`
  - `NavLink`
  - `CustomPage`
  - `Section(id, type, enabled, JsonNode settings)`
  - Keeping `settings` as `JsonNode` is the pragmatic choice; you can type each section later.
- [ ] `StoreTheme` entity: `@JdbcTypeCode(SqlTypes.JSON) ThemeDocument document`, `@Version long version`.
- [ ] `GET /theme`, `PUT /theme` (validation from [05 Theme](../05-api-contract.md#theme--week-3)), `POST /theme/reset`.
- [ ] Defaults: port `DEFAULT_GLOBAL_THEME`, `DEFAULT_ABOUT_PAGE`, `DEFAULT_CONTACT_PAGE`, `DEFAULT_SHOP_PAGE`, `DEFAULT_NAV_LINKS` and `defaultSections()` to `resources/templates/defaults.json`.

## Day 3 — Templates
- [ ] Export each template from `web/src/lib/theme/templates.ts` into `resources/templates/{id}.json`:
  - `{id, name, industry, description, theme: ThemeSnapshot, catalog: {categories, products}, portfolio: []}`
  - Tip: a one-off Node script in `/web` that imports `STARTER_TEMPLATES` and writes `JSON.stringify(t.build())` and the seeds.
  - Swap `loremflickr` URLs for stable image URLs you control later.
- [ ] `TemplateService` loads them at startup. `GET /templates` (public).
- [ ] `POST /theme/apply-template`: port the `applyTemplate` rules:
  - keep `pages`
  - reset about, contact and shop
  - footer text `© {storeName}. All rights reserved.`
  - nav = template nav (or the default) + custom-page links

## Day 4 — Onboarding endpoint (theme part)
- [ ] `POST /onboarding {templateId}` (OWNER):
  1. `409` if `hasOnboarded`.
  2. Apply the template, with the global `storeName` = `tenant.business_name`.
  3. If the template's `businessType` is portfolio, set `business_mode=portfolio`.
  4. `has_onboarded = true`.
- [ ] Seeding the products and portfolio from the template plugs in during Weeks 4 and 7. Use an `OnboardingSeeder` interface so each module adds its own seeder.

## Day 5 — Frontend switch
- [ ] Onboarding page → `GET /templates` + `POST /onboarding`, then reload `/me` and the theme.
- [ ] Theme store:
  - `load()` from `GET /theme`
  - all edit actions stay local (it's an editor)
  - **Save** (`markSaved`) → `PUT /theme` with the version, and on `409` offer "reload / overwrite"
  - **Reset** → `POST /theme/reset`
  - Stop persisting editor UI flags to localStorage.
- [ ] Templates page → `POST /theme/apply-template`.
- [ ] Settings General tab and Notifications & Tax tab → `/settings`. Mode selector → `/settings/business-mode`.
- [ ] Sidebar reads `businessMode` from `/me`.
- [ ] Switch section ids to `crypto.randomUUID()` in `makeSectionId`.

## Done when
- [ ] New account → pick "Photography Portfolio" → the theme shows in the editor on another browser.
- [ ] Changing the store name in Settings shows up in the theme editor's preview.
- [ ] Two tabs editing the theme: the second Save gets `409`, not a silent overwrite.
