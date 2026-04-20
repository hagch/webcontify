"use client";

import { useTranslations } from "next-intl";
import { Lock } from "lucide-react";
import { Handle, Position } from "@xyflow/react";

/**
 * Placeholder for an app the current user cannot view in the dependency
 * graph. Never reveals name/slug/status — only the generic "restricted"
 * label — to prevent leaking information about isolated apps.
 */
export function RestrictedAppNode() {
  const t = useTranslations("apps.dependencies");
  return (
    <div
      data-testid="restricted-app-node"
      className="flex items-center gap-2 rounded-lg border border-dashed border-muted-foreground/40 bg-muted/30 px-4 py-3 w-[240px] text-sm text-muted-foreground"
      title={t("restrictedAppTooltip")}
    >
      <Handle
        type="target"
        position={Position.Left}
        isConnectable={false}
        className="!size-2 !bg-muted-foreground !border-2 !border-card"
      />
      <Lock className="size-4 shrink-0" />
      <span className="truncate">{t("restrictedApp")}</span>
      <Handle
        type="source"
        position={Position.Right}
        isConnectable={false}
        className="!size-2 !bg-muted-foreground !border-2 !border-card"
      />
    </div>
  );
}
