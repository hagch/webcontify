"use client";

import { useQueryClient } from "@tanstack/react-query";
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

  const resendInviteMutation = useResendInvite({
    mutation: {
      onSuccess: () => {
        toast.success("Einladung wurde erneut gesendet.");
        queryClient.invalidateQueries({ queryKey: getListUsersQueryKey() });
      },
      onError: () => {
        toast.error("Fehler beim Senden der Einladung. Bitte versuche es erneut.");
      },
    },
  });

  const updateStatusMutation = useUpdateUserStatus({
    mutation: {
      onSuccess: (response) => {
        if (response.status === 200) {
          const user = response.data;
          const label =
            user.status === UserStatus.ACTIVE
              ? "aktiviert"
              : "deaktiviert";
          toast.success(`Benutzer wurde ${label}.`);
          queryClient.invalidateQueries({ queryKey: getListUsersQueryKey() });
          queryClient.invalidateQueries({
            queryKey: getGetUserQueryKey(user.id),
          });
        }
      },
      onError: () => {
        toast.error("Fehler beim Aktualisieren des Status. Bitte versuche es erneut.");
      },
    },
  });

  const deleteUserMutation = useDeleteUser({
    mutation: {
      onSuccess: () => {
        toast.success("Benutzer wurde geloescht.");
        queryClient.invalidateQueries({ queryKey: getListUsersQueryKey() });
      },
      onError: () => {
        toast.error("Fehler beim Loeschen des Benutzers. Bitte versuche es erneut.");
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
