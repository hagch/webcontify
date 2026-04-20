# Frontend Redesign Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Rebuild the entire frontend from scratch with shadcn/ui (Indigo+Emerald+Stone theme), feature-based architecture, and clean UX patterns — backend stays unchanged.

**Architecture:** Next.js 16 App Router with shadcn/ui directly in the app (no separate UI package). Feature-based folder structure (`src/features/[domain]/`). Orval-generated React Query hooks used directly, no facade layer. Auth via NextAuth v5 + Keycloak, ported from existing code.

**Tech Stack:** Next.js 16, React 19, shadcn/ui, Tailwind v4, React Query, React Hook Form + Zod, NextAuth v5, Orval, Lucide, Sonner, next-intl, next-themes, Vitest, Playwright

---

## Chunk 1: Clean Slate + Foundation

### Task 1: Delete old frontend code

**Files:**
- Delete: `packages/ui/` (entire directory)
- Delete: `apps/platform/src/components/` (entire directory)
- Delete: `apps/platform/src/facades/` (entire directory)
- Delete: `apps/platform/src/stores/` (entire directory)
- Delete: `apps/platform/src/app/(platform)/` (entire directory)
- Delete: `apps/platform/src/app/(workspace)/` (entire directory)
- Delete: `apps/platform/src/app/signin/` (entire directory)
- Delete: `apps/platform/src/generated/` (entire directory)
- Delete: `apps/platform/src/app/globals.css`
- Delete: `apps/platform/src/app/layout.tsx`
- Delete: `apps/platform/src/app/page.tsx`
- Delete: `apps/platform/src/providers/` (entire directory)
- Modify: `apps/platform/package.json` — remove `@webcontify/ui` dependency

- [x] **Step 1: Delete old directories and files**

```bash
rm -rf packages/ui/
rm -rf apps/platform/src/components/
rm -rf apps/platform/src/facades/
rm -rf apps/platform/src/stores/
rm -rf apps/platform/src/app/(platform)/
rm -rf apps/platform/src/app/(workspace)/
rm -rf apps/platform/src/app/signin/
rm -rf apps/platform/src/generated/
rm -rf apps/platform/src/providers/
rm -f apps/platform/src/app/globals.css
rm -f apps/platform/src/app/layout.tsx
rm -f apps/platform/src/app/page.tsx
```

- [x] **Step 2: Remove @webcontify/ui dependency from package.json**

In `apps/platform/package.json`, remove the line:
```json
"@webcontify/ui": "workspace:*",
```

- [x] **Step 3: Run pnpm install to update lockfile**

Run: `pnpm install`
Expected: Lockfile updated, no errors

- [x] **Step 4: Commit clean slate**

```bash
git add -A
git commit -m "chore: delete old frontend code for clean rebuild"
```

---

### Task 2: Initialize shadcn/ui in the Next.js app

**Files:**
- Create: `apps/platform/components.json`
- Create: `apps/platform/src/lib/utils.ts`

- [x] **Step 1: Initialize shadcn**

Run from `apps/platform/`:
```bash
npx shadcn@latest init
```

When prompted:
- Style: New York
- Base color: Stone
- CSS variables: Yes
- Tailwind CSS config path: (default)
- Components alias: `@/components`
- Utils alias: `@/lib/utils`

This creates `components.json` and `src/lib/utils.ts` (with `cn()` helper).

- [x] **Step 2: Verify components.json was created**

Run: `cat apps/platform/components.json`
Expected: JSON with aliases pointing to `@/components`, `@/lib/utils`

- [x] **Step 3: Commit shadcn init**

```bash
git add apps/platform/components.json apps/platform/src/lib/utils.ts
git commit -m "chore: initialize shadcn/ui in platform app"
```

---

### Task 3: Create the Indigo+Emerald+Stone theme

**Files:**
- Create: `apps/platform/src/app/globals.css`

- [x] **Step 1: Write globals.css with Indigo+Emerald+Stone theme**

Create `apps/platform/src/app/globals.css`:

