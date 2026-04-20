"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { useTranslations } from "next-intl";
import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar";
import { AppSidebarNav } from "@/components/layout/app-sidebar-nav";
import { getAppNavSections } from "@/components/layout/app-nav-config";
import type { ContentDefinitionSummary } from "@/components/layout/app-nav-config";
import { useGetApp, useListAppVersions, type AppResponse } from "@/generated/app";
import { useListContentDefinitions } from "@/generated/content";
import {
  AppVersionProvider,
  useAppVersion,
} from "@/contexts/app-version-context";
import { cn } from "@/lib/utils";

interface AppLayoutClientProps {
  children: React.ReactNode;
  appSlug: string;
}

function isAppResponse(data: unknown): data is AppResponse {
  return (
    typeof data === "object" && data !== null && "name" in data && "slug" in data
  );
}

export function AppLayoutClient({ children, appSlug }: AppLayoutClientProps) {
  const { data: appData } = useGetApp(appSlug);
  const { data: defsData } = useListContentDefinitions(appSlug);

  const rawData = appData?.data;
  const app = isAppResponse(rawData) ? rawData : undefined;
  const appId = app?.id;
  const { data: versionsData } = useListAppVersions(appId ?? "", {
    query: { enabled: !!appId },
  });

  const versions = versionsData?.data?.items ?? [];

  if (!app) {
    return null;
  }

  const definitions: ContentDefinitionSummary[] = (
    defsData?.data?.items ?? []
  ).map((d) => ({
    slug: d.slug,
    name: d.name,
  }));

  return (
    <AppVersionProvider app={app} versions={versions}>
      <AppLayoutInner appSlug={appSlug} definitions={definitions}>
        {children}
      </AppLayoutInner>
    </AppVersionProvider>
  );
}

interface AppLayoutInnerProps {
  children: React.ReactNode;
  appSlug: string;
  definitions: ContentDefinitionSummary[];
}

function AppLayoutInner({ children, appSlug, definitions }: AppLayoutInnerProps) {
  const t = useTranslations("nav");
  const pathname = usePathname();
  const router = useRouter();
  const {
    selectedVersion,
    setSelectedVersion,
    isDraft,
    isReadOnly,
    isDataVisible,
    app,
    versions,
  } = useAppVersion();

  const appName = app.name ?? appSlug;
  const hasPublishedVersion =
    app.status === "ACTIVE" || app.status === "INACTIVE";

  const sections = getAppNavSections(
    { appSlug, definitions, hasPublishedVersion, isDataVisible },
    t,
  );

  useEffect(() => {
    if (!isDataVisible && pathname.includes("/data/")) {
      router.replace(`/apps/${appSlug}/overview`);
    }
  }, [isDataVisible, pathname, appSlug, router]);

  return (
    <SidebarProvider>
      <AppSidebarNav
        appName={appName}
        appSlug={appSlug}
        sections={sections}
        selectedVersion={selectedVersion}
        versions={versions}
        hasDraft={app.hasDraft ?? false}
        hasUnpublishedChanges={isDraft && hasPublishedVersion}
        unpublishedChangeCount={app.unpublishedChangeCount ?? 0}
        onVersionSelect={setSelectedVersion}
      />
      <SidebarInset>
        <main id="main-content" className={cn(isReadOnly && "[&>div:last-child]:opacity-75")}>
          {children}
        </main>
      </SidebarInset>
    </SidebarProvider>
  );
}
