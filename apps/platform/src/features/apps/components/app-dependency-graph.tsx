"use client";

import { useMemo, useCallback, useEffect } from "react";
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
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { Network, Info } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import {
  useGetAppDependencyGraph,
  type VersionSchemaGraphNode,
  type VersionSchemaGraphEdge,
} from "@/generated/app";
import { AppNode, type AppNodeData } from "./app-node";
import { RestrictedAppNode } from "./restricted-app-node";
import {
  DependencyEdge,
  type DependencyEdgeData,
} from "./dependency-edge";
import { computeElkLayout } from "../hooks/use-elk-layout";

const nodeTypes = {
  appNode: AppNode,
  restrictedAppNode: RestrictedAppNode,
};

const edgeTypes = {
  dependency: DependencyEdge,
};

interface AppDependencyGraphProps {
  appId: string;
  snapshotNodes?: VersionSchemaGraphNode[];
  snapshotEdges?: VersionSchemaGraphEdge[];
}

export function AppDependencyGraph({ appId, snapshotNodes, snapshotEdges }: AppDependencyGraphProps) {
  const t = useTranslations("apps");
  const { resolvedTheme } = useTheme();
  const useSnapshot = snapshotNodes !== undefined && snapshotEdges !== undefined;
  const { data, isLoading } = useGetAppDependencyGraph(appId, {
    query: { enabled: !useSnapshot },
  });

  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);

  const graphData = data?.data;
  const graphNodes = useMemo(() =>
    useSnapshot
      ? (snapshotNodes ?? [])
      : graphData && "nodes" in graphData ? graphData.nodes : [],
    [useSnapshot, snapshotNodes, graphData],
  );
  const graphEdges = useMemo(() =>
    useSnapshot
      ? (snapshotEdges ?? [])
      : graphData && "edges" in graphData ? graphData.edges : [],
    [useSnapshot, snapshotEdges, graphData],
  );

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

    // Route accessible nodes to AppNode; inaccessible nodes (no name/slug/
    // status metadata by server contract) become ghost placeholders.
    const appNodes: Node[] = graphNodes.map((node) => {
      if (!node.accessible) {
        return {
          id: node.id,
          type: "restrictedAppNode",
          position: { x: 0, y: 0 },
          data: { id: node.id },
          draggable: false,
          selectable: false,
        };
      }
      return {
        id: node.id,
        type: "appNode",
        position: { x: 0, y: 0 },
        data: {
          ...node,
          isTransitive: !node.isCurrent && !directNodeIds.has(node.id),
        } as AppNodeData,
      };
    });

    // Build edges
    const depEdges: Edge<DependencyEdgeData>[] = graphEdges.map(
      (edge, index) => ({
        id: `dep-${index}`,
        source: edge.source,
        target: edge.target,
        type: "dependency",
        data: {
          direct: edge.direct,
        },
        markerEnd: {
          type: MarkerType.ArrowClosed,
          color: "var(--muted-foreground)",
        },
      })
    );

    return { flowNodes: appNodes, flowEdges: depEdges };
  }, [graphNodes, graphEdges, directNodeIds]);

  // Run ELK layout when data changes
  useEffect(() => {
    if (flowNodes.length === 0) return;

    computeElkLayout(flowNodes, flowEdges).then((layoutedNodes) => {
      setNodes(layoutedNodes);
      setEdges(flowEdges);
    });
  }, [flowNodes, flowEdges, setNodes, setEdges]);

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
    <div data-testid="app-dependency-graph" className="relative h-full overflow-hidden rounded-lg border">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        colorMode={resolvedTheme === "dark" ? "dark" : "light"}
        fitView
        fitViewOptions={{ padding: 0.2, maxZoom: 1.5, duration: 300 }}
        proOptions={{ hideAttribution: true }}
        nodesDraggable={false}
        nodesConnectable={false}
        zoomOnScroll={false}
        panOnScroll
      >
        <Background variant={BackgroundVariant.Dots} gap={20} size={1} />
        <Controls showInteractive={false} />
      </ReactFlow>

      {/* Read-only info banner */}
      <div className="absolute bottom-12 left-1/2 -translate-x-1/2 z-10">
        <div className="flex items-center gap-2 rounded-lg border bg-background/90 px-3 py-1.5 text-xs text-muted-foreground shadow-sm backdrop-blur-sm">
          <Info className="size-3 shrink-0" />
          <span>{t("dependencies.readOnlyInfo")}</span>
        </div>
      </div>
    </div>
  );
}
