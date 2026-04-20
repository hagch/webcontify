"use client";

import { useState } from "react";
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

  if (assignees.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center rounded-lg border border-dashed p-12 text-center">
        <p className="text-lg font-medium">Keine Benutzer zugewiesen</p>
        <p className="mt-1 text-sm text-muted-foreground">
          Weise Benutzern diese Rolle zu, um ihre Berechtigungen zu verwalten.
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
          `${assigneeToRemove.firstName} ${assigneeToRemove.lastName} wurde aus der Rolle entfernt.`
        );
        setAssigneeToRemove(null);
        queryClient.invalidateQueries({ queryKey: getListRolesQueryKey() });
        queryClient.invalidateQueries({
          queryKey: getGetRoleQueryKey(roleId),
        });
      }
    } catch {
      toast.error(
        "Fehler beim Entfernen des Benutzers. Bitte versuche es erneut."
      );
    }
  }

  return (
    <>
      <Table data-testid="role-assignees-table">
        <TableHeader>
          <TableRow>
            <TableHead>Name</TableHead>
            <TableHead>E-Mail</TableHead>
            {!isSystemRole && (
              <TableHead className="text-right">Aktionen</TableHead>
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
              {!isSystemRole && (
                <TableCell className="text-right">
                  <Button
                    variant="ghost"
                    size="icon"
                    aria-label={`${assignee.firstName} ${assignee.lastName} entfernen`}
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
            <DialogTitle>Benutzer entfernen</DialogTitle>
            <DialogDescription>
              Moechtest du{" "}
              <strong>
                {assigneeToRemove?.firstName} {assigneeToRemove?.lastName}
              </strong>{" "}
              aus dieser Rolle entfernen?
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setAssigneeToRemove(null)}
              disabled={setRoleAssignees.isPending}
            >
              Abbrechen
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
              Entfernen
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
