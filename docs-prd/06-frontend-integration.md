# 06 — Frontend Integration (`/web`)

Each backend week ends by switching one part of `/web` from local or mock data to the API. This page describes the shared plumbing (set up once, in Week 2) and the switch-over pattern.

> `/web` runs **Next.js 16**, whose APIs differ from older versions (`web/AGENTS.md`). Before touching routing, `proxy.ts` or `next.config.ts`, read the matching guide in `web/node_modules/next/dist/docs/`.

## 1. Same-origin API with a rewrite (Week 2)

The browser must see the API on the app's own origin, so the httpOnly cookies are sent automatically and `proxy.ts` can see `punta_session`.

`web/next.config.ts`:
```ts
const nextConfig: NextConfig = {
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${process.env.API_ORIGIN ?? "http://localhost:8080"}/api/:path*` }];
  },
};
```
In production, put both behind the same domain (or the same load balancer) and drop the rewrite.

## 2. A tiny API client (Week 2)

`web/src/lib/api/client.ts`:
```ts
export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public details?: { field: string; message: string }[]) { super(message); }
}

let refreshing: Promise<boolean> | null = null;

export async function api<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const res = await fetch(`/api/v1${path}`, {
    ...init,
    credentials: "include",
    headers: { "Content-Type": "application/json", ...storeSlugHeader(), ...init.headers },
  });
  if (res.status === 401 && retry) {
    refreshing ??= fetch("/api/v1/auth/refresh", { method: "POST", credentials: "include" }).then(r => r.ok).finally(() => (refreshing = null));
    if (await refreshing) return api<T>(path, init, false);
  }
  if (res.status === 204) return undefined as T;
  const body = await res.json();
  if (!res.ok) throw new ApiError(res.status, body.error?.code, body.error?.message, body.error?.details);
  return body.data as T;
}

// NEXT_PUBLIC_STOREFRONT_ROOT_DOMAIN = "localhost" (dev) | "punta.shop" (prod)
const ROOT = process.env.NEXT_PUBLIC_STOREFRONT_ROOT_DOMAIN ?? "localhost";
const NOT_STORES = new Set(["app", "www", "api"]);

function storeSlugHeader(): Record<string, string> {
  if (typeof window === "undefined") return {};
  const host = window.location.hostname;                 // "acme.localhost", "acme.punta.shop"
  if (!host.endsWith(`.${ROOT}`)) return {};              // custom domains: backend resolves from Host
  const slug = host.slice(0, -(ROOT.length + 1));
  return slug && !slug.includes(".") && !NOT_STORES.has(slug) ? { "X-Store-Slug": slug } : {};
}
```

Mutating calls send `Content-Type: application/json`. The backend rejects anything else, as part of its CSRF defence.

## 3. Switch-over pattern for a zustand store

Keep each store's **action names and signatures**, so pages don't change. Change only the inside:

```ts
// before (product-store.ts)
addProduct: (input) => { const p = {...input, id: crypto.randomUUID(), …}; set(s => ({ products: [p, ...s.products] })); return p; }

// after
addProduct: async (input) => {
  const p = await api<Product>("/products", { method: "POST", body: JSON.stringify(input) });
  set(s => ({ products: [p, ...s.products] }));
  return p;
},
load: async () => set({ products: await api<Product[]>("/products"), loaded: true }),
```

Checklist for each store:
1. Add a `load()` and call it from the dashboard layout, or from the page's `useEffect`.
2. Make the actions `async`. Update the callers that rely on the return value (e.g. `addOrder` returns an `Order` in `orders/new/page.tsx`).
3. Remove `persist(...)` and the `registerUserScopedStore(...)` call. The server is now the source of truth.
4. Change `createdAt: number` to `string` in the types and update the formatters.
5. Delete the matching seed or mock data once nothing imports it.

## 4. Auth changes (Week 2)

| File | Change |
|:--|:--|
| `stores/auth-store.ts` | `register` / `login` call the API; `currentUser` comes from `GET /me` (stored in memory, not `persist`). Remove the `users` map and `passwordHash`. |
| `lib/auth/crypto.ts` | Delete. Hashing happens on the server. |
| `lib/auth/session-cookie.ts` | Delete. The server sets an httpOnly cookie, which JS can't write anyway. |
| `components/auth/register-form.tsx` | Password min **8** |
| `components/auth/login-form.tsx` | Show the server's message ("Invalid email or password") |
| `proxy.ts` | Keep as is: it only checks that `punta_session` exists. Add: on a store host (`*.localhost` / `*.punta.shop`), rewrite `/` → `/store`, `/shop` → `/store/shop`, etc. (Week 8) |
| `app/dashboard/layout.tsx` | After `/me` loads: if `!hasOnboarded`, redirect to `/onboarding` |

## 5. Storefront tenant routing (Week 8)

Today `/store/**` shows the logged-in merchant's own data. After Week 8:

| Who | URL | How the tenant is known |
|:--|:--|:--|
| Merchant previewing | `localhost:3000/store` | API client sends `X-Store-Slug` = the merchant's slug from `/me` (preview mode) |
| Shopper (dev) | `acme.localhost:3000/` | `proxy.ts` rewrites to `/store`; the client sends `X-Store-Slug: acme` |
| Shopper (prod) | `acme.punta.shop/` or `www.acmeclothing.ng/` | Same, or the backend resolves the tenant from `Host` |

Browsers resolve `*.localhost` to 127.0.0.1 with no `/etc/hosts` changes.

Storefront pages stop reading `useThemeStore` / `useProductStore` / `usePortfolioStore` and call the `/storefront/*` endpoints. Prefer server components with `fetch` here (better SEO, no flash of empty content). Pass the slug from the `host` request header.

## 6. Order of switch-over (matches the roadmap)

| Week | Frontend area |
|:--|:--|
| 2 | Auth, `/me`, logout, API client, rewrite |
| 3 | Onboarding, theme editor Save / Reset / templates, settings General tab, business mode |
| 4 | Products, categories, variants, image uploader (POST the file instead of making a data URL) |
| 5 | Inventory page (now based on products), customers |
| 6 | Orders, new order, POS (register, checkout, close), POS history, POS settings |
| 7 | Portfolio, packages, bookings |
| 8 | All `/store/**` read pages, contact, newsletter, book; domain page |
| 9 | Cart drawer, checkout page, order status page; wallet balance and transactions |
| 10 | Expenses, deliveries, messaging, team tab, payouts |
| 11 | Dashboard cards, analytics page, POS history KPIs |

## 7. Generated types (optional, Week 4+)

`npx openapi-typescript http://localhost:8080/v3/api-docs -o src/lib/api/schema.d.ts`. This catches drift between the Java DTOs and the TS types at build time.
