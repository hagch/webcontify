"use client";

import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  useDeletePlatformAdmin,
  useUpdatePlatformAdminStatus,
  useResetPlatformAdminPassword,
  getListPlatformAdminsQueryKey,
} from "@/generated/platform-admin";

export function useAdminActions() {
  const queryClient = useQueryClient();

  const deleteMutation = useDeletePlatformAdmin({
    mutation: {
      onSuccess: () => {
        toast.success("Administrator wurde entfernt.");
        queryClient.invalidateQueries({
          queryKey: getListPlatformAdminsQueryKey(),
        });
      },
      onError: () => {
        toast.error(
          "Fehler beim Entfernen des Administrators. Bitte versuche es erneut."
        );
      },
    },
  });

  const updateStatusMutation = useUpdatePlatformAdminStatus({
    mutation: {
      onSuccess: (response) => {
        if (response.status === 200) {
          const label = response.data.enabled ? "aktiviert" : "deaktiviert";
          toast.success(`Administrator wurde ${label}.`);
          queryClient.invalidateQueries({
            queryKey: getListPlatformAdminsQueryKey(),
          });
        }
      },
      onError: () => {
        toast.error(
          "Fehler beim Aktualisieren des Status. Bitte versuche es erneut."
        );
      },
    },
  });

  const resetPasswordMutation = useResetPlatformAdminPassword({
    mutation: {
      onSuccess: () => {
        toast.success(
          "Passwort-Reset wurde ausgeloest. Der Administrator erhaelt eine E-Mail."
        );
      },
      onError: () => {
        toast.error(
          "Fehler beim Passwort-Reset. Bitte versuche es erneut."
        );
      },
    },
  });

  function deleteAdmin(adminId: string, onSuccess?: () => void) {
    deleteMutation.mutate(
      { adminId },
      {
        onSuccess: () => {
          onSuccess?.();
        },
      }
    );
  }

  function activateAdmin(adminId: string) {
    updateStatusMutation.mutate({ adminId, data: { enabled: true } });
  }

  function deactivateAdmin(adminId: string) {
    updateStatusMutation.mutate({ adminId, data: { enabled: false } });
  }

  function resetPassword(adminId: string) {
    resetPasswordMutation.mutate({ adminId });
  }

  return {
    deleteAdmin,
    activateAdmin,
    deactivateAdmin,
    resetPassword,
    isDeletingAdmin: deleteMutation.isPending,
    isUpdatingStatus: updateStatusMutation.isPending,
    isResettingPassword: resetPasswordMutation.isPending,
  };
}
