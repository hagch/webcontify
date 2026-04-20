"use client";

import { useState } from "react";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
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
  getListAppsQueryKey,
  getGetAppQueryKey,
  getListAppVersionsQueryKey,
} from "@/generated/app";

interface RollbackConfirmDialogProps {
  appId: string;
  versionId: string;
  versionName: string;
  children: React.ReactNode;
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

  function invalidateQueries() {
    queryClient.invalidateQueries({ queryKey: getListAppsQueryKey() });
    queryClient.invalidateQueries({ queryKey: getGetAppQueryKey(appId) });
    queryClient.invalidateQueries({
      queryKey: getListAppVersionsQueryKey(appId),
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
          toast.error(t("versions.rollbackDialog.successToast"));
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
      >
        <DialogHeader>
          <DialogTitle>{t("versions.rollbackDialog.title")}</DialogTitle>
          <DialogDescription>
            {t("versions.rollbackDialog.description", {
              version: versionName,
            })}
          </DialogDescription>
        </DialogHeader>
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
