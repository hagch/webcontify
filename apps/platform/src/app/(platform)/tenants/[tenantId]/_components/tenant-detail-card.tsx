"use client";

import { useTranslations } from "next-intl";
import { Button, Card, CardContent } from "@webcontify/ui";
import type { TenantResponse, TenantStatus } from "@/generated/tenant";
import { TenantStatus as TenantStatusEnum } from "@/generated/tenant";

interface TenantDetailCardProps {
  tenant: TenantResponse;
  onStatusChange: (status: TenantStatus) => Promise<unknown>;
  isUpdatingStatus: boolean;
}

export function TenantDetailCard({
  tenant,
  onStatusChange,
  isUpdatingStatus,
}: TenantDetailCardProps) {
  const t = useTranslations("tenants");
  const td = useTranslations("tenants.detail");

  return (
    <Card>
      <CardContent className="pt-6">
        <dl className="space-y-4">
          <div>
            <dt className="text-sm font-medium text-muted-foreground">
              {t("name")}
            </dt>
            <dd className="mt-1 text-foreground">{tenant.name}</dd>
          </div>
          <div>
            <dt className="text-sm font-medium text-muted-foreground">
              {t("slug")}
            </dt>
            <dd className="mt-1 font-mono text-sm text-foreground">
              {tenant.slug}
            </dd>
          </div>
          <div>
            <dt className="text-sm font-medium text-muted-foreground">
              {t("createdAt")}
            </dt>
            <dd className="mt-1 text-foreground">
              {new Date(tenant.createdAt).toLocaleString()}
            </dd>
          </div>
        </dl>

        <div className="mt-6 border-t pt-4">
          <h3 className="text-sm font-medium text-muted-foreground">
            {td("statusLabel")}
          </h3>
          <div className="mt-2 flex gap-2">
            {tenant.status !== TenantStatusEnum.ACTIVE && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => onStatusChange(TenantStatusEnum.ACTIVE)}
                disabled={isUpdatingStatus}
              >
                {td("activate")}
              </Button>
            )}
            {tenant.status !== TenantStatusEnum.SUSPENDED && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => onStatusChange(TenantStatusEnum.SUSPENDED)}
                disabled={isUpdatingStatus}
              >
                {td("suspend")}
              </Button>
            )}
            {tenant.status !== TenantStatusEnum.INACTIVE && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => onStatusChange(TenantStatusEnum.INACTIVE)}
                disabled={isUpdatingStatus}
              >
                {td("deactivate")}
              </Button>
            )}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
