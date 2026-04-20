"use client";

import { useEffect } from "react";
import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { Plus } from "lucide-react";
import { toast } from "sonner";
import { AppHeader } from "@/components/layout/app-header";
import { Button } from "@/components/ui/button";
import { useListRoles } from "@/generated/identity";
import { RoleTable } from "@/features/roles/components/role-table";
import { CreateRoleDialog } from "@/features/roles/components/create-role-dialog";
import { usePermissions } from "@/hooks/use-permissions";

export default function RolesPage() {
  const { data, isLoading: isDataLoading, isRefetching } = useListRoles();
  const roles = (data?.status === 200 ? data.data.roles : undefined) ?? [];
  const t = useTranslations("roles");
  const tErrors = useTranslations("errors");
  const { canAccess, can, isLoading: isPermissionsLoading } = usePermissions();
  const router = useRouter();

  useEffect(() => {
    if (!isPermissionsLoading && !canAccess("role_management")) {
      toast.error(tErrors("noAccess"));
      router.replace("/dashboard");
    }
  }, [isPermissionsLoading, canAccess, router, tErrors]);

  if (isPermissionsLoading || !canAccess("role_management")) {
    return null;
  }

  const isLoading = isDataLoading;

  return (
    <>
      <AppHeader mode="list" title={t("title")} actions={
        can("role_management", "creator") ? (
          <CreateRoleDialog>
            <Button>
              <Plus className="mr-2 size-4" />
              {t("create")}
            </Button>
          </CreateRoleDialog>
        ) : undefined
      } />
      <div className="flex flex-1 flex-col gap-4 p-4 md:p-6">
        <RoleTable
          roles={roles}
          isLoading={isLoading}
          isRefetching={isRefetching}
        />
      </div>
    </>
  );
}
