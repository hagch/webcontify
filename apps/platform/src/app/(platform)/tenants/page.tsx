"use client";

import { Plus } from "lucide-react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { AppHeader } from "@/components/layout/app-header";
import { TenantTable } from "@/features/tenants/components/tenant-table";
import { CreateTenantDialog } from "@/features/tenants/components/create-tenant-dialog";
import { useListTenants } from "@/generated/tenant";

export default function TenantsPage() {
  const t = useTranslations("tenants");
  const { data, isLoading, isRefetching } = useListTenants();
  const tenants = data?.data?.items ?? [];

  return (
    <>
      <AppHeader mode="list" title={t("title")} description={t("description")} actions={
        <CreateTenantDialog>
          <Button>
            <Plus className="mr-2 size-4" />
            {t("create")}
          </Button>
        </CreateTenantDialog>
      } />
      <div data-testid="tenants-page" className="flex flex-1 flex-col gap-4 p-4 md:p-6">
        <TenantTable
          tenants={tenants}
          isLoading={isLoading}
          isRefetching={isRefetching}
        />
      </div>
    </>
  );
}
