"use client";

import { use, useState } from "react";
import { useRouter } from "next/navigation";
import { Loader2, ArrowLeft } from "lucide-react";
import Link from "next/link";
import { AppHeader } from "@/components/layout/app-header";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";
import { useGetUser, UserStatus } from "@/generated/identity";
import { UserStatusBadge } from "@/features/users/components/user-status-badge";
import { useUserActions } from "@/features/users/hooks/use-user-actions";

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
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);

  const { data, isLoading } = useGetUser(userId);
  const user = data?.status === 200 ? data.data : undefined;

  const { resendInvite, activateUser, deactivateUser, deleteUser, isDeletingUser, isUpdatingStatus, isResendingInvite } =
    useUserActions();

  function handleDelete() {
    deleteUser(userId, () => {
      setDeleteDialogOpen(false);
      router.push("/users");
    });
  }

  const fullName = user ? `${user.firstName} ${user.lastName}` : "Benutzer";

  return (
    <>
      <AppHeader
        breadcrumbs={[
          { label: "Benutzer", href: "/users" },
          { label: fullName },
        ]}
      />
      <div className="flex flex-1 flex-col gap-6 p-4 md:p-6">
        <div className="flex items-center gap-4">
          <Button
            variant="ghost"
            size="icon"
            render={<Link href="/users" />}
            aria-label="Zurueck zu Benutzer"
          >
            <ArrowLeft className="size-4" />
          </Button>
          <h1 className="text-2xl font-semibold tracking-tight">{fullName}</h1>
          {user && <UserStatusBadge status={user.status} />}
        </div>

        {isLoading ? (
          <UserDetailSkeleton />
        ) : user ? (
          <>
            {/* Profil */}
            <section className="rounded-lg border p-6">
              <h2 className="mb-4 text-sm font-medium text-muted-foreground uppercase tracking-wider">
                Profil
              </h2>
              <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div>
                  <dt className="text-sm text-muted-foreground">Vorname</dt>
                  <dd className="mt-1 font-medium">{user.firstName}</dd>
                </div>
                <div>
                  <dt className="text-sm text-muted-foreground">Nachname</dt>
                  <dd className="mt-1 font-medium">{user.lastName}</dd>
                </div>
                <div>
                  <dt className="text-sm text-muted-foreground">E-Mail</dt>
                  <dd className="mt-1 font-medium">{user.email}</dd>
                </div>
                <div>
                  <dt className="text-sm text-muted-foreground">Status</dt>
                  <dd className="mt-1">
                    <UserStatusBadge status={user.status} />
                  </dd>
                </div>
                <div>
                  <dt className="text-sm text-muted-foreground">Erstellt am</dt>
                  <dd className="mt-1 text-sm">{formatDate(user.createdAt)}</dd>
                </div>
                <div>
                  <dt className="text-sm text-muted-foreground">Aktualisiert am</dt>
                  <dd className="mt-1 text-sm">{formatDate(user.updatedAt)}</dd>
                </div>
                {user.invitedBy && (
                  <div>
                    <dt className="text-sm text-muted-foreground">Eingeladen von</dt>
                    <dd className="mt-1 text-sm">{user.invitedBy}</dd>
                  </div>
                )}
              </dl>
            </section>

            {/* Aktionen */}
            <section className="rounded-lg border p-6">
              <h2 className="mb-4 text-sm font-medium text-muted-foreground uppercase tracking-wider">
                Aktionen
              </h2>
              <div className="flex flex-wrap gap-3">
                {user.status === UserStatus.INVITED && (
                  <Button
                    variant="outline"
                    onClick={() => resendInvite(user.id)}
                    aria-busy={isResendingInvite}
                    disabled={isResendingInvite}
                  >
                    {isResendingInvite && (
                      <Loader2 className="mr-2 size-4 animate-spin" />
                    )}
                    Einladung erneut senden
                  </Button>
                )}
                {(user.status === UserStatus.ACTIVE || user.status === UserStatus.INVITED) && (
                  <Button
                    variant="outline"
                    onClick={() => deactivateUser(user.id)}
                    aria-busy={isUpdatingStatus}
                    disabled={isUpdatingStatus}
                  >
                    {isUpdatingStatus && (
                      <Loader2 className="mr-2 size-4 animate-spin" />
                    )}
                    Deaktivieren
                  </Button>
                )}
                {user.status === UserStatus.DEACTIVATED && (
                  <Button
                    variant="outline"
                    onClick={() => activateUser(user.id)}
                    aria-busy={isUpdatingStatus}
                    disabled={isUpdatingStatus}
                  >
                    {isUpdatingStatus && (
                      <Loader2 className="mr-2 size-4 animate-spin" />
                    )}
                    Aktivieren
                  </Button>
                )}
                <Button
                  variant="destructive"
                  onClick={() => setDeleteDialogOpen(true)}
                >
                  Loeschen
                </Button>
              </div>
            </section>

            {/* Rollen (Platzhalter) */}
            <section className="rounded-lg border p-6">
              <h2 className="mb-4 text-sm font-medium text-muted-foreground uppercase tracking-wider">
                Rollen
              </h2>
              <p className="text-sm text-muted-foreground">
                Rollenverwaltung wird in einem zukuenftigen Update implementiert.
              </p>
            </section>
          </>
        ) : (
          <div className="flex flex-col items-center justify-center rounded-lg border border-dashed p-12 text-center">
            <p className="text-lg font-medium">Benutzer nicht gefunden</p>
            <p className="mt-1 text-sm text-muted-foreground">
              Der Benutzer existiert nicht oder wurde geloescht.
            </p>
            <Button
              variant="outline"
              className="mt-4"
              render={<Link href="/users" />}
            >
              Zurueck zur Uebersicht
            </Button>
          </div>
        )}
      </div>

      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent showCloseButton={false}>
          <DialogHeader>
            <DialogTitle>Benutzer loeschen</DialogTitle>
            <DialogDescription>
              Moechtest du <strong>{fullName}</strong> wirklich loeschen? Diese
              Aktion kann nicht rueckgaengig gemacht werden.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setDeleteDialogOpen(false)}
              disabled={isDeletingUser}
            >
              Abbrechen
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
              Loeschen
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
