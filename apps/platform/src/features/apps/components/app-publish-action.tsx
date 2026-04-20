"use client";

import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { useAppVersion } from "@/contexts/app-version-context";
import { useListAppProblems } from "@/generated/app";
import type { AppResponseEffectiveActionsItem } from "@/generated/app";
import { PublishAppDialog } from "./publish-app-dialog";

export function AppPublishAction() {
  const t = useTranslations("apps");
  const { app, isDraft } = useAppVersion();
  const actions = app.effectiveActions ?? [];
  const canAdmin = actions.includes("admin" as AppResponseEffectiveActionsItem);
  const { data: problemsData } = useListAppProblems(app.slug);
  const problemsResponse = problemsData?.data;
  const hasErrors =
    problemsResponse && "errorCount" in problemsResponse
      ? problemsResponse.errorCount > 0
      : false;

  const canPublish = app.hasDraft || app.status === "DRAFT";
  if (!isDraft || !canPublish || !canAdmin) return null;

  if (hasErrors) {
    return (
      <Button size="sm" disabled title={t("problems.publishBlocked")}>
        {t("versions.publish")}
      </Button>
    );
  }

  return (
    <PublishAppDialog appId={app.id} appSlug={app.slug} unpublishedChangeCount={app.unpublishedChangeCount ?? 0}>
      <Button size="sm">{t("versions.publish")}</Button>
    </PublishAppDialog>
  );
}
