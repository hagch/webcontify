"use client";

import Link from "next/link";
import { Badge } from "@/components/ui/badge";
import { format as formatDateFns } from "date-fns";
import type {
  FieldDefinitionResponse,
  ContentDefinitionResponse,
  DateFieldConfig,
  ReferenceFieldConfig,
} from "@/generated/content";

interface FieldCellRendererProps {
  field: FieldDefinitionResponse;
  value: unknown;
  appSlug: string;
  defSlug: string;
  allDefinitions?: ContentDefinitionResponse[];
}

/**
 * Renders a cell value based on field type.
 * Used by EntryTable to render dynamic columns.
 */
export function FieldCellRenderer({
  field,
  value,
  appSlug,
  defSlug,
  allDefinitions,
}: FieldCellRendererProps) {
  if (value == null) {
    return <span className="text-muted-foreground">-</span>;
  }

  switch (field.fieldType) {
    case "BOOLEAN":
      return (
        <Badge
          data-testid={`cell-${field.fieldKey}-badge`}
          variant={value ? "default" : "secondary"}
        >
          {value ? "Yes" : "No"}
        </Badge>
      );

    case "DATE": {
      const dateConfig = field.config as DateFieldConfig | undefined;
      return (
        <span data-testid={`cell-${field.fieldKey}`}>
          {formatDate(String(value), dateConfig?.dateFormat)}
        </span>
      );
    }

    case "ENUM":
      return (
        <Badge
          data-testid={`cell-${field.fieldKey}-badge`}
          variant="outline"
        >
          {String(value)}
        </Badge>
      );

    case "REFERENCE": {
      // Value can be a UUID string or an object { id, display }
      if (typeof value === "object" && value !== null && "display" in value) {
        const ref = value as { id: string; display: string };
        const refConfig = field.config as ReferenceFieldConfig | undefined;
        const targetDef = allDefinitions?.find(
          (d) => d.id === refConfig?.targetDefinitionId
        );
        const targetSlug = targetDef?.slug ?? defSlug;
        return (
          <Link
            href={`/apps/${appSlug}/data/${targetSlug}/${ref.id}`}
            className="text-primary hover:underline"
            data-testid={`cell-${field.fieldKey}-link`}
          >
            {ref.display}
          </Link>
        );
      }
      return (
        <span
          className="font-mono text-xs text-muted-foreground"
          data-testid={`cell-${field.fieldKey}`}
        >
          {String(value).substring(0, 8)}...
        </span>
      );
    }

    case "MIRROR": {
      // Mirror values are arrays of references
      if (Array.isArray(value)) {
        return (
          <Badge
            data-testid={`cell-${field.fieldKey}-badge`}
            variant="secondary"
          >
            {value.length}
          </Badge>
        );
      }
      return <span className="text-muted-foreground">-</span>;
    }

    case "INTEGER":
    case "DECIMAL":
      return (
        <span
          className="font-mono"
          data-testid={`cell-${field.fieldKey}`}
        >
          {String(value)}
        </span>
      );

    case "TEXT":
    default:
      return (
        <span
          className="max-w-[200px] truncate"
          data-testid={`cell-${field.fieldKey}`}
        >
          {String(value)}
        </span>
      );
  }
}

/**
 * Builds column definitions from field definitions.
 */
export function buildTableColumns(fields: FieldDefinitionResponse[]) {
  return fields
    .sort((a, b) => a.position - b.position)
    .filter((f) => f.fieldType !== "MIRROR" || true) // show all including MIRROR
    .map((field) => ({
      key: `cf_${field.fieldKey}`,
      label: field.fieldKey,
      field,
      sortable: field.sortable,
      filterable: field.filterable,
    }));
}

function formatDate(dateString: string, dateFormat?: string): string {
  try {
    if (dateFormat) {
      return formatDateFns(new Date(dateString), dateFormat);
    }
    return new Intl.DateTimeFormat("en-US", {
      year: "numeric",
      month: "short",
      day: "numeric",
    }).format(new Date(dateString));
  } catch {
    return dateString;
  }
}

/**
 * Formats an audit timestamp for display.
 */
export function formatAuditDate(dateString: string): string {
  try {
    return new Intl.DateTimeFormat("en-US", {
      year: "numeric",
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    }).format(new Date(dateString));
  } catch {
    return dateString;
  }
}
