# App Detail Consistency & Version Switch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix layout inconsistencies on Entry Form pages, add a version switch to the app sidebar, make schema pages version-aware with read-only mode, and move the Publish button into the global app header.

**Architecture:** React Context (`AppVersionProvider`) in `app-layout-client.tsx` holds the selected version state. A `PageActionsProvider` lets pages register header actions without layout coupling. A new backend endpoint serves schema snapshots for historical versions. The sidebar gets a version selector dropdown, and a `VersionInfoBar` appears when viewing non-draft versions.

**Tech Stack:** Next.js 16, React 19, shadcn/ui, React Query (Orval), Spring Boot 4, S3 (MinIO), OpenAPI contract-first

**Design Spec:** `docs/plans/2026-04-19-app-detail-consistency-design.md`

---

## File Structure

### New Files (Frontend)
| File | Responsibility |
|------|---------------|
| `src/contexts/app-version-context.tsx` | `AppVersionProvider` + `useAppVersion()` hook |
| `src/contexts/page-actions-context.tsx` | `PageActionsProvider` + `usePageActions()` hook |
| `src/components/layout/app-version-selector.tsx` | Sidebar dropdown for version switching |
| `src/components/layout/version-info-bar.tsx` | Amber sticky bar for read-only mode |
| `src/contexts/__tests__/app-version-context.test.tsx` | Tests for version context |
| `src/contexts/__tests__/page-actions-context.test.tsx` | Tests for page actions context |
| `src/components/layout/__tests__/app-version-selector.test.tsx` | Tests for version selector |
| `src/components/layout/__tests__/version-info-bar.test.tsx` | Tests for info bar |

### New Files (Backend)
| File | Responsibility |
|------|---------------|
| `AppVersionSchemaResponse.java` (model) | Response DTO for version schema endpoint |
| `AppVersionSchemaControllerTest.java` (test) | Integration test for new endpoint |

### Modified Files (Frontend)
| File | Change |
|------|--------|
| `src/app/(workspace)/apps/[appSlug]/app-layout-client.tsx` | Wrap with AppVersionProvider, PageActionsProvider, render VersionInfoBar + Publish button |
| `src/components/layout/app-sidebar-nav.tsx` | Add AppVersionSelector in header, conditional Data section |
| `src/components/layout/app-nav-config.ts` | Add version-aware Data section logic |
| `src/app/(workspace)/apps/[appSlug]/data/[defSlug]/new/page.tsx` | Add AppHeader + padding wrapper around EntryFormPage |
| `src/app/(workspace)/apps/[appSlug]/data/[defSlug]/[entryId]/page.tsx` | Add AppHeader + padding wrapper around EntryFormPage |
| `src/features/content-entries/components/entry-form-page.tsx` | Remove internal header, expose actions, add form id |
| `src/app/(workspace)/apps/[appSlug]/overview/page.tsx` | Remove inline Publish button (moved to layout) |
| `src/app/(workspace)/apps/[appSlug]/definitions/page.tsx` | Use version context for data source |
| `src/app/(workspace)/apps/[appSlug]/dependencies/page.tsx` | Use version context for data source |
| `src/messages/en.json` | i18n keys for version selector, info bar, data hint |
| `src/messages/de.json` | i18n keys for version selector, info bar, data hint |

### Modified Files (Backend)
| File | Change |
|------|--------|
| `openapi/app-api.yaml` | Add `getVersionSchema` endpoint |
| `AppVersionController.java` | Add `getVersionSchema` method |
| `AppVersionService.java` | Add `getVersionSchema` method |

All frontend paths are relative to `apps/platform/`. All backend paths are relative to `apps/backend/src/main/java/io/webcontify/app/`.

---

## Task 1: Fix Entry Form Page Layout (AppHeader + Padding)

**Files:**
- Modify: `apps/platform/src/features/content-entries/components/entry-form-page.tsx`
- Modify: `apps/platform/src/app/(workspace)/apps/[appSlug]/data/[defSlug]/new/page.tsx`
- Modify: `apps/platform/src/app/(workspace)/apps/[appSlug]/data/[defSlug]/[entryId]/page.tsx`

- [ ] **Step 1: Refactor EntryFormPage — remove internal header, add form id**

In `entry-form-page.tsx`, remove the header div (lines 172-246) and add `id="entry-form"` to the `<form>` element. Export the actions as a separate component so the page shell can render them in AppHeader.

Replace the current `EntryFormPage` component structure. Remove lines 170-246 (the Form wrapper opening, the internal header div with breadcrumbs and action buttons). Keep the form fields grid and everything below it.

```tsx
// entry-form-page.tsx — new structure

// Add this new exported component for header actions
export function EntryFormActions({
  isEdit,
  isPending,
  onHistoryOpen,
  onDeleteOpen,
}: {
  isEdit: boolean;
  isPending: boolean;
  onHistoryOpen: () => void;
  onDeleteOpen: () => void;
}) {
  const t = useTranslations("entries");
  return (
    <div className="flex items-center gap-1">
      {isEdit && (
        <Tooltip>
          <TooltipTrigger
            render={
              <Button
                type="button"
                variant="ghost"
                size="icon"
                data-testid="entry-history-button"
                onClick={onHistoryOpen}
              />
            }
          >
            <Clock className="size-4" />
          </TooltipTrigger>
          <TooltipContent>{t("history.title")}</TooltipContent>
        </Tooltip>
      )}
      <Tooltip>
        <TooltipTrigger
          render={
            <Button
              type="submit"
              form="entry-form"
              variant="ghost"
              size="icon"
              data-testid="entry-save-button"
              disabled={isPending}
            />
          }
        >
          {isPending ? (
            <Loader2 className="size-4 animate-spin" />
          ) : (
            <Save className="size-4" />
          )}
        </TooltipTrigger>
        <TooltipContent>{t("form.save")}</TooltipContent>
      </Tooltip>
      {isEdit && (
        <Tooltip>
          <TooltipTrigger
            render={
              <Button
                type="button"
                variant="ghost"
                size="icon"
                data-testid="entry-delete-button"
                onClick={onDeleteOpen}
              />
            }
          >
            <Trash2 className="size-4" />
          </TooltipTrigger>
          <TooltipContent>{t("delete")}</TooltipContent>
        </Tooltip>
      )}
    </div>
  );
}
```

In the `EntryFormPage` component itself, replace the return block starting at line 167. Remove the header div entirely, add `id="entry-form"` to the form, and expose state for the parent:

```tsx
// Inside EntryFormPage, replace the return statement (line 167 onwards)
  return (
    <>
      <Form {...form}>
        <form id="entry-form" onSubmit={form.handleSubmit(onSubmit)}>
          {/* Form Fields Grid — no header above this anymore */}
          <div
            data-testid="entry-form-grid"
            className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4"
          >
            {definition.fields
              .filter((f) => f.fieldType !== "MIRROR")
              .sort((a, b) => a.position - b.position)
              .map((field) => (
                <DynamicFieldRenderer
                  key={field.id}
                  field={field}
                  control={form.control}
                  appSlug={appSlug}
                  allDefinitions={allDefinitions}
                />
              ))}
          </div>

          {/* System Fields */}
          {isEdit && entry && (
            <>
              <Separator className="my-6" />
              {/* ... rest unchanged ... */}
            </>
          )}
        </form>
      </Form>

      {/* History Sheet — unchanged */}
      {/* Delete Dialog — unchanged */}
    </>
  );
```

Also add these to the component's return so parent pages can access state. Change the component to accept callback props:

```tsx
interface EntryFormPageProps {
  definition: ContentDefinitionResponse;
  appSlug: string;
  entry?: ContentEntryResponse;
  allDefinitions?: ContentDefinitionResponse[];
  onActionsReady?: (actions: {
    isEdit: boolean;
    isPending: boolean;
    onHistoryOpen: () => void;
    onDeleteOpen: () => void;
  }) => void;
}
```

Use a `useEffect` to call `onActionsReady` when state changes:

```tsx
const actionState = useMemo(() => ({
  isEdit,
  isPending,
  onHistoryOpen: () => setHistoryOpen(true),
  onDeleteOpen: () => setDeleteOpen(true),
}), [isEdit, isPending]);

useEffect(() => {
  onActionsReady?.(actionState);
}, [onActionsReady, actionState]);
```

- [ ] **Step 2: Update new/page.tsx — add AppHeader + padding wrapper**

Replace `apps/platform/src/app/(workspace)/apps/[appSlug]/data/[defSlug]/new/page.tsx`:

