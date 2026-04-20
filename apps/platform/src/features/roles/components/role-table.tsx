"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Loader2, Shield, MoreHorizontal, Pencil, Trash2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
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
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Skeleton } from "@/components/ui/skeleton";
import type { RoleResponse } from "@/generated/identity";
import { useRoleActions } from "../hooks/use-role-actions";

interface RoleTableProps {
  roles: RoleResponse[];
  isLoading: boolean;
  isRefetching: boolean;
}

function RoleTableSkeleton() {
  return (
    <Table data-testid="table-skeleton">
      <TableHeader>
        <TableRow>
          <TableHead>Name</TableHead>
          <TableHead>Beschreibung</TableHead>
          <TableHead>Typ</TableHead>
          <TableHead>Mitglieder</TableHead>
          <TableHead className="text-right">Aktionen</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {Array.from({ length: 3 }).map((_, i) => (
          <TableRow key={i}>
            <TableCell>
              <Skeleton className="h-4 w-32" />
            </TableCell>
            <TableCell>
              <Skeleton className="h-4 w-48" />
            </TableCell>
            <TableCell>
              <Skeleton className="h-5 w-20 rounded-full" />
            </TableCell>
            <TableCell>
              <Skeleton className="h-4 w-8" />
            </TableCell>
            <TableCell className="text-right">
              <Skeleton className="ml-auto h-8 w-8 rounded" />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

export function RoleTable({ roles, isLoading, isRefetching }: RoleTableProps) {
  const [roleToDelete, setRoleToDelete] = useState<RoleResponse | null>(null);
  const { deleteRole, isDeletingRole } = useRoleActions();
  const router = useRouter();

  if (isLoading) {
    return <RoleTableSkeleton />;
  }

  if (roles.length === 0) {
    return (
      <div data-testid="empty-state" className="flex flex-col items-center justify-center rounded-lg border border-dashed p-12 text-center">
        <p className="text-lg font-medium">Noch keine Rollen erstellt</p>
        <p className="mt-1 text-sm text-muted-foreground">
          Erstelle eine Rolle, um Benutzerberechtigungen zu verwalten.
        </p>
      </div>
    );
  }

  function handleDeleteConfirm() {
    if (!roleToDelete) return;
    deleteRole(roleToDelete.id, () => {
      setRoleToDelete(null);
    });
  }

  return (
    <>
      <div className="relative">
        {isRefetching && (
          <div className="absolute inset-0 z-10 flex items-center justify-center rounded-lg bg-background/60">
            <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          </div>
        )}
        <Table data-testid="role-table">
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead>Beschreibung</TableHead>
              <TableHead>Typ</TableHead>
              <TableHead>Mitglieder</TableHead>
              <TableHead className="text-right">Aktionen</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {roles.map((role) => (
              <TableRow key={role.id} data-testid="role-table-row">
                <TableCell className="font-medium">
                  <Link
                    href={`/roles/${role.id}`}
                    className="hover:underline"
                  >
                    {role.name}
                  </Link>
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {role.description ?? (
                    <span className="italic">Keine Beschreibung</span>
                  )}
                </TableCell>
                <TableCell>
                  {role.systemRole ? (
                    <Badge
                      variant="outline"
                      className="bg-indigo-100 text-indigo-800 border-indigo-200 dark:bg-indigo-900/30 dark:text-indigo-400 dark:border-indigo-800"
                    >
                      <Shield className="mr-1 size-3" />
                      System
                    </Badge>
                  ) : (
                    <Badge
                      variant="outline"
                      className="bg-stone-100 text-stone-600 border-stone-200 dark:bg-stone-900/30 dark:text-stone-400 dark:border-stone-800"
                    >
                      Benutzerdefiniert
                    </Badge>
                  )}
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {role.assigneeCount}
                </TableCell>
                <TableCell className="text-right">
                  {!role.systemRole && (
                    <DropdownMenu>
                      <DropdownMenuTrigger
                        render={
                          <Button
                            variant="ghost"
                            size="icon"
                            aria-label={`Aktionen fuer ${role.name}`}
                          />
                        }
                      >
                        <MoreHorizontal className="size-4" />
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem
                          onClick={() => router.push(`/roles/${role.id}`)}
                        >
                          <Pencil className="mr-2 size-4" />
                          Bearbeiten
                        </DropdownMenuItem>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem
                          variant="destructive"
                          onClick={() => setRoleToDelete(role)}
                        >
                          <Trash2 className="mr-2 size-4" />
                          Loeschen
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      <Dialog
        open={roleToDelete !== null}
        onOpenChange={(open) => {
          if (!open) setRoleToDelete(null);
        }}
      >
        <DialogContent showCloseButton={false}>
          <DialogHeader>
            <DialogTitle>Rolle loeschen</DialogTitle>
            <DialogDescription>
              Moechtest du die Rolle{" "}
              <strong>{roleToDelete?.name}</strong> wirklich loeschen? Diese
              Aktion kann nicht rueckgaengig gemacht werden.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setRoleToDelete(null)}
              disabled={isDeletingRole}
            >
              Abbrechen
            </Button>
            <Button
              type="button"
              variant="destructive"
              onClick={handleDeleteConfirm}
              aria-busy={isDeletingRole}
            >
              {isDeletingRole && (
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
