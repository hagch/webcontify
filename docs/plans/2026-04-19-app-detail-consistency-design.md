# App Detail Consistency & Version Switch Design

## 1. Overview

**What:** Three interconnected improvements to the App Detail area of the Platform UI:
1. Consistent page layout for Entry Form pages (AppHeader + Padding)
2. Version switch in the sidebar to toggle between Draft, Active, and older versions
3. Global Publish button in the header on all schema pages

**Who:** Tenant users with Editor/Admin permissions managing apps — defining schemas, managing entries, comparing versions, and publishing.

**Why:** The current UI has layout inconsistencies on Entry Form pages (missing AppHeader, no standard padding), the Publish button is only accessible on the Overview page, and there is no way to switch between Draft and published versions — even though the backend already stores snapshots and the navigation redesign plan specifies this feature.

## 2. Functional Areas

### 2.1 Consistent Page Layout

Entry Form pages (`new/page.tsx`, `[entryId]/page.tsx`) are migrated to the standard page shell pattern:

- Page shell renders `AppHeader` (mode="detail", breadcrumbs + actions) + padding wrapper (`p-4 md:p-6`)
- `EntryFormPage` becomes pure form content without its own header
- Actions (Save, History, Delete) move into the AppHeader actions slot
- Form submit via `form="entry-form"` attribute on the Save button + `id="entry-form"` on the `<form>` element
- Loading/Error states maintain the same shell pattern (already correct)

### 2.2 Version Switch in the Sidebar

`AppVersionProvider` (React Context) in `app-layout-client.tsx`:

- State: `selectedVersion` (string: `"draft"` | version-id), `isReadOnly` (computed), `isDraft` (computed)
- Default: `"draft"` when `hasDraft=true`, otherwise Active version
- Sidebar header shows dropdown below the app name
- Dropdown content: Draft (with change-count badge), Active (with version number), all older versions chronologically descending
- Scrollable list in popover
- Schema pages read `useAppVersion()` and load either live data (Draft) or snapshot data (version)
- Backend: New endpoint `GET /apps/{appId}/versions/{versionId}/schema` loads the S3 snapshot and returns content definitions

### 2.3 Read-only Mode

When a non-Draft version is selected:

- Sticky info bar between AppHeader and content: amber-tinted, "Viewing v1.2.0 (read-only)" left, "Switch to Draft" button right
- Content wrapper with `opacity-75` for subtle visual shift
- All edit actions disappear completely (not disabled)
- Publish button not visible
- Rendered in `app-layout-client.tsx`, controlled by version context

### 2.4 Global Publish Button

- Moved from `overview/page.tsx` into `app-layout-client.tsx`
- Visible in AppHeader on all app detail pages except Data pages
- Conditions: Draft selected + `hasDraft=true` + `canAdmin`
- Error count badge on validation problems, disabled when errors > 0
- Opens existing `PublishAppDialog`
- After publish: version context switches to new Active version
- Page-specific actions (e.g., "Create Entry") come via an actions slot in the layout, Publish button always rightmost

### 2.5 Data Section Visibility

- Draft or Active selected: Data section normally visible, entries editable (live)
- Older version selected: Data section replaced by muted clickable entry "Entries — Switch to Draft to manage data"
- Redirect logic: If user is on a Data page and selects an old version → `useEffect` in version provider redirects to Overview
- Click on muted entry switches version context back to Draft

## 3. Architecture

**Chosen approach:** React Context + Layout Shell

### Component Hierarchy

```
app-layout-client.tsx
├── AppVersionProvider
│   ├── SidebarProvider
│   │   └── AppSidebarNav
│   │       ├── AppVersionSelector (NEW — dropdown in header)
│   │       ├── Schema Nav Items (Definitions, Dependencies, etc.)
│   │       ├── Data Nav Items (conditional via version context)
│   │       └── Config Nav Items (Overview, Versions, Revisions)
│   └── SidebarInset
│       ├── AppHeader
│       │   ├── Page Actions Slot (left, via PageActionsProvider)
│       │   └── Publish Button (right, conditional via version context)
│       ├── VersionInfoBar (NEW — conditional, amber, read-only)
│       └── <main className={cn(isReadOnly && "opacity-75")}>
│           └── {children} (Page Content)
```

### AppVersionProvider Context

```typescript
interface AppVersionContext {
  selectedVersion: string;           // "draft" | version-id
  setSelectedVersion: (v: string) => void;
  isDraft: boolean;                  // selectedVersion === "draft"
  isActive: boolean;                 // selectedVersion === active version id
  isReadOnly: boolean;               // !isDraft (Active + older = read-only)
  isDataVisible: boolean;            // isDraft || isActive
  app: AppResponse;                  // App data from useGetApp
  versions: AppVersionResponse[];    // All versions from useListAppVersions
}
```

The provider initializes `selectedVersion` based on app state:
- `hasDraft=true` → Default `"draft"`
- `hasDraft=false` → Default Active version ID

### PageActionsProvider Context

Allows pages to register their own header actions without the layout knowing every page:

