# Interactive Dependency Graph Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the app dependency graph from read-only visualization to a fullscreen interactive editor with auto-layout, drag-to-connect, plus-node for adding dependencies, and edge delete buttons.

**Architecture:** Replace the existing `AppDependencyGraph` component with a fullscreen React Flow canvas using ELK.js for automatic DAG layout (left-to-right). Custom node types (AppNode, AddDependencyNode) and a custom edge type (DependencyEdge) handle all interaction. The separate AddDependencyDialog is replaced by a popover on the plus-node; RemoveDependencyDialog is reused from edge delete buttons.

**Tech Stack:** @xyflow/react 12, elkjs, React Query (Orval hooks), shadcn/ui (Popover, Badge, Button), next-intl, next-themes

**Spec:** `docs/superpowers/specs/2026-04-06-interactive-dependency-graph-design.md`

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `apps/platform/src/features/apps/components/app-node.tsx` | Create | Custom React Flow node: gradient icon, name+slug, status pill, definition count |
| `apps/platform/src/features/apps/components/add-dependency-node.tsx` | Create | Plus-node for adding new dependencies via popover |
| `apps/platform/src/features/apps/components/dependency-edge.tsx` | Create | Custom edge: straight line + hover delete button (direct only) |
| `apps/platform/src/features/apps/components/add-dependency-popover.tsx` | Create | Popover with searchable app list, replaces AddDependencyDialog |
| `apps/platform/src/features/apps/hooks/use-elk-layout.ts` | Create | ELK.js layout hook: takes nodes+edges, returns positioned nodes |
| `apps/platform/src/features/apps/components/app-dependency-graph.tsx` | Rewrite | Main graph: fullscreen, ELK.js layout, onConnect, isValidConnection |
| `apps/platform/src/features/apps/components/add-dependency-dialog.tsx` | Delete | Replaced by add-dependency-popover.tsx |
| `apps/platform/src/app/(workspace)/apps/[appId]/page.tsx` | Modify | Fullscreen dependencies tab |
| `apps/platform/src/app/globals.css` | Modify | React Flow dark mode CSS overrides |
| `apps/platform/messages/en.json` | Modify | New i18n keys for graph interactions |
| `apps/platform/messages/de.json` | Modify | German translations for graph interactions |
| `apps/e2e/tests/apps/app-dependencies.spec.ts` | Modify | Update E2E tests for new interaction model |

---

### Task 1: Install elkjs dependency

**Files:**
- Modify: `apps/platform/package.json`

- [ ] **Step 1: Install elkjs**

```bash
cd apps/platform && pnpm add elkjs
```

- [ ] **Step 2: Verify installation**

```bash
cd apps/platform && node -e "const ELK = require('elkjs/lib/elk.bundled.js'); console.log('ELK loaded:', typeof ELK)"
```

Expected: `ELK loaded: function`

- [ ] **Step 3: Commit**

```bash
git add apps/platform/package.json pnpm-lock.yaml
git commit -m "chore: add elkjs for dependency graph auto-layout"
```

---

### Task 2: Update i18n keys

**Files:**
- Modify: `apps/platform/messages/en.json`
- Modify: `apps/platform/messages/de.json`

- [ ] **Step 1: Add new i18n keys to en.json**

In the `apps.dependencies` section, add/update these keys:

```json
"dependencies": {
  "title": "Dependencies",
  "noDependencies": "No dependencies yet. Click the + node to add one.",
  "direct": "Direct",
  "transitive": "Transitive",
  "addNode": {
    "label": "Add Dependency"
  },
  "addPopover": {
    "title": "Add Dependency",
    "searchPlaceholder": "Search apps...",
    "noAppsAvailable": "No apps available",
    "successToast": "Dependency added",
    "cyclicError": "Adding this dependency would create a cycle",
    "draftError": "Cannot depend on a draft app"
  },
  "removeDialog": {
    "title": "Remove Dependency",
    "description": "Are you sure you want to remove this dependency?",
    "confirm": "Remove",
    "cancel": "Cancel",
    "successToast": "Dependency removed",
    "errorToast": "Failed to remove dependency"
  },
  "graph": {
    "currentApp": "Current",
    "connectHint": "Drag from handle to connect",
    "hoverHint": "Hover edge to remove"
  }
}
```

Remove the old `addDependency`, `removeDependency`, and `addDialog` keys.

- [ ] **Step 2: Add corresponding German keys to de.json**

