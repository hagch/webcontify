"use client";

import { useState, useCallback, useRef, useEffect } from "react";
import { useRouter } from "next/navigation";
import { MoreHorizontal, ArrowUp, ArrowDown, Eye, Pencil, Trash2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Skeleton } from "@/components/ui/skeleton";
import type {
  ContentDefinitionResponse,
  ContentEntryResponse,
  ContentEntryListResponse,
} from "@/generated/content";
import {
  useListContentEntries,
  useDeleteContentEntry,
  getListContentEntriesQueryKey,
} from "@/generated/content";
import { buildTableColumns, FieldCellRenderer } from "./entry-table-columns";
import { EntryFormDialog } from "./entry-form-dialog";
import { EntryDeleteDialog } from "./entry-delete-dialog";

interface EntryTableProps {
  definition: ContentDefinitionResponse;
  appSlug: string;
  allDefinitions?: ContentDefinitionResponse[];
}

interface SortCriterion {
  column: string;
  direction: "asc" | "desc";
}

const DEBOUNCE_MS = 400;

export function EntryTable({
  definition,
  appSlug,
  allDefinitions,
}: EntryTableProps) {
  const router = useRouter();
  const t = useTranslations("entries");
  const queryClient = useQueryClient();
  const [sorts, setSorts] = useState<SortCriterion[]>([]);
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [debouncedFilters, setDebouncedFilters] = useState<Record<string, string>>({});
  const [allEntries, setAllEntries] = useState<ContentEntryResponse[]>([]);
  const [cursor, setCursor] = useState<string | undefined>(undefined);
  const [deleteEntry, setDeleteEntry] = useState<ContentEntryResponse | null>(null);
  const filterTimers = useRef<Record<string, ReturnType<typeof setTimeout>>>({});

  // Build sort param string
  const sortParam = sorts.length > 0
    ? sorts.map((s) => `${s.column}:${s.direction}`).join(",")
    : undefined;

  // Build filter query params
  const filterParams: Record<string, string> = {};
  for (const [key, value] of Object.entries(debouncedFilters)) {
    if (value.trim()) {
      filterParams[key] = `like:${value}`;
    }
  }

  const { data, isLoading, isRefetching } = useListContentEntries(
    appSlug,
    definition.slug,
    {
      size: 20,
      cursor,
      sort: sortParam,
      ...filterParams,
    }
  );

  const responseData = data?.data as ContentEntryListResponse | undefined;
  const items = responseData?.items ?? [];
  const hasNext = responseData?.hasNext ?? false;
  const nextCursor = responseData?.nextCursor;

  // Accumulate entries for cursor pagination
  useEffect(() => {
    if (items.length > 0) {
      if (cursor) {
        setAllEntries((prev) => {
          const existingIds = new Set(prev.map((e) => e.id));
          const newItems = items.filter(
            (item: ContentEntryResponse) => !existingIds.has(item.id)
          );
          return [...prev, ...newItems];
        });
      } else {
        setAllEntries(items);
      }
    } else if (!cursor) {
      setAllEntries([]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data]);

  // Reset on sort/filter change
  useEffect(() => {
    setCursor(undefined);
    setAllEntries([]);
  }, [sortParam, debouncedFilters]);

  const columns = buildTableColumns(definition.fields);

  const handleSort = useCallback(
    (columnKey: string) => {
      setSorts((prev) => {
        const existing = prev.find((s) => s.column === columnKey);
        if (!existing) {
          const newSorts = [...prev, { column: columnKey, direction: "asc" as const }];
          return newSorts.slice(-3); // max 3 sorts
        }
        if (existing.direction === "asc") {
          return prev.map((s) =>
            s.column === columnKey ? { ...s, direction: "desc" as const } : s
          );
        }
        // Remove sort (was desc)
        return prev.filter((s) => s.column !== columnKey);
      });
    },
    []
  );

  const handleFilterChange = useCallback(
    (columnKey: string, value: string) => {
      setFilters((prev) => ({ ...prev, [columnKey]: value }));

      if (filterTimers.current[columnKey]) {
        clearTimeout(filterTimers.current[columnKey]);
      }
      filterTimers.current[columnKey] = setTimeout(() => {
        setDebouncedFilters((prev) => ({ ...prev, [columnKey]: value }));
      }, DEBOUNCE_MS);
    },
    []
  );

  const handleLoadMore = useCallback(() => {
    if (hasNext && nextCursor) {
      setCursor(nextCursor);
    }
  }, [hasNext, nextCursor]);

  if (isLoading && allEntries.length === 0) {
    return <EntryTableSkeleton columnCount={columns.length} />;
  }

  if (allEntries.length === 0 && !isLoading) {
    const hasFilters = Object.values(debouncedFilters).some((v) => v.trim());
    return (
      <div
        data-testid="entries-empty-state"
        className="flex flex-col items-center justify-center rounded-lg border border-dashed p-12 text-center"
      >
        <p className="text-lg font-medium">
          {hasFilters ? t("table.noResults") : t("empty")}
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="relative">
        {isRefetching && (
          <div className="absolute inset-0 z-10 flex items-center justify-center rounded-lg bg-background/60">
            <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          </div>
        )}
        <Table data-testid="entry-table">
          <TableHeader>
            <TableRow>
              {columns.map((col) => (
                <TableHead key={col.key}>
                  <div className="flex flex-col gap-1">
                    <button
                      type="button"
                      className="flex items-center gap-1 text-left hover:text-foreground"
                      onClick={() => col.sortable && handleSort(col.key)}
                      disabled={!col.sortable}
                      data-testid={`sort-${col.key}`}
                    >
                      {col.label}
                      {col.sortable && <SortIndicator sorts={sorts} columnKey={col.key} />}
                    </button>
                    {col.filterable && (
                      <Input
                        data-testid={`filter-${col.key}`}
                        className="h-6 text-xs"
                        placeholder={t("table.filter")}
                        value={filters[col.key] ?? ""}
                        onChange={(e) =>
                          handleFilterChange(col.key, e.target.value)
                        }
                      />
                    )}
                  </div>
                </TableHead>
              ))}
              <TableHead className="text-right">{t("table.actions")}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {allEntries.map((entry) => (
              <TableRow key={entry.id} data-testid="entry-table-row">
                {columns.map((col) => (
                  <TableCell key={col.key}>
                    <FieldCellRenderer
                      field={col.field}
                      value={entry.values[col.key]}
                      appSlug={appSlug}
                      defSlug={definition.slug}
                    />
                  </TableCell>
                ))}
                <TableCell className="text-right">
                  <DropdownMenu>
                    <DropdownMenuTrigger
                      render={
                        <Button
                          variant="ghost"
                          size="icon"
                          aria-label={t("table.actions")}
                          data-testid={`entry-actions-${entry.id}`}
                        />
                      }
                    >
                      <MoreHorizontal className="size-4" />
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      <DropdownMenuItem
                        onSelect={() =>
                          router.push(
                            `/apps/${appSlug}/content/${definition.slug}/entries/${entry.id}`
                          )
                        }
                      >
                        <Eye className="mr-2 size-4" />
                        {t("table.view")}
                      </DropdownMenuItem>
                      <EntryFormDialog
                        definition={definition}
                        appSlug={appSlug}
                        entry={entry}
                        allDefinitions={allDefinitions}
                      >
                        <DropdownMenuItem onSelect={(e) => e.preventDefault()}>
                          <Pencil className="mr-2 size-4" />
                          {t("table.edit")}
                        </DropdownMenuItem>
                      </EntryFormDialog>
                      <DropdownMenuItem
                        onSelect={() => setDeleteEntry(entry)}
                        className="text-destructive"
                      >
                        <Trash2 className="mr-2 size-4" />
                        {t("table.delete")}
                      </DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      {hasNext && (
        <div className="flex justify-center">
          <Button
            data-testid="load-more-entries"
            variant="outline"
            onClick={handleLoadMore}
            aria-busy={isRefetching}
          >
            {t("loadMore")}
          </Button>
        </div>
      )}

      {deleteEntry && (
        <EntryDeleteDialog
          appSlug={appSlug}
          defSlug={definition.slug}
          entry={deleteEntry}
          open={!!deleteEntry}
          onOpenChange={(open) => {
            if (!open) setDeleteEntry(null);
          }}
        />
      )}
    </div>
  );
}

function SortIndicator({
  sorts,
  columnKey,
}: {
  sorts: SortCriterion[];
  columnKey: string;
}) {
  const sort = sorts.find((s) => s.column === columnKey);
  if (!sort) return null;

  return sort.direction === "asc" ? (
    <ArrowUp className="size-3" />
  ) : (
    <ArrowDown className="size-3" />
  );
}

function EntryTableSkeleton({ columnCount }: { columnCount: number }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          {Array.from({ length: columnCount + 1 }).map((_, i) => (
            <TableHead key={i}>
              <Skeleton className="h-4 w-20" />
            </TableHead>
          ))}
        </TableRow>
      </TableHeader>
      <TableBody>
        {Array.from({ length: 5 }).map((_, rowIdx) => (
          <TableRow key={rowIdx}>
            {Array.from({ length: columnCount + 1 }).map((_, colIdx) => (
              <TableCell key={colIdx}>
                <Skeleton className="h-4 w-24" />
              </TableCell>
            ))}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
