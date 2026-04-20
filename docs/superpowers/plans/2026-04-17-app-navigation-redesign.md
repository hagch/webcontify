# App Navigation Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the 5-tab app detail page with a sidebar-based context-switch navigation, and refactor entry editing from dialog to full-page form.

**Architecture:** When a user navigates into an app (`/apps/[appSlug]/*`), the workspace sidebar is replaced by an app-scoped sidebar with sections (Schema, Data, Config). Each former tab becomes its own route page. Entry create/edit becomes a full-page form instead of a dialog. Entry history moves to a Sheet component.

**Tech Stack:** Next.js 16, React 19, shadcn/ui sidebar components, React Hook Form + Zod, next-intl, Vitest + Testing Library

**Spec:** `docs/plans/2026-04-17-app-navigation-redesign.md`

---

## Task 1: Add i18n Keys for App Sidebar Navigation

**Files:**
- Modify: `apps/platform/messages/en.json`
- Modify: `apps/platform/messages/de.json`

- [ ] **Step 1: Add English i18n keys**

Add a new `appSidebar` section inside the `nav` object in `apps/platform/messages/en.json`:

```json
"appSidebar": {
  "backToApps": "Apps",
  "schema": "Schema",
  "definitions": "Definitions",
  "dependencies": "Dependencies",
  "data": "Data",
  "dataLive": "Data (Live)",
  "publishFirst": "Publish the app to manage entries",
  "pages": "Pages",
  "workflows": "Workflows",
  "config": "Config",
  "overview": "Overview",
  "versions": "Versions",
  "revisions": "Revisions"
}
```

Also add new entry page keys inside the `entries` object:

```json
"page": {
  "new": "New Entry",
  "save": "Save",
  "saving": "Saving...",
  "saved": "Entry saved",
  "history": "History",
  "delete": "Delete",
  "systemFields": "System Information",
  "unsavedChanges": "You have unsaved changes"
}
```

- [ ] **Step 2: Add German i18n keys**

Add the same sections in `apps/platform/messages/de.json`:

```json
"appSidebar": {
  "backToApps": "Apps",
  "schema": "Schema",
  "definitions": "Definitionen",
  "dependencies": "Abhaengigkeiten",
  "data": "Daten",
  "dataLive": "Daten (Live)",
  "publishFirst": "App veroeffentlichen um Eintraege zu verwalten",
  "pages": "Seiten",
  "workflows": "Workflows",
  "config": "Konfiguration",
  "overview": "Uebersicht",
  "versions": "Versionen",
  "revisions": "Revisionen"
}
```

And German entry page keys:

```json
"page": {
  "new": "Neuer Eintrag",
  "save": "Speichern",
  "saving": "Speichert...",
  "saved": "Eintrag gespeichert",
  "history": "Verlauf",
  "delete": "Loeschen",
  "systemFields": "Systeminformationen",
  "unsavedChanges": "Ungespeicherte Aenderungen"
}
```

- [ ] **Step 3: Commit**

```bash
git add apps/platform/messages/en.json apps/platform/messages/de.json
git commit -m "feat(i18n): add app sidebar and entry page translation keys"
```

---

## Task 2: Create App Nav Config (TDD)

**Files:**
- Create: `apps/platform/src/components/layout/__tests__/app-nav-config.test.ts`
- Create: `apps/platform/src/components/layout/app-nav-config.ts`

- [ ] **Step 1: Write the failing test**

Create `apps/platform/src/components/layout/__tests__/app-nav-config.test.ts`:

```typescript
import { describe, it, expect } from "vitest";
import { getAppNavSections } from "../app-nav-config";

const t = (key: string) => key;

describe("getAppNavSections", () => {
  it("returns schema section with definitions and dependencies", () => {
    const sections = getAppNavSections({ appSlug: "shop", definitions: [], hasPublishedVersion: true }, t);
    const schema = sections.find((s) => s.label === "appSidebar.schema");
    expect(schema).toBeDefined();
    expect(schema!.items).toHaveLength(2);
    expect(schema!.items[0].href).toBe("/apps/shop/definitions");
    expect(schema!.items[1].href).toBe("/apps/shop/dependencies");
  });

  it("returns data section with definition sub-items when published", () => {
    const sections = getAppNavSections({
      appSlug: "shop",
      definitions: [
        { slug: "products", name: "Products", entryCount: 24 },
        { slug: "orders", name: "Orders", entryCount: 156 },
      ],
      hasPublishedVersion: true,
    }, t);
    const data = sections.find((s) => s.label === "appSidebar.data");
    expect(data).toBeDefined();
    expect(data!.items).toHaveLength(2);
    expect(data!.items[0].href).toBe("/apps/shop/data/products");
    expect(data!.items[0].label).toBe("Products");
    expect(data!.items[0].badge).toBe("24");
    expect(data!.items[1].href).toBe("/apps/shop/data/orders");
  });

  it("returns empty data section when not published", () => {
    const sections = getAppNavSections({
      appSlug: "shop",
      definitions: [],
      hasPublishedVersion: false,
    }, t);
    const data = sections.find((s) => s.label === "appSidebar.data");
    expect(data).toBeDefined();
    expect(data!.items).toHaveLength(0);
    expect(data!.disabledHint).toBe("appSidebar.publishFirst");
  });

  it("returns config section with overview, versions, revisions", () => {
    const sections = getAppNavSections({ appSlug: "shop", definitions: [], hasPublishedVersion: true }, t);
    const config = sections.find((s) => s.label === "appSidebar.config");
    expect(config).toBeDefined();
    expect(config!.items).toHaveLength(3);
    expect(config!.items[0].href).toBe("/apps/shop/overview");
    expect(config!.items[1].href).toBe("/apps/shop/versions");
    expect(config!.items[2].href).toBe("/apps/shop/revisions");
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/platform && pnpm vitest run src/components/layout/__tests__/app-nav-config.test.ts`
Expected: FAIL — module not found

- [ ] **Step 3: Write minimal implementation**

Create `apps/platform/src/components/layout/app-nav-config.ts`:

```typescript
import {
  Grid3X3,
  GitBranch,
  Database,
  Settings,
  Info,
  History,
  FileStack,
  type LucideIcon,
} from "lucide-react";

export interface AppNavItem {
  label: string;
  href: string;
  icon: LucideIcon;
  testId: string;
  badge?: string;
}

export interface AppNavSection {
  label: string;
  items: AppNavItem[];
  disabledHint?: string;
}

export interface ContentDefinitionSummary {
  slug: string;
  name: string;
  entryCount?: number;
}

export interface AppNavOptions {
  appSlug: string;
  definitions: ContentDefinitionSummary[];
  hasPublishedVersion: boolean;
}

type TranslationFn = (key: string) => string;

export function getAppNavSections(
  options: AppNavOptions,
  t: TranslationFn
): AppNavSection[] {
  const { appSlug, definitions, hasPublishedVersion } = options;
  const base = `/apps/${appSlug}`;

  const sections: AppNavSection[] = [
    {
      label: t("appSidebar.schema"),
      items: [
        { label: t("appSidebar.definitions"), href: `${base}/definitions`, icon: Grid3X3, testId: "nav-definitions" },
        { label: t("appSidebar.dependencies"), href: `${base}/dependencies`, icon: GitBranch, testId: "nav-dependencies" },
      ],
    },
  ];

  if (hasPublishedVersion) {
    sections.push({
      label: t("appSidebar.data"),
      items: definitions.map((def) => ({
        label: def.name,
        href: `${base}/data/${def.slug}`,
        icon: Database,
        testId: `nav-data-${def.slug}`,
        badge: def.entryCount != null ? String(def.entryCount) : undefined,
      })),
    });
  } else {
    sections.push({
      label: t("appSidebar.data"),
      items: [],
      disabledHint: t("appSidebar.publishFirst"),
    });
  }

  sections.push({
    label: t("appSidebar.config"),
    items: [
      { label: t("appSidebar.overview"), href: `${base}/overview`, icon: Info, testId: "nav-overview" },
      { label: t("appSidebar.versions"), href: `${base}/versions`, icon: FileStack, testId: "nav-versions" },
      { label: t("appSidebar.revisions"), href: `${base}/revisions`, icon: History, testId: "nav-revisions" },
    ],
  });

  return sections;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd apps/platform && pnpm vitest run src/components/layout/__tests__/app-nav-config.test.ts`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add apps/platform/src/components/layout/__tests__/app-nav-config.test.ts apps/platform/src/components/layout/app-nav-config.ts
