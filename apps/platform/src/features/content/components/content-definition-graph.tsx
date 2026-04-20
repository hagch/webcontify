"use client";

import { useMemo, useCallback, useState, useEffect, useRef } from "react";
import { useTranslations } from "next-intl";
import { useTheme } from "next-themes";
import {
  ReactFlow,
  Background,
  BackgroundVariant,
  Controls,
  MiniMap,
  MarkerType,
  useNodesState,
  useEdgesState,
  useReactFlow,
  ReactFlowProvider,
  useUpdateNodeInternals,
  type Node,
  type Edge,
  type Connection,
  type NodeChange,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { Plus, Search, LayoutGrid, Undo2, Redo2 } from "lucide-react";
import { toast } from "sonner";
import { useQueryClient } from "@tanstack/react-query";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import {
  useListContentDefinitions,
  useBatchUpdatePositions,
  useUndoContentChange,
  useRedoContentChange,
  getListContentDefinitionsQueryKey,
  type ContentDefinitionResponse,
  type ReferenceFieldConfig,
} from "@/generated/content";
import { DefinitionNode, type DefinitionNodeData } from "./definition-node";
import {
  GhostDefinitionNode,
} from "./ghost-definition-node";
import { ReferenceEdge, type ReferenceEdgeData } from "./reference-edge";
import { ContentSpotlight } from "./content-spotlight";
import { ConnectDialog } from "./connect-dialog";
import { DeleteDefinitionDialog } from "./delete-definition-dialog";
import { RestrictedDefinitionNode } from "./restricted-definition-node";
import { DefinitionPopover } from "./definition-popover";
import { FieldPopover, AddFieldPopover } from "./field-popover";
import { computeElkLayout } from "@/features/apps/hooks/use-elk-layout";

const nodeTypes = {
  definition: DefinitionNode,
  ghost: GhostDefinitionNode,
  restrictedDefinition: RestrictedDefinitionNode,
};

const edgeTypes = {
  reference: ReferenceEdge,
};

interface ContentDefinitionGraphProps {
  appSlug: string;
  appId: string;
  /**
   * App-level layout right. Drives drag&drop, connect edges, "+" button,
   * undo/redo, and spotlight "create definition" action. Equivalent to
   * editor:app:{appId}. Per-definition actions (rename, field add/edit,
   * delete) are derived from each definition's effectiveActions instead.
   */
  canEdit?: boolean;
}

function ContentDefinitionGraphInner({
  appSlug,
  canEdit = false,
}: ContentDefinitionGraphProps) {
  const t = useTranslations("content");
  const { resolvedTheme } = useTheme();
  const queryClient = useQueryClient();
  const { fitView, flowToScreenPosition, getNode, getNodes } = useReactFlow();
  const updateNodeInternals = useUpdateNodeInternals();

  const { data, isLoading } = useListContentDefinitions(appSlug);
  const batchUpdatePositions = useBatchUpdatePositions();
  const undoMutation = useUndoContentChange();
  const redoMutation = useRedoContentChange();

  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);

  const [spotlightOpen, setSpotlightOpen] = useState(false);
  const [creatingDefinition, setCreatingDefinition] = useState(false);
  const [editingDefinition, setEditingDefinition] = useState<{
    id: string;
  } | null>(null);
  const [editingField, setEditingField] = useState<{
    definitionId: string;
    fieldId: string;
  } | null>(null);
  const [addingField, setAddingField] = useState<{
    definitionId: string;
  } | null>(null);
  const [deletingDefinition, setDeletingDefinition] = useState<{
    id: string;
    name: string;
  } | null>(null);
  const pendingPopoverRef = useRef<(() => void) | null>(null);
  const openPopoverRef = useRef<(nodeId: string, openFn: () => void) => void>(() => {});
  const [connectDialog, setConnectDialog] = useState<{
    sourceDefId: string;
    sourceDefName: string;
    targetDefId: string;
  } | null>(null);

  const positionTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const changedPositionsRef = useRef(
    new Map<string, { x: number; y: number }>()
  );

  const definitions: ContentDefinitionResponse[] = useMemo(
    () => data?.data?.items ?? [],
    [data]
  );

  const referencedDefinitions = useMemo(
    () => data?.data?.referencedDefinitions ?? [],
    [data]
  );

  // Build nodes from definitions
  const { flowNodes, needsLayout } = useMemo(() => {
    if (definitions.length === 0 && referencedDefinitions.length === 0)
      return { flowNodes: [], needsLayout: false };

    let hasNodesWithoutPosition = false;

    const defNodes: Node<DefinitionNodeData>[] = definitions.map((def) => {
      const hasPosition =
        def.positionX != null && def.positionY != null;
      if (!hasPosition) hasNodesWithoutPosition = true;

      // Granular per-definition flags derived from effectiveActions returned
      // by the backend. Falls back to empty set for definitions without the
      // field populated (older server versions).
      const defActions = (def.effectiveActions ?? []) as ReadonlyArray<string>;
      const canEditDef = defActions.includes("editor");
      const canDeleteDef = defActions.includes("deleter");

      return {
        id: def.id,
        type: "definition",
        position: hasPosition
          ? { x: def.positionX!, y: def.positionY! }
          : { x: 0, y: 0 },
        data: {
          definition: def,
          onEditDefinition: canEditDef
            ? (id: string) => openPopoverRef.current(id, () => setEditingDefinition({ id }))
            : undefined,
          onEditField: canEditDef
            ? (definitionId: string, fieldId: string) =>
                openPopoverRef.current(definitionId, () => setEditingField({ definitionId, fieldId }))
            : undefined,
          onAddField: canEditDef
            ? (definitionId: string) => openPopoverRef.current(definitionId, () => setAddingField({ definitionId }))
            : undefined,
          onDeleteDefinition: canDeleteDef
            ? (id: string) =>
                setDeletingDefinition({ id, name: def.name })
            : undefined,
        },
      };
    });

    // Ghost nodes for REFERENCE targets not in items. Inaccessible entries
    // render as RestrictedDefinitionNode (no name leak). Accessible cross-app
    // entries remain handled by the existing ghost mechanism elsewhere.
    const ghostNodes: Node[] = referencedDefinitions
      .filter((ref) => !ref.accessible)
      .map((ref) => ({
        id: ref.id,
        type: "restrictedDefinition",
        position: { x: 0, y: 0 },
        data: {},
        draggable: false,
        selectable: false,
      }));
    if (ghostNodes.length > 0) hasNodesWithoutPosition = true;

    return {
      flowNodes: [...(defNodes as Node[]), ...ghostNodes],
      needsLayout: hasNodesWithoutPosition,
    };
  }, [definitions, referencedDefinitions, canEdit]);

  // Build edges from REFERENCE fields — recomputes when node positions change
  // Returns edges + a map of fieldId → handle side for each connected field
  const buildEdgesAndHandles = useCallback(
    (currentNodes: Node[]): { edges: Edge<ReferenceEdgeData>[]; handleSides: Record<string, "left" | "right"> } => {
      const defMap = new Map<string, ContentDefinitionResponse>(
        definitions.map((d) => [d.id, d])
      );
      const nodePositionMap = new Map<string, number>(
        currentNodes.map((n) => [n.id, n.position.x])
      );

      const refEdges: Edge<ReferenceEdgeData>[] = [];
      const handleSides: Record<string, "left" | "right"> = {};

      for (const def of definitions) {
        for (const field of def.fields ?? []) {
          if (field.fieldType === "REFERENCE" && field.config) {
            const config = field.config as ReferenceFieldConfig;
            const targetDef = defMap.get(config.targetDefinitionId);
            const isCrossApp = !targetDef;

            const sourceX = nodePositionMap.get(def.id) ?? 0;
            const targetX = nodePositionMap.get(config.targetDefinitionId) ?? 0;
            const sourceIsLeft = sourceX <= targetX;

            const sourceSide = sourceIsLeft ? "right" : "left";
            const targetSide = sourceIsLeft ? "left" : "right";

            handleSides[field.id] = sourceSide;

            const mirrorField = targetDef?.fields?.find(
              (f) => f.fieldType === "MIRROR" && f.config &&
                (f.config as { sourceFieldId?: string }).sourceFieldId === field.id
            );
            let targetHandleId: string | undefined;
            if (mirrorField) {
              handleSides[mirrorField.id] = targetSide;
              targetHandleId = `field-${mirrorField.id}-${targetSide}`;
            }

            refEdges.push({
              id: `ref-${field.id}`,
              source: def.id,
              sourceHandle: `field-${field.id}-${sourceSide}`,
              target: config.targetDefinitionId,
              targetHandle: targetHandleId,
              type: "reference",
              data: {
                relationType: config.relationType,
                crossApp: isCrossApp,
              },
              markerEnd: {
                type: MarkerType.ArrowClosed,
                color: "var(--primary)",
              },
            });
          }
        }
      }
      return { edges: refEdges, handleSides };
    },
    [definitions]
  );

  // Apply handle sides to node data so FieldRow knows which handle to render
  const applyHandleSides = useCallback(
    (currentNodes: Node[], handleSides: Record<string, "left" | "right">): Node[] => {
      return currentNodes.map((node) => {
        const data = node.data as DefinitionNodeData;
        const prev = data.activeHandleSides;
        // Only update if handle sides changed to avoid unnecessary re-renders
        const changed = !prev || Object.keys(handleSides).some((k) => prev[k] !== handleSides[k]);
        if (!changed) return node;
        return { ...node, data: { ...data, activeHandleSides: handleSides } };
      });
    },
    []
  );

  const updateEdgesAndHandles = useCallback(
    (currentNodes: Node[]) => {
      const { edges, handleSides } = buildEdgesAndHandles(currentNodes);
      const updatedNodes = applyHandleSides(currentNodes, handleSides);
      setNodes(updatedNodes);
      setEdges(edges);
      // Notify React Flow that handle positions changed
      for (const node of updatedNodes) {
        updateNodeInternals(node.id);
      }
    },
    [buildEdgesAndHandles, applyHandleSides, setNodes, setEdges, updateNodeInternals]
  );

  // Run ELK layout for nodes without positions or on initial load
  useEffect(() => {
    if (flowNodes.length === 0) return;

    if (needsLayout) {
      const { edges: initialEdges } = buildEdgesAndHandles(flowNodes);
      computeElkLayout(flowNodes, initialEdges).then((layoutedNodes) => {
        updateEdgesAndHandles(layoutedNodes);
        setTimeout(() => fitView({ padding: 0.2, duration: 300 }), 50);
      });
    } else {
      updateEdgesAndHandles(flowNodes);
    }
  }, [flowNodes, needsLayout, fitView, buildEdgesAndHandles, updateEdgesAndHandles]);

  // Debounced position save
  const handleNodesChange = useCallback(
    (changes: NodeChange[]) => {
      onNodesChange(changes);

      if (!canEdit) return;

      const positionChanges = changes.filter(
        (c) => c.type === "position" && c.dragging === false && c.position
      );

      if (positionChanges.length > 0) {
        // Update edge handles based on new positions
        updateEdgesAndHandles(getNodes());

        for (const change of positionChanges) {
          if (change.type === "position" && change.position) {
            changedPositionsRef.current.set(change.id, {
              x: Math.round(change.position.x),
              y: Math.round(change.position.y),
            });
          }
        }

        if (positionTimerRef.current) clearTimeout(positionTimerRef.current);
        positionTimerRef.current = setTimeout(() => {
          const positions = Array.from(
            changedPositionsRef.current.entries()
          ).map(([id, pos]) => ({
            definitionId: id,
            positionX: pos.x,
            positionY: pos.y,
          }));

          if (positions.length > 0) {
            batchUpdatePositions.mutate(
              { appSlug, data: { positions } },
              {
                onError: () => {
                  toast.error("Failed to save positions");
                },
              }
            );
            changedPositionsRef.current.clear();
          }
        }, 500);
      }
    },
    [onNodesChange, canEdit, appSlug, batchUpdatePositions, updateEdgesAndHandles, getNodes]
  );

  // Reset layout
  const handleResetLayout = useCallback(() => {
    computeElkLayout(nodes, edges).then((layoutedNodes) => {
      setNodes(layoutedNodes);
      setTimeout(() => fitView({ padding: 0.2, duration: 300 }), 50);

      // Save new positions
      if (canEdit) {
        const positions = layoutedNodes.map((n) => ({
          definitionId: n.id,
          positionX: Math.round(n.position.x),
          positionY: Math.round(n.position.y),
        }));
        if (positions.length > 0) {
          batchUpdatePositions.mutate({ appSlug, data: { positions } });
        }
      }
    });
  }, [nodes, edges, setNodes, fitView, canEdit, appSlug, batchUpdatePositions]);

  const invalidateQueries = useCallback(() => {
    queryClient.invalidateQueries({
      queryKey: getListContentDefinitionsQueryKey(appSlug),
    });
  }, [queryClient, appSlug]);

  // Focus node, shift left, then open popover after animation
  const openPopoverWithFocus = useCallback(
    (nodeId: string, openFn: () => void) => {
      const node = getNode(nodeId);
      if (!node) {
        openFn();
        return;
      }
      pendingPopoverRef.current = openFn;
      fitView({
        nodes: [node],
        padding: 0.8,
        duration: 300,
        maxZoom: 1.5,
      });
      // After fitView animation completes, open the popover
      setTimeout(() => {
        if (pendingPopoverRef.current) {
          pendingPopoverRef.current();
          pendingPopoverRef.current = null;
        }
      }, 350);
    },
    [getNode, fitView]
  );
  useEffect(() => {
    openPopoverRef.current = openPopoverWithFocus;
  }, [openPopoverWithFocus]);

  // Get screen position for a node (right side, vertically centered)
  const getNodeScreenPosition = useCallback(
    (nodeId: string) => {
      const node = getNode(nodeId);
      if (!node) return undefined;
      const nodeWidth = node.measured?.width ?? 220;
      const nodeHeight = node.measured?.height ?? 100;
      return flowToScreenPosition({
        x: node.position.x + nodeWidth + 16,
        y: node.position.y + nodeHeight / 2,
      });
    },
    [getNode, flowToScreenPosition]
  );

  // Drag-to-connect: validate connection
  const isValidConnection = useCallback(
    (connection: Edge | Connection) => {
      if (connection.source === connection.target) return false;
      const targetNode = nodes.find((n) => n.id === connection.target);
      if (!targetNode || targetNode.type !== "definition") return false;
      return true;
    },
    [nodes]
  );

  // Drag-to-connect: open connect dialog
  const onConnect = useCallback(
    (connection: Connection) => {
      if (!connection.source || !connection.target) return;
      const sourceDef = definitions.find((d) => d.id === connection.source);
      if (!sourceDef) return;

      setConnectDialog({
        sourceDefId: connection.source,
        sourceDefName: sourceDef.name,
        targetDefId: connection.target,
      });
    },
    [definitions, setConnectDialog]
  );

  // Focus a node on the canvas
  const handleFocusNode = useCallback(
    (definitionId: string) => {
      const node = nodes.find((n) => n.id === definitionId);
      if (node) {
        fitView({
          nodes: [node],
          padding: 0.5,
          duration: 500,
        });
      }
    },
    [nodes, fitView]
  );

  // Keyboard shortcuts
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      const isCtrlOrMeta = e.ctrlKey || e.metaKey;

      // Cmd+K: open spotlight
      if (isCtrlOrMeta && e.key === "k") {
        e.preventDefault();
        setSpotlightOpen(true);
        return;
      }

      if (!isCtrlOrMeta || !canEdit) return;

      if (e.key === "z" && !e.shiftKey) {
        e.preventDefault();
        undoMutation.mutate(
          { appSlug },
          {
            onSuccess: (res) => {
              if (res.status === 200) {
                toast.success(t("undo.success"));
                invalidateQueries();
              }
            },
            onError: () => {
              toast.info(t("undo.nothingToUndo"));
            },
          }
        );
      } else if (
        (e.key === "y") ||
        (e.key === "z" && e.shiftKey)
      ) {
        e.preventDefault();
        redoMutation.mutate(
          { appSlug },
          {
            onSuccess: (res) => {
              if (res.status === 200) {
                toast.success(t("redo.success"));
                invalidateQueries();
              }
            },
            onError: () => {
              toast.info(t("redo.nothingToRedo"));
            },
          }
        );
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [canEdit, appSlug, undoMutation, redoMutation, t, invalidateQueries, setSpotlightOpen]);

  if (isLoading) {
    return (
      <div
        data-testid="content-definition-graph"
        className="flex h-full items-center justify-center"
      >
        <Skeleton className="h-full w-full rounded-lg" />
      </div>
    );
  }

  if (definitions.length === 0 && !creatingDefinition) {
    return (
      <div
        data-testid="content-definition-graph"
        className="flex h-full flex-col items-center justify-center rounded-lg border border-dashed p-8 text-center"
      >
        <div className="flex size-12 items-center justify-center rounded-full bg-primary/10">
          <Plus className="size-6 text-primary" />
        </div>
        <p className="mt-3 text-sm text-muted-foreground">
          {t("noDefinitions")}
        </p>
        {canEdit && (
          <Button
            variant="outline"
            size="sm"
            className="mt-4"
            onClick={() => setCreatingDefinition(true)}
            data-testid="create-first-definition"
          >
            {t("addFirst")}
          </Button>
        )}
      </div>
    );
  }

  return (
    <div
      data-testid="content-definition-graph"
      className="relative h-full overflow-hidden rounded-lg border"
    >
      <style>{`.react-flow__minimap-node { rx: 25; ry: 25; }`}</style>
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        onNodesChange={handleNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={canEdit ? onConnect : undefined}
        isValidConnection={canEdit ? isValidConnection : undefined}
        colorMode={resolvedTheme === "dark" ? "dark" : "light"}
        snapToGrid
        snapGrid={[20, 20]}
        fitView
        fitViewOptions={{ padding: 0.2, maxZoom: 1.5, duration: 300 }}
        proOptions={{ hideAttribution: true }}
        nodesDraggable={canEdit}
        nodesConnectable={canEdit}
        zoomOnScroll={false}
        panOnScroll
      >
        <Background variant={BackgroundVariant.Dots} gap={20} size={1} />
        <Controls showInteractive={false} />
        <MiniMap
            nodeStrokeWidth={3}
            pannable
            zoomable
            className="!rounded-lg !border !border-border !shadow-sm"
          />
      </ReactFlow>

      {/* Toolbar */}
      <div className="absolute left-3 top-3 z-10 flex items-center gap-1.5">
        {canEdit && (
          <Button
            variant="outline"
            size="icon"
            className="size-8 bg-background/80 backdrop-blur-sm"
            onClick={() => setCreatingDefinition(true)}
            data-testid="create-definition"
          >
            <Plus className="size-4" />
          </Button>
        )}
        <Button
          variant="outline"
          size="icon"
          className="size-8 bg-background/80 backdrop-blur-sm"
          onClick={() => setSpotlightOpen(true)}
          title="⌘K"
          data-testid="open-spotlight"
        >
          <Search className="size-4" />
        </Button>
        <Button
          variant="outline"
          size="icon"
          className="size-8 bg-background/80 backdrop-blur-sm"
          onClick={handleResetLayout}
          title={t("graph.layoutReset")}
          data-testid="reset-layout"
        >
          <LayoutGrid className="size-4" />
        </Button>
        {canEdit && (
          <>
            <div className="mx-0.5 h-4 w-px bg-border" />
            <Button
              variant="outline"
              size="icon"
              className="size-8 bg-background/80 backdrop-blur-sm"
              onClick={() => undoMutation.mutate(
                { appSlug },
                {
                  onSuccess: (res) => {
                    if (res.status === 200) {
                      toast.success(t("undo.success"));
                      invalidateQueries();
                    }
                  },
                  onError: () => toast.info(t("undo.nothingToUndo")),
                }
              )}
              title={`${t("undo.title")} (⌘Z)`}
              data-testid="undo-button"
            >
              <Undo2 className="size-4" />
            </Button>
            <Button
              variant="outline"
              size="icon"
              className="size-8 bg-background/80 backdrop-blur-sm"
              onClick={() => redoMutation.mutate(
                { appSlug },
                {
                  onSuccess: (res) => {
                    if (res.status === 200) {
                      toast.success(t("redo.success"));
                      invalidateQueries();
                    }
                  },
                  onError: () => toast.info(t("redo.nothingToRedo")),
                }
              )}
              title={`${t("redo.title")} (⌘⇧Z)`}
              data-testid="redo-button"
            >
              <Redo2 className="size-4" />
            </Button>
          </>
        )}
      </div>

      {/* Spotlight */}
      <ContentSpotlight
        open={spotlightOpen}
        onOpenChange={setSpotlightOpen}
        currentAppSlug={appSlug}
        onCreateDefinition={() => setCreatingDefinition(true)}
        onAddGhostNode={() => {}}
        onFocusNode={handleFocusNode}
        canCreate={canEdit}
      />

      {/* Delete definition dialog */}
      {deletingDefinition && (
        <DeleteDefinitionDialog
          open={true}
          onOpenChange={(open) => {
            if (!open) setDeletingDefinition(null);
          }}
          appSlug={appSlug}
          definitionId={deletingDefinition.id}
          definitionName={deletingDefinition.name}
        />
      )}

      {/* Connect dialog */}
      {connectDialog && (
        <ConnectDialog
          open={true}
          onOpenChange={(open) => {
            if (!open) setConnectDialog(null);
          }}
          sourceDefinitionId={connectDialog.sourceDefId}
          sourceDefinitionName={connectDialog.sourceDefName}
          targetDefinitionId={connectDialog.targetDefId}
          appSlug={appSlug}
          onSuccess={() => {
            setConnectDialog(null);
            invalidateQueries();
          }}
        />
      )}

      {/* Create definition popover */}
      {creatingDefinition && (
        <DefinitionPopover
          mode="create"
          appSlug={appSlug}
          open={true}
          onOpenChange={(open) => {
            if (!open) setCreatingDefinition(false);
          }}
          onCreated={() => {
            setCreatingDefinition(false);
            invalidateQueries();
          }}
        />
      )}

      {/* Definition popover */}
      {editingDefinition && (
        <DefinitionPopover
          definitionId={editingDefinition.id}
          appSlug={appSlug}
          open={true}
          onOpenChange={(open) => {
            if (!open) setEditingDefinition(null);
          }}
          anchorPosition={getNodeScreenPosition(editingDefinition.id)}
        />
      )}

      {/* Field popover */}
      {editingField && (
        <FieldPopover
          definitionId={editingField.definitionId}
          fieldId={editingField.fieldId}
          appSlug={appSlug}
          open={true}
          onOpenChange={(open) => {
            if (!open) setEditingField(null);
          }}
          anchorPosition={getNodeScreenPosition(editingField.definitionId)}
        />
      )}

      {/* Add field popover */}
      {addingField && (
        <AddFieldPopover
          definitionId={addingField.definitionId}
          appSlug={appSlug}
          open={true}
          onOpenChange={(open) => {
            if (!open) setAddingField(null);
          }}
          anchorPosition={getNodeScreenPosition(addingField.definitionId)}
          onSuccess={() => {
            setAddingField(null);
            invalidateQueries();
          }}
        />
      )}
    </div>
  );
}

export function ContentDefinitionGraph(props: ContentDefinitionGraphProps) {
  return (
    <ReactFlowProvider>
      <ContentDefinitionGraphInner {...props} />
    </ReactFlowProvider>
  );
}
