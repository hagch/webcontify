"use client";

import { memo, useState } from "react";
import {
  BaseEdge,
  getSmoothStepPath,
  Position,
  type EdgeProps,
  type Edge,
} from "@xyflow/react";

export type DependencyEdgeData = {
  direct: boolean;
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

  const [edgePath] = getSmoothStepPath({
    sourceX,
    sourceY,
    sourcePosition: Position.Right,
    targetX,
    targetY,
    targetPosition: Position.Left,
    borderRadius: 8,
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
          stroke: hovered ? "var(--primary)" : "var(--muted-foreground)",
          strokeWidth: 1,
          strokeDasharray: isDirect ? undefined : "4 4",
          opacity: isDirect ? 1 : 0.5,
          transition: "stroke 150ms ease",
        }}
      />
    </>
  );
}

export const DependencyEdge = memo(DependencyEdgeComponent);
