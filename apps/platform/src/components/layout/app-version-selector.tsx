"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Check, ChevronsUpDown, GitBranch } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { cn } from "@/lib/utils";
import type { AppVersionResponse } from "@/generated/app";

interface AppVersionSelectorProps {
  selectedVersion: string;
  versions: AppVersionResponse[];
  hasDraft: boolean;
  hasUnpublishedChanges: boolean;
  unpublishedChangeCount: number;
  onSelect: (versionId: string) => void;
}

export function AppVersionSelector({
  selectedVersion,
  versions,
  hasDraft,
  hasUnpublishedChanges,
  unpublishedChangeCount,
  onSelect,
}: AppVersionSelectorProps) {
  const t = useTranslations("appVersion");
  const [open, setOpen] = useState(false);

  const isDraft = selectedVersion === "draft";
  const activeVersion = versions[0];

  function getSelectionLabel(): string {
    if (isDraft) return t("draft");
    const found = versions.find((v) => v.id === selectedVersion);
    if (!found) return selectedVersion;
    const isActive = found.id === activeVersion?.id;
    return isActive
      ? `${found.version} (${t("active")})`
      : (found.version ?? selectedVersion);
  }

  // No versions exist and only draft — static label, no dropdown
  if (versions.length === 0 && hasDraft) {
    return (
      <div className="flex items-center gap-1.5 px-1 text-sm text-muted-foreground">
        <GitBranch className="size-3.5 shrink-0" />
        <span>{t("draft")}</span>
        {hasUnpublishedChanges && unpublishedChangeCount > 0 && (
          <Badge variant="secondary" className="text-xs">
            {unpublishedChangeCount}
          </Badge>
        )}
      </div>
    );
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger
        data-testid="version-selector-trigger"
        className="group/button inline-flex h-7 w-full items-center justify-between gap-1 rounded-md px-2 text-sm font-normal hover:bg-accent hover:text-accent-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <span className="flex items-center gap-1.5 truncate">
          <GitBranch className="size-3.5 shrink-0" />
          <span className="truncate">{getSelectionLabel()}</span>
          {isDraft && hasUnpublishedChanges && unpublishedChangeCount > 0 && (
            <Badge variant="secondary" className="text-xs">
              {unpublishedChangeCount}
            </Badge>
          )}
        </span>
        <ChevronsUpDown className="size-3.5 shrink-0 opacity-50" />
      </PopoverTrigger>
      <PopoverContent className="w-56 p-1" align="start">
        <div className="max-h-64 overflow-y-auto">
          {hasDraft && (
            <button
              type="button"
              className={cn(
                "flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-sm hover:bg-accent hover:text-accent-foreground",
                isDraft && "font-medium"
              )}
              onClick={() => {
                onSelect("draft");
                setOpen(false);
              }}
            >
              <Check
                className={cn("size-4 shrink-0", !isDraft && "invisible")}
              />
              <span className="flex-1 truncate">{t("draft")}</span>
              {hasUnpublishedChanges && unpublishedChangeCount > 0 && (
                <Badge variant="secondary" className="text-xs">
                  {unpublishedChangeCount}
                </Badge>
              )}
            </button>
          )}
          {versions.map((version, index) => {
            const isActive = version.id === activeVersion?.id;
            const isSelected = version.id === selectedVersion;
            const label = isActive
              ? `${version.version} (${t("active")})`
              : (version.version ?? version.id ?? "");
            return (
              <button
                key={version.id ?? index}
                type="button"
                className={cn(
                  "flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-sm hover:bg-accent hover:text-accent-foreground",
                  isSelected && "font-medium"
                )}
                onClick={() => {
                  if (version.id) {
                    onSelect(version.id);
                    setOpen(false);
                  }
                }}
              >
                <Check
                  className={cn("size-4 shrink-0", !isSelected && "invisible")}
                />
                <span className="flex-1 truncate">{label}</span>
              </button>
            );
          })}
        </div>
      </PopoverContent>
    </Popover>
  );
}
