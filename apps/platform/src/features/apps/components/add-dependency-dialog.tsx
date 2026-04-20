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
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  useListApps,
  useAddAppDependency,
  useListAppDependencies,
  getListAppDependenciesQueryKey,
  getGetAppDependencyGraphQueryKey,
  getListAppProblemsQueryKey,
  type ProblemDetail,
} from "@/generated/app";

interface AddDependencyDialogProps {
  appId: string;
  children: React.ReactNode;
}

export function AddDependencyDialog({
  appId,
  children,
}: AddDependencyDialogProps) {
  const [open, setOpen] = useState(false);
  const [selectedAppId, setSelectedAppId] = useState<string | null>(null);
  const t = useTranslations("apps");
  const queryClient = useQueryClient();
  const addDependency = useAddAppDependency();

  const { data: appsData } = useListApps(undefined, {
    query: { enabled: open },
  });
  const { data: depsData } = useListAppDependencies(appId, {
    query: { enabled: open },
  });

  const allApps = appsData?.data?.items ?? [];
  const depsResponse = depsData?.data;
  const existingDeps =
    depsResponse && "items" in depsResponse ? depsResponse.items : [];
  const existingDepIds = new Set(existingDeps.map((d) => d.dependsOnAppId));

  const availableApps = allApps.filter(
    (app) =>
      app.id !== appId &&
      !existingDepIds.has(app.id) &&
      app.status === "ACTIVE"
  );

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
  }

  function handleSubmit() {
    if (!selectedAppId) return;
    addDependency.mutate(
      { appId, data: { dependsOnAppId: selectedAppId } },
      {
        onSuccess: () => {
          toast.success(t("dependencies.addDialog.successToast"));
          invalidateQueries();
          setOpen(false);
          setSelectedAppId(null);
        },
        onError: (error: unknown) => {
          const err = error as ProblemDetail;
          if (err.status === 409) {
            toast.error(t("dependencies.addDialog.cyclicError"));
          } else if (err.status === 400) {
            toast.error(t("dependencies.addDialog.draftError"));
          } else {
            toast.error(err.detail ?? err.title);
          }
        },
      }
    );
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(isOpen) => {
        setOpen(isOpen);
        if (!isOpen) setSelectedAppId(null);
      }}
    >
      <DialogTrigger render={children as React.ReactElement} />
      <DialogContent
        data-testid="add-dependency-dialog"
        className="sm:max-w-md"
      >
        <DialogHeader>
          <DialogTitle>{t("dependencies.addDialog.title")}</DialogTitle>
          <DialogDescription>
            {t("dependencies.addDialog.description")}
          </DialogDescription>
        </DialogHeader>

        {availableApps.length === 0 ? (
          <p className="py-4 text-center text-sm text-muted-foreground">
            {t("dependencies.addDialog.noAppsAvailable")}
          </p>
        ) : (
          <div className="py-4">
            <Select
              value={selectedAppId ?? undefined}
              onValueChange={(val) => setSelectedAppId(val as string)}
            >
              <SelectTrigger className="w-full">
                <SelectValue
                  placeholder={t("dependencies.addDialog.selectApp")}
                />
              </SelectTrigger>
              <SelectContent>
                {availableApps.map((app) => (
                  <SelectItem key={app.id} value={app.id}>
                    {app.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}

        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            onClick={() => setOpen(false)}
            disabled={addDependency.isPending}
          >
            {t("dependencies.addDialog.cancel")}
          </Button>
          <Button
            type="button"
            onClick={handleSubmit}
            disabled={!selectedAppId || addDependency.isPending}
            aria-busy={addDependency.isPending}
          >
            {addDependency.isPending && (
              <Loader2 className="mr-2 size-4 animate-spin" />
            )}
            {t("dependencies.addDialog.submit")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
