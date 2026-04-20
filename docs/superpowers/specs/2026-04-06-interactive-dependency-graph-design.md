# Interactive Dependency Graph — Design Spec

**Date:** 2026-04-06
**Branch:** `feat/app-domain-2-custom-enhancements-superpowers`
**Dependencies:** App Domain Phase 4 (App CRUD + Dependencies already implemented)

---

## Overview

Upgrade the app dependency graph from a read-only visualization to a fully interactive, fullscreen graph editor. Users can add and remove dependencies directly in the diagram instead of using separate dialogs/buttons.

### Goals

- Dependencies are managed entirely within the graph (no external Add/Remove UI)
- Professional node design with useful metadata at a glance
- Auto-layout that scales to complex dependency trees
- Dark mode works correctly (fixes current zoom button bug)

### Non-Goals

- Creating new apps from within the graph (only connecting existing apps)
- Drag-to-reposition nodes (layout is automatic)
- Nested/grouped nodes

---

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Layout integration | Fullscreen Tab | Dependencies tab uses full available height (`calc(100vh - header)`) |
| Layout direction | Left-to-Right (LR) | Natural reading flow, better use of widescreen displays |
| Layout engine | ELK.js (`elkjs`) | Best DAG layout algorithm, async via Web Worker, configurable |
| Node design | Detailed with metadata | Gradient icon, name + slug, status pill, definition count + version |
| Edge design | Straight Minimal | Solid = direct, dashed = transitive, arrow marker at target |
| Add dependency | Plus-Node in graph | Dashed-border "+" node next to dependencies, click opens popover with app selection |
| Remove dependency | X-Button on edge hover | Small delete button appears on edge midpoint on hover, click opens confirmation |
| Interaction model | Drag-to-Connect + Plus-Node | Drag from source handle to target handle creates dependency, plus-node for adding new apps to graph |

---

## Component Architecture

### AppDependencyGraph (refactored)

The existing component gets a complete rewrite. New structure:

```
AppDependencyGraph
├── Toolbar (title only, no action buttons)
├── ReactFlow (fullscreen)
│   ├── Background (dots pattern)
│   ├── Controls (zoom +/-, fit view — dark mode fixed)
│   ├── AppNode (custom node type — detailed design)
│   ├── AddDependencyNode (custom node type — "+" node)
│   ├── DependencyEdge (custom edge type — with delete button)
│   └── ELK.js layout (auto-positioning)
├── AddDependencyPopover (app selection, appears at plus-node position)
└── RemoveDependencyDialog (confirmation dialog, triggered from edge delete button)
```

### Files to create/modify

| File | Action | Purpose |
|------|--------|---------|
| `features/apps/components/app-dependency-graph.tsx` | Rewrite | Main graph component with ELK.js layout, fullscreen, interactivity |
| `features/apps/components/app-node.tsx` | New | Custom node with detailed design (icon, name, slug, status, defs, version) |
| `features/apps/components/add-dependency-node.tsx` | New | Plus-node for adding new dependencies |
| `features/apps/components/dependency-edge.tsx` | New | Custom edge with hover delete button |
| `features/apps/components/add-dependency-popover.tsx` | New | Popover with app selection (replaces AddDependencyDialog) |
| `features/apps/components/add-dependency-dialog.tsx` | Delete | Replaced by popover on plus-node |
| `features/apps/components/remove-dependency-dialog.tsx` | Keep | Reused as confirmation when clicking edge delete button |
| `app/(workspace)/apps/[appId]/page.tsx` | Modify | Dependencies tab content becomes fullscreen graph |

### Dependencies to install

| Package | Purpose |
|---------|---------|
| `elkjs` | Auto-layout engine for DAG positioning |

---

## Node Design

### AppNode (custom node)

```
+---------------------------------------------------+
|  [Gradient Icon]  Name                Status Pill  |
|                   slug                             |
|  ─────────────────────────────────────────────     |
|  X definitions                           vX.Y.Z   |
+---------------------------------------------------+
```

- **Icon**: 32x32px rounded square with gradient using tenant primary color (CSS variable `--primary` via branding provider), fallback to indigo gradient. First letter of app name in white bold.
- **Name**: `text-sm font-semibold`, truncated
- **Slug**: `text-xs text-muted-foreground`, shown below name
- **Status Pill**: Small rounded pill (ACTIVE = tenant accent color, DRAFT = outline, INACTIVE = secondary)
- **Separator**: 1px border-top
- **Footer**: Definition count (left), both `text-xs text-muted-foreground`. Note: `currentVersion` is not in the `DependencyGraphNode` API type — version display requires a backend enhancement (future task).
- **Current app indicator**: Accent color border + ring using tenant accent color (CSS variable `--accent` via branding provider), fallback to emerald. Applied via Tailwind: `border-accent ring-2 ring-accent/20`.
- **Transitive nodes**: Reduced opacity (`opacity-65`)
- **Width**: 240px fixed
- **Handles**: Small dots (8px), `bg-muted-foreground`, visible on hover with `bg-primary` highlight. Source handle on right, target handle on left.

### AddDependencyNode (plus-node)

- Dashed border (`border-dashed`), same height as AppNode
- Centered `+` icon (lucide `Plus`)
- Text: "Add Dependency" below icon
- `text-muted-foreground`, hover brightens
- Click opens `AddDependencyPopover` positioned at the node
- Positioned by ELK.js as a regular node in the graph (connected to current app with a dashed edge)

---

## Edge Design

### DependencyEdge (custom edge)

