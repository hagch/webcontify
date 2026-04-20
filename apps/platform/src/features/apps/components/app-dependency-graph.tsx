"use client";

import { useMemo, useCallback } from "react";
import { useTranslations } from "next-intl";
import {
  ReactFlow,
  Background,
  Controls,
  type Node,
  type Edge,
  type NodeProps,
  Handle,
  Position,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { Plus, Trash2, Network } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  useGetAppDependencyGraph,
  type DependencyGraphNode,
  type AppStatus,
} from "@/generated/app";
import { AddDependencyDialog } from "./add-dependency-dialog";
import { RemoveDependencyDialog } from "./remove-dependency-dialog";

function statusBadgeVariant(
  status: AppStatus
): "default" | "outline" | "secondary" {
  switch (status) {
    case "DRAFT":
      return "outline";
    case "ACTIVE":
      return "default";
    case "INACTIVE":
      return "secondary";
    default:
      return "outline";
  }
}

type AppNodeData = DependencyGraphNode & { [key: string]: unknown };

function AppNode({ data }: NodeProps<Node<AppNodeData>>) {
  const t = useTranslations("apps");

  return (
    <div
      className={`rounded-lg border bg-card px-4 py-3 shadow-sm min-w-[180px] ${
        data.isCurrent
          ? "border-emerald-500 ring-2 ring-emerald-500/20"
          : "border-border"
      }`}
    >
      <Handle type="target" position={Position.Left} className="!bg-border" />
      <div className="flex flex-col gap-1.5">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium truncate max-w-[150px]">
            {data.name}
          </span>
          {data.isCurrent && (
            <Badge
              variant="outline"
              className="border-emerald-500 text-emerald-600 text-[10px] px-1 py-0"
            >
              {t("dependencies.graph.currentApp")}
            </Badge>
          )}
        </div>
        <div className="flex items-center gap-2">
          <Badge variant={statusBadgeVariant(data.status)} className="text-xs">
            {t(`statusValues.${data.status}`)}
          </Badge>
          <span className="text-xs text-muted-foreground">
            {data.definitionCount} defs
          </span>
        </div>
      </div>
      <Handle type="source" position={Position.Right} className="!bg-border" />
    </div>
  );
}

const nodeTypes = { appNode: AppNode };

interface AppDependencyGraphProps {
  appId: string;
}

export function AppDependencyGraph({ appId }: AppDependencyGraphProps) {
  const t = useTranslations("apps");
  const { data, isLoading } = useGetAppDependencyGraph(appId);

  const graphData = data?.data;
  const graphNodes =
    graphData && "nodes" in graphData ? graphData.nodes : [];
  const graphEdges =
    graphData && "edges" in graphData ? graphData.edges : [];

  const { nodes, edges } = useMemo(() => {
    if (graphNodes.length === 0) return { nodes: [], edges: [] };

    // Find the current app node
    const currentNode = graphNodes.find((n) => n.isCurrent);
    const depNodes = graphNodes.filter((n) => !n.isCurrent);

    // Simple tree layout: current app on the right, dependencies to the left
    const HORIZONTAL_SPACING = 280;
    const VERTICAL_SPACING = 100;

    const flowNodes: Node<AppNodeData>[] = [];

    // Position current app on the right
    if (currentNode) {
      const yCenter = ((depNodes.length - 1) * VERTICAL_SPACING) / 2;
      flowNodes.push({
        id: currentNode.id,
        type: "appNode",
        position: { x: HORIZONTAL_SPACING, y: Math.max(0, yCenter) },
        data: { ...currentNode },
      });
    }

    // Position dependencies to the left
    depNodes.forEach((node, index) => {
      flowNodes.push({
        id: node.id,
        type: "appNode",
        position: { x: 0, y: index * VERTICAL_SPACING },
        data: { ...node },
      });
    });

    const flowEdges: Edge[] = graphEdges.map((edge, index) => ({
      id: `edge-${index}`,
      source: edge.source,
      target: edge.target,
      style: edge.direct ? {} : { strokeDasharray: "5 5" },
      label: edge.direct
        ? t("dependencies.direct")
        : t("dependencies.transitive"),
      labelStyle: { fontSize: 10, fill: "hsl(var(--muted-foreground))" },
    }));

    return { nodes: flowNodes, edges: flowEdges };
  }, [graphNodes, graphEdges, t]);

  const directDeps = graphNodes.filter(
    (n) =>
      !n.isCurrent &&
      graphEdges.some((e) => e.target === n.id && e.direct)
  );

  const onInit = useCallback(() => {
    // noop — React Flow initializes itself
  }, []);

  if (isLoading) {
    return (
      <div data-testid="app-dependency-graph" className="space-y-4">
        <Skeleton className="h-10 w-48" />
        <Skeleton className="h-[400px] w-full rounded-lg" />
      </div>
    );
  }

  return (
    <div data-testid="app-dependency-graph" className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-medium text-muted-foreground uppercase tracking-wider">
          {t("dependencies.title")}
        </h3>
        <AddDependencyDialog appId={appId}>
          <Button size="sm">
            <Plus className="mr-2 size-4" />
            {t("dependencies.addDependency")}
          </Button>
        </AddDependencyDialog>
      </div>

      {nodes.length <= 1 ? (
        <div className="flex flex-col items-center justify-center rounded-lg border border-dashed p-8 text-center">
          <Network className="size-10 text-muted-foreground/50" />
          <p className="mt-3 text-sm text-muted-foreground">
            {t("dependencies.noDependencies")}
          </p>
        </div>
      ) : (
        <>
          <div className="h-[400px] rounded-lg border bg-muted/20">
            <ReactFlow
              nodes={nodes}
              edges={edges}
              nodeTypes={nodeTypes}
              onInit={onInit}
              fitView
              proOptions={{ hideAttribution: true }}
              nodesDraggable={false}
              nodesConnectable={false}
              zoomOnScroll={false}
              panOnScroll
            >
              <Background />
              <Controls showInteractive={false} />
            </ReactFlow>
          </div>

          {/* Direct dependencies list with remove buttons */}
          {directDeps.length > 0 && (
            <div className="space-y-2">
              {directDeps.map((dep) => (
                <div
                  key={dep.id}
                  className="flex items-center justify-between rounded-lg border p-3"
                >
                  <div className="flex items-center gap-3">
                    <span className="text-sm font-medium">{dep.name}</span>
                    <Badge
                      variant={statusBadgeVariant(dep.status)}
                      className="text-xs"
                    >
                      {t(`statusValues.${dep.status}`)}
                    </Badge>
                  </div>
                  <RemoveDependencyDialog
                    appId={appId}
                    depAppId={dep.id}
                    depAppName={dep.name}
                  >
                    <Button variant="ghost" size="sm">
                      <Trash2 className="mr-1 size-3.5" />
                      {t("dependencies.removeDependency")}
                    </Button>
                  </RemoveDependencyDialog>
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}
