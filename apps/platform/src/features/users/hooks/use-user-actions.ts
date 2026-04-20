"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import {
  useResendInvite,
  useUpdateUserStatus,
  useDeleteUser,
  getListUsersQueryKey,
  getGetUserQueryKey,
  UserStatus,
  UserStatusUpdate,
} from "@/generated/identity";

export function useUserActions() {
  const queryClient = useQueryClient();
  const t = useTranslations("users");

  const resendInviteMutation = useResendInvite({
    mutation: {
      onSuccess: () => {
        toast.success(t("toasts.inviteResent"));
        queryClient.invalidateQueries({ queryKey: getListUsersQueryKey() });
      },
      onError: () => {
        toast.error(t("toasts.resendError"));
      },
    },
  });

  const updateStatusMutation = useUpdateUserStatus({
    mutation: {
      onSuccess: (response) => {
        if (response.status === 200) {
          const user = response.data;
          toast.success(
            user.status === UserStatus.ACTIVE
              ? t("toasts.activated")
              : t("toasts.deactivated")
          );
          queryClient.invalidateQueries({ queryKey: getListUsersQueryKey() });
          queryClient.invalidateQueries({
            queryKey: getGetUserQueryKey(user.id),
          });
        }
      },
      onError: () => {
        toast.error(t("toasts.statusError"));
      },
    },
  });

  const deleteUserMutation = useDeleteUser({
    mutation: {
      onSuccess: () => {
        toast.success(t("toasts.deleted"));
        queryClient.invalidateQueries({ queryKey: getListUsersQueryKey() });
      },
      onError: () => {
        toast.error(t("toasts.deleteError"));
      },
    },
  });

  function resendInvite(userId: string) {
    resendInviteMutation.mutate({ userId });
  }

  function activateUser(userId: string) {
    updateStatusMutation.mutate({
      userId,
      data: { status: UserStatusUpdate.ACTIVE },
    });
  }

  function deactivateUser(userId: string) {
    updateStatusMutation.mutate({
      userId,
      data: { status: UserStatusUpdate.DEACTIVATED },
    });
  }

  function deleteUser(userId: string, onSuccess?: () => void) {
    deleteUserMutation.mutate(
      { userId },
      {
        onSuccess: () => {
          onSuccess?.();
        },
      }
    );
  }

  return {
    resendInvite,
    activateUser,
    deactivateUser,
    deleteUser,
    isResendingInvite: resendInviteMutation.isPending,
    isUpdatingStatus: updateStatusMutation.isPending,
    isDeletingUser: deleteUserMutation.isPending,
  };
}