```typescript
interface PageActionsContext {
  actions: ReactNode | null;
  setActions: (node: ReactNode | null) => void;
}
```

Pages use a `usePageActions(actions)` hook that sets actions on mount and clears on unmount. The layout reads actions and renders them left of the Publish button in the AppHeader.

### Backend Change

New endpoint in the App API:

```
GET /api/v1/apps/{appId}/versions/{versionId}/schema
```

Response: Content definitions + dependencies from the S3 snapshot. Uses existing `AppSnapshotService.downloadSnapshot()` and extracts the relevant data. No new DB schema required.

### Data Flow for Schema Pages

```
Page (e.g., Definitions)
  → useAppVersion() → { selectedVersion, isDraft }
  → if isDraft:
      useListContentDefinitions(appSlug)     // existing endpoint, live data
  → else:
      useGetVersionSchema(appId, versionId)  // new endpoint, snapshot data
  → Same component, different data source
```

## 4. UX Flows

### Flow 1: Create/Edit Entry (Layout Fix)

1. User is on entry list (`/apps/my-app/data/products`)
2. Clicks "Create" in header → navigates to `/apps/my-app/data/products/new`
3. Sees entry form with same header style as the list
4. Actions in header: Save (primary icon), optionally Cancel
5. Fills fields, clicks Save → toast "Entry created", redirect to edit page
6. Edit page: header shows breadcrumbs with entry name, actions: Save, History, Delete
7. Browser back → returns to list, same header style

### Flow 2: Switch Version and Compare Schema

1. User is on Definitions page, sidebar shows `Draft · 2 changes`
2. Clicks version selector in sidebar
3. Popover opens: Draft (selected, badge "2"), v1.2.0 (Active), v1.1.0, v1.0.0
4. Selects "v1.1.0"
5. Amber info bar appears: "Viewing v1.1.0 (read-only) — Switch to Draft"
6. Content becomes slightly muted, schema graph shows historical state from snapshot
7. Publish button disappears, edit actions gone
8. Data section in sidebar replaced by muted hint
9. User clicks "Switch to Draft" in info bar → back to draft mode

### Flow 3: Publish from Schema Page

1. User works on Definitions page, Draft selected
2. Sees Publish button right in header (next to page actions)
3. Clicks Publish → dialog opens
4. Version suggestion (e.g., "v1.3.0"), release notes textarea
5. Clicks "Publish" → loading state
6. Success: toast, dialog closes
7. Version selector switches to new Active version (v1.3.0)
8. Page is now read-only (new Active version, no Draft)
9. Publish button disappears (hasDraft=false)

### Flow 4: Data Page with Old Version

1. User is on entry list (`/apps/my-app/data/products`), Draft selected
2. Opens version selector, selects "v1.0.0"
3. Automatic redirect to `/apps/my-app/overview`
4. Info bar: "Viewing v1.0.0 (read-only)"
5. Sidebar: Data section shows muted "Entries — Switch to Draft to manage data"
6. Clicks muted entry → version switches to Draft
7. Data section reappears, user navigates to entries

### Flow 5: App Without Published Version

1. New app, status DRAFT, no versions
2. Version selector in sidebar shows only "Draft" — no dropdown, just label
3. Publish button visible (if admin + no errors)
4. Data section hidden (as already implemented: "Publish the app first")
5. After first publish: selector shows "Draft" and "v1.0.0 (Active)"

## 5. BDU Test Script

### BDU 1: Entry Form Layout Consistency

- **Entry point:** `/apps/my-app/data/products`
- **Steps:**
  1. Click "Create" button in header
  2. Verify: sticky header present with breadcrumbs and actions
  3. Verify: content has same spacing as list page
  4. Fill first field, click Save in header
  5. Verify: redirect to edit page, header shows entry name in breadcrumbs
  6. Click History icon in header → sheet opens on right
  7. Close sheet, click Delete icon → confirm dialog
  8. Cancel, click browser back → entry list
- **Success:** Header style identical on list, create, and edit. Padding consistent. Actions functional.
- **Failure modes:** Double-click Save (loading state must block), back without save (no guard, accepted)
- **Playwright E2E:** YES
- **Verify in browser:** YES — specifically visual comparison of header height, padding, sticky behavior on scroll

### BDU 2: Version Switch Basic Flow

- **Entry point:** `/apps/my-app/definitions` (app with at least 2 published versions + draft)
- **Steps:**
  1. Verify: sidebar shows version selector with "Draft · N changes"
  2. Click selector → popover with Draft, Active, older versions
  3. Select oldest version
  4. Verify: amber info bar appears with "Viewing vX.X.X (read-only)"
  5. Verify: content is muted, no edit buttons visible
  6. Verify: Publish button not in header
  7. Click "Switch to Draft" in info bar
  8. Verify: info bar disappears, content normal, edit buttons back
- **Success:** Seamless switch, clear visual distinction, all controls react correctly
- **Failure modes:** Rapid switching (no race conditions on API calls), selector during API loading (skeleton, not stale data)
- **Playwright E2E:** YES
- **Verify in browser:** YES — specifically amber info bar styling, opacity effect, transition behavior

