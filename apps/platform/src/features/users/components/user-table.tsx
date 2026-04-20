"use client";

import { useState } from "react";
import Link from "next/link";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import type { UserResponse } from "@/generated/identity";
import { UserStatusBadge } from "./user-status-badge";
import { UserActionsMenu } from "./user-actions-menu";
import { UserTableSkeleton } from "./user-table-skeleton";
import { InviteUserDialog } from "./invite-user-dialog";
import { useUserActions } from "../hooks/use-user-actions";

interface UserTableProps {
  users: UserResponse[];
  isLoading: boolean;
  isRefetching: boolean;
}

function formatDate(dateString: string): string {
  return new Intl.DateTimeFormat("de-DE", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  }).format(new Date(dateString));
}

export function UserTable({ users, isLoading, isRefetching }: UserTableProps) {
  const [userToDelete, setUserToDelete] = useState<UserResponse | null>(null);
  const [cloneFromUserId, setCloneFromUserId] = useState<string | undefined>();
  const { deleteUser, isDeletingUser } = useUserActions();

  if (isLoading) {
    return <UserTableSkeleton />;
  }

  if (users.length === 0) {
    return (
      <div data-testid="empty-state" className="flex flex-col items-center justify-center rounded-lg border border-dashed p-12 text-center">
        <p className="text-lg font-medium">Noch keine Benutzer</p>
        <p className="mt-1 text-sm text-muted-foreground">
          Lade Benutzer ein, um mit der Zusammenarbeit zu beginnen.
        </p>
      </div>
    );
  }

  function handleDeleteConfirm() {
    if (!userToDelete) return;
    deleteUser(userToDelete.id, () => {
      setUserToDelete(null);
    });
  }

  function handleCloneRolesRequest(user: UserResponse) {
    setCloneFromUserId(user.id);
  }

  return (
    <>
      <div className="relative">
        {isRefetching && (
          <div className="absolute inset-0 z-10 flex items-center justify-center rounded-lg bg-background/60">
            <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          </div>
        )}
        <Table data-testid="user-table">
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead>E-Mail</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Eingeladen am</TableHead>
              <TableHead className="text-right">Aktionen</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {users.map((user) => (
              <TableRow key={user.id} data-testid="user-table-row">
                <TableCell className="font-medium">
                  <Link
                    href={`/users/${user.id}`}
                    className="hover:underline"
                  >
                    {user.firstName} {user.lastName}
                  </Link>
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {user.email}
                </TableCell>
                <TableCell>
                  <UserStatusBadge status={user.status} />
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {formatDate(user.createdAt)}
                </TableCell>
                <TableCell className="text-right">
                  <UserActionsMenu
                    user={user}
                    onDeleteRequest={(u) => setUserToDelete(u)}
                    onCloneRolesRequest={handleCloneRolesRequest}
                  />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      <Dialog
        open={userToDelete !== null}
        onOpenChange={(open) => {
          if (!open) setUserToDelete(null);
        }}
      >
        <DialogContent showCloseButton={false}>
          <DialogHeader>
            <DialogTitle>Benutzer loeschen</DialogTitle>
            <DialogDescription>
              Moechtest du{" "}
              <strong>
                {userToDelete?.firstName} {userToDelete?.lastName}
              </strong>{" "}
              wirklich loeschen? Diese Aktion kann nicht rueckgaengig gemacht
              werden.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setUserToDelete(null)}
              disabled={isDeletingUser}
            >
              Abbrechen
            </Button>
            <Button
              type="button"
              variant="destructive"
              onClick={handleDeleteConfirm}
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

      {cloneFromUserId && (
        <InviteUserDialog
          key={cloneFromUserId}
          initialCopyFromUserId={cloneFromUserId}
        >
          <span />
        </InviteUserDialog>
      )}
    </>
  );
}