```json
"dependencies": {
  "title": "Abhaengigkeiten",
  "noDependencies": "Noch keine Abhaengigkeiten. Klicke auf den + Knoten um eine hinzuzufuegen.",
  "direct": "Direkt",
  "transitive": "Transitiv",
  "addNode": {
    "label": "Abhaengigkeit hinzufuegen"
  },
  "addPopover": {
    "title": "Abhaengigkeit hinzufuegen",
    "searchPlaceholder": "Apps suchen...",
    "noAppsAvailable": "Keine Apps verfuegbar",
    "successToast": "Abhaengigkeit hinzugefuegt",
    "cyclicError": "Diese Abhaengigkeit wuerde einen Zyklus erzeugen",
    "draftError": "Kann keine Draft-App als Abhaengigkeit verwenden"
  },
  "removeDialog": {
    "title": "Abhaengigkeit entfernen",
    "description": "Bist du sicher, dass du diese Abhaengigkeit entfernen willst?",
    "confirm": "Entfernen",
    "cancel": "Abbrechen",
    "successToast": "Abhaengigkeit entfernt",
    "errorToast": "Fehler beim Entfernen der Abhaengigkeit"
  },
  "graph": {
    "currentApp": "Aktuell",
    "connectHint": "Vom Handle ziehen zum Verbinden",
    "hoverHint": "Ueber Edge hovern zum Entfernen"
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add apps/platform/messages/en.json apps/platform/messages/de.json
git commit -m "feat: update i18n keys for interactive dependency graph"
```

---

### Task 3: React Flow dark mode CSS overrides

**Files:**
- Modify: `apps/platform/src/app/globals.css`

- [ ] **Step 1: Add React Flow overrides to globals.css**

Append at the end of the file:

```css
/* React Flow — theme integration */
.react-flow__controls-button {
  background: oklch(var(--card)) !important;
  color: oklch(var(--card-foreground)) !important;
  border-color: oklch(var(--border)) !important;
  fill: oklch(var(--card-foreground)) !important;
}

.react-flow__controls-button:hover {
  background: oklch(var(--accent)) !important;
  color: oklch(var(--accent-foreground)) !important;
  fill: oklch(var(--accent-foreground)) !important;
}

.react-flow__controls-button svg {
  fill: inherit;
}
```

Note: The project uses oklch color values. Check the existing CSS variables in globals.css — they use `oklch(...)` format. The React Flow controls override must match this format.

Actually, since shadcn variables are already in oklch format (e.g. `--card: oklch(1.0 0 0)`), and Tailwind uses `hsl(var(--card))` or `oklch(var(--card))`, we need to check how the variables are referenced. Looking at globals.css, the values are stored as full oklch expressions. So we reference them via `var(--card)` directly:

```css
/* React Flow — theme integration */
.react-flow__controls-button {
  background: var(--card) !important;
  color: var(--card-foreground) !important;
  border-color: var(--border) !important;
}

.react-flow__controls-button:hover {
  background: var(--accent) !important;
  color: var(--accent-foreground) !important;
}

.react-flow__controls-button svg {
  fill: currentColor;
}
```

- [ ] **Step 2: Verify in dark mode**

Run `nx serve platform`, open `platform.localhost`, switch to dark mode, navigate to an app's Dependencies tab. The zoom controls (+, -, fit) should be visible with proper contrast.

- [ ] **Step 3: Commit**

```bash
git add apps/platform/src/app/globals.css
git commit -m "fix: react flow controls dark mode contrast"
```

---

### Task 4: Create ELK.js layout hook

**Files:**
- Create: `apps/platform/src/features/apps/hooks/use-elk-layout.ts`

- [ ] **Step 1: Create the hook**

```typescript
"use client";

import { useCallback, useEffect, useRef } from "react";
import type { Node, Edge } from "@xyflow/react";
import ELK from "elkjs/lib/elk.bundled.js";

const elk = new ELK();

const DEFAULT_NODE_WIDTH = 240;
const DEFAULT_NODE_HEIGHT = 90;

const layoutOptions = {
  "elk.algorithm": "layered",
  "elk.direction": "RIGHT",
  "elk.spacing.nodeNode": "50",
  "elk.layered.spacing.nodeNodeBetweenLayers": "100",
  "elk.layered.crossingMinimization.strategy": "LAYER_SWEEP",
  "elk.layered.nodePlacement.strategy": "BRANDES_KOEPF",
};

export async function computeElkLayout(
  nodes: Node[],
  edges: Edge[]
): Promise<Node[]> {
  if (nodes.length === 0) return [];

  const elkGraph = {
    id: "root",
    layoutOptions,
    children: nodes.map((node) => ({
      id: node.id,
      width: node.measured?.width ?? DEFAULT_NODE_WIDTH,
      height: node.measured?.height ?? DEFAULT_NODE_HEIGHT,
    })),
    edges: edges.map((edge) => ({
      id: edge.id,
      sources: [edge.source],
      targets: [edge.target],
    })),
  };

  const layoutedGraph = await elk.layout(elkGraph);

  return nodes.map((node) => {
    const elkNode = layoutedGraph.children?.find((n) => n.id === node.id);
    if (!elkNode) return node;
    return {
      ...node,
      position: { x: elkNode.x ?? 0, y: elkNode.y ?? 0 },
    };
  });
}

export function useElkLayout() {
  const layoutInProgress = useRef(false);

  const runLayout = useCallback(
    async (
      nodes: Node[],
      edges: Edge[],
      onLayouted: (nodes: Node[]) => void
    ) => {
      if (layoutInProgress.current) return;
      layoutInProgress.current = true;

      try {
        const layoutedNodes = await computeElkLayout(nodes, edges);
        onLayouted(layoutedNodes);
      } finally {
        layoutInProgress.current = false;
      }
    },
    []
  );

  return { runLayout };
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/platform/src/features/apps/hooks/use-elk-layout.ts
git commit -m "feat: elk.js layout hook for dependency graph"
```

