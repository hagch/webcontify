"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Loader2, Search } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import {
  useListApps,
  useAddAppDependency,
  useListAppDependencies,
  getListAppDependenciesQueryKey,
  getGetAppDependencyGraphQueryKey,
  getListAppProblemsQueryKey,
  getListAppRevisionsQueryKey,
  getGetAppQueryKey,
  type ProblemDetail,
} from "@/generated/app";

interface AddDependencyPopoverContentProps {
  appId: string;
  onSuccess: () => void;
}

export function AddDependencyPopoverContent({
  appId,
  onSuccess,
}: AddDependencyPopoverContentProps) {
  const [search, setSearch] = useState("");
  const t = useTranslations("apps");
  const queryClient = useQueryClient();
  const addDependency = useAddAppDependency();

  const { data: appsData } = useListApps();
  const { data: depsData } = useListAppDependencies(appId);

  const allApps = appsData?.data?.items ?? [];
  const depsResponse = depsData?.data;
  const existingDeps =
    depsResponse && "items" in depsResponse ? depsResponse.items : [];
  const existingDepIds = new Set(existingDeps.map((d) => d.dependsOnAppId));

  const availableApps = allApps.filter(
    (app) =>
      app.id !== appId &&
      !existingDepIds.has(app.id) &&
      app.status === "ACTIVE" &&
      app.name.toLowerCase().includes(search.toLowerCase())
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
    queryClient.invalidateQueries({
      queryKey: getListAppRevisionsQueryKey(appId),
    });
    queryClient.invalidateQueries({
      queryKey: getGetAppQueryKey(appId),
    });
  }

  function handleSelect(dependsOnAppId: string) {
    addDependency.mutate(
      { appId, data: { dependsOnAppId } },
      {
        onSuccess: () => {
          toast.success(t("dependencies.addPopover.successToast"));
          invalidateQueries();
          onSuccess();
        },
        onError: (error: unknown) => {
          const err = error as ProblemDetail;
          if (err.status === 409) {
            toast.error(t("dependencies.addPopover.cyclicError"));
          } else if (err.status === 400) {
            toast.error(t("dependencies.addPopover.draftError"));
          } else {
            toast.error(err.detail ?? err.title);
          }
        },
      }
    );
  }

  return (
    <div className="w-[260px]" data-testid="add-dependency-popover">
      <p className="mb-2 text-sm font-medium">
        {t("dependencies.addPopover.title")}
      </p>
      <div className="relative mb-2">
        <Search className="absolute left-2 top-2.5 size-3.5 text-muted-foreground" />
        <Input
          placeholder={t("dependencies.addPopover.searchPlaceholder")}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="pl-8 h-8 text-sm"
          data-testid="add-dependency-search"
        />
      </div>
      <div className="max-h-[200px] overflow-y-auto">
        {availableApps.length === 0 ? (
          <p className="py-3 text-center text-xs text-muted-foreground">
            {t("dependencies.addPopover.noAppsAvailable")}
          </p>
        ) : (
          availableApps.map((app) => (
            <Button
              key={app.id}
              variant="ghost"
              size="sm"
              className="w-full justify-start gap-2 h-9"
              onClick={() => handleSelect(app.id)}
              disabled={addDependency.isPending}
              data-testid={`add-dep-option-${app.slug}`}
            >
              {addDependency.isPending ? (
                <Loader2 className="size-3.5 animate-spin" />
              ) : (
                <div className="flex size-6 shrink-0 items-center justify-center rounded bg-gradient-to-br from-primary to-primary/70">
                  <span className="text-[10px] font-bold text-primary-foreground">
                    {app.name.charAt(0).toUpperCase()}
                  </span>
                </div>
              )}
              <span className="truncate text-sm">{app.name}</span>
            </Button>
          ))
        )}
      </div>
    </div>
  );
}
