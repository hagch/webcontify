"use client";

import { Plus } from "lucide-react";
import { AppHeader } from "@/components/layout/app-header";
import { Button } from "@/components/ui/button";
import { useListRoles } from "@/generated/identity";
import { RoleTable } from "@/features/roles/components/role-table";
import { CreateRoleDialog } from "@/features/roles/components/create-role-dialog";

export default function RolesPage() {
  const { data, isLoading, isRefetching } = useListRoles();
  const roles = (data?.status === 200 ? data.data.roles : undefined) ?? [];

  return (
    <>
      <AppHeader breadcrumbs={[{ label: "Rollen" }]} />
      <div className="flex flex-1 flex-col gap-4 p-4 md:p-6">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-semibold tracking-tight">Rollen</h1>
          <CreateRoleDialog>
            <Button>
              <Plus className="mr-2 size-4" />
              Rolle erstellen
            </Button>
          </CreateRoleDialog>
        </div>
        <RoleTable
          roles={roles}
          isLoading={isLoading}
          isRefetching={isRefetching}
        />
      </div>
    </>
  );
}
