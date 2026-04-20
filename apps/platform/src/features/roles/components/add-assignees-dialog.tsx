"use client";

import { useState } from "react";
import { Loader2, UserPlus } from "lucide-react";
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
  DialogTrigger,
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
import {
  useListUsers,
  useSetRoleAssignees,
  getListRolesQueryKey,
  getGetRoleQueryKey,
} from "@/generated/identity";

interface AddAssigneesDialogProps {
  roleId: string;
  currentAssigneeIds: string[];
  children: React.ReactNode;
}

export function AddAssigneesDialog({
  roleId,
  currentAssigneeIds,
  children,
}: AddAssigneesDialogProps) {
  const [open, setOpen] = useState(false);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);

  const queryClient = useQueryClient();
  const { data, isLoading } = useListUsers();
  const setRoleAssignees = useSetRoleAssignees();

  const allUsers = (data?.status === 200 ? data.data.items : undefined) ?? [];
  const availableUsers = allUsers.filter(
    (user) => !currentAssigneeIds.includes(user.id)
  );

  function toggleUser(userId: string) {
    setSelectedIds((prev) =>
      prev.includes(userId)
        ? prev.filter((id) => id !== userId)
        : [...prev, userId]
    );
  }

  async function handleAdd() {
    if (selectedIds.length === 0) return;

    const newAssigneeIds = [...currentAssigneeIds, ...selectedIds];

    try {
      const response = await setRoleAssignees.mutateAsync({
        roleId,
        data: { userIds: newAssigneeIds },
      });
      if (response.status === 200) {
        toast.success(
          `${selectedIds.length} Benutzer wurden der Rolle zugewiesen.`
        );
        setOpen(false);
        setSelectedIds([]);
        queryClient.invalidateQueries({ queryKey: getListRolesQueryKey() });
        queryClient.invalidateQueries({
          queryKey: getGetRoleQueryKey(roleId),
        });
      }
    } catch {
      toast.error(
        "Fehler beim Hinzufuegen der Benutzer. Bitte versuche es erneut."
      );
    }
  }

  function handleOpenChange(nextOpen: boolean) {
    setOpen(nextOpen);
    if (!nextOpen) {
      setSelectedIds([]);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger render={children as React.ReactElement} />
      <DialogContent data-testid="add-assignees-dialog" className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Benutzer hinzufuegen</DialogTitle>
          <DialogDescription>
            Waehle Benutzer aus, die dieser Rolle zugewiesen werden sollen.
          </DialogDescription>
        </DialogHeader>

        <div className="max-h-72 overflow-y-auto rounded-md border">
          {isLoading ? (
            <div className="space-y-2 p-3">
              {Array.from({ length: 4 }).map((_, i) => (
                <Skeleton key={i} className="h-10 w-full" />
              ))}
            </div>
          ) : availableUsers.length === 0 ? (
            <div className="flex flex-col items-center justify-center p-8 text-center">
              <UserPlus className="mb-2 size-8 text-muted-foreground" />
              <p className="text-sm font-medium">Alle Benutzer zugewiesen</p>
              <p className="mt-1 text-xs text-muted-foreground">
                Es gibt keine weiteren Benutzer, die hinzugefuegt werden
                koennen.
              </p>
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-10" />
                  <TableHead>Name</TableHead>
                  <TableHead>E-Mail</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {availableUsers.map((user) => {
                  const isSelected = selectedIds.includes(user.id);
                  return (
                    <TableRow
                      key={user.id}
                      className="cursor-pointer"
                      onClick={() => toggleUser(user.id)}
                      data-selected={isSelected}
                    >
                      <TableCell>
                        <input
                          type="checkbox"
                          checked={isSelected}
                          onChange={() => toggleUser(user.id)}
                          onClick={(e) => e.stopPropagation()}
                          aria-label={`${user.firstName} ${user.lastName} auswaehlen`}
                          className="size-4 cursor-pointer rounded border-input accent-primary"
                        />
                      </TableCell>
                      <TableCell className="font-medium">
                        {user.firstName} {user.lastName}
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {user.email}
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </div>

        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            onClick={() => handleOpenChange(false)}
            disabled={setRoleAssignees.isPending}
          >
            Abbrechen
          </Button>
          <Button
            type="button"
            onClick={handleAdd}
            disabled={selectedIds.length === 0}
            aria-busy={setRoleAssignees.isPending}
          >
            {setRoleAssignees.isPending && (
              <Loader2 className="mr-2 size-4 animate-spin" />
            )}
            {selectedIds.length > 0
              ? `${selectedIds.length} Benutzer hinzufuegen`
              : "Hinzufuegen"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
