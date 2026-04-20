"use client";

import { Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { AppHeader } from "@/components/layout/app-header";
import { TenantTable } from "@/features/tenants/components/tenant-table";
import { CreateTenantDialog } from "@/features/tenants/components/create-tenant-dialog";
import { useListTenants } from "@/generated/tenant";

export default function TenantsPage() {
  const { data, isLoading, isRefetching } = useListTenants();
  const tenants = data?.data?.items ?? [];

  return (
    <>
      <AppHeader breadcrumbs={[{ label: "Tenants" }]} />
      <div className="flex flex-1 flex-col gap-4 p-4 md:p-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">Tenants</h1>
            <p className="text-sm text-muted-foreground">
              Manage all tenants on the platform.
            </p>
          </div>
          <CreateTenantDialog>
            <Button>
              <Plus className="mr-2 size-4" />
              Create Tenant
            </Button>
          </CreateTenantDialog>
        </div>
        <TenantTable
          tenants={tenants}
          isLoading={isLoading}
          isRefetching={isRefetching}
        />
      </div>
    </>
  );
}
