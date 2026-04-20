"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowLeft, Pencil, Trash2 } from "lucide-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Skeleton } from "@/components/ui/skeleton";
import type {
  ContentDefinitionResponse,
  ContentEntryResponse,
} from "@/generated/content";
import {
  useGetContentEntry,
  useListContentDefinitions,
} from "@/generated/content";
import { EntryDetailValues } from "./entry-detail-values";
import { EntryHistoryList } from "./entry-history-list";
import { EntryFormDialog } from "./entry-form-dialog";
import { EntryDeleteDialog } from "./entry-delete-dialog";

interface EntryDetailPageProps {
  appSlug: string;
  defSlug: string;
  entryId: string;
}

export function EntryDetailPage({
  appSlug,
  defSlug,
  entryId,
}: EntryDetailPageProps) {
  const router = useRouter();
  const t = useTranslations("entries");
  const [deleteOpen, setDeleteOpen] = useState(false);

  const { data: definitionsData, isLoading: defsLoading } =
    useListContentDefinitions(appSlug);
  const { data: entryData, isLoading: entryLoading } = useGetContentEntry(
    appSlug,
    defSlug,
    entryId
  );

  const allDefinitions = definitionsData?.data?.items ?? [];
  const definition = allDefinitions.find(
    (d: ContentDefinitionResponse) => d.slug === defSlug
  );
  const entry = entryData?.data as ContentEntryResponse | undefined;

  const isLoading = defsLoading || entryLoading;

  if (isLoading) {
    return (
      <div className="flex flex-1 flex-col gap-6 p-4 md:p-6">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  if (!definition || !entry) {
    return (
      <div className="flex flex-1 flex-col items-center justify-center p-12">
        <p className="text-lg font-medium">{t("notFound")}</p>
        <Link
          href={`/apps/${appSlug}/content/${defSlug}/entries`}
          className="mt-4 text-sm text-primary hover:underline"
        >
          {t("detail.back")}
        </Link>
      </div>
    );
  }

  return (
    <div
      data-testid="entry-detail-page"
      className="flex flex-1 flex-col gap-6 p-4 md:p-6"
    >
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Button
            variant="ghost"
            size="icon"
            onClick={() =>
              router.push(
                `/apps/${appSlug}/content/${defSlug}/entries`
              )
            }
            aria-label={t("detail.back")}
            data-testid="back-to-entries"
          >
            <ArrowLeft className="size-4" />
          </Button>
          <div>
            <h1 className="text-xl font-semibold">{definition.name}</h1>
            <p className="text-sm text-muted-foreground">
              {t("detail.title")}
            </p>
          </div>
        </div>
        <div className="flex gap-2">
          <EntryFormDialog
            definition={definition}
            appSlug={appSlug}
            entry={entry}
            allDefinitions={allDefinitions}
          >
            <Button variant="outline" data-testid="edit-entry-button">
              <Pencil className="mr-2 size-4" />
              {t("edit")}
            </Button>
          </EntryFormDialog>
          <Button
            variant="destructive"
            onClick={() => setDeleteOpen(true)}
            data-testid="delete-entry-button"
          >
            <Trash2 className="mr-2 size-4" />
            {t("delete")}
          </Button>
        </div>
      </div>

      {/* Tabs */}
      <Tabs defaultValue="details">
        <TabsList>
          <TabsTrigger value="details" data-testid="tab-details">
            {t("detail.tabs.details")}
          </TabsTrigger>
          <TabsTrigger value="history" data-testid="tab-history">
            {t("detail.tabs.history")}
          </TabsTrigger>
        </TabsList>

        <TabsContent value="details" className="mt-4">
          <div className="rounded-lg border p-6">
            <EntryDetailValues
              entry={entry}
              fields={definition.fields}
              appSlug={appSlug}
              defSlug={defSlug}
            />
          </div>
        </TabsContent>

        <TabsContent value="history" className="mt-4">
          <EntryHistoryList
            appSlug={appSlug}
            defSlug={defSlug}
            entryId={entryId}
          />
        </TabsContent>
      </Tabs>

      {/* Delete dialog */}
      <EntryDeleteDialog
        appSlug={appSlug}
        defSlug={defSlug}
        entry={entry}
        open={deleteOpen}
        onOpenChange={(open) => {
          setDeleteOpen(open);
          if (!open) {
            // Navigate back after successful delete
            router.push(`/apps/${appSlug}/content/${defSlug}/entries`);
          }
        }}
      />
    </div>
  );
}