```css
@import "tailwindcss";

@custom-variant dark (&:is(.dark *));

@theme inline {
  --color-background: var(--background);
  --color-foreground: var(--foreground);
  --color-card: var(--card);
  --color-card-foreground: var(--card-foreground);
  --color-popover: var(--popover);
  --color-popover-foreground: var(--popover-foreground);
  --color-primary: var(--primary);
  --color-primary-foreground: var(--primary-foreground);
  --color-secondary: var(--secondary);
  --color-secondary-foreground: var(--secondary-foreground);
  --color-muted: var(--muted);
  --color-muted-foreground: var(--muted-foreground);
  --color-accent: var(--accent);
  --color-accent-foreground: var(--accent-foreground);
  --color-destructive: var(--destructive);
  --color-destructive-foreground: var(--destructive-foreground);
  --color-border: var(--border);
  --color-input: var(--input);
  --color-ring: var(--ring);
  --color-success: var(--success);
  --color-success-foreground: var(--success-foreground);
  --color-warning: var(--warning);
  --color-warning-foreground: var(--warning-foreground);
  --color-info: var(--info);
  --color-info-foreground: var(--info-foreground);
  --font-sans: var(--font-geist-sans);
  --font-mono: var(--font-geist-mono);
  --radius-sm: calc(var(--radius) - 4px);
  --radius-md: calc(var(--radius) - 2px);
  --radius-lg: var(--radius);
  --radius-xl: calc(var(--radius) + 4px);
  --sidebar-width: 13.75rem;
  --sidebar-width-icon: 3.5rem;
  --color-sidebar: var(--sidebar);
  --color-sidebar-foreground: var(--sidebar-foreground);
  --color-sidebar-primary: var(--sidebar-primary);
  --color-sidebar-primary-foreground: var(--sidebar-primary-foreground);
  --color-sidebar-accent: var(--sidebar-accent);
  --color-sidebar-accent-foreground: var(--sidebar-accent-foreground);
  --color-sidebar-border: var(--sidebar-border);
  --color-sidebar-ring: var(--sidebar-ring);
}

/*
 * Indigo + Emerald + Stone Theme
 * Primary: Indigo 500/600 | Accent: Emerald 500/400
 * Neutrals: Stone (warm grays) | Signals: fixed
 */
:root {
  --background: oklch(0.985 0.002 75);       /* Stone 50 #FAFAF9 */
  --foreground: oklch(0.216 0.006 56);        /* Stone 950 #1C1917 */
  --card: oklch(1.0 0.0 0);                   /* White */
  --card-foreground: oklch(0.216 0.006 56);   /* Stone 950 */
  --popover: oklch(1.0 0.0 0);               /* White */
  --popover-foreground: oklch(0.216 0.006 56);/* Stone 950 */
  --primary: oklch(0.585 0.233 277);          /* Indigo 500 #6366F1 */
  --primary-foreground: oklch(1.0 0.0 0);     /* White */
  --secondary: oklch(0.97 0.001 75);          /* Stone 100 #F5F5F4 */
  --secondary-foreground: oklch(0.216 0.006 56);/* Stone 950 */
  --muted: oklch(0.97 0.001 75);             /* Stone 100 */
  --muted-foreground: oklch(0.553 0.013 58);  /* Stone 500 #78716C -> muted text */
  --accent: oklch(0.765 0.177 163);           /* Emerald 500 #10B981 */
  --accent-foreground: oklch(1.0 0.0 0);      /* White */
  --destructive: oklch(0.637 0.237 25);       /* Red 500 #EF4444 */
  --destructive-foreground: oklch(1.0 0.0 0); /* White */
  --border: oklch(0.923 0.003 75);            /* Stone 200 #E7E5E4 */
  --input: oklch(0.923 0.003 75);             /* Stone 200 */
  --ring: oklch(0.585 0.233 277);             /* Indigo 500 */
  --radius: 0.5rem;

  /* Signal Colors */
  --success: oklch(0.596 0.145 163);          /* Emerald 600 #059669 */
  --success-foreground: oklch(1.0 0.0 0);
  --warning: oklch(0.769 0.188 70);           /* Amber 500 #F59E0B */
  --warning-foreground: oklch(0.216 0.006 56);
  --info: oklch(0.685 0.169 237);             /* Sky 500 #0EA5E9 */
  --info-foreground: oklch(1.0 0.0 0);

  /* Sidebar */
  --sidebar: oklch(0.985 0.002 75);           /* Stone 50 */
  --sidebar-foreground: oklch(0.444 0.011 73);/* Stone 600 */
  --sidebar-primary: oklch(0.585 0.233 277);  /* Indigo 500 */
  --sidebar-primary-foreground: oklch(1.0 0.0 0);
  --sidebar-accent: oklch(0.98 0.003 277);    /* Indigo 50 #EEF2FF */
  --sidebar-accent-foreground: oklch(0.585 0.233 277); /* Indigo 500 */
  --sidebar-border: oklch(0.923 0.003 75);    /* Stone 200 */
  --sidebar-ring: oklch(0.585 0.233 277);     /* Indigo 500 */
}

.dark {
  --background: oklch(0.216 0.006 56);        /* Stone 900 #1C1917 */
  --foreground: oklch(0.985 0.002 75);        /* Stone 50 #FAFAF9 */
  --card: oklch(0.268 0.007 34);              /* Stone 800 #292524 */
  --card-foreground: oklch(0.985 0.002 75);   /* Stone 50 */
  --popover: oklch(0.268 0.007 34);           /* Stone 800 */
  --popover-foreground: oklch(0.985 0.002 75);/* Stone 50 */
  --primary: oklch(0.715 0.199 277);          /* Indigo 400 #818CF8 */
  --primary-foreground: oklch(0.216 0.006 56);/* Stone 900 */
  --secondary: oklch(0.321 0.009 17);         /* Stone 700 approx */
  --secondary-foreground: oklch(0.923 0.003 75);/* Stone 200 */
  --muted: oklch(0.268 0.007 34);             /* Stone 800 */
  --muted-foreground: oklch(0.709 0.01 56);   /* Stone 400 #A8A29E */
  --accent: oklch(0.765 0.177 163);           /* Emerald 400 #34D399 */
  --accent-foreground: oklch(0.216 0.006 56); /* Stone 900 */
  --destructive: oklch(0.577 0.245 27);       /* Red 600 */
  --destructive-foreground: oklch(0.985 0.002 75);
  --border: oklch(0.321 0.009 17);            /* Stone 700 */
  --input: oklch(0.321 0.009 17);             /* Stone 700 */
  --ring: oklch(0.715 0.199 277);             /* Indigo 400 */

  /* Signal Colors (Dark) */
  --success: oklch(0.765 0.177 163);          /* Emerald 400 */
  --success-foreground: oklch(0.216 0.006 56);
  --warning: oklch(0.769 0.188 70);           /* Amber 500 */
  --warning-foreground: oklch(0.216 0.006 56);
  --info: oklch(0.685 0.169 237);             /* Sky 500 */
  --info-foreground: oklch(0.216 0.006 56);

  /* Sidebar (Dark) */
  --sidebar: oklch(0.216 0.006 56);           /* Stone 900 */
  --sidebar-foreground: oklch(0.869 0.005 56);/* Stone 300 #D6D3D1 */
  --sidebar-primary: oklch(0.715 0.199 277);  /* Indigo 400 */
  --sidebar-primary-foreground: oklch(0.216 0.006 56);
  --sidebar-accent: oklch(0.268 0.007 34);    /* Stone 800 */
  --sidebar-accent-foreground: oklch(0.869 0.005 56); /* Stone 300 */
  --sidebar-border: oklch(0.321 0.009 17);    /* Stone 700 */
  --sidebar-ring: oklch(0.715 0.199 277);     /* Indigo 400 */
}

body {
  background: var(--background);
  color: var(--foreground);
  font-family: var(--font-geist-sans), Arial, Helvetica, sans-serif;
  font-variant-numeric: tabular-nums;
}

/* Skip-to-content link for keyboard users */
.skip-to-content {
  position: absolute;
  left: -9999px;
  z-index: 9999;
  padding: 0.5rem 1rem;
  background: var(--primary);
  color: var(--primary-foreground);
  border-radius: var(--radius);
}

.skip-to-content:focus {
  left: 1rem;
  top: 1rem;
}
```

- [x] **Step 2: Verify the CSS parses correctly**

