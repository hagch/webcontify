"use client";

import { useState, useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
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
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import {
  usePublishApp,
  suggestNextVersion,
  getListAppsQueryKey,
  getGetAppQueryKey,
  getListAppVersionsQueryKey,
  type ProblemDetail,
} from "@/generated/app";
import {
  publishAppSchema,
  type PublishAppFormValues,
} from "../schemas/publish-app";

interface PublishAppDialogProps {
  appId: string;
  /** App slug for cache invalidation (route uses slug, cache key differs from UUID) */
  appSlug?: string;
  unpublishedChangeCount?: number;
  children: React.ReactNode;
}

export function PublishAppDialog({ appId, appSlug, unpublishedChangeCount, children }: PublishAppDialogProps) {
  const [open, setOpen] = useState(false);
  const publishApp = usePublishApp();
  const t = useTranslations("apps");
  const queryClient = useQueryClient();

  const form = useForm<PublishAppFormValues>({
    resolver: zodResolver(publishAppSchema),
    defaultValues: {
      version: "",
      releaseNotes: "",
    },
  });

  useEffect(() => {
    if (open) {
      suggestNextVersion(appId)
        .then((response) => {
          if (response.data?.suggestedVersion) {
            form.setValue("version", response.data.suggestedVersion);
          }
        })
        .catch(() => {
          // Ignore — user can enter manually
        });
    }
  }, [open, appId, form]);

  function invalidateQueries() {
    queryClient.invalidateQueries({ queryKey: getListAppsQueryKey() });
    queryClient.invalidateQueries({ queryKey: getGetAppQueryKey(appId) });
    if (appSlug && appSlug !== appId) {
      queryClient.invalidateQueries({ queryKey: getGetAppQueryKey(appSlug) });
    }
    queryClient.invalidateQueries({
      queryKey: getListAppVersionsQueryKey(appId),
    });
  }

  async function onSubmit(values: PublishAppFormValues) {
    try {
      const response = await publishApp.mutateAsync({
        appId,
        data: {
          version: values.version,
          releaseNotes: values.releaseNotes || undefined,
        },
      });
      if (response.status === 201) {
        toast.success(t("versions.publishDialog.successToast"));
        invalidateQueries();
        setOpen(false);
        form.reset();
      }
    } catch (error: unknown) {
      const err = error as ProblemDetail;
      if (err.status === 409) {
        form.setError("version", {
          message: t("versions.publishDialog.versionExists"),
        });
        return;
      }
      toast.error(err.detail ?? err.title);
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={children as React.ReactElement} />
      <DialogContent
        data-testid="publish-app-dialog"
        className="sm:max-w-md"
      >
        <DialogHeader>
          <DialogTitle>{t("versions.publishDialog.title")}</DialogTitle>
          <DialogDescription>
            {t("versions.publishDialog.description")}
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            {unpublishedChangeCount != null && unpublishedChangeCount > 0 && (
              <p className="text-sm text-muted-foreground">
                {t("versions.publishDialog.changeCount", { count: unpublishedChangeCount })}
              </p>
            )}
            <FormField
              control={form.control}
              name="version"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    {t("versions.publishDialog.versionLabel")}{" "}
                    <span className="text-destructive">*</span>
                  </FormLabel>
                  <FormControl>
                    <Input
                      data-testid="publish-version-input"
                      placeholder={t(
                        "versions.publishDialog.versionPlaceholder"
                      )}
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="releaseNotes"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    {t("versions.publishDialog.releaseNotesLabel")}
                  </FormLabel>
                  <FormControl>
                    <Textarea
                      data-testid="publish-release-notes-input"
                      placeholder={t(
                        "versions.publishDialog.releaseNotesPlaceholder"
                      )}
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setOpen(false)}
              >
                {t("versions.publishDialog.cancel")}
              </Button>
              <Button
                data-testid="publish-app-submit"
                type="submit"
                aria-busy={publishApp.isPending}
              >
                {publishApp.isPending && (
                  <Loader2 className="mr-2 size-4 animate-spin" />
                )}
                {t("versions.publishDialog.submit")}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
