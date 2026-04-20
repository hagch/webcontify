"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Loader2, Trash2 } from "lucide-react";
import { toast } from "sonner";
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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  useSetRoleAssignees,
  getListRolesQueryKey,
  getGetRoleQueryKey,
  type RoleAssigneeResponse,
} from "@/generated/identity";
import { usePermissions } from "@/hooks/use-permissions";

interface RoleAssigneesTableProps {
  assignees: RoleAssigneeResponse[];
  roleId: string;
  isSystemRole: boolean;
}

export function RoleAssigneesTable({
  assignees,
  roleId,
  isSystemRole,
}: RoleAssigneesTableProps) {
  const [assigneeToRemove, setAssigneeToRemove] =
    useState<RoleAssigneeResponse | null>(null);
  const queryClient = useQueryClient();
  const setRoleAssignees = useSetRoleAssignees();
  const t = useTranslations("roles");
  const te = useTranslations("errors");
  const { can } = usePermissions();
  const canRemoveAssignees = can("role_management", "editor");

  if (assignees.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center rounded-lg border border-dashed p-12 text-center">
        <p className="text-lg font-medium">{t("assigneesTable.emptyTitle")}</p>
        <p className="mt-1 text-sm text-muted-foreground">
          {t("assigneesTable.emptyDescription")}
        </p>
      </div>
    );
  }

  async function handleRemoveConfirm() {
    if (!assigneeToRemove) return;
    const remainingIds = assignees
      .filter((a) => a.userId !== assigneeToRemove.userId)
      .map((a) => a.userId);

    try {
      const response = await setRoleAssignees.mutateAsync({
        roleId,
        data: { userIds: remainingIds },
      });
      if (response.status === 200) {
        toast.success(
          t("toasts.assigneesUpdated", {
            firstName: assigneeToRemove.firstName,
            lastName: assigneeToRemove.lastName,
          })
        );
        setAssigneeToRemove(null);
        queryClient.invalidateQueries({ queryKey: getListRolesQueryKey() });
        queryClient.invalidateQueries({
          queryKey: getGetRoleQueryKey(roleId),
        });
      }
    } catch (error: unknown) {
      const err = error as { code?: string };
      toast.error(te.has(err.code ?? "") ? te(err.code!) : te("generic"));
    }
  }

  return (
    <>
      <Table data-testid="role-assignees-table">
        <TableHeader>
          <TableRow>
            <TableHead>{t("assigneesTable.nameHeader")}</TableHead>
            <TableHead>{t("assigneesTable.emailHeader")}</TableHead>
            {!isSystemRole && canRemoveAssignees && (
              <TableHead className="text-right">{t("assigneesTable.actionsHeader")}</TableHead>
            )}
          </TableRow>
        </TableHeader>
        <TableBody>
          {assignees.map((assignee) => (
            <TableRow key={assignee.userId}>
              <TableCell className="font-medium">
                {assignee.firstName} {assignee.lastName}
              </TableCell>
              <TableCell className="text-muted-foreground">
                {assignee.email}
              </TableCell>
              {!isSystemRole && canRemoveAssignees && (
                <TableCell className="text-right">
                  <Button
                    variant="ghost"
                    size="icon"
                    aria-label={t("assigneesTable.removeLabel", {
                      name: `${assignee.firstName} ${assignee.lastName}`,
                    })}
                    onClick={() => setAssigneeToRemove(assignee)}
                  >
                    <Trash2 className="size-4 text-destructive" />
                  </Button>
                </TableCell>
              )}
            </TableRow>
          ))}
        </TableBody>
      </Table>

      <Dialog
        open={assigneeToRemove !== null}
        onOpenChange={(open) => {
          if (!open) setAssigneeToRemove(null);
        }}
      >
        <DialogContent showCloseButton={false}>
          <DialogHeader>
            <DialogTitle>{t("assigneesTable.removeDialog.title")}</DialogTitle>
            <DialogDescription>
              {t("assigneesTable.removeDialog.description", {
                name: assigneeToRemove
                  ? `${assigneeToRemove.firstName} ${assigneeToRemove.lastName}`
                  : "",
              })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setAssigneeToRemove(null)}
              disabled={setRoleAssignees.isPending}
            >
              {t("assigneesTable.removeDialog.cancel")}
            </Button>
            <Button
              type="button"
              variant="destructive"
              onClick={handleRemoveConfirm}
              aria-busy={setRoleAssignees.isPending}
            >
              {setRoleAssignees.isPending && (
                <Loader2 className="mr-2 size-4 animate-spin" />
              )}
              {t("assigneesTable.removeDialog.confirm")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