---

### Task 5: Create AppNode component

**Files:**
- Create: `apps/platform/src/features/apps/components/app-node.tsx`

- [ ] **Step 1: Create the custom node**

```tsx
"use client";

import { memo } from "react";
import { Handle, Position, type NodeProps, type Node } from "@xyflow/react";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { DependencyGraphNode, AppStatus } from "@/generated/app";

function statusBadgeVariant(
  status: AppStatus
): "default" | "outline" | "secondary" {
  switch (status) {
    case "ACTIVE":
      return "default";
    case "DRAFT":
      return "outline";
    case "INACTIVE":
      return "secondary";
    default:
      return "outline";
  }
}

export type AppNodeData = DependencyGraphNode & { isTransitive?: boolean; [key: string]: unknown };

function AppNodeComponent({ data }: NodeProps<Node<AppNodeData>>) {
  const firstLetter = data.name.charAt(0).toUpperCase();

  return (
    <div
      className={cn(
        "rounded-lg border bg-card px-4 py-3 shadow-sm w-[240px]",
        data.isCurrent && "border-accent ring-2 ring-accent/20",
        !data.isCurrent && "border-border",
        data.isTransitive && "opacity-65"
      )}
    >
      <Handle
        type="target"
        position={Position.Left}
        className="!size-2 !bg-muted-foreground !border-2 !border-card hover:!bg-primary"
      />
      <div className="flex items-center gap-2.5 mb-2">
        <div className="flex size-8 shrink-0 items-center justify-center rounded-md bg-gradient-to-br from-primary to-primary/70">
          <span className="text-sm font-bold text-primary-foreground">
            {firstLetter}
          </span>
        </div>
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold">{data.name}</p>
          <p className="truncate text-xs text-muted-foreground">{data.slug}</p>
        </div>
        <Badge variant={statusBadgeVariant(data.status)} className="text-[10px] shrink-0">
          {data.status}
        </Badge>
      </div>
      <div className="border-t pt-2">
        <span className="text-xs text-muted-foreground">
          {data.definitionCount} definitions
        </span>
      </div>
      <Handle
        type="source"
        position={Position.Right}
        className="!size-2 !bg-muted-foreground !border-2 !border-card hover:!bg-primary"
      />
    </div>
  );
}

export const AppNode = memo(AppNodeComponent);
```

- [ ] **Step 2: Commit**

```bash
git add apps/platform/src/features/apps/components/app-node.tsx
git commit -m "feat: custom AppNode component for dependency graph"
```

---

### Task 6: Create AddDependencyNode component

**Files:**
- Create: `apps/platform/src/features/apps/components/add-dependency-node.tsx`

- [ ] **Step 1: Create the plus-node**

```tsx
"use client";

import { memo } from "react";
import { Handle, Position, type NodeProps, type Node } from "@xyflow/react";
import { Plus } from "lucide-react";
import { useTranslations } from "next-intl";
import { cn } from "@/lib/utils";

export type AddDependencyNodeData = {
  onAdd: () => void;
  [key: string]: unknown;
};

function AddDependencyNodeComponent({
  data,
}: NodeProps<Node<AddDependencyNodeData>>) {
  const t = useTranslations("apps");

  return (
    <div
      className={cn(
        "flex w-[240px] cursor-pointer flex-col items-center justify-center",
        "rounded-lg border border-dashed border-muted-foreground/30 bg-card/50",
        "px-4 py-6 transition-colors hover:border-primary hover:bg-card"
      )}
      onClick={data.onAdd}
      data-testid="add-dependency-node"
    >
      <Handle
        type="target"
        position={Position.Left}
        className="!size-2 !bg-muted-foreground !border-2 !border-card"
      />
      <Plus className="size-6 text-muted-foreground" />
      <span className="mt-2 text-xs text-muted-foreground">
        {t("dependencies.addNode.label")}
      </span>
    </div>
  );
}

export const AddDependencyNode = memo(AddDependencyNodeComponent);
```

- [ ] **Step 2: Commit**

```bash
git add apps/platform/src/features/apps/components/add-dependency-node.tsx
git commit -m "feat: AddDependencyNode plus-node component"
```

