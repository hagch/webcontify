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
