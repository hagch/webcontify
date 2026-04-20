# App Navigation Redesign

## Status: Draft

## Problem

The current app detail page uses a 5-tab layout (Overview, Content, Dependencies, Versions, Revisions) that doesn't scale well. Content Definitions are only accessible via the graph canvas, and Content Entries require navigating through the graph. With Pages and Workflows coming later, the tab bar would grow even further.

Additionally, there is no concept of switching between the active (published) version and the current draft — the admin always sees the working copy without clear context of what's live.

## Target Audience

These pages are for **administrators**, not end users. Admins configure apps (schema, pages, workflows). End users interact with the app through Pages + Workflows later.

## Decision: Pattern 1 — Sidebar Context Switch

When an admin enters an app, the workspace sidebar is replaced by an app-scoped sidebar. A "Back to Apps" link and an app picker dropdown provide navigation back to the workspace level.

### Why This Pattern

- An app is a self-contained working context (definitions, pages, workflows)
- Admins don't need workspace features (Users, Settings) while configuring an app
- Scales well for apps with many content definitions
- The shadcn sidebar already supports all required building blocks (groups, sub-items, badges, actions, header slot)
- The existing `collapsible="icon"` mode continues to work

### Alternatives Considered

| Pattern | Rejected Because |
|---------|-----------------|
| Minibar + Main Sidebar (NocoDB/Slack) | Too much horizontal space, visual complexity |
| Drill-Down Sidebar (Notion/iOS) | Workspace nav completely hidden, no quick access |

## Sidebar Layout

```
+---------------------------+
| < Apps                    |  Back to app list
| Shop v                   |  App picker dropdown (switch between apps)
|---------------------------|
| * Draft  . v1.3.0        |  Version switch: Draft / Active / older versions
|   3 unpublished [Publish] |
|---------------------------|
| SCHEMA                    |  Section: affected by version switch
|   Definitions             |  -> Graph canvas (content definition editor)
|   Dependencies            |  -> Dependency graph
|---------------------------|
| DATA (Live)               |  Section: always live data, ignores version switch
|   Products           (24) |  -> Entries table
|   Orders            (156) |
|   Categories          (8) |
|---------------------------|
| PAGES                     |  Section: affected by version switch (later)
|---------------------------|
| WORKFLOWS                 |  Section: affected by version switch (later)
|---------------------------|
| CONFIG                    |  Section: app metadata and history
|   Overview                |  -> App metadata (name, description, slug, status)
|   Versions                |  -> Published version history
|   Revisions               |  -> Draft change history
+---------------------------+
```

## Sections

### Schema

Admin's primary workspace for designing the data model.

| Item | Target | Notes |
|------|--------|-------|
| Definitions | Graph canvas | Existing content definition graph editor |
| Dependencies | Dependency graph | Existing app dependency graph |

Behavior per version switch:
- **Draft selected**: Fully editable
- **Active/older version selected**: Read-only view of that version's snapshot

### Data (Live)

Always shows live data against the **published schema** (active version). Ignores the version switch entirely.

| Item | Target | Notes |
|------|--------|-------|
| One entry per content definition | Entries table | Dynamic list based on published definitions |

Behavior:
- Only visible when the app has been published at least once (status = ACTIVE or INACTIVE with versions)
- When app is in initial DRAFT (never published): section shows disabled state with hint "Publish the app to manage entries"
- Entry count badge shows number of entries per definition
- Sub-items are dynamically generated from the app's published content definitions

### Pages (Later)

Visual page builder for end-user-facing UI.

Behavior per version switch: same as Schema (draft = editable, active = read-only).

### Workflows (Later)

Workflow editor for business logic and automations.

Behavior per version switch: same as Schema (draft = editable, active = read-only).

### Config

App administration and history.

| Item | Target | Notes |
|------|--------|-------|
| Overview | App metadata page | Name, description, slug, status, created/updated |
| Versions | Published version list | Immutable snapshots, rollback capability |
| Revisions | Draft change history | JSON Patch records, revert capability |

## Version Switch

Located in the sidebar header, below the app picker.

### States

| Selection | What the admin sees | UI mode |
|-----------|-------------------|---------|
| **Draft** (default) | Current working copy | Everything editable |
| **Active (v1.3.0)** | Last published snapshot | Read-only, grayed out editing |
| **Older version (v1.2.0)** | Historical snapshot | Read-only + rollback button |

### Rules

- Default selection: Draft (if `has_draft=true`) or Active (if no unpublished changes)
- "Publish" button is prominent when Draft is selected and there are unpublished changes
- The version switch affects Schema, Pages, and Workflows sections
- The Data section is **always live** and unaffected by the version switch
- When viewing a non-draft version, a visual indicator (e.g., banner or muted background) makes the read-only state obvious

### Entry Schema Relationship (Option A — Confirmed)

Content Entries always run against the **published schema** (active version), not the draft. This means:
- Draft schema changes don't affect existing entries until published
- Admins can safely iterate on the schema without breaking live data
- The Data section shows entries based on the published definitions
- If a draft adds a new content definition, it won't appear in the Data section until published

## Routing Changes

### Current

```
/apps                                          App list
/apps/[appSlug]                                App detail (5 tabs)
/apps/[appSlug]/content/[defSlug]/entries      Entries list
/apps/[appSlug]/content/[defSlug]/entries/[id] Entry detail
```

### Proposed

