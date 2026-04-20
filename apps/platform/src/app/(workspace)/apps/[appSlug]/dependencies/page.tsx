"use client";

import { use } from "react";
import { notFound } from "next/navigation";
import {
  useGetApp,
  useGetVersionSchema,
  type AppResponse,
  type VersionSchemaGraphNode,
  type VersionSchemaGraphEdge,
} from "@/generated/app";
import { AppDependencyGraph } from "@/features/apps/components/app-dependency-graph";
import { AppHeader } from "@/components/layout/app-header";
import { Skeleton } from "@/components/ui/skeleton";
import { AppPublishAction } from "@/features/apps/components/app-publish-action";
import { VersionInfoBar } from "@/components/layout/version-info-bar";
import { useAppVersion } from "@/contexts/app-version-context";

function isAppResponse(data: unknown): data is AppResponse {
  return (
    typeof data === "object" && data !== null && "name" in data && "slug" in data
  );
}

export default function DependenciesPage({
  params,
}: {
  params: Promise<{ appSlug: string }>;
}) {
  const { appSlug } = use(params);
  const { data, isLoading, error } = useGetApp(appSlug);
  const rawData = data?.data;
  const app = isAppResponse(rawData) ? rawData : undefined;

  const { isDraft, selectedVersion } = useAppVersion();

  // Load snapshot graph when viewing a published version
  const { data: schemaData, isLoading: isSchemaLoading } = useGetVersionSchema(
    app?.id ?? "",
    selectedVersion,
    { query: { enabled: !isDraft && !!app?.id && selectedVersion !== "draft" } },
  );

  const schemaResponse =
    !isDraft && schemaData?.data && "graphNodes" in schemaData.data
      ? schemaData.data
      : undefined;
  const snapshotNodes: VersionSchemaGraphNode[] | undefined =
    schemaResponse?.graphNodes;
  const snapshotEdges: VersionSchemaGraphEdge[] | undefined =
    schemaResponse?.graphEdges;

  if (error && !isLoading) notFound();

  const loading = isLoading || (!isDraft && isSchemaLoading);

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
        {loading ? (
          <Skeleton className="h-[calc(100vh-9rem)] w-full" />
        ) : app ? (
          <div className="h-[calc(100vh-9rem)]">
            <AppDependencyGraph
              appId={app.id}
              snapshotNodes={snapshotNodes}
              snapshotEdges={snapshotEdges}
            />
          </div>
        ) : null}
      </div>
    </>
  );
}
