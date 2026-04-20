"use client";

import { useTranslations } from "next-intl";
import { Lock } from "lucide-react";
import { Handle, Position } from "@xyflow/react";

/**
 * Placeholder for REFERENCE targets that exist but the current user cannot
 * view (override mode) or cross-app targets without access. Never displays
 * the referenced definition's name — only the generic "restricted" label —
 * so override mode does not leak metadata about hidden definitions.
 */
export function RestrictedDefinitionNode() {
  const t = useTranslations("content.graph");
  return (
    <div
      data-testid="restricted-definition-node"
      className="flex items-center gap-2 rounded-md border border-dashed border-muted-foreground/40 bg-muted/30 px-4 py-3 text-sm text-muted-foreground"
      title={t("restrictedTooltip")}
    >
      <Handle
        type="target"
        position={Position.Left}
        isConnectable={false}
        className="!size-2 !bg-muted-foreground !border-2 !border-card"
      />
      <Lock className="size-4" />
      <span>{t("restrictedDefinition")}</span>
      <Handle
        type="source"
        position={Position.Right}
        isConnectable={false}
        className="!size-2 !bg-muted-foreground !border-2 !border-card"
      />
    </div>
  );
}
