"use client";

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
} from "@/components/ui/dialog";
import type { ContentEntryResponse, ProblemDetail } from "@/generated/content";
import {
  useDeleteContentEntry,
  getListContentEntriesQueryKey,
} from "@/generated/content";

interface EntryDeleteDialogProps {
  appSlug: string;
  defSlug: string;
  entry: ContentEntryResponse;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function EntryDeleteDialog({
  appSlug,
  defSlug,
  entry,
  open,
  onOpenChange,
}: EntryDeleteDialogProps) {
  const t = useTranslations("entries");
  const queryClient = useQueryClient();
  const deleteEntry = useDeleteContentEntry();

  async function handleDelete() {
    try {
      await deleteEntry.mutateAsync({
        appSlug,
        defSlug,
        entryId: entry.id,
      });
      toast.success(t("deleted"));
      queryClient.invalidateQueries({
        queryKey: getListContentEntriesQueryKey(appSlug, defSlug),
      });
      onOpenChange(false);
    } catch (error: unknown) {
      const err = error as { data?: ProblemDetail; status?: number };
      if (err.status === 409) {
        toast.error(t("deleteRestricted"));
      } else {
        toast.error(
          err.data?.detail ?? t("notFound")
        );
      }
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        data-testid="entry-delete-dialog"
        showCloseButton={false}
      >
        <DialogHeader>
          <DialogTitle>{t("delete")}</DialogTitle>
          <DialogDescription>{t("deleteConfirm")}</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={deleteEntry.isPending}
          >
            {t("form.cancel")}
          </Button>
          <Button
            data-testid="entry-delete-confirm"
            type="button"
            variant="destructive"
            onClick={handleDelete}
            aria-busy={deleteEntry.isPending}
            disabled={deleteEntry.isPending}
          >
            {deleteEntry.isPending && (
              <Loader2 className="mr-2 size-4 animate-spin" />
            )}
            {t("delete")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
