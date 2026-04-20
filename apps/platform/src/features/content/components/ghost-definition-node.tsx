"use client";

import { memo } from "react";
import { Handle, Position, type NodeProps, type Node } from "@xyflow/react";
import { X, Type, Hash, ToggleLeft, Calendar, List, Sigma, ArrowRight, ArrowLeftRight } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { useTranslations } from "next-intl";
import type {
  ContentDefinitionResponse,
  FieldDefinitionResponse,
  FieldType,
} from "@/generated/content";

export type GhostNodeData = {
  definition: ContentDefinitionResponse;
  appName: string;
  onRemoveGhost?: (definitionId: string, appId: string) => void;
  [key: string]: unknown;
};

const FIELD_TYPE_ICONS: Record<FieldType, typeof Type> = {
  TEXT: Type,
  INTEGER: Hash,
  DECIMAL: Sigma,
  BOOLEAN: ToggleLeft,
  DATE: Calendar,
  ENUM: List,
  REFERENCE: ArrowRight,
  MIRROR: ArrowLeftRight,
};

const MAX_VISIBLE_FIELDS = 5;

function GhostFieldRow({ field }: { field: FieldDefinitionResponse }) {
  const Icon = FIELD_TYPE_ICONS[field.fieldType] ?? Type;
  const isMirror = field.fieldType === "MIRROR";

  return (
    <div className="relative flex items-center gap-2 px-3 py-1 text-xs">
      <Handle
        type="target"
        position={Position.Left}
        id={`field-${field.id}-target`}
        className="!size-2 !bg-muted-foreground !border-2 !border-card hover:!bg-primary !left-0"
        isConnectable={false}
      />
      <Icon className="size-3 shrink-0 text-muted-foreground" />
      <span
        className={cn(
          "truncate flex-1 text-muted-foreground",
          isMirror && "italic"
        )}
      >
        {field.fieldKey}
      </span>
      <span className="text-[10px] text-muted-foreground uppercase shrink-0">
        {field.fieldType}
      </span>
    </div>
  );
}

function GhostDefinitionNodeComponent({
  data,
}: NodeProps<Node<GhostNodeData>>) {
  const t = useTranslations("content");
  const { definition, appName, onRemoveGhost } = data;
  const fields = definition.fields ?? [];
  const visibleFields = fields.slice(0, MAX_VISIBLE_FIELDS);
  const hiddenCount = fields.length - visibleFields.length;

  return (
    <div
      className="group relative w-[280px] rounded-lg border border-dashed border-border bg-card opacity-60 shadow-sm"
      data-testid={`ghost-node-${definition.id}`}
    >
      {/* Remove button */}
      {onRemoveGhost && (
        <Button
          variant="default"
          size="icon-xs"
          className={cn(
            "absolute -right-2 -top-2 rounded-full bg-destructive text-destructive-foreground shadow-sm",
            "opacity-0 transition-all group-hover:opacity-100 hover:scale-110 hover:bg-destructive/90"
          )}
          onClick={(e) => {
            e.stopPropagation();
            onRemoveGhost(definition.id, definition.appId);
          }}
          data-testid={`remove-ghost-${definition.id}`}
        >
          <X className="size-3" />
        </Button>
      )}

      {/* Header */}
      <div className="flex items-center gap-2 px-3 py-2 border-b">
        <div className="flex size-6 shrink-0 items-center justify-center rounded bg-muted">
          <span className="text-[10px] font-bold text-muted-foreground">
            {definition.name.charAt(0).toUpperCase()}
          </span>
        </div>
        <span className="truncate flex-1 text-sm font-semibold text-muted-foreground">
          {definition.name}
        </span>
        <Badge variant="outline" className="text-[10px] shrink-0">
          {t("graph.fromApp", { appName })}
        </Badge>
      </div>

      {/* Fields */}
      <div className="py-1">
        {visibleFields.map((field) => (
          <GhostFieldRow key={field.id} field={field} />
        ))}
        {hiddenCount > 0 && (
          <div className="px-3 py-1 text-[10px] text-muted-foreground">
            {t("graph.moreFields", { count: hiddenCount })}
          </div>
        )}
        {fields.length === 0 && (
          <div className="px-3 py-2 text-xs text-muted-foreground italic">
            {t("graph.noFields")}
          </div>
        )}
      </div>
    </div>
  );
}

export const GhostDefinitionNode = memo(GhostDefinitionNodeComponent);
