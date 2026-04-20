"use client";

import { use } from "react";
import { useTranslations } from "next-intl";
import { Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  useListContentDefinitions,
  type ContentDefinitionResponse,
} from "@/generated/content";
import { EntryTable } from "@/features/content-entries/components/entry-table";
import { EntryFormDialog } from "@/features/content-entries/components/entry-form-dialog";

export default function EntriesPage({
  params,
}: {
  params: Promise<{ appSlug: string; defSlug: string }>;
}) {
  const { appSlug, defSlug } = use(params);
  const t = useTranslations("entries");

  const { data: definitionsData, isLoading } =
    useListContentDefinitions(appSlug);

  const allDefinitions = definitionsData?.data?.items ?? [];
  const definition = allDefinitions.find(
    (d: ContentDefinitionResponse) => d.slug === defSlug
  );

  if (isLoading) {
    return (
      <div className="flex flex-1 flex-col gap-6 p-4 md:p-6">
        <div className="flex items-center justify-between">
          <Skeleton className="h-8 w-48" />
          <Skeleton className="h-9 w-32" />
        </div>
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  if (!definition) {
    return (
      <div className="flex flex-1 flex-col items-center justify-center p-12">
        <p className="text-lg font-medium">{t("notFound")}</p>
      </div>
    );
  }

  return (
    <div
      data-testid="entries-page"
      className="flex flex-1 flex-col gap-6 p-4 md:p-6"
    >
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold">{definition.name}</h1>
          <p className="text-sm text-muted-foreground">{t("title")}</p>
        </div>
        <EntryFormDialog
          definition={definition}
          appSlug={appSlug}
          allDefinitions={allDefinitions}
        >
          <Button data-testid="create-entry-button">
            <Plus className="mr-2 size-4" />
            {t("create")}
          </Button>
        </EntryFormDialog>
      </div>
      <EntryTable
        definition={definition}
        appSlug={appSlug}
        allDefinitions={allDefinitions}
      />
    </div>
  );
}
