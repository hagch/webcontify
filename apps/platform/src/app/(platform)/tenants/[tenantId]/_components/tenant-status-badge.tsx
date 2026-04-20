"use client";

import { useTranslations } from "next-intl";
import { Badge } from "@webcontify/ui";
import type { TenantStatus } from "@/generated/tenant";

interface TenantStatusBadgeProps {
  status: TenantStatus;
}

const statusVariantMap: Record<string, "success" | "secondary" | "destructive"> = {
  ACTIVE: "success",
  INACTIVE: "secondary",
  SUSPENDED: "destructive",
};

export function TenantStatusBadge({ status }: TenantStatusBadgeProps) {
  const t = useTranslations("tenants.statusValues");

  return (
    <Badge variant={statusVariantMap[status] ?? "secondary"}>
      {t(status)}
    </Badge>
  );
}
