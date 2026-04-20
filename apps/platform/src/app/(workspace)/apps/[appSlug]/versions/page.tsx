"use client";

import { use } from "react";
import { notFound } from "next/navigation";
import { useGetApp, type AppResponse } from "@/generated/app";
import { AppVersionList } from "@/features/apps/components/app-version-list";
import { AppHeader } from "@/components/layout/app-header";
import { Skeleton } from "@/components/ui/skeleton";
import { AppPublishAction } from "@/features/apps/components/app-publish-action";
import { VersionInfoBar } from "@/components/layout/version-info-bar";

function isAppResponse(data: unknown): data is AppResponse {
  return (
    typeof data === "object" && data !== null && "name" in data && "slug" in data
  );
}

export default function VersionsPage({
  params,
}: {
  params: Promise<{ appSlug: string }>;
}) {
  const { appSlug } = use(params);
  const { data, isLoading, error } = useGetApp(appSlug);
  const rawData = data?.data;
  const app = isAppResponse(rawData) ? rawData : undefined;

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
      <div className="flex flex-1 flex-col gap-6 p-4 md:p-6">
        {isLoading ? (
          <Skeleton className="h-64 w-full" />
        ) : app ? (
          <AppVersionList appId={app.id} hasDraft={app.hasDraft ?? false} />
        ) : null}
      </div>
    </>
  );
}