---

### Task 7: Create DependencyEdge component

**Files:**
- Create: `apps/platform/src/features/apps/components/dependency-edge.tsx`

- [ ] **Step 1: Create the custom edge**

```tsx
"use client";

import { memo, useState } from "react";
import {
  BaseEdge,
  EdgeLabelRenderer,
  getStraightPath,
  type EdgeProps,
  type Edge,
  MarkerType,
} from "@xyflow/react";
import { X } from "lucide-react";
import { cn } from "@/lib/utils";

export type DependencyEdgeData = {
  direct: boolean;
  onDelete?: (edgeId: string) => void;
  [key: string]: unknown;
};

function DependencyEdgeComponent({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  data,
  markerEnd,
}: EdgeProps<Edge<DependencyEdgeData>>) {
  const [hovered, setHovered] = useState(false);

  const [edgePath, labelX, labelY] = getStraightPath({
    sourceX,
    sourceY,
    targetX,
    targetY,
  });

  const isDirect = data?.direct ?? false;

  return (
    <>
      {/* Invisible wider path for hover detection */}
      <path
        d={edgePath}
        fill="none"
        stroke="transparent"
        strokeWidth={20}
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => setHovered(false)}
      />
      <BaseEdge
        id={id}
        path={edgePath}
        markerEnd={markerEnd}
        style={{
          stroke: hovered
            ? "hsl(var(--primary))"
            : "hsl(var(--muted-foreground))",
          strokeWidth: 1,
          strokeDasharray: isDirect ? undefined : "4 4",
          opacity: isDirect ? 1 : 0.5,
          transition: "stroke 150ms ease",
        }}
      />
      {isDirect && hovered && data?.onDelete && (
        <EdgeLabelRenderer>
          <button
            className={cn(
              "pointer-events-auto absolute flex size-5 cursor-pointer items-center justify-center",
              "rounded-full bg-destructive text-destructive-foreground",
              "shadow-sm transition-transform hover:scale-110"
            )}
            style={{
              transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`,
            }}
            onClick={(e) => {
              e.stopPropagation();
              data.onDelete!(id);
            }}
            data-testid={`edge-delete-${id}`}
          >
            <X className="size-3" />
          </button>
        </EdgeLabelRenderer>
      )}
    </>
  );
}

export const DependencyEdge = memo(DependencyEdgeComponent);
```

- [ ] **Step 2: Commit**

```bash
git add apps/platform/src/features/apps/components/dependency-edge.tsx
git commit -m "feat: DependencyEdge with hover delete button"
```

---

### Task 8: Create AddDependencyPopover component

**Files:**
- Create: `apps/platform/src/features/apps/components/add-dependency-popover.tsx`

- [ ] **Step 1: Create the popover**

```tsx
"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2, Search } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  useListApps,
  useAddAppDependency,
  useListAppDependencies,
  getListAppDependenciesQueryKey,
  getGetAppDependencyGraphQueryKey,
  getListAppProblemsQueryKey,
  getListAppRevisionsQueryKey,
  getGetAppQueryKey,
  type ProblemDetail,
} from "@/generated/app";

interface AddDependencyPopoverContentProps {
  appId: string;
  onSuccess: () => void;
}

