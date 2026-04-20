"use client";

import { useState } from "react";
import { Loader2, ArrowRight, Plus, Minus, Pencil } from "lucide-react";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { useQueryClient } from "@tanstack/react-query";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import {
  useRollbackAppVersion,
  useGetVersionDiff,
  getListAppsQueryKey,
  getGetAppQueryKey,
  getListAppVersionsQueryKey,
  getListAppRevisionsQueryKey,
  type VersionDiffEntry,
  type VersionDiffResponse,
} from "@/generated/app";

interface RollbackConfirmDialogProps {
  appId: string;
  versionId: string;
  versionName: string;
  children: React.ReactNode;
}

function DiffEntryRow({ entry }: { entry: VersionDiffEntry }) {
  const t = useTranslations("apps");
  const isDepChange = entry.field?.startsWith("dependency:");
  const fieldLabel = isDepChange
    ? entry.field?.replace("dependency:", "")
    : t(`versions.rollbackDialog.fields.${entry.field}` as "versions.rollbackDialog.fields.name");

  return (
    <div className="flex items-start gap-2 rounded border px-3 py-2 text-sm">
      {entry.changeType === "added" && (
        <Plus className="mt-0.5 size-3.5 shrink-0 text-emerald-600" />
      )}
      {entry.changeType === "removed" && (
        <Minus className="mt-0.5 size-3.5 shrink-0 text-red-600" />
      )}
      {entry.changeType === "modified" && (
        <Pencil className="mt-0.5 size-3.5 shrink-0 text-amber-600" />
      )}
      <div className="min-w-0 flex-1">
        <span className="font-medium">{fieldLabel}</span>
        {entry.changeType === "modified" && (
          <div className="mt-1 space-y-1">
            <div className="flex items-center gap-2 text-muted-foreground">
              <span className="truncate text-red-600 dark:text-red-400 line-through">
                {entry.currentValue}
              </span>
              <ArrowRight className="size-3 shrink-0" />
              <span className="truncate text-emerald-600 dark:text-emerald-400">
                {entry.versionValue}
              </span>
            </div>
          </div>
        )}
      </div>
      <Badge
        variant="secondary"
        className={
          entry.changeType === "added"
            ? "bg-emerald-100 text-emerald-800 dark:bg-emerald-900 dark:text-emerald-300"
            : entry.changeType === "removed"
              ? "bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-300"
              : "bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-300"
        }
      >
        {entry.changeType === "added" && t("revisions.diffLabels.added")}
        {entry.changeType === "removed" && t("revisions.diffLabels.removed")}
        {entry.changeType === "modified" && t("revisions.diffLabels.replaced")}
      </Badge>
    </div>
  );
}

export function RollbackConfirmDialog({
  appId,
  versionId,
  versionName,
  children,
}: RollbackConfirmDialogProps) {
  const [open, setOpen] = useState(false);
  const rollback = useRollbackAppVersion();
  const t = useTranslations("apps");
  const queryClient = useQueryClient();

  const { data: diffData, isLoading: isDiffLoading } = useGetVersionDiff(
    appId,
    versionId,
    { query: { enabled: open } }
  );
  const diff = diffData?.status === 200
    ? (diffData.data as VersionDiffResponse)
    : undefined;

  function invalidateQueries() {
    queryClient.invalidateQueries({ queryKey: getListAppsQueryKey() });
    queryClient.invalidateQueries({ queryKey: getGetAppQueryKey(appId) });
    queryClient.invalidateQueries({
      queryKey: getListAppVersionsQueryKey(appId),
    });
    queryClient.invalidateQueries({
      queryKey: getListAppRevisionsQueryKey(appId),
    });
  }

  function handleConfirm() {
    rollback.mutate(
      { appId, versionId },
      {
        onSuccess: () => {
          toast.success(t("versions.rollbackDialog.successToast"));
          invalidateQueries();
          setOpen(false);
        },
        onError: () => {
          toast.error(t("versions.rollbackDialog.errorToast"));
        },
      }
    );
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={children as React.ReactElement} />
      <DialogContent
        data-testid="rollback-confirm-dialog"
        showCloseButton={false}
        className="max-w-lg"
      >
        <DialogHeader>
          <DialogTitle>{t("versions.rollbackDialog.title")}</DialogTitle>
          <DialogDescription>
            {t("versions.rollbackDialog.description", {
              version: versionName,
            })}
          </DialogDescription>
        </DialogHeader>

        <div className="max-h-[40vh] space-y-2 overflow-y-auto">
          {isDiffLoading && (
            <>
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
            </>
          )}
          {diff && !diff.hasChanges && (
            <p className="py-2 text-center text-sm text-muted-foreground">
              {t("versions.rollbackDialog.noChanges")}
            </p>
          )}
          {diff?.changes?.map((entry, i) => (
            <DiffEntryRow key={i} entry={entry} />
          ))}
        </div>

        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            onClick={() => setOpen(false)}
            disabled={rollback.isPending}
          >
            {t("versions.rollbackDialog.cancel")}
          </Button>
          <Button
            type="button"
            onClick={handleConfirm}
            aria-busy={rollback.isPending}
            disabled={rollback.isPending}
          >
            {rollback.isPending && (
              <Loader2 className="mr-2 size-4 animate-spin" />
            )}
            {t("versions.rollbackDialog.confirm")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
