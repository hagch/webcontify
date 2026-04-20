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
  useRevertAppRevision,
  getListAppsQueryKey,
  getGetAppQueryKey,
  getListAppRevisionsQueryKey,
  getListAppDependenciesQueryKey,
  getGetAppDependencyGraphQueryKey,
} from "@/generated/app";

interface RevertRevisionDialogProps {
  appId: string;
  revisionId: string;
  children: React.ReactNode;
}

export function RevertRevisionDialog({
  appId,
  revisionId,
  children,
}: RevertRevisionDialogProps) {
  const [open, setOpen] = useState(false);
  const revert = useRevertAppRevision();
  const t = useTranslations("apps");
  const queryClient = useQueryClient();

  function invalidateQueries() {
    queryClient.invalidateQueries({ queryKey: getListAppsQueryKey() });
    queryClient.invalidateQueries({ queryKey: getGetAppQueryKey(appId) });
    queryClient.invalidateQueries({ queryKey: getListAppRevisionsQueryKey(appId) });
    queryClient.invalidateQueries({ queryKey: getListAppDependenciesQueryKey(appId) });
    queryClient.invalidateQueries({ queryKey: getGetAppDependencyGraphQueryKey(appId) });
  }

  function handleConfirm() {
    revert.mutate(
      { appId, revisionId },
      {
        onSuccess: () => {
          toast.success(t("revisions.revertDialog.successToast"));
          invalidateQueries();
          setOpen(false);
        },
        onError: () => {
          toast.error(t("revisions.revertDialog.errorToast"));
        },
      },
    );
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={children as React.ReactElement} />
      <DialogContent
        data-testid="revert-revision-dialog"
        showCloseButton={false}
      >
        <DialogHeader>
          <DialogTitle>{t("revisions.revertDialog.title")}</DialogTitle>
          <DialogDescription>
            {t("revisions.revertDialog.description")}
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            onClick={() => setOpen(false)}
            disabled={revert.isPending}
          >
            {t("revisions.revertDialog.cancel")}
          </Button>
          <Button
            type="button"
            onClick={handleConfirm}
            aria-busy={revert.isPending}
            disabled={revert.isPending}
          >
            {revert.isPending && (
              <Loader2 className="mr-2 size-4 animate-spin" />
            )}
            {t("revisions.revertDialog.confirm")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