export function AddDependencyPopoverContent({
  appId,
  onSuccess,
}: AddDependencyPopoverContentProps) {
  const [search, setSearch] = useState("");
  const t = useTranslations("apps");
  const queryClient = useQueryClient();
  const addDependency = useAddAppDependency();

  const { data: appsData } = useListApps();
  const { data: depsData } = useListAppDependencies(appId);

  const allApps = appsData?.data?.items ?? [];
  const depsResponse = depsData?.data;
  const existingDeps =
    depsResponse && "items" in depsResponse ? depsResponse.items : [];
  const existingDepIds = new Set(existingDeps.map((d) => d.dependsOnAppId));

  const availableApps = allApps.filter(
    (app) =>
      app.id !== appId &&
      !existingDepIds.has(app.id) &&
      app.status === "ACTIVE" &&
      app.name.toLowerCase().includes(search.toLowerCase())
  );

  function invalidateQueries() {
    queryClient.invalidateQueries({
      queryKey: getListAppDependenciesQueryKey(appId),
    });
    queryClient.invalidateQueries({
      queryKey: getGetAppDependencyGraphQueryKey(appId),
    });
    queryClient.invalidateQueries({
      queryKey: getListAppProblemsQueryKey(appId),
    });
    queryClient.invalidateQueries({
      queryKey: getListAppRevisionsQueryKey(appId),
    });
    queryClient.invalidateQueries({
      queryKey: getGetAppQueryKey(appId),
    });
  }

  function handleSelect(dependsOnAppId: string) {
    addDependency.mutate(
      { appId, data: { dependsOnAppId } },
      {
        onSuccess: () => {
          toast.success(t("dependencies.addPopover.successToast"));
          invalidateQueries();
          onSuccess();
        },
        onError: (error: unknown) => {
          const err = error as ProblemDetail;
          if (err.status === 409) {
            toast.error(t("dependencies.addPopover.cyclicError"));
          } else if (err.status === 400) {
            toast.error(t("dependencies.addPopover.draftError"));
          } else {
            toast.error(err.detail ?? err.title);
          }
        },
      }
    );
  }

  return (
    <div className="w-[260px]" data-testid="add-dependency-popover">
      <p className="mb-2 text-sm font-medium">
        {t("dependencies.addPopover.title")}
      </p>
      <div className="relative mb-2">
        <Search className="absolute left-2 top-2.5 size-3.5 text-muted-foreground" />
        <Input
          placeholder={t("dependencies.addPopover.searchPlaceholder")}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="pl-8 h-8 text-sm"
          data-testid="add-dependency-search"
        />
      </div>
      <div className="max-h-[200px] overflow-y-auto">
        {availableApps.length === 0 ? (
          <p className="py-3 text-center text-xs text-muted-foreground">
            {t("dependencies.addPopover.noAppsAvailable")}
          </p>
        ) : (
          availableApps.map((app) => (
            <Button
              key={app.id}
              variant="ghost"
              size="sm"
              className="w-full justify-start gap-2 h-9"
              onClick={() => handleSelect(app.id)}
              disabled={addDependency.isPending}
              data-testid={`add-dep-option-${app.slug}`}
            >
              {addDependency.isPending ? (
                <Loader2 className="size-3.5 animate-spin" />
              ) : (
                <div className="flex size-6 shrink-0 items-center justify-center rounded bg-gradient-to-br from-primary to-primary/70">
                  <span className="text-[10px] font-bold text-primary-foreground">
                    {app.name.charAt(0).toUpperCase()}
                  </span>
                </div>
              )}
              <span className="truncate text-sm">{app.name}</span>
            </Button>
          ))
        )}
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/platform/src/features/apps/components/add-dependency-popover.tsx
git commit -m "feat: AddDependencyPopover for graph-based dependency adding"
```

---

### Task 9: Rewrite AppDependencyGraph (main component)

**Files:**
- Rewrite: `apps/platform/src/features/apps/components/app-dependency-graph.tsx`

This is the largest task. It replaces the entire existing component.

- [ ] **Step 1: Rewrite the component**

```tsx
"use client";

import { useMemo, useCallback, useState, useEffect } from "react";
import { useTranslations } from "next-intl";
import { useTheme } from "next-themes";
import {
  ReactFlow,
  Background,
  BackgroundVariant,
  Controls,
  MarkerType,
  useNodesState,
  useEdgesState,
  type Node,
  type Edge,
  type Connection,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { Network } from "lucide-react";
import { toast } from "sonner";
import { useQueryClient } from "@tanstack/react-query";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import {
  useGetAppDependencyGraph,
  useAddAppDependency,
  getListAppDependenciesQueryKey,
  getGetAppDependencyGraphQueryKey,
  getListAppProblemsQueryKey,
  getListAppRevisionsQueryKey,
  getGetAppQueryKey,
  type ProblemDetail,
} from "@/generated/app";
import { AppNode, type AppNodeData } from "./app-node";
import {
  AddDependencyNode,
  type AddDependencyNodeData,
} from "./add-dependency-node";
import {
  DependencyEdge,
  type DependencyEdgeData,
} from "./dependency-edge";
import { AddDependencyPopoverContent } from "./add-dependency-popover";
import { RemoveDependencyDialog } from "./remove-dependency-dialog";
import { computeElkLayout } from "../hooks/use-elk-layout";

const ADD_NODE_ID = "__add_dependency__";

const nodeTypes = {
  appNode: AppNode,
  addNode: AddDependencyNode,
};

const edgeTypes = {
  dependency: DependencyEdge,
};

interface AppDependencyGraphProps {
  appId: string;
}

export function AppDependencyGraph({ appId }: AppDependencyGraphProps) {
  const t = useTranslations("apps");
  const { resolvedTheme } = useTheme();
  const queryClient = useQueryClient();
  const { data, isLoading } = useGetAppDependencyGraph(appId);
  const addDependency = useAddAppDependency();

  const [nodes, setNodes, onNodesChange] = useNodesState([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState([]);
  const [popoverOpen, setPopoverOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<{
    depAppId: string;
    depAppName: string;
  } | null>(null);

  const graphData = data?.data;
  const graphNodes =
    graphData && "nodes" in graphData ? graphData.nodes : [];
  const graphEdges =
    graphData && "edges" in graphData ? graphData.edges : [];

  // Determine which nodes are transitive (not directly connected)
  const directNodeIds = useMemo(() => {
    const ids = new Set<string>();
    for (const edge of graphEdges) {
      if (edge.direct) {
        ids.add(edge.source);
        ids.add(edge.target);
      }
    }
    return ids;
  }, [graphEdges]);

  // Build React Flow nodes and edges from API data
  const { flowNodes, flowEdges } = useMemo(() => {
    if (graphNodes.length === 0) return { flowNodes: [], flowEdges: [] };

    const currentNode = graphNodes.find((n) => n.isCurrent);

    const appNodes: Node<AppNodeData>[] = graphNodes.map((node) => ({
      id: node.id,
      type: "appNode",
      position: { x: 0, y: 0 }, // ELK will reposition
      data: {
        ...node,
        isTransitive: !node.isCurrent && !directNodeIds.has(node.id),
      },
    }));

    // Add the plus-node
    const addNode: Node<AddDependencyNodeData> = {
      id: ADD_NODE_ID,
      type: "addNode",
      position: { x: 0, y: 0 },
      data: { onAdd: () => setPopoverOpen(true) },
    };

    const allNodes = [...appNodes, addNode];

    // Build edges
    const depEdges: Edge<DependencyEdgeData>[] = graphEdges.map(
      (edge, index) => ({
        id: `dep-${index}`,
        source: edge.source,
        target: edge.target,
        type: "dependency",
        data: {
          direct: edge.direct,
          onDelete: edge.direct
            ? (edgeId: string) => {
                const targetNode = graphNodes.find(
                  (n) => n.id === edge.target
                );
                if (targetNode) {
                  setDeleteTarget({
                    depAppId: edge.target,
                    depAppName: targetNode.name,
                  });
                }
              }
            : undefined,
        },
        markerEnd: {
          type: MarkerType.ArrowClosed,
          color: "hsl(var(--muted-foreground))",
        },
      })
    );

    // Edge from current app to plus-node (dashed, visual only)
    if (currentNode) {
      depEdges.push({
        id: "edge-add-node",
        source: currentNode.id,
        target: ADD_NODE_ID,
        type: "dependency",
        data: { direct: false },
        markerEnd: {
          type: MarkerType.ArrowClosed,
          color: "hsl(var(--muted-foreground))",
        },
      });
    }

    return { flowNodes: allNodes, flowEdges: depEdges };
  }, [graphNodes, graphEdges, directNodeIds]);

  // Run ELK layout when data changes
  useEffect(() => {
    if (flowNodes.length === 0) return;

    computeElkLayout(flowNodes, flowEdges).then((layoutedNodes) => {
      setNodes(layoutedNodes);
      setEdges(flowEdges);
    });
  }, [flowNodes, flowEdges, setNodes, setEdges]);

  function invalidateQueries() {
    queryClient.invalidateQueries({
      queryKey: getListAppDependenciesQueryKey(appId),
    });
    queryClient.invalidateQueries({
      queryKey: getGetAppDependencyGraphQueryKey(appId),
    });
    queryClient.invalidateQueries({
      queryKey: getListAppProblemsQueryKey(appId),
    });
    queryClient.invalidateQueries({
      queryKey: getListAppRevisionsQueryKey(appId),
    });
    queryClient.invalidateQueries({
      queryKey: getGetAppQueryKey(appId),
    });
  }

  // Drag-to-connect: validate connection
  const isValidConnection = useCallback(
    (connection: Connection) => {
      if (connection.source === connection.target) return false;
      if (
        connection.source === ADD_NODE_ID ||
        connection.target === ADD_NODE_ID
      )
        return false;
      // Check for existing edge
      const exists = edges.some(
        (e) =>
          e.source === connection.source && e.target === connection.target
      );
      return !exists;
    },
    [edges]
  );

  // Drag-to-connect: create dependency
  const onConnect = useCallback(
    (connection: Connection) => {
      if (!connection.source || !connection.target) return;
      addDependency.mutate(
        { appId, data: { dependsOnAppId: connection.target } },
        {
          onSuccess: () => {
            toast.success(t("dependencies.addPopover.successToast"));
            invalidateQueries();
          },
          onError: (error: unknown) => {
            const err = error as ProblemDetail;
            if (err.status === 409) {
              toast.error(t("dependencies.addPopover.cyclicError"));
            } else {
              toast.error(err.detail ?? err.title);
            }
          },
        }
      );
    },
    [appId, addDependency, t]
  );

  if (isLoading) {
    return (
      <div data-testid="app-dependency-graph" className="flex h-full items-center justify-center">
        <Skeleton className="h-full w-full rounded-lg" />
      </div>
    );
  }

  if (graphNodes.length === 0) {
    return (
      <div
        data-testid="app-dependency-graph"
        className="flex h-full flex-col items-center justify-center rounded-lg border border-dashed p-8 text-center"
      >
        <Network className="size-10 text-muted-foreground/50" />
        <p className="mt-3 text-sm text-muted-foreground">
          {t("dependencies.noDependencies")}
        </p>
      </div>
    );
  }

  return (
    <div data-testid="app-dependency-graph" className="relative h-full">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnect}
        isValidConnection={isValidConnection}
        colorMode={resolvedTheme === "dark" ? "dark" : "light"}
        fitView
        fitViewOptions={{ padding: 0.2, maxZoom: 1.5, duration: 300 }}
        proOptions={{ hideAttribution: true }}
        nodesDraggable={false}
        nodesConnectable
        zoomOnScroll={false}
        panOnScroll
      >
        <Background variant={BackgroundVariant.Dots} gap={20} size={1} />
        <Controls showInteractive={false} />
      </ReactFlow>

      {/* Popover for add dependency — positioned absolutely over the graph */}
      {popoverOpen && (
        <div className="absolute inset-0 z-10" onClick={() => setPopoverOpen(false)}>
          <div
            className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="rounded-xl border bg-background p-4 shadow-md">
              <AddDependencyPopoverContent
                appId={appId}
                onSuccess={() => setPopoverOpen(false)}
              />
            </div>
          </div>
        </div>
      )}

      {/* Remove dependency dialog */}
      {deleteTarget && (
        <RemoveDependencyDialog
          appId={appId}
          depAppId={deleteTarget.depAppId}
          depAppName={deleteTarget.depAppName}
          open
          onOpenChange={(open) => {
            if (!open) setDeleteTarget(null);
          }}
        />
      )}
    </div>
  );
}
```

Note: The `RemoveDependencyDialog` needs a small modification to accept `open` and `onOpenChange` props directly instead of using a trigger. This is handled in the next task.

- [ ] **Step 2: Commit**

```bash
git add apps/platform/src/features/apps/components/app-dependency-graph.tsx
git commit -m "feat: rewrite dependency graph with ELK.js layout and interactivity"
```

---

### Task 10: Update RemoveDependencyDialog to support controlled mode

**Files:**
- Modify: `apps/platform/src/features/apps/components/remove-dependency-dialog.tsx`

The dialog currently only works with a `DialogTrigger` child. We need it to also accept `open` and `onOpenChange` props for programmatic control from edge delete buttons.

- [ ] **Step 1: Update the component**

Replace the entire file with:

```tsx
"use client";

import { useState } from "react";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { useQueryClient } from "@tanstack/react-query";
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
  useRemoveAppDependency,
  getListAppDependenciesQueryKey,
  getGetAppDependencyGraphQueryKey,
  getListAppProblemsQueryKey,
  getListAppRevisionsQueryKey,
  getGetAppQueryKey,
} from "@/generated/app";

interface RemoveDependencyDialogProps {
  appId: string;
  depAppId: string;
  depAppName: string;
  children?: React.ReactNode;
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
}

export function RemoveDependencyDialog({
  appId,
  depAppId,
  depAppName,
  children,
  open: controlledOpen,
  onOpenChange: controlledOnOpenChange,
}: RemoveDependencyDialogProps) {
  const [internalOpen, setInternalOpen] = useState(false);
  const removeDependency = useRemoveAppDependency();
  const t = useTranslations("apps");
  const queryClient = useQueryClient();

  const isControlled = controlledOpen !== undefined;
  const open = isControlled ? controlledOpen : internalOpen;
  const onOpenChange = isControlled
    ? controlledOnOpenChange!
    : setInternalOpen;

  function invalidateQueries() {
    queryClient.invalidateQueries({
      queryKey: getListAppDependenciesQueryKey(appId),
    });
    queryClient.invalidateQueries({
      queryKey: getGetAppDependencyGraphQueryKey(appId),
    });
    queryClient.invalidateQueries({
      queryKey: getListAppProblemsQueryKey(appId),
    });
    queryClient.invalidateQueries({
      queryKey: getListAppRevisionsQueryKey(appId),
    });
    queryClient.invalidateQueries({
      queryKey: getGetAppQueryKey(appId),
    });
  }

  function handleConfirm() {
    removeDependency.mutate(
      { appId, depAppId },
      {
        onSuccess: () => {
          toast.success(t("dependencies.removeDialog.successToast"));
          invalidateQueries();
          onOpenChange(false);
        },
        onError: () => {
          toast.error(t("dependencies.removeDialog.errorToast"));
        },
      }
    );
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {children && (
        <DialogTrigger render={children as React.ReactElement} />
      )}
      <DialogContent
        data-testid="remove-dependency-dialog"
        showCloseButton={false}
      >
        <DialogHeader>
          <DialogTitle>{t("dependencies.removeDialog.title")}</DialogTitle>
          <DialogDescription>
            {t("dependencies.removeDialog.description")}
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={removeDependency.isPending}
          >
            {t("dependencies.removeDialog.cancel")}
          </Button>
          <Button
            type="button"
            variant="destructive"
            onClick={handleConfirm}
            aria-busy={removeDependency.isPending}
            disabled={removeDependency.isPending}
          >
            {removeDependency.isPending && (
              <Loader2 className="mr-2 size-4 animate-spin" />
            )}
            {t("dependencies.removeDialog.confirm")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add apps/platform/src/features/apps/components/remove-dependency-dialog.tsx
git commit -m "feat: support controlled mode in RemoveDependencyDialog"
```

---

### Task 11: Update page.tsx for fullscreen dependencies tab

**Files:**
- Modify: `apps/platform/src/app/(workspace)/apps/[appId]/page.tsx`

- [ ] **Step 1: Update the dependencies tab content**

In `page.tsx`, change the TabsContent for dependencies from:

```tsx
<TabsContent value="dependencies">
  <AppDependencyGraph appId={app.id} />
</TabsContent>
```

to:

```tsx
<TabsContent value="dependencies" className="flex-1">
  <div className="h-[calc(100vh-12rem)]">
    <AppDependencyGraph appId={app.id} />
  </div>
</TabsContent>
```

The `12rem` accounts for the AppHeader + Tabs header + padding. Adjust if needed after visual testing.

- [ ] **Step 2: Commit**

```bash
git add apps/platform/src/app/(workspace)/apps/[appId]/page.tsx
git commit -m "feat: fullscreen dependencies tab layout"
```

---

### Task 12: Delete AddDependencyDialog

**Files:**
- Delete: `apps/platform/src/features/apps/components/add-dependency-dialog.tsx`

- [ ] **Step 1: Verify no remaining imports**

Search for any remaining imports of `AddDependencyDialog` or `add-dependency-dialog`:

```bash
cd apps/platform && grep -r "add-dependency-dialog\|AddDependencyDialog" src/ --include="*.tsx" --include="*.ts"
```

Expected: No results (the import was removed when `app-dependency-graph.tsx` was rewritten).

- [ ] **Step 2: Delete the file**

```bash
rm apps/platform/src/features/apps/components/add-dependency-dialog.tsx
```

- [ ] **Step 3: Commit**

```bash
git add -A apps/platform/src/features/apps/components/add-dependency-dialog.tsx
git commit -m "chore: delete AddDependencyDialog, replaced by graph popover"
```

---

### Task 13: Visual testing and adjustments

**Files:** Various — adjustments based on testing

- [ ] **Step 1: Start the dev server**

```bash
pnpm dev
```

- [ ] **Step 2: Test the dependency graph**

Open `platform.localhost` in the browser. Navigate to an app with dependencies. Verify:

1. Graph renders fullscreen in the Dependencies tab
2. Nodes show correct metadata (icon, name, slug, status, definition count)
3. ELK.js positions nodes left-to-right without overlap
4. Plus-node appears and opens popover on click
5. Edges are straight with arrows, dashed for transitive
6. Hover on direct edge shows delete button
7. Delete button opens confirmation dialog
8. Dark mode: controls are visible, nodes look correct
9. Empty state shows when no dependencies exist
10. Branding: icon gradient uses tenant primary color, current app indicator uses accent color

- [ ] **Step 3: Fix any visual issues found**

Adjust spacing, colors, or layout as needed. Common adjustments:
- ELK spacing values (nodeNode, nodeNodeBetweenLayers)
- Node width
- Handle visibility
- Popover positioning
- Fullscreen height calculation

- [ ] **Step 4: Commit fixes**

```bash
git add -A
git commit -m "fix: dependency graph visual adjustments"
```

---

### Task 14: Update E2E tests

**Files:**
- Modify: `apps/e2e/tests/apps/app-dependencies.spec.ts`

- [ ] **Step 1: Read current E2E tests**

Read `apps/e2e/tests/apps/app-dependencies.spec.ts` to understand current test structure and selectors.

- [ ] **Step 2: Update tests for new interaction model**

Key changes:
- **Add dependency:** Instead of clicking a toolbar button + dialog, click `[data-testid="add-dependency-node"]` → search in `[data-testid="add-dependency-search"]` → click app option
- **Remove dependency:** Instead of clicking list remove button, hover over the edge element in `.react-flow__edge` → click `[data-testid^="edge-delete-"]` → confirm in dialog
- **Graph assertions:** Keep existing assertions for node visibility, adjust selectors if needed

The exact test code depends on the current test structure — read the file first and adapt.

- [ ] **Step 3: Run E2E tests**

```bash
cd apps/e2e && npx playwright test tests/apps/app-dependencies.spec.ts
```

- [ ] **Step 4: Commit**

```bash
git add apps/e2e/tests/apps/app-dependencies.spec.ts
git commit -m "test: update E2E tests for interactive dependency graph"
```