### BDU 3: Publish from Schema Page

- **Entry point:** `/apps/my-app/dependencies` (app with draft changes)
- **Steps:**
  1. Verify: Publish button visible in header
  2. Click Publish → dialog opens
  3. Enter release notes
  4. Click Publish in dialog
  5. Verify: loading state, then success toast
  6. Verify: version selector shows new Active version
  7. Verify: page is now read-only (info bar visible)
  8. Verify: Publish button gone
- **Success:** Publish works from any schema page, state update correct
- **Failure modes:** Publish with validation errors (button disabled, error count visible), network error (toast, dialog stays open)
- **Playwright E2E:** YES
- **Verify in browser:** YES — specifically Publish button position relative to page actions

### BDU 4: Data Section on Version Switch

- **Entry point:** `/apps/my-app/data/products` (Draft selected)
- **Steps:**
  1. Verify: entry table visible, sidebar shows Data section
  2. Open version selector, select old version
  3. Verify: automatic redirect to `/apps/my-app/overview`
  4. Verify: sidebar Data section shows muted hint
  5. Click muted "Entries" entry
  6. Verify: version switches to Draft, Data section visible again
  7. Navigate back to entry list
- **Success:** No dead state, clear redirect, hint functional
- **Failure modes:** Manual URL entry `/data/products` with old version (redirect must work), refresh on overview with old version (stays on overview, read-only)
- **Playwright E2E:** YES
- **Verify in browser:** YES — specifically redirect timing, muted entry visually recognizable

### BDU 5: App Without Published Version

- **Entry point:** `/apps/new-app/overview` (freshly created app, DRAFT only)
- **Steps:**
  1. Verify: version selector shows only "Draft", no dropdown
  2. Verify: Publish button visible in header
  3. Verify: Data section shows "Publish the app first"
  4. Click Publish, fill dialog
  5. Verify: after publish, selector shows "Draft" and "v1.0.0 (Active)"
  6. Verify: Data section becomes visible
- **Success:** First publish flow works, UI updates correctly
- **Failure modes:** Publish without content definitions (server validates, error toast)
- **Playwright E2E:** YES
- **Verify in browser:** YES

## 6. Non-Functional Requirements

**Performance:**
- Version switch must not trigger full page reload — only content area updates
- Snapshot endpoint (S3 download) should respond < 500ms (small JSON files, no caching needed initially)
- React Query caches snapshot data per version ID — repeated switching to same version does not reload (`staleTime: Infinity` for snapshots, they are immutable)
- Version list is loaded with app data, no separate API call when opening the dropdown

**Security:**
- Snapshot endpoint checks same permissions as existing app endpoints (tenant isolation via RLS, app permissions via OpenFGA)
- Read-only mode is purely visual in frontend — backend already has write protection on published versions
- No new attack vector: snapshot data is a subset of what the user can already see

**Accessibility:**
- Version selector: keyboard navigable (arrow keys in popover, Enter to select, Escape to close)
- Info bar: `role="status"` + `aria-live="polite"` so screen readers announce version switch
- Read-only content: `aria-readonly="true"` on interactive elements that visually disappear
- Publish button disabled state: `aria-disabled="true"` with tooltip text as `aria-label`

**i18n:**
- All new UI texts via `useTranslations()`: version selector labels, info bar text, muted data hint, publish button states
- Keys maintained in `en.json` + `de.json`
- Version numbers and date formats locale-aware (already via `Intl.DateTimeFormat`)

## 7. Scope Boundaries

**IN scope:**
- Entry Form pages migrated to standard page shell (AppHeader + padding)
- `EntryFormPage` refactored: own header removed, actions moved to page shell
- `AppVersionProvider` (React Context) in `app-layout-client.tsx`
- `AppVersionSelector` dropdown component in sidebar
- `VersionInfoBar` component (amber, sticky, read-only hint)
- `PageActionsProvider` context for page-specific header actions
- Publish button moved from `overview/page.tsx` to layout
- Data section conditional rendering based on version context
- Redirect logic for Data page + old version
- Backend: `GET /apps/{appId}/versions/{versionId}/schema` endpoint
- Schema pages (Definitions, Dependencies) adapted to version context
- i18n keys for all new UI texts (EN + DE)
- Playwright E2E tests for all 5 BDU scenarios

**OUT of scope:**
- Unsaved changes guard (browser back without save) — separate feature
- Command Palette (Cmd+K) — separate feature
- Tenant switcher for platform admins — separate feature
- Zustand store for global user state — separate feature
- Rollback button on older versions — can be added later on versions page
- Diff view between two versions (side-by-side) — separate feature
- Search field in version dropdown — implement when >10 versions realistic, prepare basic structure
- Mobile-specific adjustments to version selector — shadcn popover is already responsive
- Caching optimizations for snapshots — `staleTime: Infinity` sufficient, no CDN/Redis needed
