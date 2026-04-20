"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { ChevronDown, History, AlertCircle, Download } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import { useListAppVersions, exportAppBlueprint, type AppVersionResponse } from "@/generated/app";
import { RollbackConfirmDialog } from "./rollback-confirm-dialog";

function formatDate(dateString: string): string {
  return new Intl.DateTimeFormat("de-DE", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(dateString));
}

interface AppVersionListProps {
  appId: string;
  hasDraft: boolean;
}

function VersionRow({
  version,
  appId,
}: {
  version: AppVersionResponse;
  appId: string;
}) {
  const t = useTranslations("apps");
  const [isOpen, setIsOpen] = useState(false);

  async function handleExport() {
    try {
      const response = await exportAppBlueprint(appId, version.id ?? "");
      const json = JSON.stringify(response.data, null, 2);
      const blob = new Blob([json], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `blueprint-${appId}-v${version.version}.json`;
      a.click();
      URL.revokeObjectURL(url);
      toast.success(t("importExport.exportSuccess"));
    } catch {
      // silent fail — toast not needed for download errors
    }
  }

  return (
    <Collapsible open={isOpen} onOpenChange={setIsOpen}>
      <CollapsibleTrigger className="flex w-full items-center justify-between rounded-lg border p-4 hover:bg-muted/50 transition-colors cursor-pointer">
        <div className="flex items-center gap-3">
          <Badge variant="secondary" className="bg-indigo-100 text-indigo-700 dark:bg-indigo-900 dark:text-indigo-300">
            v{version.version}
          </Badge>
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            {version.publishedAt && (
              <span>{t("versions.publishedAt")} {formatDate(version.publishedAt)}</span>
            )}
            {version.publishedByUser && (
              <span>
                {t("versions.publishedBy")} {version.publishedByUser.firstName} {version.publishedByUser.lastName}
              </span>
            )}
          </div>
        </div>
        <ChevronDown
          className={`size-4 text-muted-foreground transition-transform ${isOpen ? "rotate-180" : ""}`}
        />
      </CollapsibleTrigger>
      <CollapsibleContent className="border-x border-b rounded-b-lg px-4 pb-4 pt-2">
        <div className="space-y-3">
          {version.releaseNotes && (
            <div>
              <p className="text-sm font-medium text-muted-foreground">
                {t("versions.releaseNotes")}
              </p>
              <p className="mt-1 text-sm whitespace-pre-wrap">
                {version.releaseNotes}
              </p>
            </div>
          )}
          <div className="flex items-center justify-end gap-2">
            <Button variant="outline" size="sm" onClick={handleExport} data-testid="export-blueprint-button">
              <Download className="mr-2 size-4" />
              {t("importExport.export")}
            </Button>
            <RollbackConfirmDialog
              appId={appId}
              versionId={version.id ?? ""}
              versionName={version.version ?? ""}
            >
              <Button variant="outline" size="sm">
                <History className="mr-2 size-4" />
                {t("versions.rollback")}
              </Button>
            </RollbackConfirmDialog>
          </div>
        </div>
      </CollapsibleContent>
    </Collapsible>
  );
}

export function AppVersionList({ appId, hasDraft }: AppVersionListProps) {
  const t = useTranslations("apps");
  const { data, isLoading } = useListAppVersions(appId);
  const versions = data?.data?.items ?? [];

  if (isLoading) {
    return (
      <div data-testid="app-version-list" className="space-y-3">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-16 w-full rounded-lg" />
        ))}
      </div>
    );
  }

  return (
    <div data-testid="app-version-list" className="space-y-4">
      {hasDraft && (
        <div className="flex items-center gap-2 rounded-lg border border-amber-300 bg-amber-50 p-3 dark:border-amber-700 dark:bg-amber-950">
          <AlertCircle className="size-4 text-amber-600 dark:text-amber-400" />
          <span className="text-sm font-medium text-amber-700 dark:text-amber-300">
            {t("versions.unpublishedChanges")}
          </span>
        </div>
      )}

      {versions.length === 0 ? (
        <div className="flex flex-col items-center justify-center rounded-lg border border-dashed p-8 text-center">
          <History className="size-10 text-muted-foreground/50" />
          <p className="mt-3 text-sm text-muted-foreground">
            {t("versions.noVersions")}
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {versions.map((version) => (
            <VersionRow
              key={version.id}
              version={version}
              appId={appId}
            />
          ))}
        </div>
      )}
    </div>
  );
}
