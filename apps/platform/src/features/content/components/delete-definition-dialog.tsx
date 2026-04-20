"use client";

import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { useQueryClient } from "@tanstack/react-query";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import {
  useDeleteContentDefinition,
  getListContentDefinitionsQueryKey,
} from "@/generated/content";
import { getGetAppQueryKey } from "@/generated/app";

interface DeleteDefinitionDialogProps {
  appSlug: string;
  definitionId: string;
  definitionName: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Confirm dialog for soft-deleting a content definition. Wired only when the
 * current user has `deleter:content_definition:{id}`; otherwise the caller
 * keeps `onDeleteDefinition` undefined and the button stays hidden in the
 * node.
 */
export function DeleteDefinitionDialog({
  appSlug,
  definitionId,
  definitionName,
  open,
  onOpenChange,
}: DeleteDefinitionDialogProps) {
  const t = useTranslations("content.delete");
  const qc = useQueryClient();
  const { mutate, isPending } = useDeleteContentDefinition({
    mutation: {
      onSuccess: () => {
        toast.success(t("success"));
        qc.invalidateQueries({
          queryKey: getListContentDefinitionsQueryKey(appSlug),
        });
        qc.invalidateQueries({
          queryKey: getGetAppQueryKey(appSlug),
        });
        onOpenChange(false);
      },
      onError: () => {
        toast.error(t("error"));
      },
    },
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent data-testid="delete-definition-dialog">
        <DialogHeader>
          <DialogTitle>{t("title")}</DialogTitle>
          <DialogDescription>
            {t("body", { name: definitionName })}
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button
            variant="outline"
            disabled={isPending}
            onClick={() => onOpenChange(false)}
          >
            {t("cancelButton")}
          </Button>
          <Button
            data-testid="confirm-delete-definition"
            variant="destructive"
            disabled={isPending}
            onClick={() => mutate({ appSlug, definitionId })}
          >
            {t("confirmButton")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