git commit -m "feat(nav): add app-scoped navigation config with tests"
```

---

## Task 3: Create App Sidebar Component

**Files:**
- Create: `apps/platform/src/components/layout/app-sidebar-nav.tsx`

This is the app-scoped sidebar that replaces the workspace sidebar when inside an app. It renders the sections from `getAppNavSections` and includes the back-to-apps link and app picker.

- [ ] **Step 1: Create the app sidebar component**

Create `apps/platform/src/components/layout/app-sidebar-nav.tsx`:

```typescript
"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { ChevronLeft } from "lucide-react";
import { useTranslations } from "next-intl";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuBadge,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
  SidebarSeparator,
} from "@/components/ui/sidebar";
import { NavUser } from "./nav-user";
import type { AppNavSection } from "./app-nav-config";

interface AppSidebarNavProps {
  appName: string;
  appSlug: string;
  sections: AppNavSection[];
}

export function AppSidebarNav({ appName, appSlug, sections }: AppSidebarNavProps) {
  const pathname = usePathname();
  const t = useTranslations("nav");

  return (
    <Sidebar collapsible="icon">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              size="sm"
              render={<Link href="/apps" />}
              tooltip={t("appSidebar.backToApps")}
            >
              <ChevronLeft />
              <span>{t("appSidebar.backToApps")}</span>
            </SidebarMenuButton>
          </SidebarMenuItem>
          <SidebarMenuItem>
            <SidebarMenuButton size="lg" render={<Link href={`/apps/${appSlug}/definitions`} />}>
              <div className="flex aspect-square size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
                <span className="text-sm font-bold">{appName.charAt(0).toUpperCase()}</span>
              </div>
              <div className="grid flex-1 text-left text-sm leading-tight">
                <span className="truncate font-semibold">{appName}</span>
              </div>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        {sections.map((section, index) => (
          <div key={section.label}>
            {index > 0 && <SidebarSeparator />}
            <SidebarGroup>
              <SidebarGroupLabel>{section.label}</SidebarGroupLabel>
              {section.disabledHint && section.items.length === 0 ? (
                <p className="px-2 py-1 text-xs text-muted-foreground">
                  {section.disabledHint}
                </p>
              ) : (
                <SidebarMenu>
                  {section.items.map((item) => {
                    const isActive = pathname === item.href || pathname.startsWith(item.href + "/");
                    return (
                      <SidebarMenuItem key={item.href}>
                        <SidebarMenuButton
                          render={<Link href={item.href} data-testid={item.testId} />}
                          isActive={isActive}
                          tooltip={item.label}
                        >
                          <item.icon />
                          <span>{item.label}</span>
                        </SidebarMenuButton>
                        {item.badge && (
                          <SidebarMenuBadge>{item.badge}</SidebarMenuBadge>
                        )}
                      </SidebarMenuItem>
                    );
                  })}
                </SidebarMenu>
              )}
            </SidebarGroup>
          </div>
        ))}
      </SidebarContent>
      <SidebarFooter>
        <NavUser />
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/platform/src/components/layout/app-sidebar-nav.tsx
git commit -m "feat(nav): add app-scoped sidebar component"
```

---

## Task 4: Create App Layout with Sidebar Context Switch

**Files:**
- Create: `apps/platform/src/app/(workspace)/apps/[appSlug]/layout.tsx`
- Create: `apps/platform/src/app/(workspace)/apps/[appSlug]/app-layout-client.tsx`

This layout replaces the workspace sidebar with the app sidebar for all routes under `/apps/[appSlug]/*`.

- [ ] **Step 1: Create the app layout client component**

Create `apps/platform/src/app/(workspace)/apps/[appSlug]/app-layout-client.tsx`:

```typescript
"use client";

import { useTranslations } from "next-intl";
import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar";
import { AppSidebarNav } from "@/components/layout/app-sidebar-nav";
import { getAppNavSections } from "@/components/layout/app-nav-config";
import type { ContentDefinitionSummary } from "@/components/layout/app-nav-config";

interface AppLayoutClientProps {
  children: React.ReactNode;
  appName: string;
  appSlug: string;
  definitions: ContentDefinitionSummary[];
  hasPublishedVersion: boolean;
}

export function AppLayoutClient({
  children,
  appName,
  appSlug,
  definitions,
  hasPublishedVersion,
}: AppLayoutClientProps) {
  const t = useTranslations("nav");

  const sections = getAppNavSections(
    { appSlug, definitions, hasPublishedVersion },
    t
  );

  return (
    <SidebarProvider>
      <AppSidebarNav appName={appName} appSlug={appSlug} sections={sections} />
      <SidebarInset>
        <main id="main-content">{children}</main>
      </SidebarInset>
    </SidebarProvider>
  );
}
```

- [ ] **Step 2: Create the app layout server component**

Create `apps/platform/src/app/(workspace)/apps/[appSlug]/layout.tsx`:

```typescript
import { AppLayoutClient } from "./app-layout-client";
import type { ContentDefinitionSummary } from "@/components/layout/app-nav-config";

// This is a server-side passthrough layout.
// Data fetching for nav items happens client-side in the AppLayoutClient
// via React Query hooks to stay in sync with mutations.
// We pass minimal props; the client component fetches the rest.

export default async function AppLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ appSlug: string }>;
}) {
  const { appSlug } = await params;

  return (
    <AppLayoutClientWrapper appSlug={appSlug}>
      {children}
    </AppLayoutClientWrapper>
  );
}

// Client wrapper that fetches app data for sidebar
"use client";
import { useGetApp, type AppResponse } from "@/generated/app";
import { useListContentDefinitions } from "@/generated/content";

function AppLayoutClientWrapper({
  appSlug,
  children,
}: {
  appSlug: string;
  children: React.ReactNode;
}) {
  const { data: appData } = useGetApp(appSlug);
  const { data: defsData } = useListContentDefinitions(appSlug);

  const app = appData?.data as AppResponse | undefined;
  const appName = app?.name ?? appSlug;
  const hasPublishedVersion = app?.status === "ACTIVE" || app?.status === "INACTIVE";

  const definitions: ContentDefinitionSummary[] = (defsData?.data?.items ?? []).map(
    (d: { slug: string; name: string }) => ({
      slug: d.slug,
      name: d.name,
    })
  );

  return (
    <AppLayoutClient
      appName={appName}
      appSlug={appSlug}
      definitions={definitions}
      hasPublishedVersion={hasPublishedVersion}
    >
      {children}
    </AppLayoutClient>
  );
}
```

**Important:** The layout nests inside the workspace layout. Since the workspace layout already renders a `<SidebarProvider>` + `<AppSidebar>`, we need the app layout to **replace** the workspace sidebar. This requires restructuring the workspace layout to NOT render a sidebar when inside an app route.

- [ ] **Step 3: Modify workspace layout to skip sidebar for app detail routes**

Modify `apps/platform/src/app/(workspace)/workspace-layout-client.tsx` to detect when we're inside an app detail page and skip the workspace sidebar:

The app layout will provide its own `SidebarProvider` and sidebar. The workspace layout should only render the sidebar for non-app-detail routes. We achieve this by moving the `/apps/[appSlug]/*` routes to render their own sidebar via the nested layout.

Update `apps/platform/src/app/(workspace)/workspace-layout-client.tsx`:

```typescript
"use client";

import { useTranslations } from "next-intl";
import { usePathname } from "next/navigation";
import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar";
import { AppSidebar } from "@/components/layout/app-sidebar";
import { getWorkspaceNavItems } from "@/components/layout/nav-config";
import { usePermissions } from "@/hooks/use-permissions";
import { BrandingProvider, type ServerTenantConfig } from "@/providers/branding-provider";

interface WorkspaceLayoutClientProps {
  children: React.ReactNode;
  initialConfig: ServerTenantConfig | null;
}

export function WorkspaceLayoutClient({ children, initialConfig }: WorkspaceLayoutClientProps) {
  const t = useTranslations("nav");
  const pathname = usePathname();
  const { canAccess, isLoading } = usePermissions();

  // Check if we're inside an app detail page (has its own sidebar)
  const isAppDetailRoute = /^\/apps\/[^/]+\/.+/.test(pathname);

  const sections = getWorkspaceNavItems(
    {
      canAccessUserManagement: canAccess("user_management"),
      canAccessRoleManagement: canAccess("role_management"),
      canAccessTenantConfig: canAccess("tenant_config"),
      canAccessApps: canAccess("app_management"),
    },
    t
  );

  return (
    <BrandingProvider initialConfig={initialConfig}>
      {isAppDetailRoute ? (
        // App detail routes provide their own sidebar via app layout
        <>{children}</>
      ) : (
        <SidebarProvider>
          <AppSidebar sections={sections} isLoading={isLoading} />
          <SidebarInset>
            <main id="main-content">{children}</main>
          </SidebarInset>
        </SidebarProvider>
      )}
    </BrandingProvider>
  );
}
```

- [ ] **Step 4: Verify the build compiles**

Run: `cd apps/platform && npx next build 2>&1 | head -30`
Expected: No TypeScript errors (build may not fully complete without all route pages, but no TS errors)

- [ ] **Step 5: Commit**

```bash
git add apps/platform/src/app/(workspace)/apps/[appSlug]/layout.tsx apps/platform/src/app/(workspace)/apps/[appSlug]/app-layout-client.tsx apps/platform/src/app/(workspace)/workspace-layout-client.tsx
git commit -m "feat(nav): add app-scoped layout with sidebar context switch"
```

---

## Task 5: Create Route Pages (Extract from Tabs)

**Files:**
- Create: `apps/platform/src/app/(workspace)/apps/[appSlug]/definitions/page.tsx`
- Create: `apps/platform/src/app/(workspace)/apps/[appSlug]/dependencies/page.tsx`
- Create: `apps/platform/src/app/(workspace)/apps/[appSlug]/overview/page.tsx`
- Create: `apps/platform/src/app/(workspace)/apps/[appSlug]/versions/page.tsx`
- Create: `apps/platform/src/app/(workspace)/apps/[appSlug]/revisions/page.tsx`
- Modify: `apps/platform/src/app/(workspace)/apps/[appSlug]/page.tsx` (redirect)

Each page extracts content from the current tab-based detail page (`apps/[appSlug]/page.tsx`).

- [ ] **Step 1: Create definitions page (graph canvas)**

Create `apps/platform/src/app/(workspace)/apps/[appSlug]/definitions/page.tsx`:

```typescript
"use client";

import { use } from "react";
import { notFound } from "next/navigation";
import { useTranslations } from "next-intl";
import { AppHeader } from "@/components/layout/app-header";
import { Skeleton } from "@/components/ui/skeleton";
import { useGetApp, type AppResponse } from "@/generated/app";
import { ContentDefinitionGraph } from "@/features/content/components/content-definition-graph";
import type { AppResponseEffectiveActionsItem } from "@/generated/app";

export default function DefinitionsPage({
  params,
}: {
  params: Promise<{ appSlug: string }>;
}) {
  const { appSlug } = use(params);
  const t = useTranslations("apps");
  const { data, isLoading, error } = useGetApp(appSlug);

  const app = data?.data as AppResponse | undefined;
  const canEdit = (app?.effectiveActions ?? []).includes("editor" as AppResponseEffectiveActionsItem);

  if (error && !isLoading) {
    notFound();
  }

  return (
    <>
      <AppHeader
        mode="detail"
        breadcrumbs={[
          { label: isLoading ? "..." : (app?.name ?? appSlug) },
        ]}
      />
      <div className="flex flex-1 flex-col p-4 md:p-6">
        {isLoading ? (
          <Skeleton className="h-[calc(100vh-9rem)] w-full" />
        ) : app ? (
          <div className="h-[calc(100vh-9rem)]">
            <ContentDefinitionGraph appSlug={app.slug} appId={app.id} canEdit={canEdit} />
          </div>
        ) : null}
      </div>
    </>
  );
}
```

- [ ] **Step 2: Create dependencies page**

Create `apps/platform/src/app/(workspace)/apps/[appSlug]/dependencies/page.tsx`:

```typescript
"use client";

import { use } from "react";
import { notFound } from "next/navigation";
import { useTranslations } from "next-intl";
import { AppHeader } from "@/components/layout/app-header";
import { Skeleton } from "@/components/ui/skeleton";
import { useGetApp, type AppResponse } from "@/generated/app";
import { AppDependencyGraph } from "@/features/apps/components/app-dependency-graph";

export default function DependenciesPage({
  params,
}: {
  params: Promise<{ appSlug: string }>;
}) {
  const { appSlug } = use(params);
  const t = useTranslations("apps");
  const { data, isLoading, error } = useGetApp(appSlug);

  const app = data?.data as AppResponse | undefined;

  if (error && !isLoading) {
    notFound();
  }

  return (
    <>
      <AppHeader
        mode="detail"
        breadcrumbs={[
          { label: isLoading ? "..." : (app?.name ?? appSlug) },
        ]}
      />
      <div className="flex flex-1 flex-col p-4 md:p-6">
        {isLoading ? (
          <Skeleton className="h-[calc(100vh-9rem)] w-full" />
        ) : app ? (
          <div className="h-[calc(100vh-9rem)]">
            <AppDependencyGraph appId={app.id} />
          </div>
        ) : null}
      </div>
    </>
  );
}
```

- [ ] **Step 3: Create overview page**

Create `apps/platform/src/app/(workspace)/apps/[appSlug]/overview/page.tsx`:

Extract the overview tab content + admin actions from the old detail page. This is the largest extraction since it includes the edit form and status/delete dialogs.

```typescript
"use client";

import { use, useState } from "react";
import { notFound, useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { Loader2, Pencil, X } from "lucide-react";
import { toast } from "sonner";
import { useQueryClient } from "@tanstack/react-query";
import { AppHeader } from "@/components/layout/app-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  useGetApp,
  useUpdateApp,
  useUpdateAppStatus,
  useDeleteApp,
  getListAppsQueryKey,
  getGetAppQueryKey,
  type AppResponse,
} from "@/generated/app";
import { useListAppProblems } from "@/generated/app";
import { PublishAppDialog } from "@/features/apps/components/publish-app-dialog";
import { AppProblemsPanel } from "@/features/apps/components/app-problems-panel";
import type { AppResponseEffectiveActionsItem } from "@/generated/app";

function formatDate(dateString: string): string {
  return new Intl.DateTimeFormat("de-DE", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(dateString));
}

function statusBadgeVariant(status: string): "default" | "outline" | "secondary" {
  switch (status) {
    case "DRAFT": return "outline";
    case "ACTIVE": return "default";
    case "INACTIVE": return "secondary";
    default: return "outline";
  }
}

export default function OverviewPage({
  params,
}: {
  params: Promise<{ appSlug: string }>;
}) {
  const { appSlug } = use(params);
  const router = useRouter();
  const t = useTranslations("apps");
  const queryClient = useQueryClient();
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [statusDialogOpen, setStatusDialogOpen] = useState(false);
  const [pendingStatus, setPendingStatus] = useState<"ACTIVE" | "INACTIVE" | null>(null);
  const [editing, setEditing] = useState(false);
  const [editName, setEditName] = useState("");
  const [editDescription, setEditDescription] = useState("");

  const { data, isLoading, error } = useGetApp(appSlug);
  const updateApp = useUpdateApp();
  const updateStatus = useUpdateAppStatus();
  const deleteApp = useDeleteApp();
  const { data: problemsData } = useListAppProblems(appSlug);
  const problemsResponse = problemsData?.data;
  const hasErrors = problemsResponse && "errorCount" in problemsResponse
    ? problemsResponse.errorCount > 0
    : false;

  const app = data?.data as AppResponse | undefined;
  const actions = app?.effectiveActions ?? [];
  const canEdit = actions.includes("editor" as AppResponseEffectiveActionsItem);
  const canAdmin = actions.includes("admin" as AppResponseEffectiveActionsItem);

  if (error && !isLoading) {
    notFound();
  }

  function invalidateQueries() {
    queryClient.invalidateQueries({ queryKey: getListAppsQueryKey() });
    queryClient.invalidateQueries({ queryKey: getGetAppQueryKey(appSlug) });
  }

  function startEditing() {
    if (!app) return;
    setEditName(app.name);
    setEditDescription(app.description ?? "");
    setEditing(true);
  }

  function cancelEditing() {
    setEditing(false);
  }

  function saveEdit() {
    if (!editName.trim()) return;
    updateApp.mutate(
      { appId: app!.id, data: { name: editName.trim(), description: editDescription.trim() || undefined } },
      {
        onSuccess: () => {
          invalidateQueries();
          setEditing(false);
          toast.success(t("detail.editSuccess"));
        },
        onError: (error) => {
          const err = error as { data?: { code?: string } };
          if (err.data?.code === "app.name_already_exists") {
            toast.error(t("createDialog.nameAlreadyExists"));
          } else {
            toast.error(t("detail.editError"));
          }
        },
      }
    );
  }

  function handleStatusChange(newStatus: "ACTIVE" | "INACTIVE") {
    setPendingStatus(newStatus);
    setStatusDialogOpen(true);
  }

  function confirmStatusChange() {
    if (!pendingStatus) return;
    updateStatus.mutate(
      { appId: app!.id, data: { status: pendingStatus } },
      {
        onSuccess: () => {
          invalidateQueries();
          setStatusDialogOpen(false);
          setPendingStatus(null);
          toast.success(t("detail.statusChangeSuccess"));
        },
        onError: () => {
          toast.error(t("detail.statusChangeError"));
        },
      }
    );
  }

  function handleDelete() {
    deleteApp.mutate(
      { appId: app!.id },
      {
        onSuccess: () => {
          queryClient.invalidateQueries({ queryKey: getListAppsQueryKey() });
          setDeleteDialogOpen(false);
          toast.success(t("detail.deleteSuccess"));
          router.push("/apps");
        },
        onError: () => {
          toast.error(t("detail.deleteError"));
        },
      }
    );
  }

  return (
    <>
      <AppHeader
        mode="detail"
        breadcrumbs={[
          { label: isLoading ? "..." : (app?.name ?? appSlug) },
        ]}
        actions={
          app && !isLoading && canAdmin ? (
            (app.status === "DRAFT" || app.hasDraft) ? (
              hasErrors ? (
                <Button size="sm" disabled title={t("problems.publishBlocked")}>
                  {t("versions.publish")}
                </Button>
              ) : (
                <PublishAppDialog appId={app.id} appSlug={app.slug}>
                  <Button size="sm">{t("versions.publish")}</Button>
                </PublishAppDialog>
              )
            ) : undefined
          ) : undefined
        }
      />
      <div data-testid="app-overview-page" className="flex flex-1 flex-col gap-6 p-4 md:p-6">
        {isLoading ? (
          <Skeleton className="h-64 w-full" />
        ) : app ? (
          <>
            <AppProblemsPanel appId={app.id} />

            {/* Overview */}
            <section className="rounded-lg border p-6">
              <div className="mb-4 flex items-center justify-between">
                <h2 className="text-sm font-medium text-muted-foreground uppercase tracking-wider">
                  {t("detail.overviewSection")}
                </h2>
                {!editing ? (
                  canEdit && (
                    <Button variant="ghost" size="sm" onClick={startEditing}>
                      <Pencil className="mr-1.5 size-3.5" />
                      {t("edit")}
                    </Button>
                  )
                ) : (
                  <div className="flex gap-2">
                    <Button variant="ghost" size="sm" onClick={cancelEditing} disabled={updateApp.isPending}>
                      <X className="mr-1.5 size-3.5" />
                      {t("detail.cancelEdit")}
                    </Button>
                    <Button size="sm" onClick={saveEdit} disabled={updateApp.isPending || !editName.trim()}>
                      {updateApp.isPending && <Loader2 className="mr-1.5 size-3.5 animate-spin" />}
                      {t("detail.saveEdit")}
                    </Button>
                  </div>
                )}
              </div>
              <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                {editing && (
                  <div className="sm:col-span-2">
                    <dt className="text-sm text-muted-foreground mb-1">{t("name")}</dt>
                    <dd>
                      <Input value={editName} onChange={(e) => setEditName(e.target.value)} maxLength={255} />
                    </dd>
                  </div>
                )}
                <div className={editing ? "sm:col-span-2" : ""}>
                  <dt className="text-sm text-muted-foreground">{t("detail.descriptionLabel")}</dt>
                  <dd className="mt-1">
                    {editing ? (
                      <Textarea value={editDescription} onChange={(e) => setEditDescription(e.target.value)} maxLength={2000} rows={3} />
                    ) : (
                      <span className="font-medium">{app.description || t("detail.noDescription")}</span>
                    )}
                  </dd>
                </div>
                <div>
                  <dt className="text-sm text-muted-foreground">{t("detail.slugLabel")}</dt>
                  <dd className="mt-1 font-mono text-sm">{app.slug}</dd>
                </div>
                <div>
                  <dt className="text-sm text-muted-foreground">{t("detail.statusLabel")}</dt>
                  <dd className="mt-1">
                    <Badge variant={statusBadgeVariant(app.status)}>{t(`statusValues.${app.status}`)}</Badge>
                  </dd>
                </div>
                <div>
                  <dt className="text-sm text-muted-foreground">{t("detail.versionLabel")}</dt>
                  <dd className="mt-1 font-mono text-sm">{app.currentVersion ?? t("detail.noVersion")}</dd>
                </div>
                <div>
                  <dt className="text-sm text-muted-foreground">{t("detail.createdAtLabel")}</dt>
                  <dd className="mt-1 text-sm">{formatDate(app.createdAt)}</dd>
                </div>
                <div>
                  <dt className="text-sm text-muted-foreground">{t("detail.updatedAtLabel")}</dt>
                  <dd className="mt-1 text-sm">{formatDate(app.updatedAt)}</dd>
                </div>
              </dl>
            </section>

            {/* Actions — only for admins */}
            {canAdmin && (
              <section className="rounded-lg border p-6">
                <h2 className="mb-4 text-sm font-medium text-muted-foreground uppercase tracking-wider">
                  {t("detail.actionsSection")}
                </h2>
                <div className="flex flex-wrap gap-3">
                  {app.status === "INACTIVE" && (
                    <Button variant="outline" onClick={() => handleStatusChange("ACTIVE")} aria-busy={updateStatus.isPending} disabled={updateStatus.isPending}>
                      {updateStatus.isPending && <Loader2 className="mr-2 size-4 animate-spin" />}
                      {t("detail.activate")}
                    </Button>
                  )}
                  {app.status === "ACTIVE" && (
                    <Button variant="outline" onClick={() => handleStatusChange("INACTIVE")} aria-busy={updateStatus.isPending} disabled={updateStatus.isPending}>
                      {updateStatus.isPending && <Loader2 className="mr-2 size-4 animate-spin" />}
                      {t("detail.deactivate")}
                    </Button>
                  )}
                  <Button variant="destructive" onClick={() => setDeleteDialogOpen(true)}>
                    {t("detail.delete")}
                  </Button>
                </div>
              </section>
            )}
          </>
        ) : null}
      </div>

      {/* Status change confirmation dialog */}
      <Dialog open={statusDialogOpen} onOpenChange={setStatusDialogOpen}>
        <DialogContent showCloseButton={false}>
          <DialogHeader>
            <DialogTitle>
              {pendingStatus === "ACTIVE" ? t("detail.statusChangeConfirm.activateTitle") : t("detail.statusChangeConfirm.deactivateTitle")}
            </DialogTitle>
            <DialogDescription>
              {pendingStatus === "ACTIVE" ? t("detail.statusChangeConfirm.activateDescription") : t("detail.statusChangeConfirm.deactivateDescription")}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => { setStatusDialogOpen(false); setPendingStatus(null); }} disabled={updateStatus.isPending}>
              {t("detail.statusChangeConfirm.cancel")}
            </Button>
            <Button type="button" onClick={confirmStatusChange} aria-busy={updateStatus.isPending} disabled={updateStatus.isPending}>
              {updateStatus.isPending && <Loader2 className="mr-2 size-4 animate-spin" />}
              {t("detail.statusChangeConfirm.confirm")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete confirmation dialog */}
      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent showCloseButton={false}>
          <DialogHeader>
            <DialogTitle>{t("detail.deleteConfirm.title")}</DialogTitle>
            <DialogDescription>{t("detail.deleteConfirm.description")}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setDeleteDialogOpen(false)} disabled={deleteApp.isPending}>
              {t("detail.deleteConfirm.cancel")}
            </Button>
            <Button type="button" variant="destructive" onClick={handleDelete} aria-busy={deleteApp.isPending} disabled={deleteApp.isPending}>
              {deleteApp.isPending && <Loader2 className="mr-2 size-4 animate-spin" />}
              {t("detail.deleteConfirm.confirm")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
```

- [ ] **Step 4: Create versions page**

Create `apps/platform/src/app/(workspace)/apps/[appSlug]/versions/page.tsx`:

```typescript
"use client";

import { use } from "react";
import { notFound } from "next/navigation";
import { useTranslations } from "next-intl";
import { AppHeader } from "@/components/layout/app-header";
import { Skeleton } from "@/components/ui/skeleton";
import { useGetApp, type AppResponse } from "@/generated/app";
import { AppVersionList } from "@/features/apps/components/app-version-list";

export default function VersionsPage({
  params,
}: {
  params: Promise<{ appSlug: string }>;
}) {
  const { appSlug } = use(params);
  const t = useTranslations("apps");
  const { data, isLoading, error } = useGetApp(appSlug);

  const app = data?.data as AppResponse | undefined;

  if (error && !isLoading) {
    notFound();
  }

  return (
    <>
      <AppHeader
        mode="detail"
        breadcrumbs={[
          { label: isLoading ? "..." : (app?.name ?? appSlug) },
        ]}
      />
      <div className="flex flex-1 flex-col gap-6 p-4 md:p-6">
        {isLoading ? (
          <Skeleton className="h-64 w-full" />
        ) : app ? (
          <AppVersionList appId={app.id} hasDraft={app.hasDraft ?? false} />
        ) : null}
      </div>
    </>
  );
}
```

- [ ] **Step 5: Create revisions page**

Create `apps/platform/src/app/(workspace)/apps/[appSlug]/revisions/page.tsx`:

```typescript
"use client";

import { use } from "react";
import { notFound } from "next/navigation";
import { useTranslations } from "next-intl";
import { AppHeader } from "@/components/layout/app-header";
import { Skeleton } from "@/components/ui/skeleton";
import { useGetApp, type AppResponse } from "@/generated/app";
import { AppRevisionList } from "@/features/apps/components/app-revision-list";

export default function RevisionsPage({
  params,
}: {
  params: Promise<{ appSlug: string }>;
}) {
  const { appSlug } = use(params);
  const t = useTranslations("apps");
  const { data, isLoading, error } = useGetApp(appSlug);

  const app = data?.data as AppResponse | undefined;

  if (error && !isLoading) {
    notFound();
  }

  return (
    <>
      <AppHeader
        mode="detail"
        breadcrumbs={[
          { label: isLoading ? "..." : (app?.name ?? appSlug) },
        ]}
      />
      <div className="flex flex-1 flex-col gap-6 p-4 md:p-6">
        {isLoading ? (
          <Skeleton className="h-64 w-full" />
        ) : app ? (
          <AppRevisionList appId={app.id} />
        ) : null}
      </div>
    </>
  );
}
```

- [ ] **Step 6: Replace old app detail page with redirect**

Replace `apps/platform/src/app/(workspace)/apps/[appSlug]/page.tsx` with a redirect:

```typescript
import { redirect } from "next/navigation";

export default async function AppDetailPage({
  params,
}: {
  params: Promise<{ appSlug: string }>;
}) {
  const { appSlug } = await params;
  redirect(`/apps/${appSlug}/definitions`);
}
```

- [ ] **Step 7: Commit**

```bash
git add apps/platform/src/app/(workspace)/apps/[appSlug]/definitions/ apps/platform/src/app/(workspace)/apps/[appSlug]/dependencies/ apps/platform/src/app/(workspace)/apps/[appSlug]/overview/ apps/platform/src/app/(workspace)/apps/[appSlug]/versions/ apps/platform/src/app/(workspace)/apps/[appSlug]/revisions/ apps/platform/src/app/(workspace)/apps/[appSlug]/page.tsx
git commit -m "feat(nav): extract app detail tabs into separate route pages"
```

---

## Task 6: Create Entry Form Page Component (TDD)

**Files:**
- Create: `apps/platform/src/features/content-entries/components/__tests__/entry-form-page.test.tsx`
- Create: `apps/platform/src/features/content-entries/components/entry-form-page.tsx`

This is the new full-page entry form that replaces both the detail page and the dialog.

- [ ] **Step 1: Write the failing test**

Create `apps/platform/src/features/content-entries/components/__tests__/entry-form-page.test.tsx`:

```typescript
import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { EntryFormPage } from "../entry-form-page";
import type { ContentDefinitionResponse, ContentEntryResponse } from "@/generated/content";

vi.mock("next-intl", () => ({
  useTranslations: () => (key: string) => key,
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), back: vi.fn() }),
}));

