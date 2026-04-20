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
  canEdit?: boolean;
}

export function AppDependencyGraph({ appId, canEdit = false }: AppDependencyGraphProps) {
  const t = useTranslations("apps");
  const { resolvedTheme } = useTheme();
  const queryClient = useQueryClient();
  const { data, isLoading } = useGetAppDependencyGraph(appId);
  const addDependency = useAddAppDependency();

  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
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
      position: { x: 0, y: 0 },
      data: {
        ...node,
        isTransitive: !node.isCurrent && !directNodeIds.has(node.id),
        onDelete: canEdit && !node.isCurrent && directNodeIds.has(node.id)
          ? (appId: string, appName: string) => {
              setDeleteTarget({ depAppId: appId, depAppName: appName });
            }
          : undefined,
      },
    }));

    const allNodes: Node[] = [...appNodes];

    // Add the plus-node only for editors
    if (canEdit) {
      const addNode: Node<AddDependencyNodeData> = {
        id: ADD_NODE_ID,
        type: "addNode",
        position: { x: 0, y: 0 },
        data: { onAdd: () => setPopoverOpen(true) },
      };
      allNodes.push(addNode);
    }

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

    // Edge from current app to plus-node (dashed, visual only)
    if (canEdit && currentNode) {
      depEdges.push({
        id: "edge-add-node",
        source: currentNode.id,
        target: ADD_NODE_ID,
        type: "dependency",
        data: { direct: false },
        markerEnd: {
          type: MarkerType.ArrowClosed,
          color: "var(--muted-foreground)",
        },
      });
    }

    return { flowNodes: allNodes, flowEdges: depEdges };
  }, [graphNodes, graphEdges, directNodeIds, canEdit]);

  // Run ELK layout when data changes
  useEffect(() => {
    if (flowNodes.length === 0) return;

    computeElkLayout(flowNodes, flowEdges).then((layoutedNodes) => {
      setNodes(layoutedNodes);
      setEdges(flowEdges);
    });
  }, [flowNodes, flowEdges, setNodes, setEdges]);

  const invalidateQueries = useCallback(() => {
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
  }, [queryClient, appId]);

  // Drag-to-connect: validate connection
  const isValidConnection = useCallback(
    (connection: Edge | Connection) => {
      if (connection.source === connection.target) return false;
      if (
        connection.source === ADD_NODE_ID ||
        connection.target === ADD_NODE_ID
      )
        return false;
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
    [appId, addDependency, t, invalidateQueries]
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
    <div data-testid="app-dependency-graph" className="relative h-full overflow-hidden rounded-lg border">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={canEdit ? onConnect : undefined}
        isValidConnection={canEdit ? isValidConnection : undefined}
        colorMode={resolvedTheme === "dark" ? "dark" : "light"}
        fitView
        fitViewOptions={{ padding: 0.2, maxZoom: 1.5, duration: 300 }}
        proOptions={{ hideAttribution: true }}
        nodesDraggable={false}
        nodesConnectable={canEdit}
        zoomOnScroll={false}
        panOnScroll
      >
        <Background variant={BackgroundVariant.Dots} gap={20} size={1} />
        <Controls showInteractive={false} />
      </ReactFlow>

      {/* Popover for add dependency */}
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