Run: `cd apps/platform && npx next build --no-lint 2>&1 | head -20`
Expected: No CSS parse errors (build may fail due to missing layout.tsx, that's expected)

- [x] **Step 3: Commit theme**

```bash
git add apps/platform/src/app/globals.css
git commit -m "feat: add Indigo+Emerald+Stone theme (globals.css)"
```

---

### Task 4: Create root layout with providers

**Files:**
- Create: `apps/platform/src/providers/query-client-provider.tsx`
- Create: `apps/platform/src/providers/session-provider.tsx`
- Create: `apps/platform/src/providers/auth-token-sync.tsx`
- Create: `apps/platform/src/providers/theme-provider.tsx`
- Create: `apps/platform/src/app/layout.tsx`
- Create: `apps/platform/src/app/page.tsx`

- [x] **Step 1: Create providers directory and files**

Create `apps/platform/src/providers/query-client-provider.tsx`:
```tsx
"use client";

import {
  QueryClient,
  QueryClientProvider as TanstackProvider,
} from "@tanstack/react-query";
import { useState } from "react";

export function QueryClientProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 60 * 1000,
            refetchOnWindowFocus: false,
          },
        },
      })
  );

  return <TanstackProvider client={queryClient}>{children}</TanstackProvider>;
}
```

Create `apps/platform/src/providers/session-provider.tsx`:
```tsx
"use client";

import { SessionProvider as NextAuthSessionProvider } from "next-auth/react";

export function SessionProvider({ children }: { children: React.ReactNode }) {
  return <NextAuthSessionProvider>{children}</NextAuthSessionProvider>;
}
```

Create `apps/platform/src/providers/auth-token-sync.tsx`:
```tsx
"use client";

import { useEffect } from "react";
import { useSession } from "next-auth/react";
import { setClientAccessToken } from "@/lib/api-client";

export function AuthTokenSync() {
  const { data: session } = useSession();

  useEffect(() => {
    setClientAccessToken(session?.accessToken ?? undefined);
  }, [session?.accessToken]);

  return null;
}
```

Create `apps/platform/src/providers/theme-provider.tsx`:
```tsx
"use client";

import { ThemeProvider as NextThemesProvider } from "next-themes";
import type { ComponentProps } from "react";

type ThemeProviderProps = ComponentProps<typeof NextThemesProvider>;

export function ThemeProvider({ children, ...props }: ThemeProviderProps) {
  return <NextThemesProvider {...props}>{children}</NextThemesProvider>;
}
```

- [x] **Step 2: Create root layout**

Create `apps/platform/src/app/layout.tsx`:
```tsx
import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import { NextIntlClientProvider } from "next-intl";
import { getLocale, getMessages } from "next-intl/server";
import { Toaster } from "sonner";
import { QueryClientProvider } from "@/providers/query-client-provider";
import { SessionProvider } from "@/providers/session-provider";
import { AuthTokenSync } from "@/providers/auth-token-sync";
import { ThemeProvider } from "@/providers/theme-provider";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Webcontify Platform",
  description: "Webcontify Low-Code Platform",
};

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const locale = await getLocale();
  const messages = await getMessages();

  return (
    <html lang={locale} suppressHydrationWarning>
      <body
        className={`${geistSans.variable} ${geistMono.variable} min-h-dvh antialiased`}
      >
        <ThemeProvider
          attribute="class"
          defaultTheme="system"
          enableSystem
          disableTransitionOnChange
        >
          <NextIntlClientProvider messages={messages}>
            <SessionProvider>
              <AuthTokenSync />
              <QueryClientProvider>
                <a href="#main-content" className="skip-to-content">
                  Skip to content
                </a>
                {children}
              </QueryClientProvider>
            </SessionProvider>
            <Toaster richColors />
          </NextIntlClientProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
```

- [x] **Step 3: Create root page (redirect)**

Create `apps/platform/src/app/page.tsx`:
```tsx
import { redirect } from "next/navigation";
import { auth } from "@/auth";

export default async function RootPage() {
  const session = await auth();

  if (!session) {
    redirect("/signin");
  }

  // TODO: Check if platform admin → /tenants, else → /dashboard
  redirect("/tenants");
}
```

- [x] **Step 4: Verify build compiles**

Run: `cd apps/platform && npx next build --no-lint 2>&1 | tail -10`
Expected: Build succeeds (or only warnings about missing pages)

- [x] **Step 5: Commit providers and root layout**

```bash
git add apps/platform/src/providers/ apps/platform/src/app/layout.tsx apps/platform/src/app/page.tsx
git commit -m "feat: add root layout with providers and skip-to-content"
```

---

### Task 5: Install core shadcn components

**Files:**
- Create: `apps/platform/src/components/ui/button.tsx`
- Create: `apps/platform/src/components/ui/input.tsx`
- Create: `apps/platform/src/components/ui/label.tsx`
- Create: `apps/platform/src/components/ui/card.tsx`
- Create: `apps/platform/src/components/ui/dialog.tsx`
- Create: `apps/platform/src/components/ui/dropdown-menu.tsx`
- Create: `apps/platform/src/components/ui/separator.tsx`
- Create: `apps/platform/src/components/ui/skeleton.tsx`
- Create: `apps/platform/src/components/ui/table.tsx`
- Create: `apps/platform/src/components/ui/badge.tsx`
- Create: `apps/platform/src/components/ui/tooltip.tsx`
- Create: `apps/platform/src/components/ui/sheet.tsx`
- Create: `apps/platform/src/components/ui/sidebar.tsx`
- Create: `apps/platform/src/components/ui/avatar.tsx`
- Create: `apps/platform/src/components/ui/breadcrumb.tsx`
- Create: `apps/platform/src/components/ui/form.tsx`
- Create: `apps/platform/src/components/ui/textarea.tsx`
- Create: `apps/platform/src/components/ui/tabs.tsx`
- Create: `apps/platform/src/components/ui/sonner.tsx`
- Create: `apps/platform/src/components/ui/select.tsx`
- Create: `apps/platform/src/components/ui/collapsible.tsx`

- [x] **Step 1: Install shadcn components via CLI**

Run from `apps/platform/`:
```bash
npx shadcn@latest add button input label card dialog dropdown-menu separator skeleton table badge tooltip sheet sidebar avatar breadcrumb form textarea tabs sonner select collapsible
```

- [x] **Step 2: Verify components were created**

Run: `ls apps/platform/src/components/ui/`
Expected: All listed component files present

- [x] **Step 3: Commit shadcn components**

```bash
git add apps/platform/src/components/ui/
git commit -m "feat: install core shadcn/ui components"
```

---

### Task 6: Regenerate Orval API client

**Files:**
- Create: `apps/platform/src/generated/tenant.ts`

- [x] **Step 1: Run Orval generation**

Run from `apps/platform/`:
```bash
npx orval
```

Expected: `src/generated/tenant.ts` created with React Query hooks

- [x] **Step 2: Verify generated file exists**

Run: `head -5 apps/platform/src/generated/tenant.ts`
Expected: Generated file with auto-generated header

- [x] **Step 3: Commit generated client**

```bash
git add apps/platform/src/generated/
git commit -m "chore: regenerate Orval API client"
```

---

## Chunk 2: App Shell (Sidebar + Header + Layouts)

### Task 7: Create useAuth hook

**Files:**
- Test: `apps/platform/src/hooks/__tests__/use-auth.test.ts`
- Create: `apps/platform/src/hooks/use-auth.ts`

- [x] **Step 1: Write the failing test**

Create `apps/platform/src/hooks/__tests__/use-auth.test.ts`:
```ts
import { describe, it, expect, vi } from "vitest";
import { renderHook } from "@testing-library/react";

// Mock next-auth/react
vi.mock("next-auth/react", () => ({
  useSession: vi.fn(),
}));

import { useAuth } from "../use-auth";
import { useSession } from "next-auth/react";

const mockedUseSession = vi.mocked(useSession);

describe("useAuth", () => {
  it("returns unauthenticated state when no session", () => {
    mockedUseSession.mockReturnValue({
      data: null,
      status: "unauthenticated",
      update: vi.fn(),
    });

    const { result } = renderHook(() => useAuth());

    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.isPlatformAdmin).toBe(false);
    expect(result.current.user).toBeNull();
    expect(result.current.isLoading).toBe(false);
  });

  it("returns loading state", () => {
    mockedUseSession.mockReturnValue({
      data: null,
      status: "loading",
      update: vi.fn(),
    });

    const { result } = renderHook(() => useAuth());

    expect(result.current.isLoading).toBe(true);
  });

  it("returns authenticated user", () => {
    mockedUseSession.mockReturnValue({
      data: {
        user: { name: "Test User", email: "test@example.com" },
        accessToken: "test-token",
        expires: "2099-01-01",
      },
      status: "authenticated",
      update: vi.fn(),
    });

    const { result } = renderHook(() => useAuth());

    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.user?.name).toBe("Test User");
    expect(result.current.user?.email).toBe("test@example.com");
  });

  it("detects platform admin from access token", () => {
    // JWT with PLATFORM_ADMIN role in realm_access
    const payload = btoa(
      JSON.stringify({ realm_access: { roles: ["PLATFORM_ADMIN"] } })
    );
    const fakeJwt = `header.${payload}.signature`;

    mockedUseSession.mockReturnValue({
      data: {
        user: { name: "Admin" },
        accessToken: fakeJwt,
        expires: "2099-01-01",
      },
      status: "authenticated",
      update: vi.fn(),
    });

    const { result } = renderHook(() => useAuth());

    expect(result.current.isPlatformAdmin).toBe(true);
  });
});
```

- [x] **Step 2: Run test to verify it fails**

Run: `cd apps/platform && npx vitest run src/hooks/__tests__/use-auth.test.ts`
Expected: FAIL — module not found

- [x] **Step 3: Write minimal implementation**

Create `apps/platform/src/hooks/use-auth.ts`:
```ts
"use client";

import { useSession } from "next-auth/react";

function decodeJwtPayload(token: string): Record<string, unknown> | null {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    const payload = atob(parts[1]);
    return JSON.parse(payload);
  } catch {
    return null;
  }
}

export function useAuth() {
  const { data: session, status } = useSession();

  const isLoading = status === "loading";
  const isAuthenticated = status === "authenticated";
  const user = session?.user ?? null;

  let isPlatformAdmin = false;
  if (session?.accessToken) {
    const payload = decodeJwtPayload(session.accessToken);
    const roles =
      (payload?.realm_access as { roles?: string[] })?.roles ?? [];
    isPlatformAdmin = roles.includes("PLATFORM_ADMIN");
  }

  return {
    user,
    isAuthenticated,
    isLoading,
    isPlatformAdmin,
  };
}
```

- [x] **Step 4: Run test to verify it passes**

Run: `cd apps/platform && npx vitest run src/hooks/__tests__/use-auth.test.ts`
Expected: PASS (4 tests)

- [x] **Step 5: Commit**

```bash
git add apps/platform/src/hooks/use-auth.ts apps/platform/src/hooks/__tests__/use-auth.test.ts
git commit -m "feat: add useAuth hook with platform admin detection"
```

---

### Task 8: Create sidebar navigation config

**Files:**
- Create: `apps/platform/src/components/layout/nav-config.ts`
- Test: `apps/platform/src/components/layout/__tests__/nav-config.test.ts`

- [x] **Step 1: Write the failing test**

Create `apps/platform/src/components/layout/__tests__/nav-config.test.ts`:
```ts
import { describe, it, expect } from "vitest";
import {
  getPlatformNavItems,
  getWorkspaceNavItems,
  type NavSection,
} from "../nav-config";

describe("nav-config", () => {
  describe("getPlatformNavItems", () => {
    it("returns platform admin sections", () => {
      const sections = getPlatformNavItems();
      expect(sections.length).toBeGreaterThan(0);

      const firstSection = sections[0];
      expect(firstSection.label).toBeDefined();
      expect(firstSection.items.length).toBeGreaterThan(0);

      const tenantItem = sections
        .flatMap((s) => s.items)
        .find((i) => i.href === "/tenants");
      expect(tenantItem).toBeDefined();
      expect(tenantItem?.label).toBe("Tenants");
    });
  });

  describe("getWorkspaceNavItems", () => {
    it("returns workspace sections for admin role", () => {
      const sections = getWorkspaceNavItems({ isAdmin: true });
      const allItems = sections.flatMap((s) => s.items);

      expect(allItems.find((i) => i.href === "/dashboard")).toBeDefined();
      expect(allItems.find((i) => i.href === "/apps")).toBeDefined();
      expect(allItems.find((i) => i.href === "/users")).toBeDefined();
      expect(
        allItems.find((i) => i.href === "/settings/branding")
      ).toBeDefined();
    });

    it("returns limited workspace sections for user role", () => {
      const sections = getWorkspaceNavItems({ isAdmin: false });
      const allItems = sections.flatMap((s) => s.items);

      expect(allItems.find((i) => i.href === "/dashboard")).toBeDefined();
      expect(allItems.find((i) => i.href === "/apps")).toBeDefined();
      expect(allItems.find((i) => i.href === "/users")).toBeUndefined();
      expect(
        allItems.find((i) => i.href === "/settings/branding")
      ).toBeUndefined();
    });
  });
});
```

- [x] **Step 2: Run test to verify it fails**

Run: `cd apps/platform && npx vitest run src/components/layout/__tests__/nav-config.test.ts`
Expected: FAIL — module not found

- [x] **Step 3: Write minimal implementation**

Create `apps/platform/src/components/layout/nav-config.ts`:
```ts
import {
  Building2,
  LayoutDashboard,
  AppWindow,
  Users,
  Shield,
  Settings,
  Palette,
  Mail,
  Globe,
  ShieldCheck,
  type LucideIcon,
} from "lucide-react";

export interface NavItem {
  label: string;
  href: string;
  icon: LucideIcon;
}

export interface NavSection {
  label: string;
  items: NavItem[];
}

export function getPlatformNavItems(): NavSection[] {
  return [
    {
      label: "Management",
      items: [
        { label: "Tenants", href: "/tenants", icon: Building2 },
        { label: "Platform Admins", href: "/admins", icon: ShieldCheck },
      ],
    },
    {
      label: "System",
      items: [
        { label: "Settings", href: "/settings", icon: Settings },
      ],
    },
  ];
}

export function getWorkspaceNavItems(options: {
  isAdmin: boolean;
}): NavSection[] {
  const sections: NavSection[] = [
    {
      label: "Overview",
      items: [
        { label: "Dashboard", href: "/dashboard", icon: LayoutDashboard },
        { label: "Apps", href: "/apps", icon: AppWindow },
      ],
    },
  ];

  if (options.isAdmin) {
    sections.push({
      label: "Management",
      items: [
        { label: "Users", href: "/users", icon: Users },
        { label: "Roles", href: "/roles", icon: Shield },
      ],
    });

    sections.push({
      label: "Settings",
      items: [
        { label: "Branding", href: "/settings/branding", icon: Palette },
        { label: "Email", href: "/settings/email", icon: Mail },
        { label: "Domain", href: "/settings/domain", icon: Globe },
      ],
    });
  }

  return sections;
}
```

- [x] **Step 4: Run test to verify it passes**

Run: `cd apps/platform && npx vitest run src/components/layout/__tests__/nav-config.test.ts`
Expected: PASS (3 tests)

- [x] **Step 5: Commit**

```bash
git add apps/platform/src/components/layout/nav-config.ts apps/platform/src/components/layout/__tests__/nav-config.test.ts
git commit -m "feat: add sidebar navigation config with role-based visibility"
```

---

### Task 9: Build the App Sidebar component

**Files:**
- Create: `apps/platform/src/components/layout/app-sidebar.tsx`
- Create: `apps/platform/src/components/layout/nav-user.tsx`

- [x] **Step 1: Create NavUser component**

Create `apps/platform/src/components/layout/nav-user.tsx`:
```tsx
"use client";

import { signOut } from "next-auth/react";
import { ChevronsUpDown, LogOut, Moon, Sun, Monitor } from "lucide-react";
import { useTheme } from "next-themes";
import { useAuth } from "@/hooks/use-auth";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/ui/sidebar";

export function NavUser() {
  const { user } = useAuth();
  const { setTheme } = useTheme();
  const { isMobile } = useSidebar();

  const initials = (user?.name ?? "U")
    .split(" ")
    .map((n) => n[0])
    .join("")
    .toUpperCase()
    .slice(0, 2);

  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <SidebarMenuButton
              size="lg"
              className="data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground"
            >
              <Avatar className="h-8 w-8 rounded-lg">
                <AvatarFallback className="rounded-lg">
                  {initials}
                </AvatarFallback>
              </Avatar>
              <div className="grid flex-1 text-left text-sm leading-tight">
                <span className="truncate font-semibold">
                  {user?.name ?? "User"}
                </span>
                <span className="truncate text-xs text-muted-foreground">
                  {user?.email ?? ""}
                </span>
              </div>
              <ChevronsUpDown className="ml-auto size-4" />
            </SidebarMenuButton>
          </DropdownMenuTrigger>
          <DropdownMenuContent
            className="w-56 rounded-lg"
            side={isMobile ? "bottom" : "right"}
            align="end"
            sideOffset={4}
          >
            <DropdownMenuLabel className="p-0 font-normal">
              <div className="flex items-center gap-2 px-1 py-1.5 text-left text-sm">
                <Avatar className="h-8 w-8 rounded-lg">
                  <AvatarFallback className="rounded-lg">
                    {initials}
                  </AvatarFallback>
                </Avatar>
                <div className="grid flex-1 text-left text-sm leading-tight">
                  <span className="truncate font-semibold">
                    {user?.name ?? "User"}
                  </span>
                  <span className="truncate text-xs text-muted-foreground">
                    {user?.email ?? ""}
                  </span>
                </div>
              </div>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuSub>
              <DropdownMenuSubTrigger>
                <Sun className="mr-2 size-4 rotate-0 scale-100 transition-all dark:-rotate-90 dark:scale-0" />
                <Moon className="absolute mr-2 size-4 rotate-90 scale-0 transition-all dark:rotate-0 dark:scale-100" />
                Theme
              </DropdownMenuSubTrigger>
              <DropdownMenuSubContent>
                <DropdownMenuItem onClick={() => setTheme("light")}>
                  <Sun className="mr-2 size-4" />
                  Light
                </DropdownMenuItem>
                <DropdownMenuItem onClick={() => setTheme("dark")}>
                  <Moon className="mr-2 size-4" />
                  Dark
                </DropdownMenuItem>
                <DropdownMenuItem onClick={() => setTheme("system")}>
                  <Monitor className="mr-2 size-4" />
                  System
                </DropdownMenuItem>
              </DropdownMenuSubContent>
            </DropdownMenuSub>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={() => signOut()}>
              <LogOut className="mr-2 size-4" />
              Sign out
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </SidebarMenuItem>
    </SidebarMenu>
  );
}
```

- [x] **Step 2: Create AppSidebar component**

Create `apps/platform/src/components/layout/app-sidebar.tsx`:
```tsx
"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarSeparator,
} from "@/components/ui/sidebar";
import { NavUser } from "./nav-user";
import type { NavSection } from "./nav-config";

interface AppSidebarProps {
  sections: NavSection[];
  headerSlot?: React.ReactNode;
}

export function AppSidebar({ sections, headerSlot }: AppSidebarProps) {
  const pathname = usePathname();

  return (
    <Sidebar collapsible="icon">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton size="lg" asChild>
              <Link href="/">
                <div className="flex aspect-square size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
                  <span className="text-sm font-bold">W</span>
                </div>
                <div className="grid flex-1 text-left text-sm leading-tight">
                  <span className="truncate font-semibold">Webcontify</span>
                </div>
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
        {headerSlot}
      </SidebarHeader>
      <SidebarContent>
        {sections.map((section, index) => (
          <div key={section.label}>
            {index > 0 && <SidebarSeparator />}
            <SidebarGroup>
              <SidebarGroupLabel>{section.label}</SidebarGroupLabel>
              <SidebarMenu>
                {section.items.map((item) => {
                  const isActive = pathname.startsWith(item.href);
                  return (
                    <SidebarMenuItem key={item.href}>
                      <SidebarMenuButton
                        asChild
                        isActive={isActive}
                        tooltip={item.label}
                      >
                        <Link href={item.href}>
                          <item.icon />
                          <span>{item.label}</span>
                        </Link>
                      </SidebarMenuButton>
                    </SidebarMenuItem>
                  );
                })}
              </SidebarMenu>
            </SidebarGroup>
          </div>
        ))}
      </SidebarContent>
      <SidebarFooter>
        <NavUser />
      </SidebarFooter>
    </Sidebar>
  );
}
```

- [x] **Step 3: Commit sidebar components**

```bash
git add apps/platform/src/components/layout/app-sidebar.tsx apps/platform/src/components/layout/nav-user.tsx
git commit -m "feat: add AppSidebar and NavUser components"
```

---

### Task 10: Build the Header component

**Files:**
- Create: `apps/platform/src/components/layout/app-header.tsx`

- [x] **Step 1: Create AppHeader component**

Create `apps/platform/src/components/layout/app-header.tsx`:
```tsx
"use client";

import { SidebarTrigger } from "@/components/ui/sidebar";
import { Separator } from "@/components/ui/separator";
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb";

interface BreadcrumbSegment {
  label: string;
  href?: string;
}

interface AppHeaderProps {
  breadcrumbs?: BreadcrumbSegment[];
}

export function AppHeader({ breadcrumbs = [] }: AppHeaderProps) {
  return (
    <header className="flex h-14 shrink-0 items-center gap-2 border-b px-4">
      <SidebarTrigger className="-ml-1" />
      {breadcrumbs.length > 0 && (
        <>
          <Separator orientation="vertical" className="mr-2 h-4" />
          <Breadcrumb>
            <BreadcrumbList>
              {breadcrumbs.map((segment, index) => {
                const isLast = index === breadcrumbs.length - 1;
                return (
                  <BreadcrumbItem key={segment.label}>
                    {index > 0 && <BreadcrumbSeparator />}
                    {isLast ? (
                      <BreadcrumbPage>{segment.label}</BreadcrumbPage>
                    ) : (
                      <BreadcrumbLink href={segment.href}>
                        {segment.label}
                      </BreadcrumbLink>
                    )}
                  </BreadcrumbItem>
                );
              })}
            </BreadcrumbList>
          </Breadcrumb>
        </>
      )}
    </header>
  );
}
```

- [x] **Step 2: Commit**

```bash
git add apps/platform/src/components/layout/app-header.tsx
git commit -m "feat: add AppHeader with breadcrumbs"
```

---

### Task 11: Create (auth) routing group with signin page

**Files:**
- Create: `apps/platform/src/app/(auth)/signin/page.tsx`

- [x] **Step 1: Create signin page**

Create `apps/platform/src/app/(auth)/signin/page.tsx`:
```tsx
import { signIn } from "@/auth";

export default function SignInPage() {
  return (
    <div className="flex min-h-dvh items-center justify-center">
      <form
        action={async () => {
          "use server";
          await signIn("keycloak");
        }}
      >
        <button
          type="submit"
          className="rounded-lg bg-primary px-6 py-3 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90"
        >
          Sign in with Keycloak
        </button>
      </form>
    </div>
  );
}
```

- [x] **Step 2: Commit**

```bash
git add apps/platform/src/app/(auth)/
git commit -m "feat: add signin page with Keycloak redirect"
```

---

### Task 12: Create (platform) layout with sidebar

**Files:**
- Create: `apps/platform/src/app/(platform)/layout.tsx`

- [x] **Step 1: Create platform layout**

Create `apps/platform/src/app/(platform)/layout.tsx`:
```tsx
import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar";
import { AppSidebar } from "@/components/layout/app-sidebar";
import { getPlatformNavItems } from "@/components/layout/nav-config";

export default function PlatformLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const sections = getPlatformNavItems();

  return (
    <SidebarProvider>
      <AppSidebar sections={sections} />
      <SidebarInset>
        <main id="main-content">{children}</main>
      </SidebarInset>
    </SidebarProvider>
  );
}
```

- [x] **Step 2: Commit**

```bash
git add apps/platform/src/app/(platform)/layout.tsx
git commit -m "feat: add (platform) layout with sidebar"
```

---

### Task 13: Create (workspace) layout with sidebar

**Files:**
- Create: `apps/platform/src/app/(workspace)/layout.tsx`

- [x] **Step 1: Create workspace layout**

Create `apps/platform/src/app/(workspace)/layout.tsx`:
```tsx
import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar";
import { AppSidebar } from "@/components/layout/app-sidebar";
import { getWorkspaceNavItems } from "@/components/layout/nav-config";

export default function WorkspaceLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  // TODO: Determine isAdmin from auth context (OpenFGA in Phase 3)
  const sections = getWorkspaceNavItems({ isAdmin: true });

  return (
    <SidebarProvider>
      <AppSidebar sections={sections} />
      <SidebarInset>
        <main id="main-content">{children}</main>
      </SidebarInset>
    </SidebarProvider>
  );
}
```

- [x] **Step 2: Commit**

```bash
git add apps/platform/src/app/(workspace)/layout.tsx
git commit -m "feat: add (workspace) layout with sidebar"
```

---

## Chunk 3: Platform Admin — Tenant CRUD

### Task 14: Create Tenant list page

**Files:**
- Create: `apps/platform/src/app/(platform)/tenants/page.tsx`
- Create: `apps/platform/src/features/tenants/components/tenant-table.tsx`
- Create: `apps/platform/src/features/tenants/components/tenant-table-skeleton.tsx`

- [x] **Step 1: Create TenantTableSkeleton**

Create `apps/platform/src/features/tenants/components/tenant-table-skeleton.tsx`:
```tsx
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

export function TenantTableSkeleton() {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Name</TableHead>
          <TableHead>Slug</TableHead>
          <TableHead>Status</TableHead>
          <TableHead className="text-right">Actions</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {Array.from({ length: 5 }).map((_, i) => (
          <TableRow key={i}>
            <TableCell>
              <Skeleton className="h-4 w-32" />
            </TableCell>
            <TableCell>
              <Skeleton className="h-4 w-24" />
            </TableCell>
            <TableCell>
              <Skeleton className="h-5 w-16 rounded-full" />
            </TableCell>
            <TableCell className="text-right">
              <Skeleton className="ml-auto h-8 w-8" />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
```

- [x] **Step 2: Create TenantTable**

Create `apps/platform/src/features/tenants/components/tenant-table.tsx`:
```tsx
"use client";

import Link from "next/link";
import { MoreHorizontal, ExternalLink } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { TenantTableSkeleton } from "./tenant-table-skeleton";

interface Tenant {
  id: string;
  name: string;
  slug: string;
  status: string;
}

interface TenantTableProps {
  tenants: Tenant[];
  isLoading: boolean;
  isRefetching: boolean;
}

export function TenantTable({
  tenants,
  isLoading,
  isRefetching,
}: TenantTableProps) {
  if (isLoading) {
    return <TenantTableSkeleton />;
  }

  if (tenants.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center rounded-lg border border-dashed p-12 text-center">
        <p className="text-lg font-medium">No tenants yet</p>
        <p className="mt-1 text-sm text-muted-foreground">
          Create your first tenant to get started.
        </p>
      </div>
    );
  }

  return (
    <div className="relative">
      {isRefetching && (
        <div className="absolute inset-0 z-10 flex items-center justify-center rounded-lg bg-background/60">
          <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
        </div>
      )}
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Name</TableHead>
            <TableHead>Slug</TableHead>
            <TableHead>Status</TableHead>
            <TableHead className="text-right">Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {tenants.map((tenant) => (
            <TableRow key={tenant.id}>
              <TableCell className="font-medium">
                <Link
                  href={`/tenants/${tenant.id}`}
                  className="hover:underline"
                >
                  {tenant.name}
                </Link>
              </TableCell>
              <TableCell className="text-muted-foreground">
                {tenant.slug}
              </TableCell>
              <TableCell>
                <Badge
                  variant={
                    tenant.status === "ACTIVE" ? "default" : "secondary"
                  }
                >
                  {tenant.status}
                </Badge>
              </TableCell>
              <TableCell className="text-right">
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label={`Actions for ${tenant.name}`}
                    >
                      <MoreHorizontal className="size-4" />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem asChild>
                      <Link href={`/tenants/${tenant.id}`}>
                        <ExternalLink className="mr-2 size-4" />
                        View details
                      </Link>
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
```

- [x] **Step 3: Create Tenant list page**

Create `apps/platform/src/app/(platform)/tenants/page.tsx`:
```tsx
"use client";

import { Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { AppHeader } from "@/components/layout/app-header";
import { TenantTable } from "@/features/tenants/components/tenant-table";
import { useGetTenants } from "@/generated/tenant";

export default function TenantsPage() {
  const { data, isLoading, isRefetching } = useGetTenants();
  const tenants = data?.data?.tenants ?? [];

  return (
    <>
      <AppHeader breadcrumbs={[{ label: "Tenants" }]} />
      <div className="flex flex-1 flex-col gap-4 p-4 md:p-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">Tenants</h1>
            <p className="text-sm text-muted-foreground">
              Manage all tenants on the platform.
            </p>
          </div>
          <Button>
            <Plus className="mr-2 size-4" />
            Create Tenant
          </Button>
        </div>
        <TenantTable
          tenants={tenants}
          isLoading={isLoading}
          isRefetching={isRefetching}
        />
      </div>
    </>
  );
}
```

- [x] **Step 4: Verify page renders without build errors**

Run: `cd apps/platform && npx next build --no-lint 2>&1 | tail -20`
Expected: Build succeeds (API calls will fail at runtime without backend, that's expected)

- [x] **Step 5: Commit**

```bash
git add apps/platform/src/features/tenants/ apps/platform/src/app/(platform)/tenants/
git commit -m "feat: add tenant list page with table, skeleton, and empty state"
```

---

### Task 15: Create Tenant detail page

**Files:**
- Create: `apps/platform/src/app/(platform)/tenants/[id]/page.tsx`

- [x] **Step 1: Create tenant detail page**

Create `apps/platform/src/app/(platform)/tenants/[id]/page.tsx`:
```tsx
"use client";

import { use } from "react";
import { notFound } from "next/navigation";
import { AppHeader } from "@/components/layout/app-header";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { useGetTenantById } from "@/generated/tenant";

export default function TenantDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  const { data, isLoading, error } = useGetTenantById(id);
  const tenant = data?.data;

  if (error && !isLoading) {
    notFound();
  }

  return (
    <>
      <AppHeader
        breadcrumbs={[
          { label: "Tenants", href: "/tenants" },
          { label: isLoading ? "..." : (tenant?.name ?? "Tenant") },
        ]}
      />
      <div className="flex flex-1 flex-col gap-4 p-4 md:p-6">
        {isLoading ? (
          <Card>
            <CardHeader>
              <Skeleton className="h-6 w-48" />
            </CardHeader>
            <CardContent className="space-y-4">
              <Skeleton className="h-4 w-32" />
              <Skeleton className="h-4 w-64" />
            </CardContent>
          </Card>
        ) : tenant ? (
          <Card>
            <CardHeader>
              <div className="flex items-center gap-3">
                <CardTitle className="text-2xl">{tenant.name}</CardTitle>
                <Badge
                  variant={
                    tenant.status === "ACTIVE" ? "default" : "secondary"
                  }
                >
                  {tenant.status}
                </Badge>
              </div>
            </CardHeader>
            <CardContent>
              <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div>
                  <dt className="text-sm font-medium text-muted-foreground">
                    Slug
                  </dt>
                  <dd className="mt-1 text-sm">{tenant.slug}</dd>
                </div>
                <div>
                  <dt className="text-sm font-medium text-muted-foreground">
                    ID
                  </dt>
                  <dd className="mt-1 font-mono text-sm">{tenant.id}</dd>
                </div>
              </dl>
            </CardContent>
          </Card>
        ) : null}
      </div>
    </>
  );
}
```

- [x] **Step 2: Commit**

```bash
git add apps/platform/src/app/(platform)/tenants/[id]/
git commit -m "feat: add tenant detail page with skeleton loading"
```

---

### Task 16: Create Tenant create dialog

**Files:**
- Create: `apps/platform/src/features/tenants/schemas/create-tenant.ts`
- Create: `apps/platform/src/features/tenants/components/create-tenant-dialog.tsx`
- Modify: `apps/platform/src/app/(platform)/tenants/page.tsx`

- [x] **Step 1: Create Zod schema**

Create `apps/platform/src/features/tenants/schemas/create-tenant.ts`:
```ts
import { z } from "zod";

export const createTenantSchema = z.object({
  name: z
    .string()
    .min(1, "Name is required")
    .max(100, "Name must be 100 characters or less"),
  slug: z
    .string()
    .min(1, "Slug is required")
    .max(50, "Slug must be 50 characters or less")
    .regex(
      /^[a-z0-9]+(?:-[a-z0-9]+)*$/,
      "Slug must be lowercase, alphanumeric, with hyphens only"
    ),
  adminEmail: z
    .string()
    .min(1, "Admin email is required")
    .email("Invalid email address"),
});

export type CreateTenantInput = z.infer<typeof createTenantSchema>;
```

- [x] **Step 2: Create the dialog component**

Create `apps/platform/src/features/tenants/components/create-tenant-dialog.tsx`:
```tsx
"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { useCreateTenant } from "@/generated/tenant";
import {
  createTenantSchema,
  type CreateTenantInput,
} from "../schemas/create-tenant";

interface CreateTenantDialogProps {
  children: React.ReactNode;
}

export function CreateTenantDialog({ children }: CreateTenantDialogProps) {
  const [open, setOpen] = useState(false);
  const createTenant = useCreateTenant();

  const form = useForm<CreateTenantInput>({
    resolver: zodResolver(createTenantSchema),
    defaultValues: {
      name: "",
      slug: "",
      adminEmail: "",
    },
  });

  async function onSubmit(values: CreateTenantInput) {
    try {
      await createTenant.mutateAsync({ data: values });
      toast.success("Tenant created successfully");
      setOpen(false);
      form.reset();
    } catch (error: unknown) {
      const err = error as { data?: { errors?: Record<string, string> }; status?: number };
      if (err.status === 422 && err.data?.errors) {
        for (const [field, message] of Object.entries(err.data.errors)) {
          form.setError(field as keyof CreateTenantInput, { message });
        }
      } else {
        toast.error("Failed to create tenant. Please try again.");
      }
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Create Tenant</DialogTitle>
          <DialogDescription>
            Create a new tenant with an initial admin user.
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="name"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    Name <span className="text-destructive">*</span>
                  </FormLabel>
                  <FormControl>
                    <Input placeholder="Acme Corp" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="slug"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    Slug <span className="text-destructive">*</span>
                  </FormLabel>
                  <FormControl>
                    <Input placeholder="acme-corp" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="adminEmail"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    Admin Email <span className="text-destructive">*</span>
                  </FormLabel>
                  <FormControl>
                    <Input
                      type="email"
                      placeholder="admin@acme.com"
                      autoComplete="email"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setOpen(false)}
              >
                Cancel
              </Button>
              <Button
                type="submit"
                aria-busy={createTenant.isPending}
              >
                {createTenant.isPending && (
                  <Loader2 className="mr-2 size-4 animate-spin" />
                )}
                Create
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
```

- [x] **Step 3: Wire dialog into tenants page**

Update `apps/platform/src/app/(platform)/tenants/page.tsx` — replace the Create Tenant `<Button>` with:
```tsx
import { CreateTenantDialog } from "@/features/tenants/components/create-tenant-dialog";

// In the JSX, wrap the button:
<CreateTenantDialog>
  <Button>
    <Plus className="mr-2 size-4" />
    Create Tenant
  </Button>
</CreateTenantDialog>
```

- [x] **Step 4: Commit**

```bash
git add apps/platform/src/features/tenants/schemas/ apps/platform/src/features/tenants/components/create-tenant-dialog.tsx apps/platform/src/app/(platform)/tenants/page.tsx
git commit -m "feat: add create tenant dialog with form validation"
```

---

## Chunk 4: Workspace Shell + Self-Service Settings

### Task 17: Create workspace placeholder pages

**Files:**
- Create: `apps/platform/src/app/(workspace)/dashboard/page.tsx`
- Create: `apps/platform/src/app/(workspace)/apps/page.tsx`
- Create: `apps/platform/src/app/(workspace)/users/page.tsx`
- Create: `apps/platform/src/app/(workspace)/roles/page.tsx`
- Create: `apps/platform/src/app/(workspace)/settings/branding/page.tsx`
- Create: `apps/platform/src/app/(workspace)/settings/email/page.tsx`
- Create: `apps/platform/src/app/(workspace)/settings/domain/page.tsx`

- [x] **Step 1: Create dashboard placeholder**

Create `apps/platform/src/app/(workspace)/dashboard/page.tsx`:
```tsx
import { AppHeader } from "@/components/layout/app-header";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export default function DashboardPage() {
  return (
    <>
      <AppHeader breadcrumbs={[{ label: "Dashboard" }]} />
      <div className="flex flex-1 flex-col gap-4 p-4 md:p-6">
        <h1 className="text-2xl font-semibold tracking-tight">Dashboard</h1>
        <div className="grid gap-4 md:grid-cols-3">
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground">
                Apps
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">0</div>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground">
                Users
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">0</div>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-muted-foreground">
                Content Entries
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">0</div>
            </CardContent>
          </Card>
        </div>
      </div>
    </>
  );
}
```

- [x] **Step 2: Create remaining workspace placeholder pages**

Each placeholder follows the same pattern — AppHeader + title + empty state message.

Create `apps/platform/src/app/(workspace)/apps/page.tsx`:
```tsx
import { AppHeader } from "@/components/layout/app-header";

export default function AppsPage() {
  return (
    <>
      <AppHeader breadcrumbs={[{ label: "Apps" }]} />
      <div className="flex flex-1 flex-col gap-4 p-4 md:p-6">
        <h1 className="text-2xl font-semibold tracking-tight">Apps</h1>
        <div className="flex flex-col items-center justify-center rounded-lg border border-dashed p-12 text-center">
          <p className="text-lg font-medium">No apps yet</p>
          <p className="mt-1 text-sm text-muted-foreground">
            Create your first app to start managing content.
          </p>
        </div>
      </div>
    </>
  );
}
```

Create `apps/platform/src/app/(workspace)/users/page.tsx`:
```tsx
import { AppHeader } from "@/components/layout/app-header";

export default function UsersPage() {
  return (
    <>
      <AppHeader breadcrumbs={[{ label: "Users" }]} />
      <div className="flex flex-1 flex-col gap-4 p-4 md:p-6">
        <h1 className="text-2xl font-semibold tracking-tight">Users</h1>
        <div className="flex flex-col items-center justify-center rounded-lg border border-dashed p-12 text-center">
          <p className="text-lg font-medium">No users yet</p>
          <p className="mt-1 text-sm text-muted-foreground">
            Invite users to start collaborating.
          </p>
        </div>
      </div>
    </>
  );
}
```

Create `apps/platform/src/app/(workspace)/roles/page.tsx`:
```tsx
import { AppHeader } from "@/components/layout/app-header";

export default function RolesPage() {
  return (
    <>
      <AppHeader breadcrumbs={[{ label: "Roles" }]} />
      <div className="flex flex-1 flex-col gap-4 p-4 md:p-6">
        <h1 className="text-2xl font-semibold tracking-tight">Roles</h1>
        <div className="flex flex-col items-center justify-center rounded-lg border border-dashed p-12 text-center">
          <p className="text-lg font-medium">Roles coming soon</p>
          <p className="mt-1 text-sm text-muted-foreground">
            Role management will be available in a future update.
          </p>
        </div>
      </div>
    </>
  );
}
```

Create `apps/platform/src/app/(workspace)/settings/branding/page.tsx`:
```tsx
import { AppHeader } from "@/components/layout/app-header";

export default function BrandingSettingsPage() {
  return (
    <>
      <AppHeader
        breadcrumbs={[
          { label: "Settings" },
          { label: "Branding" },
        ]}
      />
      <div className="flex flex-1 flex-col gap-4 p-4 md:p-6">
        <h1 className="text-2xl font-semibold tracking-tight">Branding</h1>
        <p className="text-sm text-muted-foreground">
          Customize the look and feel of your workspace.
        </p>
      </div>
    </>
  );
}
```

Create `apps/platform/src/app/(workspace)/settings/email/page.tsx`:
```tsx
import { AppHeader } from "@/components/layout/app-header";

export default function EmailSettingsPage() {
  return (
    <>
      <AppHeader
        breadcrumbs={[
          { label: "Settings" },
          { label: "Email" },
        ]}
      />
      <div className="flex flex-1 flex-col gap-4 p-4 md:p-6">
        <h1 className="text-2xl font-semibold tracking-tight">
          Email Settings
        </h1>
        <p className="text-sm text-muted-foreground">
          Configure SMTP settings for transactional emails.
        </p>
      </div>
    </>
  );
}
```

Create `apps/platform/src/app/(workspace)/settings/domain/page.tsx`:
```tsx
import { AppHeader } from "@/components/layout/app-header";

export default function DomainSettingsPage() {
  return (
    <>
      <AppHeader
        breadcrumbs={[
          { label: "Settings" },
          { label: "Domain" },
        ]}
      />
      <div className="flex flex-1 flex-col gap-4 p-4 md:p-6">
        <h1 className="text-2xl font-semibold tracking-tight">
          Custom Domain
        </h1>
        <p className="text-sm text-muted-foreground">
          Configure a custom domain for your workspace.
        </p>
      </div>
    </>
  );
}
```

- [x] **Step 3: Commit**

```bash
git add apps/platform/src/app/(workspace)/
git commit -m "feat: add workspace placeholder pages (dashboard, apps, users, roles, settings)"
```

---

### Task 18: Create platform admin placeholder page

**Files:**
- Create: `apps/platform/src/app/(platform)/admins/page.tsx`
- Create: `apps/platform/src/app/(platform)/settings/page.tsx`

- [x] **Step 1: Create platform admin pages**

Create `apps/platform/src/app/(platform)/admins/page.tsx`:
```tsx
import { AppHeader } from "@/components/layout/app-header";

export default function PlatformAdminsPage() {
  return (
    <>
      <AppHeader breadcrumbs={[{ label: "Platform Admins" }]} />
      <div className="flex flex-1 flex-col gap-4 p-4 md:p-6">
        <h1 className="text-2xl font-semibold tracking-tight">
          Platform Admins
        </h1>
        <div className="flex flex-col items-center justify-center rounded-lg border border-dashed p-12 text-center">
          <p className="text-lg font-medium">Admin management coming soon</p>
          <p className="mt-1 text-sm text-muted-foreground">
            Invite and manage platform administrators.
          </p>
        </div>
      </div>
    </>
  );
}
```

Create `apps/platform/src/app/(platform)/settings/page.tsx`:
```tsx
import { AppHeader } from "@/components/layout/app-header";

export default function PlatformSettingsPage() {
  return (
    <>
      <AppHeader breadcrumbs={[{ label: "Settings" }]} />
      <div className="flex flex-1 flex-col gap-4 p-4 md:p-6">
        <h1 className="text-2xl font-semibold tracking-tight">
          System Settings
        </h1>
        <p className="text-sm text-muted-foreground">
          Platform-wide configuration options.
        </p>
      </div>
    </>
  );
}
```

- [x] **Step 2: Commit**

```bash
git add apps/platform/src/app/(platform)/admins/ apps/platform/src/app/(platform)/settings/
git commit -m "feat: add platform admin and settings placeholder pages"
```

---

## Chunk 5: Route Protection + Build Verification + E2E

### Task 19: Update route protection for new routing groups

**Files:**
- Modify: `apps/platform/src/lib/route-protection.ts`
- Test: `apps/platform/src/lib/__tests__/route-protection.test.ts`

- [x] **Step 1: Write the failing test**

Create `apps/platform/src/lib/__tests__/route-protection.test.ts`:
```ts
import { describe, it, expect } from "vitest";
import { isProtectedPath, isPublicPath } from "../route-protection";

describe("route-protection", () => {
  describe("isPublicPath", () => {
    it("returns true for auth API routes", () => {
      expect(isPublicPath("/api/auth/callback")).toBe(true);
      expect(isPublicPath("/api/auth/signin")).toBe(true);
    });

    it("returns true for signin page", () => {
      expect(isPublicPath("/signin")).toBe(true);
    });

    it("returns true for static assets", () => {
      expect(isPublicPath("/_next/static/chunk.js")).toBe(true);
      expect(isPublicPath("/favicon.ico")).toBe(true);
    });

    it("returns false for protected routes", () => {
      expect(isPublicPath("/tenants")).toBe(false);
      expect(isPublicPath("/dashboard")).toBe(false);
    });
  });

  describe("isProtectedPath", () => {
    it("returns true for platform routes", () => {
      expect(isProtectedPath("/tenants")).toBe(true);
      expect(isProtectedPath("/tenants/123")).toBe(true);
      expect(isProtectedPath("/admins")).toBe(true);
    });

    it("returns true for workspace routes", () => {
      expect(isProtectedPath("/dashboard")).toBe(true);
      expect(isProtectedPath("/apps")).toBe(true);
      expect(isProtectedPath("/apps/my-app/content")).toBe(true);
      expect(isProtectedPath("/users")).toBe(true);
      expect(isProtectedPath("/roles")).toBe(true);
      expect(isProtectedPath("/settings/branding")).toBe(true);
      expect(isProtectedPath("/settings/email")).toBe(true);
      expect(isProtectedPath("/settings/domain")).toBe(true);
    });

    it("returns false for public routes", () => {
      expect(isProtectedPath("/signin")).toBe(false);
      expect(isProtectedPath("/api/auth/callback")).toBe(false);
    });
  });
});
```

- [x] **Step 2: Run test to verify it fails**

Run: `cd apps/platform && npx vitest run src/lib/__tests__/route-protection.test.ts`
Expected: FAIL — some assertions fail because the old protected prefixes are too narrow

- [x] **Step 3: Update route-protection.ts**

Update `apps/platform/src/lib/route-protection.ts`:
```ts
const PROTECTED_PREFIXES = [
  // Platform Admin
  "/tenants",
  "/admins",
  // Workspace
  "/dashboard",
  "/apps",
  "/users",
  "/roles",
  "/settings",
];

const PUBLIC_PREFIXES = ["/api/auth", "/signin", "/_next", "/favicon"];

export function isProtectedPath(pathname: string): boolean {
  return PROTECTED_PREFIXES.some((prefix) => pathname.startsWith(prefix));
}

export function isPublicPath(pathname: string): boolean {
  return PUBLIC_PREFIXES.some((prefix) => pathname.startsWith(prefix));
}
```

- [x] **Step 4: Run test to verify it passes**

Run: `cd apps/platform && npx vitest run src/lib/__tests__/route-protection.test.ts`
Expected: PASS (all assertions)

- [x] **Step 5: Commit**

```bash
git add apps/platform/src/lib/route-protection.ts apps/platform/src/lib/__tests__/route-protection.test.ts
git commit -m "feat: update route protection for new routing groups"
```

---

### Task 20: Full build verification

- [x] **Step 1: Run all unit tests**

Run: `cd apps/platform && npx vitest run`
Expected: All tests pass

- [x] **Step 2: Run build**

Run: `cd apps/platform && npx next build`
Expected: Build succeeds without errors

- [x] **Step 3: Run lint**

Run: `cd apps/platform && npx eslint src/ --max-warnings=0`
Expected: No errors (warnings acceptable for now)

- [x] **Step 4: Verify pages are accessible**

Run: `cd apps/platform && npx next start &` (start server in background)
Then check manually or with curl:
```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:3000/
```
Expected: 302 (redirect to signin)

Stop the server after verification.

- [x] **Step 5: Commit any fixes if needed**

If any issues were found and fixed:
```bash
git add -A
git commit -m "fix: resolve build verification issues"
```

---

### Task 21: Update PROGRESS.md and memory

**Files:**
- Modify: `docs/PROGRESS.md`
- Modify: `memory/MEMORY.md` (if needed)

- [x] **Step 1: Update PROGRESS.md**

Add a new section to `docs/PROGRESS.md` documenting Phase 2e (Frontend Redesign) completion:
- Clean slate: old frontend deleted
- Foundation: shadcn/ui init, Indigo+Emerald+Stone theme, providers, root layout
- App Shell: Sidebar with nav config, header with breadcrumbs, routing groups
- Platform Admin: Tenant list (table + skeleton + empty state), detail page, create dialog
- Workspace: All placeholder pages (dashboard, apps, users, roles, settings)
- Auth: useAuth hook, route protection, signin page
- Tests: useAuth, nav-config, route-protection unit tests passing
- Build: Verified clean

- [x] **Step 2: Commit**

```bash
git add docs/PROGRESS.md
git commit -m "docs: update PROGRESS.md with frontend redesign status"
```

---

## Summary

| Chunk | Tasks | Description |
|---|---|---|
| 1 | 1-6 | Clean slate, shadcn init, theme, providers, root layout, Orval |
| 2 | 7-13 | useAuth hook, nav config, sidebar, header, routing group layouts |
| 3 | 14-16 | Tenant list, detail, create dialog (full CRUD against existing backend) |
| 4 | 17-18 | Workspace + platform placeholder pages |
| 5 | 19-21 | Route protection, build verification, docs |

Total: 21 tasks, ~15 commits