- **Type**: Straight lines with `MarkerType.ArrowClosed` at target
- **Direct dependencies**: Solid line, `stroke-width: 1`, color `hsl(var(--muted-foreground))`
- **Transitive dependencies**: Dashed line (`stroke-dasharray: 4 4`), reduced opacity
- **Hover state**: Line brightens to `hsl(var(--primary))`
- **Delete button**: Appears on hover at edge midpoint (only on direct dependency edges)
  - Small circle (20px) with X icon
  - `bg-destructive text-destructive-foreground`
  - Click opens `RemoveDependencyDialog` (existing component, reused)
- **No edge labels**: Direct/transitive distinction is clear from line style

---

## Auto-Layout (ELK.js)

### Configuration

```typescript
const layoutOptions = {
  'elk.algorithm': 'layered',
  'elk.direction': 'RIGHT',
  'elk.spacing.nodeNode': '50',
  'elk.layered.spacing.nodeNodeBetweenLayers': '100',
  'elk.layered.crossingMinimization.strategy': 'LAYER_SWEEP',
  'elk.layered.nodePlacement.strategy': 'BRANDES_KOEPF',
};
```

### Layout behavior

- Layout runs on initial load and after every add/remove dependency mutation
- Uses `elkjs/lib/elk.bundled.js` (includes Web Worker)
- After layout: `fitView({ padding: 0.2, duration: 300 })` for smooth transition
- The AddDependencyNode is included in the layout as a regular node (connected to current app)

---

## Interaction Flows

### Adding a dependency via Plus-Node

1. User clicks the "+" node in the graph
2. `AddDependencyPopover` opens, anchored to the node position
3. Popover shows a searchable dropdown with available apps (filtered: not self, not already connected, only ACTIVE)
4. User selects an app
5. `useAddAppDependency` mutation fires
6. On success: queries invalidated, graph re-renders with new node, ELK.js re-layouts
7. On error (409 cyclic, 400 draft): toast error message

### Adding a dependency via Drag-to-Connect

1. User drags from the source handle of the current app node
2. `isValidConnection` checks: no self-reference, no duplicate, target is in the graph
3. On valid drop to target handle: `onConnect` fires `useAddAppDependency` mutation
4. On error (cyclic): toast error, edge not added
5. Note: This only works between nodes already visible in the graph. For adding new apps, use the plus-node.

### Removing a dependency

1. User hovers over a direct dependency edge
2. Small X button appears at edge midpoint
3. User clicks X button
4. `RemoveDependencyDialog` opens (existing confirmation dialog)
5. User confirms → `useRemoveAppDependency` mutation
6. On success: node removed from graph (if no other connections), ELK.js re-layouts

---

## Whitelabeling / Branding

The graph respects tenant branding via CSS custom properties:

| Element | Branding | Fallback |
|---------|----------|----------|
| Node icon gradient | `--primary` (via branding provider) | Indigo gradient (#6366f1 → #818cf8) |
| Current app border + ring | `--secondary` / `--accent` (via branding provider) | Emerald (#10b981) |
| ACTIVE status pill | `--secondary` / `--accent` (via branding provider) | Emerald |
| Edges, handles, controls | Neutral (`--muted-foreground`) | No branding — stays functional |

This keeps the graph visually integrated with the tenant theme without overwhelming the UI.

---

## Dark Mode Fix

### Current bug

The React Flow `<Controls>` zoom buttons (+ / - / fit) are white-on-white in dark mode because the button inherits `color: white` from the dark theme but also has a white/light background.

### Fix

Override React Flow control styles with shadcn CSS variables:

```css
.react-flow__controls-button {
  background: hsl(var(--card));
  color: hsl(var(--card-foreground));
  border-color: hsl(var(--border));
}

.react-flow__controls-button:hover {
  background: hsl(var(--accent));
}
```

Additionally, set `colorMode="dark"` / `colorMode="light"` on `<ReactFlow>` based on the current theme (via `useTheme` from next-themes).

---

## Fullscreen Layout

The Dependencies tab content changes from fixed `h-[400px]` + dependency list below to:

```tsx
<TabsContent value="dependencies" className="flex-1 flex flex-col min-h-0">
  <div className="flex-1 min-h-0">
    <AppDependencyGraph appId={app.id} />
  </div>
</TabsContent>
```

The parent layout needs to be `flex flex-col h-[calc(100vh-<header-height>)]` so the graph fills the remaining space. The direct dependency list below the graph is removed entirely — all dependency management happens in the graph.

---

## React Flow Configuration

```tsx
<ReactFlow
  nodes={nodes}
  edges={edges}
  nodeTypes={nodeTypes}
  edgeTypes={edgeTypes}
  onConnect={handleConnect}
  isValidConnection={isValidConnection}
  colorMode={theme === 'dark' ? 'dark' : 'light'}
  fitView
  fitViewOptions={{ padding: 0.2, maxZoom: 1.5 }}
  proOptions={{ hideAttribution: true }}
  nodesDraggable={false}
  nodesConnectable={true}
  zoomOnScroll={false}
  panOnScroll
  defaultEdgeOptions={{
    type: 'dependency',
    markerEnd: { type: MarkerType.ArrowClosed, color: 'hsl(var(--muted-foreground))' },
  }}
>
  <Background variant={BackgroundVariant.Dots} gap={20} size={1} />
  <Controls showInteractive={false} />
</ReactFlow>
```

---

## E2E Test Impact

Existing tests in `apps/e2e/tests/apps/app-dependencies.spec.ts` need updating:
- Add dependency: Click plus-node → select app in popover (instead of toolbar button + dialog)
- Remove dependency: Hover edge → click X → confirm (instead of list button + dialog)
- Graph visualization assertions remain similar
- New: Test drag-to-connect between existing nodes
