"use client";

import { use, useState, useCallback } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { AppHeader } from "@/components/layout/app-header";
import { VersionInfoBar } from "@/components/layout/version-info-bar";
import {
  useListContentDefinitions,
  useGetContentEntry,
  type ContentDefinitionResponse,
  type ContentEntryResponse,
} from "@/generated/content";
import {
  EntryFormPage,
  EntryFormActions,
  type EntryFormActionsState,
} from "@/features/content-entries/components/entry-form-page";

export default function EditEntryPage({
  params,
}: {
  params: Promise<{ appSlug: string; defSlug: string; entryId: string }>;
}) {
  const { appSlug, defSlug, entryId } = use(params);
  const { data: definitionsData, isLoading: defsLoading } = useListContentDefinitions(appSlug);
  const { data: entryData, isLoading: entryLoading } = useGetContentEntry(appSlug, defSlug, entryId);

  const allDefinitions = definitionsData?.data?.items ?? [];
  const definition = allDefinitions.find(
    (d: ContentDefinitionResponse) => d.slug === defSlug
  );
  const entry = entryData?.data as ContentEntryResponse | undefined;
  const isLoading = defsLoading || entryLoading;

  const [actionsState, setActionsState] = useState<EntryFormActionsState | null>(null);
  const handleActionsReady = useCallback((state: EntryFormActionsState) => {
    setActionsState(state);
  }, []);

  if (isLoading) {
    return (
      <>
        <AppHeader mode="detail" breadcrumbs={[{ label: "..." }]} />
        <div className="flex flex-1 flex-col gap-6 p-4 md:p-6">
          <Skeleton className="h-64 w-full" />
        </div>
      </>
    );
  }

  if (!definition || !entry) return null;

  const entryLabel = resolveDisplayLabel(definition, entry);

  return (
    <>
      <AppHeader
        mode="detail"
        breadcrumbs={[
          {
            label: definition.name,
            href: `/apps/${appSlug}/data/${defSlug}`,
          },
          { label: entryLabel },
        ]}
        actions={actionsState && <EntryFormActions {...actionsState} />}
      />
      <VersionInfoBar />
      <div className="flex flex-1 flex-col gap-6 p-4 md:p-6">
        <EntryFormPage
          definition={definition}
          appSlug={appSlug}
          entry={entry}
          allDefinitions={allDefinitions}
          onActionsReady={handleActionsReady}
        />
      </div>
    </>
  );
}

/**
 * Resolves the display label for an entry using the definition's displayFormat.
 * Supports both {fieldKey} and {cf_fieldKey} placeholders.
 * Falls back to the first field value or entry ID.
 */
function resolveDisplayLabel(
  definition: ContentDefinitionResponse,
  entry: ContentEntryResponse
): string {
  const template = definition.displayFormat;
  if (template) {
    const result = template.replace(/\{([a-z0-9_]+)\}/g, (_, key: string) => {
      const columnName = key.startsWith("cf_") ? key : `cf_${key}`;
      const value = entry.values[columnName];
      if (typeof value === "object" && value !== null && "display" in value) {
        return (value as { display: string }).display;
      }
      return value != null ? String(value) : "";
    });
    if (result.trim()) return result;
  }

  // Fallback: first field value
  const firstValue = entry.values[`cf_${definition.fields[0]?.fieldKey}`];
  if (typeof firstValue === "object" && firstValue !== null && "display" in firstValue) {
    return (firstValue as { display: string }).display;
  }
  return String(firstValue ?? entry.id);
}
