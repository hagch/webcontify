"use client";

import { memo } from "react";
import { Handle, Position, type NodeProps, type Node } from "@xyflow/react";
import {
  Pencil,
  Trash2,
  Plus,
  ArrowRight,
  ArrowLeftRight,
  Type,
  Hash,
  ToggleLeft,
  Calendar,
  List,
  Sigma,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { useTranslations } from "next-intl";
import type {
  ContentDefinitionResponse,
  FieldDefinitionResponse,
  FieldType,
} from "@/generated/content";

export type DefinitionNodeData = {
  definition: ContentDefinitionResponse;
  onEditDefinition?: (id: string) => void;
  onEditField?: (definitionId: string, fieldId: string) => void;
  onAddField?: (definitionId: string) => void;
  onDeleteDefinition?: (id: string) => void;
  activeHandleSides?: Record<string, "left" | "right">;
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

function FieldRow({
  field,
  definitionId,
  onEditField,
  handleSide,
}: {
  field: FieldDefinitionResponse;
  definitionId: string;
  onEditField?: (definitionId: string, fieldId: string) => void;
  handleSide?: "left" | "right";
}) {
  const Icon = FIELD_TYPE_ICONS[field.fieldType] ?? Type;
  const isMirror = field.fieldType === "MIRROR";
  const isReference = field.fieldType === "REFERENCE";
  return (
    <div
      className="group/field relative flex items-center gap-2 px-3 py-1 text-xs hover:bg-muted/50 cursor-pointer"
      onDoubleClick={(e) => {
        e.stopPropagation();
        onEditField?.(definitionId, field.id);
      }}
      data-testid={`field-row-${field.id}`}
    >
      {(isReference || isMirror) && (
        <>
          <Handle
            type={isMirror ? "target" : "source"}
            position={Position.Left}
            id={`field-${field.id}-left`}
            className={cn(
              "!size-2 !border-2 !border-card !left-0",
              handleSide === "left" ? "!bg-primary" : "!bg-transparent !border-transparent"
            )}
            isConnectable={false}
          />
          <Handle
            type={isMirror ? "target" : "source"}
            position={Position.Right}
            id={`field-${field.id}-right`}
            className={cn(
              "!size-2 !border-2 !border-card !right-0",
              handleSide === "right" ? "!bg-primary" : "!bg-transparent !border-transparent"
            )}
            isConnectable={false}
          />
        </>
      )}
      <Icon className="size-3 shrink-0 text-muted-foreground" />
      <span
        className={cn(
          "truncate flex-1",
          isMirror && "italic text-muted-foreground"
        )}
      >
        {field.fieldKey}
      </span>
      {onEditField ? (
        <Button
          variant="ghost"
          size="icon-xs"
          className="size-4 rounded shrink-0 opacity-0 transition-opacity group-hover/field:opacity-100"
          onClick={(e) => {
            e.stopPropagation();
            onEditField(definitionId, field.id);
          }}
          data-testid={`edit-field-${field.id}`}
        >
          <Pencil className="size-2.5 text-muted-foreground" />
        </Button>
      ) : (
        <span className="text-[10px] text-muted-foreground uppercase shrink-0">
          {field.fieldType}
        </span>
      )}
    </div>
  );
}

function DefinitionNodeComponent({
  data,
}: NodeProps<Node<DefinitionNodeData>>) {
  const t = useTranslations("content");
  const { definition, onEditDefinition, onEditField, onAddField, onDeleteDefinition, activeHandleSides } = data;
  const fields = definition.fields ?? [];
  const visibleFields = fields.slice(0, MAX_VISIBLE_FIELDS);
  const hiddenCount = fields.length - visibleFields.length;

  return (
    <div
      className="group relative w-[280px] rounded-lg border border-border bg-card shadow-sm"
      data-testid={`definition-node-${definition.id}`}
    >
      {/* Header */}
      <div
        className="flex items-center gap-2 px-3 py-2 border-b"
        onDoubleClick={() => onEditDefinition?.(definition.id)}
      >
        <div className="flex size-6 shrink-0 items-center justify-center rounded bg-gradient-to-br from-primary to-primary/70">
          <span className="text-[10px] font-bold text-primary-foreground">
            {definition.name.charAt(0).toUpperCase()}
          </span>
        </div>
        <span className="truncate flex-1 text-sm font-semibold">
          {definition.name}
        </span>
        <div className="flex items-center gap-1 opacity-0 transition-opacity group-hover:opacity-100">
          {onEditDefinition && (
            <Button
              variant="ghost"
              size="icon-xs"
              className="size-5 rounded"
              onClick={(e) => {
                e.stopPropagation();
                onEditDefinition(definition.id);
              }}
              data-testid={`edit-definition-${definition.id}`}
            >
              <Pencil className="size-3 text-muted-foreground" />
            </Button>
          )}
          {onDeleteDefinition && (
            <Button
              variant="ghost"
              size="icon-xs"
              className="size-5 rounded hover:bg-destructive/10 hover:text-destructive"
              onClick={(e) => {
                e.stopPropagation();
                onDeleteDefinition(definition.id);
              }}
              data-testid={`delete-definition-${definition.id}`}
            >
              <Trash2 className="size-3 text-destructive" />
            </Button>
          )}
        </div>
      </div>

      {/* Fields */}
      <div className="py-1">
        {visibleFields.map((field) => (
          <FieldRow
            key={field.id}
            field={field}
            definitionId={definition.id}
            onEditField={onEditField}
            handleSide={activeHandleSides?.[field.id]}
          />
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

      {/* Add field button */}
      {onAddField && (
        <div className="border-t px-3 py-1.5">
          <Button
            variant="ghost"
            size="xs"
            className="w-full text-muted-foreground"
            onClick={(e) => {
              e.stopPropagation();
              onAddField(definition.id);
            }}
            data-testid={`add-field-${definition.id}`}
          >
            <Plus className="size-3" />
            {t("graph.addField")}
          </Button>
        </div>
      )}
    </div>
  );
}

export const DefinitionNode = memo(DefinitionNodeComponent);
