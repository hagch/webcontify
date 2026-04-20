"use client";

import { use, useMemo } from "react";
import { notFound } from "next/navigation";
import {
  useGetApp,
  useGetVersionSchema,
  type AppResponse,
  type VersionSchemaDefinition,
} from "@/generated/app";
import type { ContentDefinitionResponse } from "@/generated/content";
import { ContentDefinitionGraph } from "@/features/content/components/content-definition-graph";
import { AppHeader } from "@/components/layout/app-header";
import { Skeleton } from "@/components/ui/skeleton";
import type { AppResponseEffectiveActionsItem } from "@/generated/app";
import { AppPublishAction } from "@/features/apps/components/app-publish-action";
import { VersionInfoBar } from "@/components/layout/version-info-bar";
import { useAppVersion } from "@/contexts/app-version-context";

function isAppResponse(data: unknown): data is AppResponse {
  return (
    typeof data === "object" && data !== null && "name" in data && "slug" in data
  );
}

function mapSnapshotDefinition(
  def: VersionSchemaDefinition,
  appId: string,
): ContentDefinitionResponse {
  return {
    id: def.id,
    appId,
    name: def.name,
    slug: def.slug,
    description: def.description,
    displayFormat: def.displayFormat,
    fields: def.fields.map((f) => ({
      id: f.id,
      fieldKey: f.fieldKey,
      fieldType: f.fieldType as ContentDefinitionResponse["fields"][number]["fieldType"],
      required: f.required,
      unique: f.uniqueValue ?? false,
      sortable: f.sortable ?? false,
      filterable: f.filterable ?? false,
      config: f.config ? (JSON.parse(f.config) as ContentDefinitionResponse["fields"][number]["config"]) : undefined,
      position: f.position,
    })),
    createdAt: "",
    updatedAt: "",
    effectiveActions: [],
  };
}

export default function DefinitionsPage({
  params,
}: {
  params: Promise<{ appSlug: string }>;
}) {
  const { appSlug } = use(params);
  const { data, isLoading, error } = useGetApp(appSlug);
  const rawData = data?.data;
  const app = isAppResponse(rawData) ? rawData : undefined;
  const canEdit = (app?.effectiveActions ?? []).includes(
    "editor" as AppResponseEffectiveActionsItem,
  );

  const { isDraft, selectedVersion, isReadOnly, app: versionApp } = useAppVersion();

  const { data: snapshotData, isLoading: isSnapshotLoading } = useGetVersionSchema(
    versionApp.id,
    selectedVersion,
    { query: { enabled: !isDraft, staleTime: Infinity } },
  );

  const snapshotDefinitions = useMemo(() => {
    if (isDraft) return undefined;
    const defs = snapshotData?.data;
    if (!defs || !("contentDefinitions" in defs)) return undefined;
    return defs.contentDefinitions.map((def) =>
      mapSnapshotDefinition(def, versionApp.id),
    );
  }, [isDraft, snapshotData, versionApp.id]);

  const isPageLoading = isLoading || (!isDraft && isSnapshotLoading);

  if (error && !isLoading) notFound();

  return (
    <>
      <AppHeader
        mode="detail"
        breadcrumbs={[
          { label: isLoading ? "..." : (app?.name ?? appSlug) },
        ]}
        actions={<AppPublishAction />}
      />
      <VersionInfoBar />
      <div className="flex flex-1 flex-col p-4 md:p-6">
        {isPageLoading ? (
          <Skeleton className="h-[calc(100vh-9rem)] w-full" />
        ) : app ? (
          <div className="h-[calc(100vh-9rem)]">
            <ContentDefinitionGraph
              appSlug={app.slug}
              appId={app.id}
              canEdit={!isReadOnly && canEdit}
              snapshotDefinitions={snapshotDefinitions}
            />
          </div>
        ) : null}
      </div>
    </>
  );
}
