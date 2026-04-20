"use client";

import { useState, useEffect } from "react";
import { useTranslations } from "next-intl";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  useGetContentEntryHistory,
  type EntryHistoryResponse,
  type EntryHistoryListResponse,
} from "@/generated/content";
import { formatAuditDate } from "./entry-table-columns";

interface EntryHistoryListProps {
  appSlug: string;
  defSlug: string;
  entryId: string;
}

/**
 * Paginated list of history records for an entry.
 */
export function EntryHistoryList({
  appSlug,
  defSlug,
  entryId,
}: EntryHistoryListProps) {
  const t = useTranslations("entries.history");
  const [cursor, setCursor] = useState<string | undefined>(undefined);
  const [allRecords, setAllRecords] = useState<EntryHistoryResponse[]>([]);

  const { data, isLoading } = useGetContentEntryHistory(
    appSlug,
    defSlug,
    entryId,
    { size: 20, cursor }
  );

  const responseData = data?.data as EntryHistoryListResponse | undefined;
  const items = responseData?.items ?? [];
  const hasNext = responseData?.hasNext ?? false;
  const nextCursor = responseData?.nextCursor;

  useEffect(() => {
    if (items.length > 0) {
      if (cursor) {
        setAllRecords((prev) => {
          const existingIds = new Set(prev.map((r) => r.id));
          const newItems = items.filter((item) => !existingIds.has(item.id));
          return [...prev, ...newItems];
        });
      } else {
        setAllRecords(items);
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data]);

  if (isLoading && allRecords.length === 0) {
    return (
      <div className="space-y-4">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-20 w-full" />
        ))}
      </div>
    );
  }

  if (allRecords.length === 0) {
    return (
      <div
        data-testid="history-empty"
        className="flex flex-col items-center justify-center rounded-lg border border-dashed p-12 text-center"
      >
        <p className="text-muted-foreground">{t("empty")}</p>
      </div>
    );
  }

  return (
    <div data-testid="entry-history-list" className="space-y-4">
      {allRecords.map((record) => (
        <HistoryRecord key={record.id} record={record} />
      ))}

      {hasNext && (
        <div className="flex justify-center">
          <Button
            data-testid="load-more-history"
            variant="outline"
            onClick={() => setCursor(nextCursor)}
          >
            {t("loadMore")}
          </Button>
        </div>
      )}
    </div>
  );
}

function HistoryRecord({ record }: { record: EntryHistoryResponse }) {
  const t = useTranslations("entries.history");

  const actionVariant =
    record.action === "CREATED"
      ? "default"
      : record.action === "DELETED"
        ? "destructive"
        : "secondary";

  const changedFields = record.changedFields as Record<
    string,
    { old?: unknown; new?: unknown }
  > | undefined;

  return (
    <div
      data-testid={`history-record-${record.id}`}
      className="rounded-lg border p-4"
    >
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Badge variant={actionVariant as "default" | "destructive" | "secondary"}>
            {t(`action.${record.action}` as "action.CREATED" | "action.UPDATED" | "action.DELETED")}
          </Badge>
          <span className="text-sm text-muted-foreground">
            {formatAuditDate(record.changedAt)}
          </span>
        </div>
        {record.changedBy && (
          <span className="font-mono text-xs text-muted-foreground">
            {record.changedBy}
          </span>
        )}
      </div>

      {changedFields && Object.keys(changedFields).length > 0 && (
        <div className="mt-3">
          <p className="mb-2 text-xs font-medium text-muted-foreground uppercase">
            {t("changedFields")}
          </p>
          <div className="space-y-1">
            {Object.entries(changedFields).map(([fieldKey, change]) => (
              <div
                key={fieldKey}
                className="flex items-center gap-2 text-sm"
              >
                <span className="font-mono text-xs">{fieldKey}</span>
                <span className="text-muted-foreground">
                  {t("oldValue")}:{" "}
                  <span className="line-through">
                    {formatChangeValue(change.old)}
                  </span>
                </span>
                <span className="text-muted-foreground">&rarr;</span>
                <span>
                  {t("newValue")}: {formatChangeValue(change.new)}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function formatChangeValue(value: unknown): string {
  if (value == null) return "-";
  if (typeof value === "boolean") return value ? "true" : "false";
  if (typeof value === "object") return JSON.stringify(value);
  return String(value);
}