```tsx
"use client";

import { use, useState, useCallback } from "react";
import { useTranslations } from "next-intl";
import { Skeleton } from "@/components/ui/skeleton";
import { AppHeader } from "@/components/layout/app-header";
import {
  useListContentDefinitions,
  type ContentDefinitionResponse,
} from "@/generated/content";
import { EntryFormPage, EntryFormActions } from "@/features/content-entries/components/entry-form-page";

export default function NewEntryPage({
  params,
}: {
  params: Promise<{ appSlug: string; defSlug: string }>;
}) {
  const { appSlug, defSlug } = use(params);
  const t = useTranslations("entries");
  const { data: definitionsData, isLoading } = useListContentDefinitions(appSlug);
  const allDefinitions = definitionsData?.data?.items ?? [];
  const definition = allDefinitions.find(
    (d: ContentDefinitionResponse) => d.slug === defSlug
  );

  const [actionState, setActionState] = useState<{
    isEdit: boolean;
    isPending: boolean;
    onHistoryOpen: () => void;
    onDeleteOpen: () => void;
  } | null>(null);

  const handleActionsReady = useCallback((state: {
    isEdit: boolean;
    isPending: boolean;
    onHistoryOpen: () => void;
    onDeleteOpen: () => void;
  }) => {
    setActionState(state);
  }, []);

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
    <>
      <AppHeader
        mode="detail"
        breadcrumbs={[
          { label: definition.name, href: `/apps/${appSlug}/data/${defSlug}` },
          { label: t("createNew") },
        ]}
        actions={
          actionState ? (
            <EntryFormActions {...actionState} />
          ) : undefined
        }
      />
      <div className="flex flex-1 flex-col gap-6 p-4 md:p-6">
        <EntryFormPage
          definition={definition}
          appSlug={appSlug}
          allDefinitions={allDefinitions}
          onActionsReady={handleActionsReady}
        />
      </div>
    </>
  );
}
```

- [ ] **Step 3: Update [entryId]/page.tsx — add AppHeader + padding wrapper**

Replace `apps/platform/src/app/(workspace)/apps/[appSlug]/data/[defSlug]/[entryId]/page.tsx`:

```tsx
"use client";

import { use, useState, useCallback } from "react";
import { useTranslations } from "next-intl";
import { Skeleton } from "@/components/ui/skeleton";
import { AppHeader } from "@/components/layout/app-header";
import {
  useListContentDefinitions,
  useGetContentEntry,
  type ContentDefinitionResponse,
  type ContentEntryResponse,
} from "@/generated/content";
import { EntryFormPage, EntryFormActions } from "@/features/content-entries/components/entry-form-page";

export default function EditEntryPage({
  params,
}: {
  params: Promise<{ appSlug: string; defSlug: string; entryId: string }>;
}) {
  const { appSlug, defSlug, entryId } = use(params);
  const t = useTranslations("entries");
  const { data: definitionsData, isLoading: defsLoading } = useListContentDefinitions(appSlug);
  const { data: entryData, isLoading: entryLoading } = useGetContentEntry(appSlug, defSlug, entryId);

  const allDefinitions = definitionsData?.data?.items ?? [];
  const definition = allDefinitions.find(
    (d: ContentDefinitionResponse) => d.slug === defSlug
  );
  const entry = entryData?.data as ContentEntryResponse | undefined;
  const isLoading = defsLoading || entryLoading;

  const [actionState, setActionState] = useState<{
    isEdit: boolean;
    isPending: boolean;
    onHistoryOpen: () => void;
    onDeleteOpen: () => void;
  } | null>(null);

  const handleActionsReady = useCallback((state: {
    isEdit: boolean;
    isPending: boolean;
    onHistoryOpen: () => void;
    onDeleteOpen: () => void;
  }) => {
    setActionState(state);
  }, []);

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

  const entryLabel = entry.values[`cf_${definition.fields[0]?.fieldKey}`] as string ?? entry.id;

  return (
    <>
      <AppHeader
        mode="detail"
        breadcrumbs={[
          { label: definition.name, href: `/apps/${appSlug}/data/${defSlug}` },
          { label: entryLabel },
        ]}
        actions={
          actionState ? (
            <EntryFormActions {...actionState} />
          ) : undefined
        }
      />
      <div className="flex flex-1 flex-col gap-6 p-4 md:p-6">
        <EntryFormPage
          definition={definition}
          appSlug={appSlug}
          entry={entry}
          allDefinitions={allDefinitions}
          onActionsReady={handleActionsReady}
        />
      </div>
    </>
  );
}
```

- [ ] **Step 4: Run lint and verify build**

Run: `cd apps/platform && npx next build 2>&1 | head -30`
Expected: No build errors

- [ ] **Step 5: Commit**

```bash
git add apps/platform/src/features/content-entries/components/entry-form-page.tsx \
  apps/platform/src/app/\(workspace\)/apps/\[appSlug\]/data/\[defSlug\]/new/page.tsx \
  apps/platform/src/app/\(workspace\)/apps/\[appSlug\]/data/\[defSlug\]/\[entryId\]/page.tsx
git commit -m "refactor(entries): migrate entry form pages to standard page shell layout"
```

---

## Task 2: PageActionsProvider Context

**Files:**
- Create: `apps/platform/src/contexts/page-actions-context.tsx`
- Create: `apps/platform/src/contexts/__tests__/page-actions-context.test.tsx`

- [ ] **Step 1: Write the failing test**

Create `apps/platform/src/contexts/__tests__/page-actions-context.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { PageActionsProvider, usePageActions } from "../page-actions-context";

function TestActions() {
  usePageActions(<button>Test Action</button>);
  return <div>Page Content</div>;
}

function TestConsumer() {
  return (
    <PageActionsProvider>
      {({ actions }) => (
        <div>
          <header data-testid="header">{actions}</header>
          <main>
            <TestActions />
          </main>
        </div>
      )}
    </PageActionsProvider>
  );
}

describe("PageActionsProvider", () => {
  it("renders actions set by child component", () => {
    render(<TestConsumer />);
    expect(screen.getByTestId("header")).toHaveTextContent("Test Action");
  });

  it("renders null actions when no child sets them", () => {
    render(
      <PageActionsProvider>
        {({ actions }) => (
          <div data-testid="header">{actions}</div>
        )}
      </PageActionsProvider>
    );
    expect(screen.getByTestId("header")).toBeEmptyDOMElement();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/platform && pnpm vitest run src/contexts/__tests__/page-actions-context.test.tsx`
Expected: FAIL — module not found

- [ ] **Step 3: Write implementation**

Create `apps/platform/src/contexts/page-actions-context.tsx`:

```tsx
"use client";

import {
  createContext,
  useContext,
  useState,
  useEffect,
  type ReactNode,
} from "react";

interface PageActionsContextValue {
  actions: ReactNode | null;
  setActions: (node: ReactNode | null) => void;
}

const PageActionsContext = createContext<PageActionsContextValue | null>(null);

interface PageActionsProviderProps {
  children: (ctx: { actions: ReactNode | null }) => ReactNode;
}

export function PageActionsProvider({ children }: PageActionsProviderProps) {
  const [actions, setActions] = useState<ReactNode | null>(null);

  return (
    <PageActionsContext.Provider value={{ actions, setActions }}>
      {children({ actions })}
    </PageActionsContext.Provider>
  );
}

export function usePageActions(actions: ReactNode) {
  const ctx = useContext(PageActionsContext);
  useEffect(() => {
    ctx?.setActions(actions);
    return () => ctx?.setActions(null);
  });
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd apps/platform && pnpm vitest run src/contexts/__tests__/page-actions-context.test.tsx`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add apps/platform/src/contexts/page-actions-context.tsx \
  apps/platform/src/contexts/__tests__/page-actions-context.test.tsx
git commit -m "feat(context): add PageActionsProvider for page-specific header actions"
```

---

## Task 3: AppVersionProvider Context

**Files:**
- Create: `apps/platform/src/contexts/app-version-context.tsx`
- Create: `apps/platform/src/contexts/__tests__/app-version-context.test.tsx`

- [ ] **Step 1: Write the failing test**

Create `apps/platform/src/contexts/__tests__/app-version-context.test.tsx`:

```tsx
import { render, screen, act } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { AppVersionProvider, useAppVersion } from "../app-version-context";
import type { AppResponse, AppVersionResponse } from "@/generated/app";

const mockApp: Partial<AppResponse> = {
  id: "app-1",
  slug: "my-app",
  name: "My App",
  status: "ACTIVE",
  hasDraft: true,
};

