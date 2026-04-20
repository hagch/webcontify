"use client";

import { use } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { Plus } from "lucide-react";
import { AppHeader } from "@/components/layout/app-header";
import { VersionInfoBar } from "@/components/layout/version-info-bar";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  useListContentDefinitions,
  type ContentDefinitionResponse,
} from "@/generated/content";
import { EntryTable } from "@/features/content-entries/components/entry-table";

export default function DataEntriesPage({
  params,
}: {
  params: Promise<{ appSlug: string; defSlug: string }>;
}) {
  const { appSlug, defSlug } = use(params);
  const router = useRouter();
  const t = useTranslations("entries");

  const { data: definitionsData, isLoading } = useListContentDefinitions(appSlug);
  const allDefinitions = definitionsData?.data?.items ?? [];
  const definition = allDefinitions.find(
    (d: ContentDefinitionResponse) => d.slug === defSlug
  );

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

  if (!definition) {
    return (
      <>
        <AppHeader mode="detail" breadcrumbs={[{ label: t("notFound") }]} />
        <div className="flex flex-1 flex-col items-center justify-center p-12">
          <p className="text-lg font-medium">{t("notFound")}</p>
        </div>
      </>
    );
  }

  return (
    <>
      <AppHeader
        mode="detail"
        breadcrumbs={[{ label: definition.name }]}
        actions={
          <Button
            data-testid="create-entry-button"
            onClick={() => router.push(`/apps/${appSlug}/data/${defSlug}/new`)}
          >
            <Plus className="mr-2 size-4" />
            {t("create")}
          </Button>
        }
      />
      <VersionInfoBar />
      <div data-testid="entries-page" className="flex flex-1 flex-col gap-6 p-4 md:p-6">
        <EntryTable definition={definition} appSlug={appSlug} allDefinitions={allDefinitions} />
      </div>
    </>
  );
}
