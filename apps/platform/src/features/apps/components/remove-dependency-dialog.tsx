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
  useRemoveAppDependency,
  getListAppDependenciesQueryKey,
  getGetAppDependencyGraphQueryKey,
  getListAppProblemsQueryKey,
  getListAppRevisionsQueryKey,
  getGetAppQueryKey,
} from "@/generated/app";

interface RemoveDependencyDialogProps {
  appId: string;
  depAppId: string;
  depAppName: string;
  children?: React.ReactNode;
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
}

export function RemoveDependencyDialog({
  appId,
  depAppId,
  depAppName,
  children,
  open: controlledOpen,
  onOpenChange: controlledOnOpenChange,
}: RemoveDependencyDialogProps) {
  const [internalOpen, setInternalOpen] = useState(false);
  const removeDependency = useRemoveAppDependency();
  const t = useTranslations("apps");
  const queryClient = useQueryClient();

  const isControlled = controlledOpen !== undefined;
  const open = isControlled ? controlledOpen : internalOpen;
  const onOpenChange = isControlled
    ? controlledOnOpenChange!
    : setInternalOpen;

  function invalidateQueries() {
    queryClient.invalidateQueries({
      queryKey: getListAppDependenciesQueryKey(appId),
    });
    queryClient.invalidateQueries({
      queryKey: getGetAppDependencyGraphQueryKey(appId),
    });
    queryClient.invalidateQueries({
      queryKey: getListAppProblemsQueryKey(appId),
    });
    queryClient.invalidateQueries({
      queryKey: getListAppRevisionsQueryKey(appId),
    });
    queryClient.invalidateQueries({
      queryKey: getGetAppQueryKey(appId),
    });
  }

  function handleConfirm() {
    removeDependency.mutate(
      { appId, depAppId },
      {
        onSuccess: () => {
          toast.success(t("dependencies.removeDialog.successToast"));
          invalidateQueries();
          onOpenChange(false);
        },
        onError: () => {
          toast.error(t("dependencies.removeDialog.errorToast"));
        },
      }
    );
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      {children && (
        <DialogTrigger render={children as React.ReactElement} />
      )}
      <DialogContent
        data-testid="remove-dependency-dialog"
        showCloseButton={false}
      >
        <DialogHeader>
          <DialogTitle>{t("dependencies.removeDialog.title")}</DialogTitle>
          <DialogDescription>
            {t("dependencies.removeDialog.description")}
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={removeDependency.isPending}
          >
            {t("dependencies.removeDialog.cancel")}
          </Button>
          <Button
            type="button"
            variant="destructive"
            onClick={handleConfirm}
            aria-busy={removeDependency.isPending}
            disabled={removeDependency.isPending}
          >
            {removeDependency.isPending && (
              <Loader2 className="mr-2 size-4 animate-spin" />
            )}
            {t("dependencies.removeDialog.confirm")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
