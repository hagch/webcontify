"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
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
  const t = useTranslations("roles");

  const createRoleMutation = useCreateRole({
    mutation: {
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: getListRolesQueryKey() });
      },
      onError: () => {
        toast.error(t("toasts.createError"));
      },
    },
  });

  const updateRoleMutation = useUpdateRole({
    mutation: {
      onSuccess: (response) => {
        if (response.status === 200) {
          toast.success(t("toasts.updated"));
          queryClient.invalidateQueries({ queryKey: getListRolesQueryKey() });
          queryClient.invalidateQueries({
            queryKey: getGetRoleQueryKey(response.data.id),
          });
        }
      },
      onError: () => {
        toast.error(t("toasts.updateError"));
      },
    },
  });

  const deleteRoleMutation = useDeleteRole({
    mutation: {
      onSuccess: () => {
        toast.success(t("toasts.deleted"));
        queryClient.invalidateQueries({ queryKey: getListRolesQueryKey() });
      },
      onError: () => {
        toast.error(t("toasts.deleteError"));
      },
    },
  });

  const setRoleAssigneesMutation = useSetRoleAssignees({
    mutation: {
      onSuccess: (response, variables) => {
        if (response.status === 200) {
          toast.success(t("toasts.assigneesSetSuccess"));
          queryClient.invalidateQueries({ queryKey: getListRolesQueryKey() });
          queryClient.invalidateQueries({
            queryKey: getGetRoleQueryKey(variables.roleId),
          });
        }
      },
      onError: () => {
        toast.error(t("toasts.assigneesSetError"));
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
