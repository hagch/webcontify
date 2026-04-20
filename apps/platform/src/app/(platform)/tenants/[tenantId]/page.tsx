"use client";

import { use, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { Button } from "@webcontify/ui";
import { useTenantDetailFacade } from "@/facades/use-tenants-facade";
import { TenantDetailCard } from "./_components/tenant-detail-card";
import { EditTenantForm } from "./_components/edit-tenant-form";
import { TenantStatusBadge } from "./_components/tenant-status-badge";

export default function TenantDetailPage({
  params,
}: {
  params: Promise<{ tenantId: string }>;
}) {
  const { tenantId } = use(params);
  const t = useTranslations("tenants.detail");
  const router = useRouter();
  const [isEditing, setIsEditing] = useState(false);
  const {
    tenant,
    isLoading,
    updateTenant,
    isUpdating,
    updateStatus,
    isUpdatingStatus,
    deleteTenant,
    isDeleting,
  } = useTenantDetailFacade(tenantId);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-12 text-muted-foreground">
        Loading...
      </div>
    );
  }

  if (!tenant) {
    return (
      <div className="flex items-center justify-center py-12 text-muted-foreground">
        Tenant not found
      </div>
    );
  }

  const handleDelete = async () => {
    if (window.confirm(t("deleteConfirm"))) {
      await deleteTenant();
      router.push("/tenants");
    }
  };

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-bold text-foreground">
            {tenant.name}
          </h1>
          <TenantStatusBadge status={tenant.status} />
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" asChild>
            <Link href={`/tenants/${tenantId}/config`}>{t("config")}</Link>
          </Button>
          {!isEditing && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => setIsEditing(true)}
            >
              {t("edit")}
            </Button>
          )}
          <Button
            variant="destructive"
            size="sm"
            onClick={handleDelete}
            disabled={isDeleting}
          >
            {t("delete")}
          </Button>
        </div>
      </div>

      {isEditing ? (
        <EditTenantForm
          tenant={tenant}
          onSubmit={async (data) => {
            await updateTenant(data);
            setIsEditing(false);
          }}
          onCancel={() => setIsEditing(false)}
          isSubmitting={isUpdating}
        />
      ) : (
        <TenantDetailCard
          tenant={tenant}
          onStatusChange={updateStatus}
          isUpdatingStatus={isUpdatingStatus}
        />
      )}
    </div>
  );
}
