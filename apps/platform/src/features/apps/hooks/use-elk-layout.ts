"use client";

import { useCallback, useRef } from "react";
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
