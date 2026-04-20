"use client";

import { use } from "react";
import { notFound } from "next/navigation";
import { useTranslations } from "next-intl";
import { AppHeader } from "@/components/layout/app-header";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  useGetTenant,
  useRetryTenantProvisioning,
  type TenantResponse,
} from "@/generated/tenant";
import { ProvisioningStatus } from "@/features/tenants/components/provisioning-status";

function isTenantResponse(data: unknown): data is TenantResponse {
  return (
    typeof data === "object" && data !== null && "name" in data && "slug" in data
  );
}

export default function TenantDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  const t = useTranslations("tenants");

  const isProvisioning = (tenant: TenantResponse) =>
    tenant.status === "PROVISIONING";

  const { data, isLoading, error } = useGetTenant(id, {
    query: {
      refetchInterval: (query) => {
        const rawData = query.state.data?.data;
        const tenant = isTenantResponse(rawData) ? rawData : undefined;
        return tenant && isProvisioning(tenant) ? 3000 : false;
      },
    },
  });

  const retryProvisioning = useRetryTenantProvisioning();

  const rawData = data?.data;
  const tenant = isTenantResponse(rawData) ? rawData : undefined;

  if (error && !isLoading) {
    notFound();
  }

  return (
    <>
      <AppHeader
        mode="detail"
        breadcrumbs={[
          { label: t("title"), href: "/tenants" },
          {
            label: isLoading ? "..." : (tenant?.name ?? "Tenant"),
            suffix: tenant && !isLoading ? (
              <Badge
                variant={tenant.status === "ACTIVE" ? "default" : "secondary"}
              >
                {t(`statusValues.${tenant.status}`)}
              </Badge>
            ) : undefined,
          },
        ]}
      />
      <div className="flex flex-1 flex-col gap-4 p-4 md:p-6">
        {isLoading ? (
          <Card>
            <CardHeader>
              <Skeleton className="h-6 w-48" />
            </CardHeader>
            <CardContent className="space-y-4">
              <Skeleton className="h-4 w-32" />
              <Skeleton className="h-4 w-64" />
            </CardContent>
          </Card>
        ) : tenant ? (
          <div data-testid="tenant-detail" className="min-h-[300px]">
            {isProvisioning(tenant) ? (
              <ProvisioningStatus
                steps={tenant.provisioningStatus?.steps ?? null}
                onRetry={() => retryProvisioning.mutate({ tenantId: id })}
                isRetrying={retryProvisioning.isPending}
              />
            ) : (
              <Card>
                <CardContent className="pt-6">
                  <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                    <div>
                      <dt className="text-sm font-medium text-muted-foreground">
                        {t("detail.slugLabel")}
                      </dt>
                      <dd className="mt-1 text-sm">{tenant.slug}</dd>
                    </div>
                    <div>
                      <dt className="text-sm font-medium text-muted-foreground">
                        {t("detail.idLabel")}
                      </dt>
                      <dd className="mt-1 font-mono text-sm">{tenant.id}</dd>
                    </div>
                  </dl>
                </CardContent>
              </Card>
            )}
          </div>
        ) : null}
      </div>
    </>
  );
}
