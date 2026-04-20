"use client";

import { use, useState, useCallback } from "react";
import { useTranslations } from "next-intl";
import { Skeleton } from "@/components/ui/skeleton";
import { AppHeader } from "@/components/layout/app-header";
import { VersionInfoBar } from "@/components/layout/version-info-bar";
import {
  useListContentDefinitions,
  type ContentDefinitionResponse,
} from "@/generated/content";
import {
  EntryFormPage,
  EntryFormActions,
  type EntryFormActionsState,
} from "@/features/content-entries/components/entry-form-page";

export default function NewEntryPage({
  params,
}: {
  params: Promise<{ appSlug: string; defSlug: string }>;
}) {
  const { appSlug, defSlug } = use(params);
  const t = useTranslations("entries");
  const { data: definitionsData, isLoading } = useListContentDefinitions(appSlug);
  const allDefinitions = definitionsData?.data?.items ?? [];
  const definition = allDefinitions.find(
    (d: ContentDefinitionResponse) => d.slug === defSlug
  );

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

  if (!definition) return null;

  return (
    <>
      <AppHeader
        mode="detail"
        breadcrumbs={[
          {
            label: definition.name,
            href: `/apps/${appSlug}/data/${defSlug}`,
          },
          { label: t("create") },
        ]}
        actions={actionsState && <EntryFormActions {...actionsState} />}
      />
      <VersionInfoBar />
      <div className="flex flex-1 flex-col gap-6 p-4 md:p-6">
        <EntryFormPage
          definition={definition}
          appSlug={appSlug}
          allDefinitions={allDefinitions}
          onActionsReady={handleActionsReady}
        />
      </div>
    </>
  );
}
