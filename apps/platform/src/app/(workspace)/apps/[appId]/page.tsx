"use client";

import { use, useState } from "react";
import { notFound } from "next/navigation";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import { useQueryClient } from "@tanstack/react-query";
import { AppHeader } from "@/components/layout/app-header";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";
import {
  useGetApp,
  useUpdateAppStatus,
  useDeleteApp,
  getListAppsQueryKey,
  getGetAppQueryKey,
  type AppResponse,
} from "@/generated/app";
import { PublishAppDialog } from "@/features/apps/components/publish-app-dialog";
import { AppVersionList } from "@/features/apps/components/app-version-list";
import { AppRevisionList } from "@/features/apps/components/app-revision-list";
import { AppDependencyGraph } from "@/features/apps/components/app-dependency-graph";
import { AppProblemsPanel } from "@/features/apps/components/app-problems-panel";
import { useListAppProblems } from "@/generated/app";

function isAppResponse(data: unknown): data is AppResponse {
  return (
    typeof data === "object" && data !== null && "name" in data && "slug" in data
  );
}

function formatDate(dateString: string): string {
  return new Intl.DateTimeFormat("de-DE", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(dateString));
}

function statusBadgeVariant(status: string): "default" | "outline" | "secondary" {
  switch (status) {
    case "DRAFT":
      return "outline";
    case "ACTIVE":
      return "default";
    case "INACTIVE":
      return "secondary";
    default:
      return "outline";
  }
}

