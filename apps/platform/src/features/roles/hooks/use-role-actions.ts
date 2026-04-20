"use client";

import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  useCreateRole,
  useUpdateRole,
  useDeleteRole,
  useSetRoleAssignees,
  getListRolesQueryKey,
  getGetRoleQueryKey,
} from "@/generated/identity";

export function useRoleActions() {
  const queryClient = useQueryClient();

  const createRoleMutation = useCreateRole({
    mutation: {
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: getListRolesQueryKey() });
      },
      onError: () => {
        toast.error("Fehler beim Erstellen der Rolle. Bitte versuche es erneut.");
      },
    },
  });

  const updateRoleMutation = useUpdateRole({
    mutation: {
      onSuccess: (response) => {
        if (response.status === 200) {
          toast.success("Rolle wurde aktualisiert.");
          queryClient.invalidateQueries({ queryKey: getListRolesQueryKey() });
          queryClient.invalidateQueries({
            queryKey: getGetRoleQueryKey(response.data.id),
          });
        }
      },
      onError: () => {
        toast.error("Fehler beim Aktualisieren der Rolle. Bitte versuche es erneut.");
      },
    },
  });

  const deleteRoleMutation = useDeleteRole({
    mutation: {
      onSuccess: () => {
        toast.success("Rolle wurde geloescht.");
        queryClient.invalidateQueries({ queryKey: getListRolesQueryKey() });
      },
      onError: () => {
        toast.error("Fehler beim Loeschen der Rolle. Bitte versuche es erneut.");
      },
    },
  });

  const setRoleAssigneesMutation = useSetRoleAssignees({
    mutation: {
      onSuccess: (response, variables) => {
        if (response.status === 200) {
          toast.success("Rollenmitglieder wurden aktualisiert.");
          queryClient.invalidateQueries({ queryKey: getListRolesQueryKey() });
          queryClient.invalidateQueries({
            queryKey: getGetRoleQueryKey(variables.roleId),
          });
        }
      },
      onError: () => {
        toast.error("Fehler beim Aktualisieren der Rollenmitglieder. Bitte versuche es erneut.");
      },
    },
  });

  function deleteRole(roleId: string, onSuccess?: () => void) {
    deleteRoleMutation.mutate(
      { roleId },
      {
        onSuccess: () => {
          onSuccess?.();
        },
      }
    );
  }

  function setRoleAssignees(roleId: string, userIds: string[], onSuccess?: () => void) {
    setRoleAssigneesMutation.mutate(
      { roleId, data: { userIds } },
      {
        onSuccess: () => {
          onSuccess?.();
        },
      }
    );
  }

  return {
    createRoleMutation,
    updateRoleMutation,
    deleteRole,
    setRoleAssignees,
    isCreatingRole: createRoleMutation.isPending,
    isUpdatingRole: updateRoleMutation.isPending,
    isDeletingRole: deleteRoleMutation.isPending,
    isSettingAssignees: setRoleAssigneesMutation.isPending,
  };
}
