"use client";

import { useEffect } from "react";
import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { UserPlus } from "lucide-react";
import { toast } from "sonner";
import { AppHeader } from "@/components/layout/app-header";
import { Button } from "@/components/ui/button";
import { useListUsers } from "@/generated/identity";
import { InviteUserDialog } from "@/features/users/components/invite-user-dialog";
import { UserTable } from "@/features/users/components/user-table";
import { usePermissions } from "@/hooks/use-permissions";

export default function UsersPage() {
  const { data, isLoading: isDataLoading, isRefetching } = useListUsers();
  const users = (data?.status === 200 ? data.data.items : undefined) ?? [];
  const t = useTranslations("users");
  const tErrors = useTranslations("errors");
  const { canAccess, can, isLoading: isPermissionsLoading } = usePermissions();
  const router = useRouter();

  useEffect(() => {
    if (!isPermissionsLoading && !canAccess("user_management")) {
      toast.error(tErrors("noAccess"));
      router.replace("/dashboard");
    }
  }, [isPermissionsLoading, canAccess, router, tErrors]);

  if (isPermissionsLoading || !canAccess("user_management")) {
    return null;
  }

  const isLoading = isDataLoading;

  return (
    <>
      <AppHeader mode="list" title={t("title")} actions={
        can("user_management", "inviter") ? (
          <InviteUserDialog>
            <Button>
              <UserPlus className="mr-2 size-4" />
              {t("invite")}
            </Button>
          </InviteUserDialog>
        ) : undefined
      } />
      <div className="flex flex-1 flex-col gap-4 p-4 md:p-6">
        <UserTable
          users={users}
          isLoading={isLoading}
          isRefetching={isRefetching}
        />
      </div>
    </>
  );
}
