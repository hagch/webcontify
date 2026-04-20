"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Button } from "@webcontify/ui";
import { useTenantListFacade } from "@/facades/use-tenants-facade";
import { TenantTable } from "./_components/tenant-table";
import { CreateTenantDialog } from "./_components/create-tenant-dialog";

export default function TenantsPage() {
  const t = useTranslations("tenants");
  const [showCreate, setShowCreate] = useState(false);
  const { tenants, pageInfo, isLoading, createTenant, isCreating } =
    useTenantListFacade();

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-foreground">
          {t("title")}
        </h1>
        <Button onClick={() => setShowCreate(true)}>
          {t("create")}
        </Button>
      </div>

      <TenantTable tenants={tenants} isLoading={isLoading} pageInfo={pageInfo} />

      <CreateTenantDialog
        open={showCreate}
        onOpenChange={setShowCreate}
        onSubmit={async (data) => {
          await createTenant(data);
          setShowCreate(false);
        }}
        isSubmitting={isCreating}
      />
    </div>
  );
}
