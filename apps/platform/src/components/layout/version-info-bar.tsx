"use client";

import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Info } from "lucide-react";
import { useAppVersion } from "@/contexts/app-version-context";

export function VersionInfoBar() {
  const t = useTranslations("appVersion");
  const { isReadOnly, selectedVersion, versions, setSelectedVersion } =
    useAppVersion();

  if (!isReadOnly) return null;

  const versionObj = versions.find((v) => v.id === selectedVersion);
  const versionLabel = versionObj?.version ?? selectedVersion;

  return (
    <div
      role="status"
      aria-live="polite"
      className="sticky top-14 z-[9] flex items-center justify-between gap-2 border-b bg-amber-50 px-4 py-2 text-sm text-amber-900 dark:bg-amber-950/50 dark:text-amber-200"
    >
      <div className="flex items-center gap-2">
        <Info className="size-4 shrink-0" />
        <span>{t("viewingVersion", { version: versionLabel })}</span>
      </div>
      <Button
        variant="ghost"
        size="sm"
        data-testid="switch-to-draft"
        className="text-amber-900 hover:text-amber-950 dark:text-amber-200 dark:hover:text-amber-100"
        onClick={() => setSelectedVersion("draft")}
      >
        {t("switchToDraft")}
      </Button>
    </div>
  );
}
