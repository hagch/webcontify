"use client";

import { useTranslations } from "next-intl";
import { Badge } from "@/components/ui/badge";
import type {
  ContentEntryResponse,
  FieldDefinitionResponse,
} from "@/generated/content";
import { FieldCellRenderer } from "./entry-table-columns";
import { formatAuditDate } from "./entry-table-columns";

interface EntryDetailValuesProps {
  entry: ContentEntryResponse;
  fields: FieldDefinitionResponse[];
  appSlug: string;
  defSlug: string;
}

/**
 * Key-value layout showing all field values of an entry.
 */
export function EntryDetailValues({
  entry,
  fields,
  appSlug,
  defSlug,
}: EntryDetailValuesProps) {
  const t = useTranslations("entries.detail");

  const sortedFields = [...fields].sort((a, b) => a.position - b.position);

  return (
    <div data-testid="entry-detail-values" className="space-y-6">
      {/* User-defined fields */}
      <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        {sortedFields.map((field) => {
          const key = `cf_${field.fieldKey}`;
          const value = entry.values[key];

          return (
            <div key={field.id}>
              <dt className="text-sm text-muted-foreground">{field.fieldKey}</dt>
              <dd className="mt-1 font-medium">
                <FieldCellRenderer
                  field={field}
                  value={value}
                  appSlug={appSlug}
                  defSlug={defSlug}
                />
              </dd>
            </div>
          );
        })}
      </dl>

      {/* System fields */}
      <div className="border-t pt-4">
        <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <div>
            <dt className="text-sm text-muted-foreground">{t("fields.id")}</dt>
            <dd className="mt-1 font-mono text-xs">{entry.id}</dd>
          </div>
          <div>
            <dt className="text-sm text-muted-foreground">{t("fields.version")}</dt>
            <dd className="mt-1">
              <Badge variant="secondary">{entry.version}</Badge>
            </dd>
          </div>
          <div>
            <dt className="text-sm text-muted-foreground">
              {t("fields.createdAt")}
            </dt>
            <dd className="mt-1 text-sm">{formatAuditDate(entry.createdAt)}</dd>
          </div>
          <div>
            <dt className="text-sm text-muted-foreground">
              {t("fields.updatedAt")}
            </dt>
            <dd className="mt-1 text-sm">{formatAuditDate(entry.updatedAt)}</dd>
          </div>
          {entry.createdBy && (
            <div>
              <dt className="text-sm text-muted-foreground">
                {t("fields.createdBy")}
              </dt>
              <dd className="mt-1 font-mono text-xs">{entry.createdBy}</dd>
            </div>
          )}
          {entry.updatedBy && (
            <div>
              <dt className="text-sm text-muted-foreground">
                {t("fields.updatedBy")}
              </dt>
              <dd className="mt-1 font-mono text-xs">{entry.updatedBy}</dd>
            </div>
          )}
        </dl>
      </div>
    </div>
  );
}