export default function AppDetailPage({
  params,
}: {
  params: Promise<{ appId: string }>;
}) {
  const { appId } = use(params);
  const router = useRouter();
  const t = useTranslations("apps");
  const queryClient = useQueryClient();

  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [statusDialogOpen, setStatusDialogOpen] = useState(false);
  const [pendingStatus, setPendingStatus] = useState<"ACTIVE" | "INACTIVE" | null>(null);

  const { data, isLoading, error } = useGetApp(appId);
  const updateStatus = useUpdateAppStatus();
  const deleteApp = useDeleteApp();
  const { data: problemsData } = useListAppProblems(appId);
  const problemsResponse = problemsData?.data;
  const hasErrors = problemsResponse && "errorCount" in problemsResponse
    ? problemsResponse.errorCount > 0
    : false;

  const rawData = data?.data;
  const app = isAppResponse(rawData) ? rawData : undefined;

  if (error && !isLoading) {
    notFound();
  }

  function invalidateQueries() {
    queryClient.invalidateQueries({ queryKey: getListAppsQueryKey() });
    queryClient.invalidateQueries({ queryKey: getGetAppQueryKey(appId) });
  }

  function handleStatusChange(newStatus: "ACTIVE" | "INACTIVE") {
    setPendingStatus(newStatus);
    setStatusDialogOpen(true);
  }

  function confirmStatusChange() {
    if (!pendingStatus) return;
    updateStatus.mutate(
      { appId, data: { status: pendingStatus } },
      {
        onSuccess: () => {
          invalidateQueries();
          setStatusDialogOpen(false);
          setPendingStatus(null);
          toast.success(t("detail.statusChangeSuccess"));
        },
        onError: () => {
          toast.error(t("detail.statusChangeError"));
        },
      }
    );
  }

  function handleDelete() {
    deleteApp.mutate(
      { appId },
      {
        onSuccess: () => {
          queryClient.invalidateQueries({ queryKey: getListAppsQueryKey() });
          setDeleteDialogOpen(false);
          toast.success(t("detail.deleteSuccess"));
          router.push("/apps");
        },
        onError: () => {
          toast.error(t("detail.deleteError"));
        },
      }
    );
  }

  return (
    <>
      <AppHeader
        mode="detail"
        breadcrumbs={[
          { label: t("title"), href: "/apps" },
          {
            label: isLoading ? "..." : (app?.name ?? "App"),
            suffix: app && !isLoading ? (
              <div className="flex items-center gap-1.5">
                <Badge variant={statusBadgeVariant(app.status)}>
                  {t(`statusValues.${app.status}`)}
                </Badge>
                {app.hasDraft && (
                  <Badge variant="outline" className="border-amber-500 text-amber-600">
                    {t("hasDraft")}
                  </Badge>
                )}
              </div>
            ) : undefined,
          },
        ]}
        actions={
          app && !isLoading ? (
            (app.status === "DRAFT" || app.hasDraft) ? (
              hasErrors ? (
                <Button size="sm" disabled title={t("problems.publishBlocked")}>
                  {t("versions.publish")}
                </Button>
              ) : (
                <PublishAppDialog appId={app.id}>
                  <Button size="sm">{t("versions.publish")}</Button>
                </PublishAppDialog>
              )
            ) : undefined
          ) : undefined
        }
      />
      <div data-testid="app-detail-page" className="flex flex-1 flex-col gap-6 p-4 md:p-6">
        {isLoading ? (
          <Card>
            <CardContent className="pt-6">
              <div className="space-y-6">
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                  {Array.from({ length: 6 }).map((_, i) => (
                    <div key={i} className="space-y-1">
                      <Skeleton className="h-4 w-24" />
                      <Skeleton className="h-5 w-40" />
                    </div>
                  ))}
                </div>
              </div>
            </CardContent>
          </Card>
        ) : app ? (
          <>
          <AppProblemsPanel appId={app.id} />

          <Tabs defaultValue="overview">
            <TabsList>
              <TabsTrigger value="overview">{t("detail.overview")}</TabsTrigger>
              <TabsTrigger value="dependencies">{t("dependencies.title")}</TabsTrigger>
              <TabsTrigger value="versions">{t("versions.title")}</TabsTrigger>
              <TabsTrigger value="revisions">{t("revisions.title")}</TabsTrigger>
            </TabsList>

            <TabsContent value="overview">
              <div className="flex flex-col gap-6">
                {/* Overview */}
                <section className="rounded-lg border p-6">
                  <h2 className="mb-4 text-sm font-medium text-muted-foreground uppercase tracking-wider">
                    {t("detail.overviewSection")}
                  </h2>
                  <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                    <div>
                      <dt className="text-sm text-muted-foreground">
                        {t("detail.descriptionLabel")}
                      </dt>
                      <dd className="mt-1 font-medium">
                        {app.description || t("detail.noDescription")}
                      </dd>
                    </div>
                    <div>
                      <dt className="text-sm text-muted-foreground">
                        {t("detail.slugLabel")}
                      </dt>
                      <dd className="mt-1 font-mono text-sm">{app.slug}</dd>
                    </div>
                    <div>
                      <dt className="text-sm text-muted-foreground">
                        {t("detail.statusLabel")}
                      </dt>
                      <dd className="mt-1">
                        <Badge variant={statusBadgeVariant(app.status)}>
                          {t(`statusValues.${app.status}`)}
                        </Badge>
                      </dd>
                    </div>
                    <div>
                      <dt className="text-sm text-muted-foreground">
                        {t("detail.createdAtLabel")}
                      </dt>
                      <dd className="mt-1 text-sm">{formatDate(app.createdAt)}</dd>
                    </div>
                    <div>
                      <dt className="text-sm text-muted-foreground">
                        {t("detail.updatedAtLabel")}
                      </dt>
                      <dd className="mt-1 text-sm">{formatDate(app.updatedAt)}</dd>
                    </div>
                  </dl>
                </section>

                {/* Actions */}
                <section className="rounded-lg border p-6">
                  <h2 className="mb-4 text-sm font-medium text-muted-foreground uppercase tracking-wider">
                    {t("detail.actionsSection")}
                  </h2>
                  <div className="flex flex-wrap gap-3">
                    {(app.status === "DRAFT" || app.status === "INACTIVE") && (
                      <Button
                        variant="outline"
                        onClick={() => handleStatusChange("ACTIVE")}
                        aria-busy={updateStatus.isPending}
                        disabled={updateStatus.isPending}
                      >
                        {updateStatus.isPending && (
                          <Loader2 className="mr-2 size-4 animate-spin" />
                        )}
                        {t("detail.activate")}
                      </Button>
                    )}
                    {app.status === "ACTIVE" && (
                      <Button
                        variant="outline"
                        onClick={() => handleStatusChange("INACTIVE")}
                        aria-busy={updateStatus.isPending}
                        disabled={updateStatus.isPending}
                      >
                        {updateStatus.isPending && (
                          <Loader2 className="mr-2 size-4 animate-spin" />
                        )}
                        {t("detail.deactivate")}
                      </Button>
                    )}
                    <Button
                      variant="destructive"
                      onClick={() => setDeleteDialogOpen(true)}
                    >
                      {t("detail.delete")}
                    </Button>
                  </div>
                </section>
              </div>
            </TabsContent>

            <TabsContent value="dependencies">
              <AppDependencyGraph appId={app.id} />
            </TabsContent>

            <TabsContent value="versions">
              <AppVersionList appId={app.id} hasDraft={app.hasDraft ?? false} />
            </TabsContent>

            <TabsContent value="revisions">
              <AppRevisionList appId={app.id} />
            </TabsContent>
          </Tabs>
          </>
        ) : null}
      </div>

      {/* Status change confirmation dialog */}
      <Dialog open={statusDialogOpen} onOpenChange={setStatusDialogOpen}>
        <DialogContent showCloseButton={false}>
          <DialogHeader>
            <DialogTitle>
              {pendingStatus === "ACTIVE"
                ? t("detail.statusChangeConfirm.activateTitle")
                : t("detail.statusChangeConfirm.deactivateTitle")}
            </DialogTitle>
            <DialogDescription>
              {pendingStatus === "ACTIVE"
                ? t("detail.statusChangeConfirm.activateDescription")
                : t("detail.statusChangeConfirm.deactivateDescription")}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                setStatusDialogOpen(false);
                setPendingStatus(null);
              }}
              disabled={updateStatus.isPending}
            >
              {t("detail.statusChangeConfirm.cancel")}
            </Button>
            <Button
              type="button"
              onClick={confirmStatusChange}
              aria-busy={updateStatus.isPending}
              disabled={updateStatus.isPending}
            >
              {updateStatus.isPending && (
                <Loader2 className="mr-2 size-4 animate-spin" />
              )}
              {t("detail.statusChangeConfirm.confirm")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete confirmation dialog */}
      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent showCloseButton={false}>
          <DialogHeader>
            <DialogTitle>{t("detail.deleteConfirm.title")}</DialogTitle>
            <DialogDescription>
              {t("detail.deleteConfirm.description")}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setDeleteDialogOpen(false)}
              disabled={deleteApp.isPending}
            >
              {t("detail.deleteConfirm.cancel")}
            </Button>
            <Button
              type="button"
              variant="destructive"
              onClick={handleDelete}
              aria-busy={deleteApp.isPending}
              disabled={deleteApp.isPending}
            >
              {deleteApp.isPending && (
                <Loader2 className="mr-2 size-4 animate-spin" />
              )}
              {t("detail.deleteConfirm.confirm")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