```
/apps                                          App list (unchanged)
/apps/[appSlug]                                Redirects to /apps/[appSlug]/definitions
/apps/[appSlug]/definitions                    Graph canvas (Schema > Definitions)
/apps/[appSlug]/dependencies                   Dependency graph (Schema > Dependencies)
/apps/[appSlug]/data/[defSlug]                 Entries table (Data > specific definition)
/apps/[appSlug]/data/[defSlug]/new             Create new entry
/apps/[appSlug]/data/[defSlug]/[entryId]       Edit entry (form + system fields)
/apps/[appSlug]/pages                          Page list (later)
/apps/[appSlug]/pages/[pageSlug]               Page editor (later)
/apps/[appSlug]/workflows                      Workflow list (later)
/apps/[appSlug]/workflows/[workflowSlug]       Workflow editor (later)
/apps/[appSlug]/overview                       App metadata (Config > Overview)
/apps/[appSlug]/versions                       Version history (Config > Versions)
/apps/[appSlug]/revisions                      Revision history (Config > Revisions)
```

## shadcn Sidebar Components Used

All required components already exist — no new primitives needed.

| Component | Usage |
|-----------|-------|
| `SidebarHeader` + `headerSlot` | App picker + version switch |
| `SidebarGroup` + `SidebarGroupLabel` | Section headers (Schema, Data, Pages, etc.) |
| `SidebarMenu` + `SidebarMenuItem` | Menu items (Definitions, Dependencies, Overview) |
| `SidebarMenuSub` + `SidebarMenuSubItem` | Nested items (content definitions under Data) |
| `SidebarMenuSubButton` | Clickable sub-items |
| `SidebarMenuAction` | Hover actions (+ button for creating) |
| `SidebarMenuBadge` | Entry count, draft indicator |
| `SidebarSeparator` | Between sections |

## Migration from Current UI

The 5-tab app detail page dissolves into sidebar sections:

| Current Tab | New Location |
|-------------|-------------|
| Overview | Config > Overview |
| Content (Graph) | Schema > Definitions |
| Dependencies | Schema > Dependencies |
| Versions | Config > Versions |
| Revisions | Config > Revisions |

## App Picker

The app picker dropdown in the sidebar header shows all apps the admin has access to in the current tenant. It allows switching between apps without navigating back to the app list.

## Breadcrumbs

Breadcrumbs in the app header reflect the sidebar hierarchy:

| Page | Breadcrumb |
|------|-----------|
| Definitions graph | App Name |
| Dependencies graph | App Name |
| Entries table | App Name > Definition Name |
| Entry detail/create | App Name > Definition Name > Entry Label / "New" |
| Overview | App Name |
| Versions | App Name |
| Revisions | App Name |

The "App Name" segment links back to the default route (`/apps/[appSlug]/definitions`).

## Content Entry Pages

### Decision: Full Page Form (No Dialog)

Entry creation and editing moves from a dialog (`sm:max-w-lg`) to a full page. The dialog is too small for definitions with many fields or large text values.

### Detail Page = Form (Always Editable)

The entry detail page IS the form — fields are always editable, no separate read-only view. This follows Directus/Payload patterns and is standard for admin tools where the primary action is editing data.

- **Create**: `/apps/[appSlug]/data/[defSlug]/new` — empty form, no system fields
- **Edit**: `/apps/[appSlug]/data/[defSlug]/[entryId]` — pre-filled form + system fields
- Optimistic concurrency (version field) prevents accidental overwrites
- Explicit "Save" button required — no auto-save

### Header Actions (Icon Buttons)

The header shows icon-only buttons with tooltips for space efficiency:

```
+--------------------------------------------------+
| < Products                            🕐  💾  🗑  |
|--------------------------------------------------|
```

| Icon | Action | Notes |
|------|--------|-------|
| Clock | Open history sheet | Sheet slides in from right |
| Save | Save entry | Disabled when no changes |
| Trash | Delete entry | Opens confirmation dialog |

### History Sheet

Entry history opens as a **Sheet from the right** (`side="right"`), overlaying the form content. The admin stays in context and can see the form in the background while reviewing changes.

```
+--------------------------------------------------+
|                           +----------------------+|
| < Products     🕐 💾 🗑   | History              ||
|----------------------------                       |
| ...Form underneath...     | 2026-04-17 09:15     ||
|                           |   Price: 10 → 15     ||
|                           |   Stock: 50 → 45     ||
|                           |                      ||
|                           | 2026-04-15 14:23     ||
|                           |   Created            ||
|                           +----------------------+|
+--------------------------------------------------+
```

Uses the existing shadcn `Sheet` + `SheetContent` component with `side="right"`.

### Form Layout

Responsive grid for all fields — both editable form fields and read-only system fields use the same grid:

```
grid-cols-1 sm:grid-cols-2 lg:grid-cols-3
```

No exceptions — all field types (text, number, boolean, dropdown, textarea, reference) get one grid cell. Labels are positioned above the field (standard for always-editable forms).

```
lg (3 columns):
| Name              | Price            | Stock           |
| [_______________] | [______________] | [_____________] |
|                                                        |
| Category          | Active           | Description     |
| [Dropdown v     ] | [x]              | [____________] |

sm (2 columns):
| Name              | Price            |
| [_______________] | [______________] |

xs (1 column):
| Name                                |
| [_________________________________] |
```

System fields (ID, Version, Created, Updated, CreatedBy, UpdatedBy) are displayed below a separator in the same responsive grid, read-only.

### Entries Table

The entries table page (`/apps/[appSlug]/data/[defSlug]`) keeps its existing functionality (sort, filter, cursor pagination) with these adjustments:

- "Create" button in the page header navigates to `/apps/[appSlug]/data/[defSlug]/new`
- Row click navigates to `/apps/[appSlug]/data/[defSlug]/[entryId]` (the form page)
- Row actions dropdown (view, edit, delete) remains for quick access

## Open Questions

- (none currently)
