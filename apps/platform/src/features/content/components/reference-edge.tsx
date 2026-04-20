"use client";

import { memo, useState } from "react";
import {
  BaseEdge,
  getSmoothStepPath,
  Position,
  type EdgeProps,
  type Edge,
} from "@xyflow/react";

export type ReferenceEdgeData = {
  relationType: string;
  crossApp: boolean;
  [key: string]: unknown;
};

const RELATION_LABELS: Record<string, string> = {
  ONE_TO_ONE: "1:1",
  MANY_TO_ONE: "N:1",
  MANY_TO_MANY: "N:M",
};

function ReferenceEdgeComponent({
  id,
  sourceX,
  sourceY,
  sourcePosition,
  targetX,
  targetY,
  targetPosition,
  data,
  markerEnd,
}: EdgeProps<Edge<ReferenceEdgeData>>) {
  const [hovered, setHovered] = useState(false);

  const [edgePath, labelX, labelY] = getSmoothStepPath({
    sourceX,
    sourceY,
    sourcePosition: sourcePosition ?? Position.Right,
    targetX,
    targetY,
    targetPosition: targetPosition ?? Position.Left,
    borderRadius: 8,
  });

  const isCrossApp = data?.crossApp ?? false;
  const label = RELATION_LABELS[data?.relationType ?? ""] ?? data?.relationType;

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
            ? "var(--primary)"
            : isCrossApp
              ? "var(--emerald-500, #10B981)"
              : "var(--primary)",
          strokeWidth: hovered ? 2 : 1,
          strokeDasharray: isCrossApp ? "4 4" : undefined,
          opacity: isCrossApp && !hovered ? 0.6 : 1,
          transition: "stroke 150ms ease, stroke-width 150ms ease",
        }}
      />
      {/* Label */}
      {label && (
        <foreignObject
          x={labelX - 16}
          y={labelY - 10}
          width={32}
          height={20}
          className="pointer-events-none overflow-visible"
        >
          <div className="flex items-center justify-center">
            <span
              className={`rounded px-1 py-0.5 text-[10px] font-medium ${
                isCrossApp
                  ? "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400"
                  : "bg-primary/10 text-primary"
              }`}
            >
              {label}
            </span>
          </div>
        </foreignObject>
      )}
    </>
  );
}

export const ReferenceEdge = memo(ReferenceEdgeComponent);
