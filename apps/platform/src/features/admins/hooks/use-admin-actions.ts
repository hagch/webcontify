"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import {
  useDeletePlatformAdmin,
  useUpdatePlatformAdminStatus,
  useResetPlatformAdminPassword,
  getListPlatformAdminsQueryKey,
} from "@/generated/platform-admin";

export function useAdminActions() {
  const queryClient = useQueryClient();
  const t = useTranslations("admins");

  const deleteMutation = useDeletePlatformAdmin({
    mutation: {
      onSuccess: () => {
        toast.success(t("toasts.deleted"));
        queryClient.invalidateQueries({
          queryKey: getListPlatformAdminsQueryKey(),
        });
      },
      onError: () => {
        toast.error(t("toasts.deleteError"));
      },
    },
  });

  const updateStatusMutation = useUpdatePlatformAdminStatus({
    mutation: {
      onSuccess: (response) => {
        if (response.status === 200) {
          toast.success(
            response.data.enabled
              ? t("toasts.activated")
              : t("toasts.deactivated")
          );
          queryClient.invalidateQueries({
            queryKey: getListPlatformAdminsQueryKey(),
          });
        }
      },
      onError: () => {
        toast.error(t("toasts.statusError"));
      },
    },
  });

  const resetPasswordMutation = useResetPlatformAdminPassword({
    mutation: {
      onSuccess: () => {
        toast.success(t("toasts.passwordReset"));
      },
      onError: () => {
        toast.error(t("toasts.passwordResetError"));
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