const mockVersions: Partial<AppVersionResponse>[] = [
  { id: "v-2", version: "0.2.0", publishedAt: "2026-04-19T10:00:00Z" },
  { id: "v-1", version: "0.1.0", publishedAt: "2026-04-18T10:00:00Z" },
];

function TestConsumer() {
  const ctx = useAppVersion();
  return (
    <div>
      <span data-testid="selected">{ctx.selectedVersion}</span>
      <span data-testid="is-draft">{String(ctx.isDraft)}</span>
      <span data-testid="is-read-only">{String(ctx.isReadOnly)}</span>
      <span data-testid="is-data-visible">{String(ctx.isDataVisible)}</span>
      <button onClick={() => ctx.setSelectedVersion("v-1")}>Select v1</button>
      <button onClick={() => ctx.setSelectedVersion("draft")}>Select draft</button>
    </div>
  );
}

function renderWithProvider(app: Partial<AppResponse>, versions: Partial<AppVersionResponse>[] = []) {
  return render(
    <AppVersionProvider
      app={app as AppResponse}
      versions={versions as AppVersionResponse[]}
    >
      <TestConsumer />
    </AppVersionProvider>
  );
}

describe("AppVersionProvider", () => {
  it("defaults to draft when hasDraft is true", () => {
    renderWithProvider(mockApp, mockVersions);
    expect(screen.getByTestId("selected")).toHaveTextContent("draft");
    expect(screen.getByTestId("is-draft")).toHaveTextContent("true");
    expect(screen.getByTestId("is-read-only")).toHaveTextContent("false");
    expect(screen.getByTestId("is-data-visible")).toHaveTextContent("true");
  });

  it("defaults to active version when hasDraft is false", () => {
    renderWithProvider({ ...mockApp, hasDraft: false }, mockVersions);
    expect(screen.getByTestId("selected")).toHaveTextContent("v-2");
    expect(screen.getByTestId("is-draft")).toHaveTextContent("false");
    expect(screen.getByTestId("is-read-only")).toHaveTextContent("true");
  });

  it("switching to older version makes data not visible", async () => {
    renderWithProvider(mockApp, mockVersions);
    await act(() => screen.getByText("Select v1").click());
    expect(screen.getByTestId("selected")).toHaveTextContent("v-1");
    expect(screen.getByTestId("is-read-only")).toHaveTextContent("true");
    expect(screen.getByTestId("is-data-visible")).toHaveTextContent("false");
  });

  it("switching back to draft restores data visibility", async () => {
    renderWithProvider(mockApp, mockVersions);
    await act(() => screen.getByText("Select v1").click());
    await act(() => screen.getByText("Select draft").click());
    expect(screen.getByTestId("is-draft")).toHaveTextContent("true");
    expect(screen.getByTestId("is-data-visible")).toHaveTextContent("true");
  });

  it("defaults to draft for DRAFT-status app with no versions", () => {
    renderWithProvider({ ...mockApp, status: "DRAFT", hasDraft: true }, []);
    expect(screen.getByTestId("selected")).toHaveTextContent("draft");
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/platform && pnpm vitest run src/contexts/__tests__/app-version-context.test.tsx`
Expected: FAIL — module not found

- [ ] **Step 3: Write implementation**

Create `apps/platform/src/contexts/app-version-context.tsx`:

```tsx
"use client";

import {
  createContext,
  useContext,
  useState,
  useMemo,
  type ReactNode,
} from "react";
import type { AppResponse, AppVersionResponse } from "@/generated/app";

interface AppVersionContextValue {
  selectedVersion: string;
  setSelectedVersion: (v: string) => void;
  isDraft: boolean;
  isActive: boolean;
  isReadOnly: boolean;
  isDataVisible: boolean;
  app: AppResponse;
  versions: AppVersionResponse[];
}

const AppVersionContext = createContext<AppVersionContextValue | null>(null);

interface AppVersionProviderProps {
  app: AppResponse;
  versions: AppVersionResponse[];
  children: ReactNode;
}

export function AppVersionProvider({
  app,
  versions,
  children,
}: AppVersionProviderProps) {
  const activeVersionId = versions.length > 0 ? versions[0].id : null;

  const defaultVersion = app.hasDraft ? "draft" : (activeVersionId ?? "draft");
  const [selectedVersion, setSelectedVersion] = useState(defaultVersion);

  const value = useMemo<AppVersionContextValue>(() => {
    const isDraft = selectedVersion === "draft";
    const isActive = selectedVersion === activeVersionId;
    const isReadOnly = !isDraft;
    const isDataVisible = isDraft || isActive;

    return {
      selectedVersion,
      setSelectedVersion,
      isDraft,
      isActive,
      isReadOnly,
      isDataVisible,
      app,
      versions,
    };
  }, [selectedVersion, activeVersionId, app, versions]);

  return (
    <AppVersionContext.Provider value={value}>
      {children}
    </AppVersionContext.Provider>
  );
}

export function useAppVersion(): AppVersionContextValue {
  const ctx = useContext(AppVersionContext);
  if (!ctx) {
    throw new Error("useAppVersion must be used within AppVersionProvider");
  }
  return ctx;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd apps/platform && pnpm vitest run src/contexts/__tests__/app-version-context.test.tsx`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add apps/platform/src/contexts/app-version-context.tsx \
  apps/platform/src/contexts/__tests__/app-version-context.test.tsx
git commit -m "feat(context): add AppVersionProvider for version switching state"
```

---

## Task 4: VersionInfoBar Component

**Files:**
- Create: `apps/platform/src/components/layout/version-info-bar.tsx`
- Create: `apps/platform/src/components/layout/__tests__/version-info-bar.test.tsx`

- [ ] **Step 1: Write the failing test**

Create `apps/platform/src/components/layout/__tests__/version-info-bar.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { VersionInfoBar } from "../version-info-bar";

vi.mock("next-intl", () => ({
  useTranslations: () => (key: string) => key,
}));

describe("VersionInfoBar", () => {
  it("renders version label and switch button when visible", () => {
    const onSwitch = vi.fn();
    render(<VersionInfoBar version="0.1.0" onSwitchToDraft={onSwitch} />);
    expect(screen.getByRole("status")).toBeInTheDocument();
    expect(screen.getByText(/0\.1\.0/)).toBeInTheDocument();
    expect(screen.getByTestId("switch-to-draft")).toBeInTheDocument();
  });

  it("calls onSwitchToDraft when button clicked", async () => {
    const onSwitch = vi.fn();
    render(<VersionInfoBar version="0.1.0" onSwitchToDraft={onSwitch} />);
    screen.getByTestId("switch-to-draft").click();
    expect(onSwitch).toHaveBeenCalledOnce();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/platform && pnpm vitest run src/components/layout/__tests__/version-info-bar.test.tsx`
Expected: FAIL — module not found

- [ ] **Step 3: Write implementation**

Create `apps/platform/src/components/layout/version-info-bar.tsx`:

```tsx
"use client";

import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Info } from "lucide-react";

interface VersionInfoBarProps {
  version: string;
  onSwitchToDraft: () => void;
}

export function VersionInfoBar({ version, onSwitchToDraft }: VersionInfoBarProps) {
  const t = useTranslations("appVersion");

  return (
    <div
      role="status"
      aria-live="polite"
      className="sticky top-14 z-[9] flex items-center justify-between gap-2 border-b bg-amber-50 px-4 py-2 text-sm text-amber-900 dark:bg-amber-950/50 dark:text-amber-200"
    >
      <div className="flex items-center gap-2">
        <Info className="size-4 shrink-0" />
        <span>{t("viewingVersion", { version })}</span>
      </div>
      <Button
        variant="ghost"
        size="sm"
        data-testid="switch-to-draft"
        className="text-amber-900 hover:text-amber-950 dark:text-amber-200 dark:hover:text-amber-100"
        onClick={onSwitchToDraft}
      >
        {t("switchToDraft")}
      </Button>
    </div>
  );
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd apps/platform && pnpm vitest run src/components/layout/__tests__/version-info-bar.test.tsx`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add apps/platform/src/components/layout/version-info-bar.tsx \
  apps/platform/src/components/layout/__tests__/version-info-bar.test.tsx
git commit -m "feat(layout): add VersionInfoBar component for read-only mode indicator"
```

---

## Task 5: AppVersionSelector Component

**Files:**
- Create: `apps/platform/src/components/layout/app-version-selector.tsx`
- Create: `apps/platform/src/components/layout/__tests__/app-version-selector.test.tsx`

- [ ] **Step 1: Write the failing test**

Create `apps/platform/src/components/layout/__tests__/app-version-selector.test.tsx`:

```tsx
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import { AppVersionSelector } from "../app-version-selector";
import type { AppVersionResponse } from "@/generated/app";

vi.mock("next-intl", () => ({
  useTranslations: () => (key: string, params?: Record<string, string>) => {
    if (params?.version) return `${key} ${params.version}`;
    return key;
  },
}));

const mockVersions: Partial<AppVersionResponse>[] = [
  { id: "v-2", version: "0.2.0", publishedAt: "2026-04-19T10:00:00Z" },
  { id: "v-1", version: "0.1.0", publishedAt: "2026-04-18T10:00:00Z" },
];

describe("AppVersionSelector", () => {
  it("shows current selection as Draft with change count", () => {
    render(
      <AppVersionSelector
        selectedVersion="draft"
        versions={mockVersions as AppVersionResponse[]}
        hasDraft={true}
        hasUnpublishedChanges={true}
        unpublishedChangeCount={3}
        onSelect={vi.fn()}
      />
    );
    expect(screen.getByTestId("version-selector-trigger")).toBeInTheDocument();
    expect(screen.getByText(/draft/i)).toBeInTheDocument();
  });

  it("shows only Draft label when no versions exist", () => {
    render(
      <AppVersionSelector
        selectedVersion="draft"
        versions={[]}
        hasDraft={true}
        hasUnpublishedChanges={false}
        unpublishedChangeCount={0}
        onSelect={vi.fn()}
      />
    );
    expect(screen.getByText(/draft/i)).toBeInTheDocument();
    expect(screen.queryByTestId("version-selector-trigger")).not.toBeInTheDocument();
  });

  it("opens popover and shows version list on click", async () => {
    const user = userEvent.setup();
    render(
      <AppVersionSelector
        selectedVersion="draft"
        versions={mockVersions as AppVersionResponse[]}
        hasDraft={true}
        hasUnpublishedChanges={false}
        unpublishedChangeCount={0}
        onSelect={vi.fn()}
      />
    );
    await user.click(screen.getByTestId("version-selector-trigger"));
    expect(screen.getByText("0.2.0")).toBeInTheDocument();
    expect(screen.getByText("0.1.0")).toBeInTheDocument();
  });

  it("calls onSelect when a version is clicked", async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    render(
      <AppVersionSelector
        selectedVersion="draft"
        versions={mockVersions as AppVersionResponse[]}
        hasDraft={true}
        hasUnpublishedChanges={false}
        unpublishedChangeCount={0}
        onSelect={onSelect}
      />
    );
    await user.click(screen.getByTestId("version-selector-trigger"));
    await user.click(screen.getByText("0.1.0"));
    expect(onSelect).toHaveBeenCalledWith("v-1");
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/platform && pnpm vitest run src/components/layout/__tests__/app-version-selector.test.tsx`
Expected: FAIL — module not found

- [ ] **Step 3: Write implementation**

Create `apps/platform/src/components/layout/app-version-selector.tsx`:

```tsx
"use client";

import { useTranslations } from "next-intl";
import { Check, ChevronsUpDown, GitBranch } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import type { AppVersionResponse } from "@/generated/app";

interface AppVersionSelectorProps {
  selectedVersion: string;
  versions: AppVersionResponse[];
  hasDraft: boolean;
  hasUnpublishedChanges: boolean;
  unpublishedChangeCount: number;
  onSelect: (versionId: string) => void;
}

export function AppVersionSelector({
  selectedVersion,
  versions,
  hasDraft,
  hasUnpublishedChanges,
  unpublishedChangeCount,
  onSelect,
}: AppVersionSelectorProps) {
  const t = useTranslations("appVersion");

  const activeVersion = versions.length > 0 ? versions[0] : null;

  const selectedLabel = selectedVersion === "draft"
    ? t("draft")
    : versions.find((v) => v.id === selectedVersion)?.version ?? selectedVersion;

  // No dropdown needed if only draft exists (no published versions)
  if (!hasDraft && versions.length === 0) {
    return null;
  }

  if (versions.length === 0) {
    return (
      <div className="flex items-center gap-2 px-2 py-1.5 text-sm text-sidebar-foreground/70">
        <GitBranch className="size-4" />
        <span>{t("draft")}</span>
      </div>
    );
  }

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button
          variant="ghost"
          size="sm"
          data-testid="version-selector-trigger"
          className="w-full justify-between gap-2 px-2 text-sm font-normal"
        >
          <div className="flex items-center gap-2 truncate">
            <GitBranch className="size-4 shrink-0" />
            <span className="truncate">{selectedLabel}</span>
            {selectedVersion === "draft" && hasUnpublishedChanges && unpublishedChangeCount > 0 && (
              <Badge variant="secondary" className="ml-1 text-xs">
                {unpublishedChangeCount}
              </Badge>
            )}
          </div>
          <ChevronsUpDown className="size-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-56 p-1" align="start">
        <div className="max-h-64 overflow-y-auto">
          {/* Draft option */}
          {hasDraft && (
            <button
              className="flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-sm hover:bg-accent"
              onClick={() => onSelect("draft")}
            >
              <Check className={`size-4 shrink-0 ${selectedVersion === "draft" ? "opacity-100" : "opacity-0"}`} />
              <span className="flex-1 text-left">{t("draft")}</span>
              {hasUnpublishedChanges && unpublishedChangeCount > 0 && (
                <Badge variant="secondary" className="text-xs">
                  {unpublishedChangeCount}
                </Badge>
              )}
            </button>
          )}

          {/* Versions */}
          {versions.map((v) => (
            <button
              key={v.id}
              className="flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-sm hover:bg-accent"
              onClick={() => onSelect(v.id)}
            >
              <Check className={`size-4 shrink-0 ${selectedVersion === v.id ? "opacity-100" : "opacity-0"}`} />
              <span className="flex-1 text-left">
                {v.version}
                {v.id === activeVersion?.id && (
                  <span className="ml-1 text-muted-foreground">({t("active")})</span>
                )}
              </span>
            </button>
          ))}
        </div>
      </PopoverContent>
    </Popover>
  );
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd apps/platform && pnpm vitest run src/components/layout/__tests__/app-version-selector.test.tsx`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add apps/platform/src/components/layout/app-version-selector.tsx \
  apps/platform/src/components/layout/__tests__/app-version-selector.test.tsx
git commit -m "feat(layout): add AppVersionSelector dropdown for sidebar"
```

---

## Task 6: Backend — Version Schema Endpoint

**Files:**
- Modify: `openapi/app-api.yaml`
- Modify: `apps/backend/src/main/java/io/webcontify/app/api/AppVersionController.java`
- Modify: `apps/backend/src/main/java/io/webcontify/app/service/AppVersionService.java`
- Create: `apps/backend/src/test/java/io/webcontify/app/api/AppVersionSchemaControllerTest.java`

- [ ] **Step 1: Add endpoint to OpenAPI spec**

In `openapi/app-api.yaml`, add a new path after the existing `/api/v1/apps/{appId}/versions/{versionId}/diff` path:

```yaml
  /api/v1/apps/{appId}/versions/{versionId}/schema:
    get:
      operationId: getVersionSchema
      summary: Get schema snapshot for a specific version
      tags:
        - App Versions
      parameters:
        - $ref: '#/components/parameters/AppId'
        - $ref: '#/components/parameters/VersionId'
      responses:
        '200':
          description: Schema snapshot for the version
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/VersionSchemaResponse'
        '404':
          description: App or version not found
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ProblemDetail'
```

Add the response schema in the `components/schemas` section:

```yaml
    VersionSchemaResponse:
      type: object
      required:
        - versionId
        - version
        - contentDefinitions
        - dependencies
      properties:
        versionId:
          type: string
          format: uuid
        version:
          type: string
        contentDefinitions:
          type: array
          items:
            $ref: '#/components/schemas/VersionSchemaDefinition'
        dependencies:
          type: array
          items:
            $ref: '#/components/schemas/VersionSchemaDependency'

    VersionSchemaDefinition:
      type: object
      required:
        - id
        - name
        - slug
        - fields
      properties:
        id:
          type: string
        name:
          type: string
        slug:
          type: string
        description:
          type: string
        displayFormat:
          type: string
        fields:
          type: array
          items:
            $ref: '#/components/schemas/VersionSchemaField'

    VersionSchemaField:
      type: object
      required:
        - id
        - fieldKey
        - fieldType
        - required
        - position
      properties:
        id:
          type: string
        fieldKey:
          type: string
        fieldType:
          type: string
        required:
          type: boolean
        uniqueValue:
          type: boolean
        sortable:
          type: boolean
        filterable:
          type: boolean
        config:
          type: string
        position:
          type: integer

    VersionSchemaDependency:
      type: object
      required:
        - appSlug
      properties:
        appSlug:
          type: string
        version:
          type: string
```

- [ ] **Step 2: Run OpenAPI code generation**

Run: `cd apps/backend && ./gradlew openApiGenerate`
Expected: BUILD SUCCESSFUL — generated controller interface and DTOs

- [ ] **Step 3: Write the failing integration test**

Create `apps/backend/src/test/java/io/webcontify/app/api/AppVersionSchemaControllerTest.java`:

```java
package io.webcontify.app.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AppVersionSchemaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturn404ForNonExistentVersion() throws Exception {
        mockMvc.perform(get("/api/v1/apps/{appId}/versions/{versionId}/schema",
                "00000000-0000-0000-0000-000000000000",
                "00000000-0000-0000-0000-000000000000"))
            .andExpect(status().isNotFound());
    }
}
```

Note: This is a minimal test to validate the endpoint is wired up. A full integration test with data seeding (publish an app, then fetch schema) should follow the pattern of existing tests in the codebase.

- [ ] **Step 4: Implement service method**

In `AppVersionService.java`, add after the existing `getVersionDiff` method:

```java
public VersionSchemaResponse getVersionSchema(UUID appId, UUID versionId) {
    var version = getVersion(appId, versionId);
    var bucketName = tenantInformation.tenantSlug();
    var snapshot = appSnapshotService.downloadSnapshot(bucketName, version.getS3Key());

    var definitions = snapshot.contentDefinitions().stream()
        .map(def -> new VersionSchemaDefinition(
            def.id(), def.name(), def.slug(), def.description(), def.displayFormat(),
            def.fields().stream()
                .map(f -> new VersionSchemaField(
                    f.id(), f.fieldKey(), f.fieldType(), f.required(),
                    f.uniqueValue(), f.sortable(), f.filterable(), f.config(), f.position()))
                .toList()))
        .toList();

    var dependencies = snapshot.dependencies().stream()
        .map(dep -> new VersionSchemaDependency(dep.appSlug(), dep.version()))
        .toList();

    return new VersionSchemaResponse(versionId, version.getVersion(), definitions, dependencies);
}
```

Use the generated DTO classes from the OpenAPI spec (VersionSchemaResponse, VersionSchemaDefinition, VersionSchemaField, VersionSchemaDependency).

- [ ] **Step 5: Implement controller method**

In `AppVersionController.java`, add after the existing `getVersionDiff` endpoint:

```java
@Override
public ResponseEntity<VersionSchemaResponse> getVersionSchema(UUID appId, UUID versionId) {
    appService.getAppByIdOrThrow(appId);
    var schema = appVersionService.getVersionSchema(appId, versionId);
    return ResponseEntity.ok(schema);
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd apps/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --tests io.webcontify.app.api.AppVersionSchemaControllerTest`
Expected: PASS

- [ ] **Step 7: Generate frontend API client**

Run: `cd apps/platform && pnpm generate`
Expected: Orval generates `useGetVersionSchema` hook in `src/generated/app.ts`

- [ ] **Step 8: Commit**

```bash
git add openapi/app-api.yaml \
  apps/backend/src/main/java/io/webcontify/app/api/AppVersionController.java \
  apps/backend/src/main/java/io/webcontify/app/service/AppVersionService.java \
  apps/backend/src/test/java/io/webcontify/app/api/AppVersionSchemaControllerTest.java \
  apps/platform/src/generated/
git commit -m "feat(api): add GET /versions/{id}/schema endpoint for version snapshots"
```

---

## Task 7: i18n Keys

**Files:**
- Modify: `apps/platform/src/messages/en.json`
- Modify: `apps/platform/src/messages/de.json`

- [ ] **Step 1: Add English translation keys**

Add an `appVersion` section to `en.json`:

```json
{
  "appVersion": {
    "draft": "Draft",
    "active": "Active",
    "viewingVersion": "Viewing {version} (read-only)",
    "switchToDraft": "Switch to Draft",
    "dataHint": "Entries — Switch to Draft to manage data",
    "publishBlocked": "{count} errors prevent publishing",
    "selectorLabel": "App version"
  }
}
```

- [ ] **Step 2: Add German translation keys**

Add the same `appVersion` section to `de.json`:

```json
{
  "appVersion": {
    "draft": "Entwurf",
    "active": "Aktiv",
    "viewingVersion": "Ansicht {version} (schreibgeschuetzt)",
    "switchToDraft": "Zum Entwurf wechseln",
    "dataHint": "Eintraege — Zum Entwurf wechseln fuer Datenverwaltung",
    "publishBlocked": "{count} Fehler verhindern die Veroeffentlichung",
    "selectorLabel": "App-Version"
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add apps/platform/src/messages/en.json apps/platform/src/messages/de.json
git commit -m "feat(i18n): add version switch and info bar translation keys"
```

---

## Task 8: Wire Everything into App Layout

**Files:**
- Modify: `apps/platform/src/app/(workspace)/apps/[appSlug]/app-layout-client.tsx`
- Modify: `apps/platform/src/components/layout/app-sidebar-nav.tsx`
- Modify: `apps/platform/src/components/layout/app-nav-config.ts`
- Modify: `apps/platform/src/app/(workspace)/apps/[appSlug]/overview/page.tsx`

This is the integration task that wires all the pieces together.

- [ ] **Step 1: Update app-layout-client.tsx**

Replace the full content of `apps/platform/src/app/(workspace)/apps/[appSlug]/app-layout-client.tsx`:

```tsx
"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar";
import { AppSidebarNav } from "@/components/layout/app-sidebar-nav";
import { AppHeader } from "@/components/layout/app-header";
import { VersionInfoBar } from "@/components/layout/version-info-bar";
import {
  getAppNavSections,
  type ContentDefinitionSummary,
} from "@/components/layout/app-nav-config";
import { AppVersionProvider, useAppVersion } from "@/contexts/app-version-context";
import { useGetApp, useListAppVersions, useListAppProblems } from "@/generated/app";
import { useListContentDefinitions } from "@/generated/content";
import { Button } from "@/components/ui/button";
import { PublishAppDialog } from "@/features/apps/components/publish-app-dialog";
import type { AppResponseEffectiveActionsItem } from "@/generated/app";
import { cn } from "@/lib/utils";

interface AppLayoutClientProps {
  children: React.ReactNode;
  appSlug: string;
}

export default function AppLayoutClient({ children, appSlug }: AppLayoutClientProps) {
  const t = useTranslations();

  const { data: appData, isLoading: appLoading } = useGetApp(appSlug);
  const { data: versionsData } = useListAppVersions(appData?.data?.id ?? "", {
    query: { enabled: !!appData?.data?.id },
  });
  const { data: defsData } = useListContentDefinitions(appSlug);

  const app = appData?.data;
  const versions = versionsData?.data?.items ?? [];

  if (appLoading || !app) {
    return <div className="flex flex-1 items-center justify-center">{/* loading */}</div>;
  }

  const definitions: ContentDefinitionSummary[] = (defsData?.data?.items ?? []).map(
    (d) => ({ slug: d.slug, name: d.name, entryCount: d.entryCount })
  );

  return (
    <AppVersionProvider app={app} versions={versions}>
      <AppLayoutInner appSlug={appSlug} definitions={definitions}>
        {children}
      </AppLayoutInner>
    </AppVersionProvider>
  );
}

function AppLayoutInner({
  appSlug,
  definitions,
  children,
}: {
  appSlug: string;
  definitions: ContentDefinitionSummary[];
  children: React.ReactNode;
}) {
  const t = useTranslations();
  const pathname = usePathname();
  const router = useRouter();
  const { app, versions, selectedVersion, setSelectedVersion, isDraft, isActive, isReadOnly, isDataVisible } = useAppVersion();

  const hasPublishedVersion = app.status === "ACTIVE" || app.status === "INACTIVE";
  const sections = getAppNavSections(
    { appSlug, definitions, hasPublishedVersion, isDataVisible },
    (key: string) => t(`appSidebar.${key}`),
  );

  // Redirect away from data pages when viewing old version
  useEffect(() => {
    if (!isDataVisible && pathname.includes(`/apps/${appSlug}/data/`)) {
      router.push(`/apps/${appSlug}/overview`);
    }
  }, [isDataVisible, pathname, appSlug, router]);

  // Publish button logic
  const actions = app.effectiveActions ?? [];
  const canAdmin = actions.includes("admin" as AppResponseEffectiveActionsItem);
  const isDataPage = pathname.includes(`/apps/${appSlug}/data/`);
  const showPublish = isDraft && app.hasDraft && canAdmin && !isDataPage;

  const { data: problemsData } = useListAppProblems(appSlug);
  const problemsResponse = problemsData?.data;
  const hasErrors = problemsResponse && "errorCount" in problemsResponse
    ? (problemsResponse.errorCount as number) > 0
    : false;

  // Find version string for info bar
  const selectedVersionObj = versions.find((v) => v.id === selectedVersion);
  const selectedVersionLabel = selectedVersionObj?.version ?? selectedVersion;

  return (
    <SidebarProvider>
      <AppSidebarNav
        appName={app.name}
        appSlug={appSlug}
        sections={sections}
        selectedVersion={selectedVersion}
        versions={versions}
        hasDraft={!!app.hasDraft}
        hasUnpublishedChanges={!!app.hasDraft}
        unpublishedChangeCount={0}
        onVersionSelect={setSelectedVersion}
        isDataVisible={isDataVisible}
      />
      <SidebarInset>
        {children}
        {isReadOnly && (
          <VersionInfoBar
            version={selectedVersionLabel}
            onSwitchToDraft={() => setSelectedVersion("draft")}
          />
        )}
      </SidebarInset>
    </SidebarProvider>
  );
}
```

Note: The `VersionInfoBar` is rendered after `{children}` in the SidebarInset for now — each page still renders its own `AppHeader`. The info bar should appear between the header and content. A cleaner approach: pages render AppHeader, then the layout inserts the info bar. Since `SidebarInset` wraps children, we need to adjust. Actually, let's render the info bar as a portal or simply let pages handle it via context. The simplest approach: render the `VersionInfoBar` in the layout before `{children}`, and pages render their own `AppHeader` first.

Correction — fix the ordering in `SidebarInset`:

```tsx
<SidebarInset>
  <main id="main-content" className={cn(isReadOnly && "opacity-75")}>
    {children}
  </main>
</SidebarInset>
```

The `VersionInfoBar` needs to sit between AppHeader and content, but AppHeader is rendered by individual pages. The cleanest solution: each page renders `<AppHeader>`, then the layout renders `<VersionInfoBar>` between the header and the main content. Since pages are `{children}`, we can't inject between their header and content from the layout.

**Better approach:** Create a wrapper that pages use instead of rendering AppHeader directly. But that's too big a refactor. **Simplest approach that works:** The info bar is rendered by individual pages via a `useAppVersion().isReadOnly` check, OR we accept that the info bar renders at the top of `{children}` inside each page. Given that pages already render `<> <AppHeader/> <div>content</div> </>`, the info bar can be a layout-level element rendered as the first child of `SidebarInset`, before `{children}`. Pages already render their AppHeader as the first element — the info bar renders above that. This is acceptable since the info bar should be the very first thing the user sees.

Actually — the correct ordering:
1. Page renders `<AppHeader>` (sticky, z-10)
2. Info bar renders below AppHeader (sticky, z-9, top-14 to stack below header)

Since AppHeader is inside `{children}`, we render info bar AFTER children in DOM but use CSS sticky positioning with `top-14` (below the 56px header). This works because both are sticky — they'll stack.

Let me simplify: Render `VersionInfoBar` inside `SidebarInset` before `{children}`. It will appear before the AppHeader in DOM, but both pages and the info bar use sticky — we just need the info bar to not be sticky or to appear after the header. The cleanest: **Let each page render the info bar themselves** using `useAppVersion()`. No — that defeats the purpose.

**Final decision:** Render the info bar in the layout. It appears before `{children}` in DOM order. Since AppHeader has `sticky top-0 z-10` and VersionInfoBar has `sticky top-14 z-9`, the AppHeader will stick at top and info bar sticks below it. But in DOM order, info bar comes first which means it renders above the AppHeader initially. 

**Actual fix:** Render info bar AFTER children. Use `order` CSS or simply accept the layout:

```tsx
<SidebarInset>
  <div className={cn("flex flex-1 flex-col", isReadOnly && "[&>*:not(:first-child)]:opacity-75")}>
    {children}
  </div>
</SidebarInset>
```

Actually this is getting complex. Let's keep it simple: **The VersionInfoBar is part of the page layout, rendered by `app-layout-client.tsx` AFTER the sidebar but wrapping children.** Pages render AppHeader as their first element. We insert the info bar between using a flex column layout:

```tsx
<SidebarInset>
  {isReadOnly && (
    <VersionInfoBar
      version={selectedVersionLabel}
      onSwitchToDraft={() => setSelectedVersion("draft")}
    />
  )}
  <main id="main-content" className={cn(isReadOnly && "opacity-75")}>
    {children}
  </main>
</SidebarInset>
```

Wait — this won't work either because `{children}` renders `<> <AppHeader /> <div>content</div> </>` and the AppHeader needs to be before the VersionInfoBar visually.

**FINAL APPROACH:** Pages render AppHeader. The VersionInfoBar is rendered by individual pages after their AppHeader, using a shared hook. This is 1 line per page: `{isReadOnly && <VersionInfoBar ... />}`. BUT we said we wanted it in the layout...

Let me reconsider. The AppHeader is rendered by each page as a Fragment child. The layout wraps all children. The simplest correct approach:

1. Keep AppHeader in pages (they know their breadcrumbs/actions)
2. The layout renders VersionInfoBar after `<SidebarInset>` opens but the pages render AppHeader first because they are `{children}`

Actually, with fragments, the children render inline. So:

```tsx
<SidebarInset>
  {children}  {/* This is: <AppHeader/> <div className="p-4">...</div> */}
</SidebarInset>
```

We can't inject between AppHeader and content from the layout. **Accept this and put VersionInfoBar in a shared component that pages import.** The layout provides the context, pages render the bar. One import + one line per page.

OR: restructure so the layout renders AppHeader too. But pages have different breadcrumbs/actions...

**PRAGMATIC SOLUTION:** Export a `VersionAwareLayout` wrapper component that pages use around their content (not their header):

```tsx
// In each page:
<>
  <AppHeader mode="detail" breadcrumbs={[...]} actions={...} />
  <VersionInfoBar /> {/* from layout context, auto-hides when not needed */}
  <div className={cn("flex flex-1 flex-col gap-6 p-4 md:p-6", isReadOnly && "opacity-75")}>
    ...content...
  </div>
</>
```

Make `VersionInfoBar` self-contained — it reads context and renders nothing when `isDraft`:

```tsx
export function VersionInfoBar() {
  const { isReadOnly, selectedVersion, versions, setSelectedVersion } = useAppVersion();
  const t = useTranslations("appVersion");
  if (!isReadOnly) return null;
  const versionObj = versions.find(v => v.id === selectedVersion);
  return (
    <div role="status" ...>
      ...
    </div>
  );
}
```

This is the simplest. Each page adds `<VersionInfoBar />` after their `<AppHeader>`. The component handles its own visibility. The opacity goes on the content div. Let me update the plan accordingly.

- [ ] **Step 1 (revised): Update app-layout-client.tsx with providers and publish logic**

Replace `apps/platform/src/app/(workspace)/apps/[appSlug]/app-layout-client.tsx`:

```tsx
"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar";
import { AppSidebarNav } from "@/components/layout/app-sidebar-nav";
import {
  getAppNavSections,
  type ContentDefinitionSummary,
} from "@/components/layout/app-nav-config";
import { AppVersionProvider, useAppVersion } from "@/contexts/app-version-context";
import { useGetApp, useListAppVersions } from "@/generated/app";
import { useListContentDefinitions } from "@/generated/content";
import { cn } from "@/lib/utils";

interface AppLayoutClientProps {
  children: React.ReactNode;
  appSlug: string;
}

export default function AppLayoutClient({ children, appSlug }: AppLayoutClientProps) {
  const { data: appData, isLoading: appLoading } = useGetApp(appSlug);
  const { data: versionsData } = useListAppVersions(appData?.data?.id ?? "", {
    query: { enabled: !!appData?.data?.id },
  });
  const { data: defsData } = useListContentDefinitions(appSlug);

  const app = appData?.data;
  const versions = versionsData?.data?.items ?? [];

  if (appLoading || !app) {
    return <div className="flex flex-1 items-center justify-center" />;
  }

  const definitions: ContentDefinitionSummary[] = (defsData?.data?.items ?? []).map(
    (d) => ({ slug: d.slug, name: d.name, entryCount: d.entryCount })
  );

  return (
    <AppVersionProvider app={app} versions={versions}>
      <AppLayoutInner appSlug={appSlug} definitions={definitions}>
        {children}
      </AppLayoutInner>
    </AppVersionProvider>
  );
}

function AppLayoutInner({
  appSlug,
  definitions,
  children,
}: {
  appSlug: string;
  definitions: ContentDefinitionSummary[];
  children: React.ReactNode;
}) {
  const t = useTranslations();
  const pathname = usePathname();
  const router = useRouter();
  const ctx = useAppVersion();

  const hasPublishedVersion = ctx.app.status === "ACTIVE" || ctx.app.status === "INACTIVE";
  const sections = getAppNavSections(
    { appSlug, definitions, hasPublishedVersion, isDataVisible: ctx.isDataVisible },
    (key: string) => t(`appSidebar.${key}`),
  );

  // Redirect away from data pages when viewing old version
  useEffect(() => {
    if (!ctx.isDataVisible && pathname.includes(`/apps/${appSlug}/data/`)) {
      router.push(`/apps/${appSlug}/overview`);
    }
  }, [ctx.isDataVisible, pathname, appSlug, router]);

  return (
    <SidebarProvider>
      <AppSidebarNav
        appName={ctx.app.name}
        appSlug={appSlug}
        sections={sections}
        selectedVersion={ctx.selectedVersion}
        versions={ctx.versions}
        hasDraft={!!ctx.app.hasDraft}
        hasUnpublishedChanges={!!ctx.app.hasDraft}
        unpublishedChangeCount={0}
        onVersionSelect={ctx.setSelectedVersion}
        isDataVisible={ctx.isDataVisible}
      />
      <SidebarInset>
        <main id="main-content" className={cn(ctx.isReadOnly && "[&>div:last-child]:opacity-75")}>
          {children}
        </main>
      </SidebarInset>
    </SidebarProvider>
  );
}
```

- [ ] **Step 2: Update app-sidebar-nav.tsx — add version selector and data hint**

In `apps/platform/src/components/layout/app-sidebar-nav.tsx`, add the `AppVersionSelector` in the sidebar header (after the app name button, before `SidebarContent`), and update props:

Add to the props interface:

```typescript
interface AppSidebarNavProps {
  appName: string;
  appSlug: string;
  sections: AppNavSection[];
  // New version-related props:
  selectedVersion: string;
  versions: AppVersionResponse[];
  hasDraft: boolean;
  hasUnpublishedChanges: boolean;
  unpublishedChangeCount: number;
  onVersionSelect: (versionId: string) => void;
  isDataVisible: boolean;
}
```

Add `AppVersionSelector` import and render it inside `SidebarHeader`, after the existing app name button:

```tsx
import { AppVersionSelector } from "./app-version-selector";

// Inside the SidebarHeader, after the app name button:
<AppVersionSelector
  selectedVersion={selectedVersion}
  versions={versions}
  hasDraft={hasDraft}
  hasUnpublishedChanges={hasUnpublishedChanges}
  unpublishedChangeCount={unpublishedChangeCount}
  onSelect={onVersionSelect}
/>
```

For sections with `dataHint` (when data is not visible), render a muted clickable item instead of the normal section:

```tsx
{section.dataHint ? (
  <SidebarGroup key={section.label}>
    <SidebarGroupLabel>{section.label}</SidebarGroupLabel>
    <SidebarMenu>
      <SidebarMenuItem>
        <button
          className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-sm text-muted-foreground hover:text-foreground"
          onClick={() => onVersionSelect("draft")}
        >
          {section.dataHint}
        </button>
      </SidebarMenuItem>
    </SidebarMenu>
  </SidebarGroup>
) : (
  // existing section rendering
)}
```

- [ ] **Step 3: Update app-nav-config.ts — add isDataVisible and dataHint**

In `apps/platform/src/components/layout/app-nav-config.ts`:

Add `isDataVisible` to `AppNavOptions`:

```typescript
interface AppNavOptions {
  appSlug: string;
  definitions: ContentDefinitionSummary[];
  hasPublishedVersion: boolean;
  isDataVisible: boolean;  // NEW
}
```

Add `dataHint` to `AppNavSection`:

```typescript
interface AppNavSection {
  label: string;
  items: AppNavItem[];
  disabledHint?: string;
  dataHint?: string;  // NEW — shown when data section hidden due to version switch
}
```

In `getAppNavSections`, modify the Data section logic:

```typescript
// Data section — replace existing logic
const dataSection: AppNavSection = !options.hasPublishedVersion
  ? { label: t("data"), items: [], disabledHint: t("publishFirst") }
  : !options.isDataVisible
    ? { label: t("data"), items: [], dataHint: t("dataHint") }
    : {
        label: t("data"),
        items: options.definitions.map((d) => ({
          label: d.name,
          href: `${base}/data/${d.slug}`,
          icon: Database,
          testId: `nav-data-${d.slug}`,
          badge: d.entryCount != null ? String(d.entryCount) : undefined,
        })),
      };
```

Note: `t("dataHint")` should map to the `appSidebar.dataHint` key. Add this key to the existing `appSidebar` section in `en.json` and `de.json` — or reuse the `appVersion.dataHint` key depending on how translations are structured.

- [ ] **Step 4: Update overview/page.tsx — remove inline Publish button, add VersionInfoBar**

In `apps/platform/src/app/(workspace)/apps/[appSlug]/overview/page.tsx`:

Remove the Publish button from the `AppHeader` actions prop (lines 220-238). Replace with the global publish logic. Actually, the publish button should now come from the layout or be rendered by each page individually.

**Decision:** Each page renders the Publish button in its own AppHeader actions, reading from `useAppVersion()`. This avoids the complex layout injection problem. Extract the publish button logic into a reusable component:

Create a shared component in the same task:

```tsx
// apps/platform/src/features/apps/components/app-publish-action.tsx
"use client";

import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { useAppVersion } from "@/contexts/app-version-context";
import { useListAppProblems, type AppResponseEffectiveActionsItem } from "@/generated/app";
import { PublishAppDialog } from "./publish-app-dialog";

export function AppPublishAction() {
  const t = useTranslations("apps");
  const { app, isDraft } = useAppVersion();

  const actions = app.effectiveActions ?? [];
  const canAdmin = actions.includes("admin" as AppResponseEffectiveActionsItem);

  const { data: problemsData } = useListAppProblems(app.slug);
  const problemsResponse = problemsData?.data;
  const hasErrors = problemsResponse && "errorCount" in problemsResponse
    ? (problemsResponse.errorCount as number) > 0
    : false;

  if (!isDraft || !app.hasDraft || !canAdmin) return null;

  if (hasErrors) {
    return (
      <Button size="sm" disabled title={t("problems.publishBlocked")}>
        {t("versions.publish")}
      </Button>
    );
  }

  return (
    <PublishAppDialog appId={app.id} appSlug={app.slug}>
      <Button size="sm">{t("versions.publish")}</Button>
    </PublishAppDialog>
  );
}
```

Then in `overview/page.tsx`, replace the inline Publish button logic in the actions prop with:

```tsx
import { AppPublishAction } from "@/features/apps/components/app-publish-action";

// In AppHeader actions:
actions={<AppPublishAction />}
```

Remove the Publish-related imports and logic from overview/page.tsx (useListAppProblems, PublishAppDialog, hasErrors, canAdmin for publish).

Do the same for all other schema pages (definitions, dependencies, versions, revisions) — add `<AppPublishAction />` to their AppHeader actions.

- [ ] **Step 5: Add VersionInfoBar to all app detail pages**

Update `version-info-bar.tsx` to be self-contained (reads context):

```tsx
"use client";

import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Info } from "lucide-react";
import { useAppVersion } from "@/contexts/app-version-context";

export function VersionInfoBar() {
  const t = useTranslations("appVersion");
  const { isReadOnly, selectedVersion, versions, setSelectedVersion } = useAppVersion();

  if (!isReadOnly) return null;

  const versionObj = versions.find((v) => v.id === selectedVersion);
  const versionLabel = versionObj?.version ?? selectedVersion;

  return (
    <div
      role="status"
      aria-live="polite"
      className="sticky top-14 z-[9] flex items-center justify-between gap-2 border-b bg-amber-50 px-4 py-2 text-sm text-amber-900 dark:bg-amber-950/50 dark:text-amber-200"
    >
      <div className="flex items-center gap-2">
        <Info className="size-4 shrink-0" />
        <span>{t("viewingVersion", { version: versionLabel })}</span>
      </div>
      <Button
        variant="ghost"
        size="sm"
        data-testid="switch-to-draft"
        className="text-amber-900 hover:text-amber-950 dark:text-amber-200 dark:hover:text-amber-100"
        onClick={() => setSelectedVersion("draft")}
      >
        {t("switchToDraft")}
      </Button>
    </div>
  );
}
```

Add `<VersionInfoBar />` after `<AppHeader>` in each app detail page:
- `overview/page.tsx`
- `definitions/page.tsx`
- `dependencies/page.tsx`
- `versions/page.tsx`
- `revisions/page.tsx`
- `data/[defSlug]/page.tsx`
- `data/[defSlug]/new/page.tsx`
- `data/[defSlug]/[entryId]/page.tsx`

Pattern per page:

```tsx
import { VersionInfoBar } from "@/components/layout/version-info-bar";
import { AppPublishAction } from "@/features/apps/components/app-publish-action";

// In render:
<>
  <AppHeader mode="detail" breadcrumbs={[...]} actions={<AppPublishAction />} />
  <VersionInfoBar />
  <div className="flex flex-1 flex-col gap-6 p-4 md:p-6">
    ...content...
  </div>
</>
```

For data pages, omit `<AppPublishAction />` from actions (entries are live).

- [ ] **Step 6: Run lint and verify build**

Run: `cd apps/platform && npx next build 2>&1 | head -30`
Expected: No build errors

- [ ] **Step 7: Commit**

```bash
git add apps/platform/src/app/\(workspace\)/apps/\[appSlug\]/ \
  apps/platform/src/components/layout/ \
  apps/platform/src/contexts/ \
  apps/platform/src/features/apps/components/app-publish-action.tsx \
  apps/platform/src/messages/
git commit -m "feat(app-detail): wire version context, publish button, and info bar into layout"
```

---

## Task 9: Schema Pages Version-Aware Data Loading

**Files:**
- Modify: `apps/platform/src/app/(workspace)/apps/[appSlug]/definitions/page.tsx`
- Modify: `apps/platform/src/app/(workspace)/apps/[appSlug]/dependencies/page.tsx`

- [ ] **Step 1: Update definitions page to use version context**

In `definitions/page.tsx`, add version-aware data loading:

```tsx
import { useAppVersion } from "@/contexts/app-version-context";
import { useGetVersionSchema } from "@/generated/app";

// Inside component:
const { isDraft, selectedVersion, isReadOnly, app } = useAppVersion();

// Load live data for draft, snapshot data for versions
const { data: liveData, isLoading: liveLoading } = useListContentDefinitions(appSlug, {
  query: { enabled: isDraft },
});
const { data: snapshotData, isLoading: snapshotLoading } = useGetVersionSchema(
  app.id,
  selectedVersion,
  { query: { enabled: !isDraft, staleTime: Infinity } },
);

const isLoading = isDraft ? liveLoading : snapshotLoading;
// Map snapshot data to the same shape as live data for the graph component
const definitions = isDraft
  ? liveData?.data?.items ?? []
  : (snapshotData?.data?.contentDefinitions ?? []);
```

Pass `canEdit={!isReadOnly && canEdit}` to `ContentDefinitionGraph`.

- [ ] **Step 2: Update dependencies page similarly**

Same pattern: use `useAppVersion()` to determine data source, pass read-only state to graph component.

- [ ] **Step 3: Run lint and verify build**

Run: `cd apps/platform && npx next build 2>&1 | head -30`
Expected: No build errors

- [ ] **Step 4: Commit**

```bash
git add apps/platform/src/app/\(workspace\)/apps/\[appSlug\]/definitions/page.tsx \
  apps/platform/src/app/\(workspace\)/apps/\[appSlug\]/dependencies/page.tsx
git commit -m "feat(schema): make definitions and dependencies pages version-aware"
```

---

## Task 10: E2E Tests

**Files:**
- Create: `apps/e2e/tests/app-detail-consistency.spec.ts`

- [ ] **Step 1: Write E2E tests for entry form layout**

```typescript
import { test, expect } from "@playwright/test";

test.describe("Entry Form Layout Consistency", () => {
  test("entry create page has consistent header and padding", async ({ page }) => {
    // Navigate to an app's data page (requires seeded app with published version + entries)
    await page.goto("/apps/test-app/data/test-def/new");

    // Verify AppHeader exists with sticky positioning
    const header = page.locator("header").first();
    await expect(header).toBeVisible();

    // Verify breadcrumbs are present
    await expect(page.getByRole("navigation", { name: "breadcrumb" })).toBeVisible();

    // Verify save button is in header
    await expect(page.getByTestId("entry-save-button")).toBeVisible();

    // Verify content area has padding
    const content = page.locator("[data-testid='entry-form-grid']");
    await expect(content).toBeVisible();
  });

  test("entry edit page has consistent header with entry name", async ({ page }) => {
    // Navigate to existing entry
    await page.goto("/apps/test-app/data/test-def/some-entry-id");

    // Verify header shows entry name in breadcrumbs
    await expect(page.getByRole("navigation", { name: "breadcrumb" })).toBeVisible();

    // Verify all action buttons are in header
    await expect(page.getByTestId("entry-save-button")).toBeVisible();
    await expect(page.getByTestId("entry-history-button")).toBeVisible();
    await expect(page.getByTestId("entry-delete-button")).toBeVisible();
  });
});
```

- [ ] **Step 2: Write E2E tests for version switch**

```typescript
test.describe("Version Switch", () => {
  test("version selector shows draft and versions", async ({ page }) => {
    await page.goto("/apps/test-app/definitions");

    // Verify version selector exists
    const selector = page.getByTestId("version-selector-trigger");
    await expect(selector).toBeVisible();

    // Open selector
    await selector.click();

    // Verify draft option exists
    await expect(page.getByText("Draft")).toBeVisible();
  });

  test("switching to old version shows read-only info bar", async ({ page }) => {
    await page.goto("/apps/test-app/definitions");

    // Open version selector and pick a version
    await page.getByTestId("version-selector-trigger").click();
    // Click a version (depends on test data)

    // Verify info bar appears
    await expect(page.getByRole("status")).toBeVisible();
    await expect(page.getByTestId("switch-to-draft")).toBeVisible();
  });

  test("switch to draft button works", async ({ page }) => {
    // Navigate and switch to old version first
    // Then click switch to draft
    await page.getByTestId("switch-to-draft").click();

    // Verify info bar disappears
    await expect(page.getByRole("status")).not.toBeVisible();
  });
});
```

- [ ] **Step 3: Write E2E test for publish from schema page**

```typescript
test.describe("Publish from Schema Page", () => {
  test("publish button visible on definitions page", async ({ page }) => {
    await page.goto("/apps/test-app/definitions");

    // Verify publish button exists in header
    await expect(page.getByText("Publish")).toBeVisible();
  });

  test("publish button not visible on data page", async ({ page }) => {
    await page.goto("/apps/test-app/data/test-def");

    // Verify no publish button
    await expect(page.getByText("Publish")).not.toBeVisible();
  });
});
```

Note: These tests need seeded test data (an app with published versions and draft changes). Adapt the URLs and selectors based on the actual test data setup in the E2E environment.

- [ ] **Step 4: Run E2E tests**

Run: `cd apps/e2e && npx playwright test tests/app-detail-consistency.spec.ts`
Expected: Tests pass (requires running dev environment with `pnpm dev`)

- [ ] **Step 5: Commit**

```bash
git add apps/e2e/tests/app-detail-consistency.spec.ts
git commit -m "test(e2e): add app detail consistency and version switch tests"
```

---

## Summary

| Task | Description | Type |
|------|-------------|------|
| 1 | Fix Entry Form page layout (AppHeader + padding) | Frontend refactor |
| 2 | PageActionsProvider context | Frontend new |
| 3 | AppVersionProvider context | Frontend new |
| 4 | VersionInfoBar component | Frontend new |
| 5 | AppVersionSelector component | Frontend new |
| 6 | Backend version schema endpoint | Backend + API |
| 7 | i18n keys (EN + DE) | Frontend i18n |
| 8 | Wire everything into app layout | Frontend integration |
| 9 | Schema pages version-aware data loading | Frontend integration |
| 10 | E2E tests | Testing |

Tasks 1-7 can be worked on in parallel (independent components). Task 8 integrates everything and depends on 1-7. Task 9 depends on 6 (backend endpoint) and 8 (layout wiring). Task 10 depends on all.