vi.mock("@tanstack/react-query", () => ({
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

vi.mock("@/generated/content", () => ({
  useCreateContentEntry: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useUpdateContentEntry: () => ({ mutateAsync: vi.fn(), isPending: false }),
  getListContentEntriesQueryKey: vi.fn(),
  getGetContentEntryQueryKey: vi.fn(),
}));

function makeDefinition(overrides?: Partial<ContentDefinitionResponse>): ContentDefinitionResponse {
  return {
    id: "def-1",
    slug: "products",
    name: "Products",
    fields: [
      {
        id: "f1",
        fieldKey: "name",
        fieldType: "TEXT",
        required: true,
        unique: false,
        sortable: true,
        filterable: true,
        position: 0,
      },
      {
        id: "f2",
        fieldKey: "price",
        fieldType: "DECIMAL",
        required: true,
        unique: false,
        sortable: true,
        filterable: false,
        position: 1,
      },
      {
        id: "f3",
        fieldKey: "active",
        fieldType: "BOOLEAN",
        required: false,
        unique: false,
        sortable: false,
        filterable: false,
        position: 2,
      },
    ],
    ...overrides,
  } as ContentDefinitionResponse;
}

function makeEntry(): ContentEntryResponse {
  return {
    id: "entry-1",
    version: 1,
    values: { cf_name: "Widget", cf_price: 9.99, cf_active: true },
    createdAt: "2026-04-15T14:23:00Z",
    updatedAt: "2026-04-17T09:15:00Z",
    createdBy: "user-1",
    updatedBy: "user-1",
  } as ContentEntryResponse;
}

describe("EntryFormPage", () => {
  it("renders form fields from definition in a responsive grid", () => {
    const def = makeDefinition();
    render(
      <EntryFormPage definition={def} appSlug="shop" allDefinitions={[def]} />
    );
    expect(screen.getByTestId("entry-form-grid")).toHaveClass("grid-cols-1");
    expect(screen.getByTestId("entry-form-grid")).toHaveClass("lg:grid-cols-3");
  });

  it("renders all non-MIRROR fields as form inputs", () => {
    const def = makeDefinition();
    render(
      <EntryFormPage definition={def} appSlug="shop" allDefinitions={[def]} />
    );
    // 3 fields: name (TEXT), price (DECIMAL), active (BOOLEAN)
    expect(screen.getByText("name")).toBeInTheDocument();
    expect(screen.getByText("price")).toBeInTheDocument();
    expect(screen.getByText("active")).toBeInTheDocument();
  });

  it("renders save and delete icon buttons in header", () => {
    const def = makeDefinition();
    const entry = makeEntry();
    render(
      <EntryFormPage definition={def} appSlug="shop" entry={entry} allDefinitions={[def]} />
    );
    expect(screen.getByTestId("entry-save-button")).toBeInTheDocument();
    expect(screen.getByTestId("entry-delete-button")).toBeInTheDocument();
  });

  it("does not render delete button in create mode", () => {
    const def = makeDefinition();
    render(
      <EntryFormPage definition={def} appSlug="shop" allDefinitions={[def]} />
    );
    expect(screen.getByTestId("entry-save-button")).toBeInTheDocument();
    expect(screen.queryByTestId("entry-delete-button")).not.toBeInTheDocument();
  });

  it("renders system fields when entry is provided", () => {
    const def = makeDefinition();
    const entry = makeEntry();
    render(
      <EntryFormPage definition={def} appSlug="shop" entry={entry} allDefinitions={[def]} />
    );
    expect(screen.getByTestId("system-fields")).toBeInTheDocument();
    expect(screen.getByText("detail.fields.id")).toBeInTheDocument();
    expect(screen.getByText("detail.fields.version")).toBeInTheDocument();
  });

  it("does not render system fields in create mode", () => {
    const def = makeDefinition();
    render(
      <EntryFormPage definition={def} appSlug="shop" allDefinitions={[def]} />
    );
    expect(screen.queryByTestId("system-fields")).not.toBeInTheDocument();
  });

  it("renders history button only in edit mode", () => {
    const def = makeDefinition();
    const entry = makeEntry();
    render(
      <EntryFormPage definition={def} appSlug="shop" entry={entry} allDefinitions={[def]} />
    );
    expect(screen.getByTestId("entry-history-button")).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/platform && pnpm vitest run src/features/content-entries/components/__tests__/entry-form-page.test.tsx`
Expected: FAIL — module not found

- [ ] **Step 3: Write the EntryFormPage component**

Create `apps/platform/src/features/content-entries/components/entry-form-page.tsx`:

```typescript
"use client";

import { useState, useMemo } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Save, Trash2, Clock, Loader2 } from "lucide-react";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { useQueryClient } from "@tanstack/react-query";
import { AppHeader } from "@/components/layout/app-header";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Form } from "@/components/ui/form";
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import type {
  ContentDefinitionResponse,
  ContentEntryResponse,
  ValidationProblemDetail,
  ProblemDetail,
} from "@/generated/content";
import {
  useCreateContentEntry,
  useUpdateContentEntry,
  getListContentEntriesQueryKey,
  getGetContentEntryQueryKey,
} from "@/generated/content";
import { buildEntrySchema } from "../schemas/build-entry-schema";
import { DynamicFieldRenderer } from "./dynamic-field-renderer";
import { EntryDeleteDialog } from "./entry-delete-dialog";
import { EntryHistoryList } from "./entry-history-list";
import { formatAuditDate } from "./entry-table-columns";

interface EntryFormPageProps {
  definition: ContentDefinitionResponse;
  appSlug: string;
  entry?: ContentEntryResponse;
  allDefinitions?: ContentDefinitionResponse[];
}

export function EntryFormPage({
  definition,
  appSlug,
  entry,
  allDefinitions,
}: EntryFormPageProps) {
  const router = useRouter();
  const t = useTranslations("entries");
  const queryClient = useQueryClient();
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const isEdit = !!entry;

  const createEntry = useCreateContentEntry();
  const updateEntry = useUpdateContentEntry();

  const schema = useMemo(
    () => buildEntrySchema(definition.fields),
    [definition.fields]
  );

  const defaultValues = useMemo(() => {
    const values: Record<string, unknown> = {};
    for (const field of definition.fields) {
      if (field.fieldType === "MIRROR") continue;
      const key = `cf_${field.fieldKey}`;
      if (entry) {
        values[key] = entry.values[key] ?? null;
      } else {
        values[key] = field.fieldType === "BOOLEAN" ? false : null;
      }
    }
    return values;
  }, [definition.fields, entry]);

  const form = useForm({
    resolver: zodResolver(schema),
    defaultValues,
  });

  const isPending = createEntry.isPending || updateEntry.isPending;

  function invalidateQueries() {
    queryClient.invalidateQueries({
      queryKey: getListContentEntriesQueryKey(appSlug, definition.slug),
    });
    if (entry) {
      queryClient.invalidateQueries({
        queryKey: getGetContentEntryQueryKey(appSlug, definition.slug, entry.id),
      });
    }
  }

  async function onSubmit(values: Record<string, unknown>) {
    try {
      if (isEdit && entry) {
        await updateEntry.mutateAsync({
          appSlug,
          defSlug: definition.slug,
          entryId: entry.id,
          data: { version: entry.version, values },
        });
        toast.success(t("updated"));
        invalidateQueries();
      } else {
        const result = await createEntry.mutateAsync({
          appSlug,
          defSlug: definition.slug,
          data: { values },
        });
        toast.success(t("created"));
        invalidateQueries();
        // Navigate to the created entry
        const created = result?.data as ContentEntryResponse | undefined;
        if (created?.id) {
          router.push(`/apps/${appSlug}/data/${definition.slug}/${created.id}`);
        }
      }
    } catch (error: unknown) {
      const err = error as {
        data?: ValidationProblemDetail | ProblemDetail;
        status?: number;
      };

      if (err.status === 409) {
        toast.error(t("versionConflict"));
        return;
      }

      if (err.status === 400 && err.data) {
        const validationErr = err.data as ValidationProblemDetail;
        if (validationErr.errors) {
          for (const fieldError of validationErr.errors) {
            form.setError(fieldError.field as string, {
              message: fieldError.message,
            });
          }
          return;
        }
      }

      toast.error(
        (err.data as ProblemDetail)?.detail ?? t("form.fieldInvalid")
      );
    }
  }

  const editableFields = definition.fields
    .filter((f) => f.fieldType !== "MIRROR")
    .sort((a, b) => a.position - b.position);

  // Entry label for breadcrumb
  const entryLabel = (() => {
    if (!entry) return t("page.new");
    const textField = definition.fields.find((f) => f.fieldType === "TEXT");
    if (textField) {
      const val = entry.values[`cf_${textField.fieldKey}`];
      if (val) return String(val);
    }
    return entry.id.substring(0, 8);
  })();

  const entriesPath = `/apps/${appSlug}/data/${definition.slug}`;

  return (
    <>
      <AppHeader
        mode="detail"
        breadcrumbs={[
          { label: definition.name, href: entriesPath },
          { label: entryLabel },
        ]}
        actions={
          <div className="flex items-center gap-1">
            {isEdit && (
              <Tooltip>
                <TooltipTrigger render={
                  <Button
                    variant="ghost"
                    size="icon"
                    data-testid="entry-history-button"
                    onClick={() => setHistoryOpen(true)}
                  />
                }>
                  <Clock className="size-4" />
                </TooltipTrigger>
                <TooltipContent>{t("page.history")}</TooltipContent>
              </Tooltip>
            )}
            <Tooltip>
              <TooltipTrigger render={
                <Button
                  variant="ghost"
                  size="icon"
                  data-testid="entry-save-button"
                  onClick={form.handleSubmit(onSubmit)}
                  disabled={isPending}
                />
              }>
                {isPending ? (
                  <Loader2 className="size-4 animate-spin" />
                ) : (
                  <Save className="size-4" />
                )}
              </TooltipTrigger>
              <TooltipContent>{t("page.save")}</TooltipContent>
            </Tooltip>
            {isEdit && (
              <Tooltip>
                <TooltipTrigger render={
                  <Button
                    variant="ghost"
                    size="icon"
                    data-testid="entry-delete-button"
                    onClick={() => setDeleteOpen(true)}
                  />
                }>
                  <Trash2 className="size-4" />
                </TooltipTrigger>
                <TooltipContent>{t("page.delete")}</TooltipContent>
              </Tooltip>
            )}
          </div>
        }
      />
      <div data-testid="entry-form-page" className="flex flex-1 flex-col gap-6 p-4 md:p-6">
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)}>
            <div
              data-testid="entry-form-grid"
              className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3"
            >
              {editableFields.map((field) => (
                <DynamicFieldRenderer
                  key={field.id}
                  field={field}
                  control={form.control}
                  appSlug={appSlug}
                  allDefinitions={allDefinitions}
                />
              ))}
            </div>
          </form>
        </Form>

        {/* System fields (edit mode only) */}
        {entry && (
          <div data-testid="system-fields">
            <div className="border-t pt-4">
              <h3 className="mb-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">
                {t("page.systemFields")}
              </h3>
              <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                <div>
                  <dt className="text-sm text-muted-foreground">{t("detail.fields.id")}</dt>
                  <dd className="mt-1 font-mono text-xs">{entry.id}</dd>
                </div>
                <div>
                  <dt className="text-sm text-muted-foreground">{t("detail.fields.version")}</dt>
                  <dd className="mt-1">
                    <Badge variant="secondary">{entry.version}</Badge>
                  </dd>
                </div>
                <div>
                  <dt className="text-sm text-muted-foreground">{t("detail.fields.createdAt")}</dt>
                  <dd className="mt-1 text-sm">{formatAuditDate(entry.createdAt)}</dd>
                </div>
                <div>
                  <dt className="text-sm text-muted-foreground">{t("detail.fields.updatedAt")}</dt>
                  <dd className="mt-1 text-sm">{formatAuditDate(entry.updatedAt)}</dd>
                </div>
                {entry.createdBy && (
                  <div>
                    <dt className="text-sm text-muted-foreground">{t("detail.fields.createdBy")}</dt>
                    <dd className="mt-1 text-sm">
                      {(entry as Record<string, unknown>).createdByName as string ?? entry.createdBy?.substring(0, 8)}
                    </dd>
                  </div>
                )}
                {entry.updatedBy && (
                  <div>
                    <dt className="text-sm text-muted-foreground">{t("detail.fields.updatedBy")}</dt>
                    <dd className="mt-1 text-sm">
                      {(entry as Record<string, unknown>).updatedByName as string ?? entry.updatedBy?.substring(0, 8)}
                    </dd>
                  </div>
                )}
              </dl>
            </div>
          </div>
        )}
      </div>

      {/* History Sheet */}
      {isEdit && entry && (
        <Sheet open={historyOpen} onOpenChange={setHistoryOpen}>
          <SheetContent side="right" className="w-[400px] sm:w-[540px] overflow-y-auto">
            <SheetHeader>
              <SheetTitle>{t("page.history")}</SheetTitle>
            </SheetHeader>
            <div className="mt-4">
              <EntryHistoryList
                appSlug={appSlug}
                defSlug={definition.slug}
                entryId={entry.id}
              />
            </div>
          </SheetContent>
        </Sheet>
      )}

      {/* Delete dialog */}
      {isEdit && entry && (
        <EntryDeleteDialog
          appSlug={appSlug}
          defSlug={definition.slug}
          entry={entry}
          open={deleteOpen}
          onOpenChange={(open) => {
            setDeleteOpen(open);
            if (!open) {
              router.push(entriesPath);
            }
          }}
        />
      )}
    </>
  );
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd apps/platform && pnpm vitest run src/features/content-entries/components/__tests__/entry-form-page.test.tsx`
Expected: PASS (7 tests)

- [ ] **Step 5: Commit**

```bash
git add apps/platform/src/features/content-entries/components/__tests__/entry-form-page.test.tsx apps/platform/src/features/content-entries/components/entry-form-page.tsx
git commit -m "feat(entries): add full-page entry form with history sheet and TDD"
```

---

## Task 7: Create Data Route Pages (Entry List + Create + Edit)

**Files:**
- Create: `apps/platform/src/app/(workspace)/apps/[appSlug]/data/[defSlug]/page.tsx`
- Create: `apps/platform/src/app/(workspace)/apps/[appSlug]/data/[defSlug]/new/page.tsx`
- Create: `apps/platform/src/app/(workspace)/apps/[appSlug]/data/[defSlug]/[entryId]/page.tsx`

- [ ] **Step 1: Create entries list page (data route)**

Create `apps/platform/src/app/(workspace)/apps/[appSlug]/data/[defSlug]/page.tsx`:

```typescript
"use client";

import { use } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { Plus } from "lucide-react";
import { AppHeader } from "@/components/layout/app-header";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  useListContentDefinitions,
  type ContentDefinitionResponse,
} from "@/generated/content";
import { EntryTable } from "@/features/content-entries/components/entry-table";

export default function DataEntriesPage({
  params,
}: {
  params: Promise<{ appSlug: string; defSlug: string }>;
}) {
  const { appSlug, defSlug } = use(params);
  const router = useRouter();
  const t = useTranslations("entries");

  const { data: definitionsData, isLoading } =
    useListContentDefinitions(appSlug);

  const allDefinitions = definitionsData?.data?.items ?? [];
  const definition = allDefinitions.find(
    (d: ContentDefinitionResponse) => d.slug === defSlug
  );

  if (isLoading) {
    return (
      <>
        <AppHeader
          mode="detail"
          breadcrumbs={[{ label: "..." }]}
        />
        <div className="flex flex-1 flex-col gap-6 p-4 md:p-6">
          <Skeleton className="h-64 w-full" />
        </div>
      </>
    );
  }

  if (!definition) {
    return (
      <>
        <AppHeader
          mode="detail"
          breadcrumbs={[{ label: t("notFound") }]}
        />
        <div className="flex flex-1 flex-col items-center justify-center p-12">
          <p className="text-lg font-medium">{t("notFound")}</p>
        </div>
      </>
    );
  }

  return (
    <>
      <AppHeader
        mode="detail"
        breadcrumbs={[
          { label: definition.name },
        ]}
        actions={
          <Button
            data-testid="create-entry-button"
            onClick={() => router.push(`/apps/${appSlug}/data/${defSlug}/new`)}
          >
            <Plus className="mr-2 size-4" />
            {t("create")}
          </Button>
        }
      />
      <div
        data-testid="entries-page"
        className="flex flex-1 flex-col gap-6 p-4 md:p-6"
      >
        <EntryTable
          definition={definition}
          appSlug={appSlug}
          allDefinitions={allDefinitions}
        />
      </div>
    </>
  );
}
```

- [ ] **Step 2: Create new entry page**

Create `apps/platform/src/app/(workspace)/apps/[appSlug]/data/[defSlug]/new/page.tsx`:

```typescript
"use client";

import { use } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { AppHeader } from "@/components/layout/app-header";
import {
  useListContentDefinitions,
  type ContentDefinitionResponse,
} from "@/generated/content";
import { EntryFormPage } from "@/features/content-entries/components/entry-form-page";

export default function NewEntryPage({
  params,
}: {
  params: Promise<{ appSlug: string; defSlug: string }>;
}) {
  const { appSlug, defSlug } = use(params);

  const { data: definitionsData, isLoading } =
    useListContentDefinitions(appSlug);

  const allDefinitions = definitionsData?.data?.items ?? [];
  const definition = allDefinitions.find(
    (d: ContentDefinitionResponse) => d.slug === defSlug
  );

  if (isLoading) {
    return (
      <>
        <AppHeader mode="detail" breadcrumbs={[{ label: "..." }]} />
        <div className="flex flex-1 flex-col gap-6 p-4 md:p-6">
          <Skeleton className="h-64 w-full" />
        </div>
      </>
    );
  }

  if (!definition) return null;

  return (
    <EntryFormPage
      definition={definition}
      appSlug={appSlug}
      allDefinitions={allDefinitions}
    />
  );
}
```

- [ ] **Step 3: Create edit entry page**

Create `apps/platform/src/app/(workspace)/apps/[appSlug]/data/[defSlug]/[entryId]/page.tsx`:

```typescript
"use client";

import { use } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { AppHeader } from "@/components/layout/app-header";
import {
  useListContentDefinitions,
  useGetContentEntry,
  type ContentDefinitionResponse,
  type ContentEntryResponse,
} from "@/generated/content";
import { EntryFormPage } from "@/features/content-entries/components/entry-form-page";

export default function EditEntryPage({
  params,
}: {
  params: Promise<{ appSlug: string; defSlug: string; entryId: string }>;
}) {
  const { appSlug, defSlug, entryId } = use(params);

  const { data: definitionsData, isLoading: defsLoading } =
    useListContentDefinitions(appSlug);
  const { data: entryData, isLoading: entryLoading } = useGetContentEntry(
    appSlug,
    defSlug,
    entryId
  );

  const allDefinitions = definitionsData?.data?.items ?? [];
  const definition = allDefinitions.find(
    (d: ContentDefinitionResponse) => d.slug === defSlug
  );
  const entry = entryData?.data as ContentEntryResponse | undefined;

  const isLoading = defsLoading || entryLoading;

  if (isLoading) {
    return (
      <>
        <AppHeader mode="detail" breadcrumbs={[{ label: "..." }]} />
        <div className="flex flex-1 flex-col gap-6 p-4 md:p-6">
          <Skeleton className="h-64 w-full" />
        </div>
      </>
    );
  }

  if (!definition || !entry) return null;

  return (
    <EntryFormPage
      definition={definition}
      appSlug={appSlug}
      entry={entry}
      allDefinitions={allDefinitions}
    />
  );
}
```

- [ ] **Step 4: Commit**

```bash
git add apps/platform/src/app/(workspace)/apps/[appSlug]/data/
git commit -m "feat(entries): add data route pages for entry list, create, and edit"
```

---

## Task 8: Update Entry Table Navigation

**Files:**
- Modify: `apps/platform/src/features/content-entries/components/entry-table.tsx`
- Modify: `apps/platform/src/features/content-entries/components/entry-table-columns.tsx`

Update the entry table to navigate to new routes and make rows clickable.

- [ ] **Step 1: Update entry table row click and action URLs**

In `apps/platform/src/features/content-entries/components/entry-table.tsx`, update the navigation URLs from `/apps/${appSlug}/content/${definition.slug}/entries/${entry.id}` to `/apps/${appSlug}/data/${definition.slug}/${entry.id}`.

Changes:
1. Make the entire row clickable (navigate on row click)
2. Update the "View" action URL
3. Remove the edit dialog trigger from the table (entries are now edited on their own page)
4. Update the "Edit" action to navigate to the entry page

Replace the `TableRow` in the entries map (around line 228) with:

```typescript
<TableRow
  key={entry.id}
  data-testid="entry-table-row"
  className="cursor-pointer"
  onClick={() => router.push(`/apps/${appSlug}/data/${definition.slug}/${entry.id}`)}
>
```

Update the dropdown actions (around line 253-269):

```typescript
<DropdownMenuItem
  onClick={(e) => {
    e.stopPropagation();
    router.push(`/apps/${appSlug}/data/${definition.slug}/${entry.id}`);
  }}
>
  <Eye className="mr-2 size-4" />
  {t("table.view")}
</DropdownMenuItem>
<DropdownMenuItem
  onClick={(e) => {
    e.stopPropagation();
    router.push(`/apps/${appSlug}/data/${definition.slug}/${entry.id}`);
  }}
>
  <Pencil className="mr-2 size-4" />
  {t("table.edit")}
</DropdownMenuItem>
<DropdownMenuItem
  variant="destructive"
  onClick={(e) => {
    e.stopPropagation();
    setDeleteEntry(entry);
  }}
>
  <Trash2 className="mr-2 size-4" />
  {t("table.delete")}
</DropdownMenuItem>
```

Remove the `editEntry` state, the `EntryFormDialog` import and usage since editing now happens on its own page.

- [ ] **Step 2: Update reference link URLs in entry-table-columns.tsx**

In `apps/platform/src/features/content-entries/components/entry-table-columns.tsx`, update the REFERENCE field link from:
```
/apps/${appSlug}/content/${defSlug}/entries/${refId}
```
to:
```
/apps/${appSlug}/data/${defSlug}/${refId}
```

- [ ] **Step 3: Run existing tests**

Run: `cd apps/platform && pnpm vitest run src/features/content-entries/components/__tests__/entry-table-columns.test.tsx`
Expected: Tests may fail on the URL change — update the test expectation for the REFERENCE link test to use the new URL pattern.

Update the test assertion (line ~111):
```typescript
expect(link).toHaveAttribute(
  "href",
  "/apps/my-app/data/posts/550e8400-e29b-41d4-a716-446655440000"
);
```

- [ ] **Step 4: Run all entry tests**

Run: `cd apps/platform && pnpm vitest run src/features/content-entries/`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add apps/platform/src/features/content-entries/
git commit -m "refactor(entries): update table navigation to new data routes"
```

---

## Task 9: Remove Old Routes and Clean Up

**Files:**
- Delete: `apps/platform/src/app/(workspace)/apps/[appSlug]/content/` (entire directory)
- Modify: `apps/platform/src/features/content-entries/components/entry-detail-page.tsx` (can be deleted, replaced by entry-form-page)
- Modify: `apps/platform/src/features/content-entries/components/entry-detail-values.tsx` (can be deleted, replaced by system fields in entry-form-page)
- Modify: `apps/platform/src/features/content-entries/components/entry-form-dialog.tsx` (can be deleted, replaced by entry-form-page)

- [ ] **Step 1: Delete old content entries routes**

```bash
rm -rf apps/platform/src/app/\(workspace\)/apps/\[appSlug\]/content/
```

- [ ] **Step 2: Delete replaced components**

```bash
rm apps/platform/src/features/content-entries/components/entry-detail-page.tsx
rm apps/platform/src/features/content-entries/components/entry-detail-values.tsx
rm apps/platform/src/features/content-entries/components/entry-form-dialog.tsx
```

- [ ] **Step 3: Verify no imports reference deleted files**

Run: `cd apps/platform && grep -r "entry-detail-page\|entry-detail-values\|entry-form-dialog\|content/\[defSlug\]/entries" src/ --include="*.tsx" --include="*.ts" -l`

Fix any remaining imports that reference the deleted files or old routes.

- [ ] **Step 4: Run all frontend tests**

Run: `cd apps/platform && pnpm vitest run`
Expected: ALL PASS (some tests may need updates if they import deleted components)

- [ ] **Step 5: Verify lint passes**

Run: `cd apps/platform && npx next lint`
Expected: No errors

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor(nav): remove old tab-based app detail page and legacy entry routes"
```

---

## Task 10: Final Verification

- [ ] **Step 1: Run all frontend tests**

Run: `cd apps/platform && pnpm vitest run`
Expected: ALL PASS

- [ ] **Step 2: Verify build compiles**

Run: `cd apps/platform && npx next build 2>&1 | tail -20`
Expected: Build succeeds or only has warnings (not errors)

- [ ] **Step 3: Verify lint**

Run: `cd apps/platform && npx next lint`
Expected: No errors

- [ ] **Step 4: Final commit if any fixes were needed**

Only if fixes were made in steps 1-3:
```bash
git add -A
git commit -m "fix(nav): resolve build and test issues from navigation redesign"
```
