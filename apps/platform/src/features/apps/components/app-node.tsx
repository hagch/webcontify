"use client";

import { memo } from "react";
import { Handle, Position, type NodeProps, type Node } from "@xyflow/react";
import { X } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
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

export type AppNodeData = DependencyGraphNode & {
  isTransitive?: boolean;
  onDelete?: (appId: string, appName: string) => void;
  [key: string]: unknown;
};

function AppNodeComponent({ data }: NodeProps<Node<AppNodeData>>) {
  // AppNode only renders accessible dependency nodes. Ghost/inaccessible
  // nodes get routed to a separate node type (Task 13); however, the
  // generated schema now marks these metadata fields as optional so we
  // defend with fallbacks here.
  const name = data.name ?? "";
  const slug = data.slug ?? "";
  const status = data.status;
  const firstLetter = name.charAt(0).toUpperCase();

  return (
    <div
      className={cn(
        "group relative rounded-lg border bg-card px-4 py-3 shadow-sm w-[240px]",
        data.isCurrent && "border-accent ring-2 ring-accent/20",
        !data.isCurrent && "border-border",
        data.isTransitive && "opacity-65"
      )}
    >
      <Handle
        type="target"
        position={Position.Left}
        className="!size-2 !bg-muted-foreground !border-2 !border-card hover:!bg-primary"
        isConnectable={!data.isCurrent}
      />
      {!data.isCurrent && !data.isTransitive && data.onDelete && (
        <Button
          variant="default"
          size="icon-xs"
          className={cn(
            "absolute -right-2 -top-2 rounded-full bg-destructive text-destructive-foreground shadow-sm",
            "opacity-0 transition-all group-hover:opacity-100 hover:scale-110 hover:bg-destructive/90"
          )}
          onClick={(e) => {
            e.stopPropagation();
            data.onDelete!(data.id, name);
          }}
          data-testid={`delete-dependency-${data.id}`}
        >
          <X className="size-3" />
        </Button>
      )}
      <div className="flex items-center gap-2.5 mb-2">
        <div className="flex size-8 shrink-0 items-center justify-center rounded-md bg-gradient-to-br from-primary to-primary/70">
          <span className="text-sm font-bold text-primary-foreground">
            {firstLetter}
          </span>
        </div>
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold">{name}</p>
          <p className="truncate text-xs text-muted-foreground">{slug}</p>
        </div>
        {status && (
          <Badge variant={statusBadgeVariant(status)} className="text-[10px] shrink-0">
            {status}
          </Badge>
        )}
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
        isConnectable={data.isCurrent}
      />
    </div>
  );
}

export const AppNode = memo(AppNodeComponent);
