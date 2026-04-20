"use client";

import { use, useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { Loader2 } from "lucide-react";
import Link from "next/link";
import { AppHeader } from "@/components/layout/app-header";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";
import { useGetUser, useGetUserRoles, UserStatus } from "@/generated/identity";
import { UserStatusBadge } from "@/features/users/components/user-status-badge";
import { useUserActions } from "@/features/users/hooks/use-user-actions";
import { usePermissions } from "@/hooks/use-permissions";

interface UserDetailPageProps {
  params: Promise<{ userId: string }>;
}

function UserDetailSkeleton() {
  return (
    <div className="space-y-6">
      <div className="rounded-lg border p-6">
        <Skeleton className="mb-4 h-5 w-32" />
        <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="space-y-1">
              <Skeleton className="h-4 w-24" />
              <Skeleton className="h-5 w-40" />
            </div>
          ))}
        </dl>
      </div>
    </div>
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

export default function UserDetailPage({ params }: UserDetailPageProps) {
  const { userId } = use(params);
  const router = useRouter();
  const t = useTranslations("users.detailPage");
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);

  const { data, isLoading } = useGetUser(userId);
  const user = data?.status === 200 ? data.data : undefined;

  const { data: rolesData } = useGetUserRoles(userId);
  const roles = rolesData?.status === 200 ? rolesData.data.roles : [];

  const { resendInvite, activateUser, deactivateUser, deleteUser, isDeletingUser, isUpdatingStatus, isResendingInvite } =
    useUserActions();

  const { can } = usePermissions();
  const canEdit = can("user_management", "editor");
  const canDelete = can("user_management", "deleter");
  const hasAnyWritePermission = canEdit || canDelete;

  function handleDelete() {
    deleteUser(userId, () => {
      setDeleteDialogOpen(false);
      router.push("/users");
    });
  }

  const fullName = user ? `${user.firstName} ${user.lastName}` : t("fallbackName");

  return (
    <>
      <AppHeader
        mode="detail"
        breadcrumbs={[
          { label: t("breadcrumb"), href: "/users" },
          {
            label: fullName,
            suffix: user ? <UserStatusBadge status={user.status} /> : undefined,
          },
        ]}
      />
      <div className="flex flex-1 flex-col gap-6 p-4 md:p-6">
        {isLoading ? (
          <UserDetailSkeleton />
        ) : user ? (
          <>
            {/* Profil */}
            <section className="rounded-lg border p-6">
              <h2 className="mb-4 text-sm font-medium text-muted-foreground uppercase tracking-wider">
                {t("profileSection")}
              </h2>
              <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div>
                  <dt className="text-sm text-muted-foreground">{t("firstName")}</dt>
                  <dd className="mt-1 font-medium">{user.firstName}</dd>
                </div>
                <div>
                  <dt className="text-sm text-muted-foreground">{t("lastName")}</dt>
                  <dd className="mt-1 font-medium">{user.lastName}</dd>
                </div>
                <div>
                  <dt className="text-sm text-muted-foreground">{t("email")}</dt>
                  <dd className="mt-1 font-medium">{user.email}</dd>
                </div>
                <div>
                  <dt className="text-sm text-muted-foreground">{t("status")}</dt>
                  <dd className="mt-1">
                    <UserStatusBadge status={user.status} />
                  </dd>
                </div>
                <div>
                  <dt className="text-sm text-muted-foreground">{t("createdAt")}</dt>
                  <dd className="mt-1 text-sm">{formatDate(user.createdAt)}</dd>
                </div>
                <div>
                  <dt className="text-sm text-muted-foreground">{t("updatedAt")}</dt>
                  <dd className="mt-1 text-sm">{formatDate(user.updatedAt)}</dd>
                </div>
                {user.invitedBy && (
                  <div>
                    <dt className="text-sm text-muted-foreground">{t("invitedBy")}</dt>
                    <dd className="mt-1 text-sm">{user.invitedBy}</dd>
                  </div>
                )}
              </dl>
            </section>

            {/* Aktionen — nur anzeigen wenn mindestens eine Schreib-Permission vorhanden */}
            {hasAnyWritePermission && (
              <section className="rounded-lg border p-6">
                <h2 className="mb-4 text-sm font-medium text-muted-foreground uppercase tracking-wider">
                  {t("actionsSection")}
                </h2>
                <div className="flex flex-wrap gap-3">
                  {canEdit && user.status === UserStatus.INVITED && (
                    <Button
                      variant="outline"
                      onClick={() => resendInvite(user.id)}
                      aria-busy={isResendingInvite}
                      disabled={isResendingInvite}
                    >
                      {isResendingInvite && (
                        <Loader2 className="mr-2 size-4 animate-spin" />
                      )}
                      {t("resendInvite")}
                    </Button>
                  )}
                  {canEdit && (user.status === UserStatus.ACTIVE || user.status === UserStatus.INVITED) && (
                    <Button
                      variant="outline"
                      onClick={() => deactivateUser(user.id)}
                      aria-busy={isUpdatingStatus}
                      disabled={isUpdatingStatus}
                    >
                      {isUpdatingStatus && (
                        <Loader2 className="mr-2 size-4 animate-spin" />
                      )}
                      {t("deactivate")}
                    </Button>
                  )}
                  {canEdit && user.status === UserStatus.DEACTIVATED && (
                    <Button
                      variant="outline"
                      onClick={() => activateUser(user.id)}
                      aria-busy={isUpdatingStatus}
                      disabled={isUpdatingStatus}
                    >
                      {isUpdatingStatus && (
                        <Loader2 className="mr-2 size-4 animate-spin" />
                      )}
                      {t("activate")}
                    </Button>
                  )}
                  {canDelete && (
                    <Button
                      variant="destructive"
                      onClick={() => setDeleteDialogOpen(true)}
                    >
                      {t("delete")}
                    </Button>
                  )}
                </div>
              </section>
            )}

            {/* Rollen */}
            <section className="rounded-lg border p-6">
              <h2 className="mb-4 text-sm font-medium text-muted-foreground uppercase tracking-wider">
                {t("rolesSection")}
              </h2>
              {roles.length === 0 ? (
                <p className="text-sm text-muted-foreground">{t("noRoles")}</p>
              ) : (
                <div className="flex flex-wrap gap-2">
                  {roles.map((role) => (
                    <Link key={role.id} href={`/roles/${role.id}`}>
                      <Badge
                        variant={role.systemRole ? "default" : "secondary"}
                        className={
                          role.systemRole
                            ? "bg-indigo-100 text-indigo-700 hover:bg-indigo-200 dark:bg-indigo-900/30 dark:text-indigo-300"
                            : "hover:bg-secondary/80"
                        }
                      >
                        {role.name}
                      </Badge>
                    </Link>
                  ))}
                </div>
              )}
            </section>
          </>
        ) : (
          <div className="flex flex-col items-center justify-center rounded-lg border border-dashed p-12 text-center">
            <p className="text-lg font-medium">{t("notFoundTitle")}</p>
            <p className="mt-1 text-sm text-muted-foreground">
              {t("notFoundDescription")}
            </p>
            <Button
              variant="outline"
              className="mt-4"
              render={<Link href="/users" />}
            >
              {t("backToOverview")}
            </Button>
          </div>
        )}
      </div>

      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent showCloseButton={false}>
          <DialogHeader>
            <DialogTitle>{t("deleteDialogTitle")}</DialogTitle>
            <DialogDescription>
              {t.rich("deleteDialogDescription", {
                name: fullName,
                strong: (chunks) => <strong>{chunks}</strong>,
              })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setDeleteDialogOpen(false)}
              disabled={isDeletingUser}
            >
              {t("deleteDialogCancel")}
            </Button>
            <Button
              type="button"
              variant="destructive"
              onClick={handleDelete}
              aria-busy={isDeletingUser}
            >
              {isDeletingUser && (
                <Loader2 className="mr-2 size-4 animate-spin" />
              )}
              {t("deleteDialogConfirm")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
