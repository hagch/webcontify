"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Loader2 } from "lucide-react";
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
import { Skeleton } from "@/components/ui/skeleton";
import type { PlatformAdminResponse } from "@/generated/platform-admin";
import { AdminActionsMenu } from "./admin-actions-menu";
import { useAdminActions } from "../hooks/use-admin-actions";

interface AdminTableProps {
  admins: PlatformAdminResponse[];
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

function AdminTableSkeleton() {
  const t = useTranslations("admins");
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>{t("tableHeaders.name")}</TableHead>
          <TableHead>{t("tableHeaders.email")}</TableHead>
          <TableHead>{t("tableHeaders.status")}</TableHead>
          <TableHead>{t("tableHeaders.createdAt")}</TableHead>
          <TableHead className="text-right">{t("tableHeaders.actions")}</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {Array.from({ length: 3 }).map((_, i) => (
          <TableRow key={i}>
            <TableCell>
              <Skeleton className="h-4 w-36" />
            </TableCell>
            <TableCell>
              <Skeleton className="h-4 w-48" />
            </TableCell>
            <TableCell>
              <Skeleton className="h-5 w-20 rounded-full" />
            </TableCell>
            <TableCell>
              <Skeleton className="h-4 w-24" />
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

export function AdminTable({ admins, isLoading, isRefetching }: AdminTableProps) {
  const [adminToDelete, setAdminToDelete] = useState<PlatformAdminResponse | null>(null);
  const { deleteAdmin, isDeletingAdmin } = useAdminActions();
  const t = useTranslations("admins");

  if (isLoading) {
    return <AdminTableSkeleton />;
  }

  if (admins.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center rounded-lg border border-dashed p-12 text-center">
        <p className="text-lg font-medium">{t("emptyState.title")}</p>
        <p className="mt-1 text-sm text-muted-foreground">
          {t("emptyState.description")}
        </p>
      </div>
    );
  }

  function handleDeleteConfirm() {
    if (!adminToDelete) return;
    deleteAdmin(adminToDelete.id, () => {
      setAdminToDelete(null);
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
        <Table data-testid="admin-table">
          <TableHeader>
            <TableRow>
              <TableHead>{t("tableHeaders.name")}</TableHead>
              <TableHead>{t("tableHeaders.email")}</TableHead>
              <TableHead>{t("tableHeaders.status")}</TableHead>
              <TableHead>{t("tableHeaders.createdAt")}</TableHead>
              <TableHead className="text-right">{t("tableHeaders.actions")}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {admins.map((admin) => (
              <TableRow key={admin.id} data-testid="admin-table-row">
                <TableCell className="font-medium">
                  {admin.firstName} {admin.lastName}
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {admin.email}
                </TableCell>
                <TableCell>
                  {admin.enabled ? (
                    <Badge
                      variant="outline"
                      className="bg-emerald-100 text-emerald-800 border-emerald-200 dark:bg-emerald-900/30 dark:text-emerald-400 dark:border-emerald-800"
                    >
                      {t("statusValues.active")}
                    </Badge>
                  ) : (
                    <Badge
                      variant="outline"
                      className="bg-red-100 text-red-700 border-red-200 dark:bg-red-900/30 dark:text-red-400 dark:border-red-800"
                    >
                      {t("statusValues.deactivated")}
                    </Badge>
                  )}
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {formatDate(admin.createdAt)}
                </TableCell>
                <TableCell className="text-right">
                  <AdminActionsMenu
                    admin={admin}
                    onDeleteRequest={(a) => setAdminToDelete(a)}
                  />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

      <Dialog
        open={adminToDelete !== null}
        onOpenChange={(open) => {
          if (!open) setAdminToDelete(null);
        }}
      >
        <DialogContent data-testid="delete-admin-dialog" showCloseButton={false}>
          <DialogHeader>
            <DialogTitle>{t("deleteDialog.title")}</DialogTitle>
            <DialogDescription>
              {t("deleteDialog.description", {
                name: adminToDelete
                  ? `${adminToDelete.firstName} ${adminToDelete.lastName}`
                  : "",
              })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              data-testid="delete-admin-cancel"
              type="button"
              variant="outline"
              onClick={() => setAdminToDelete(null)}
              disabled={isDeletingAdmin}
            >
              {t("deleteDialog.cancel")}
            </Button>
            <Button
              data-testid="delete-admin-confirm"
              type="button"
              variant="destructive"
              onClick={handleDeleteConfirm}
              aria-busy={isDeletingAdmin}
            >
              {isDeletingAdmin && (
                <Loader2 className="mr-2 size-4 animate-spin" />
              )}
              {t("deleteDialog.confirm")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
