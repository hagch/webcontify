"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import {
  Badge,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@webcontify/ui";
import type { TenantResponse, PageInfo } from "@/generated/tenant";

interface TenantTableProps {
  tenants: TenantResponse[];
  isLoading: boolean;
  pageInfo?: PageInfo;
}

const statusVariantMap: Record<string, "success" | "secondary" | "destructive"> = {
  ACTIVE: "success",
  INACTIVE: "secondary",
  SUSPENDED: "destructive",
};

export function TenantTable({ tenants, isLoading, pageInfo }: TenantTableProps) {
  const t = useTranslations("tenants");
  const ts = useTranslations("tenants.statusValues");

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-12 text-muted-foreground">
        Loading...
      </div>
    );
  }

  if (tenants.length === 0) {
    return (
      <div className="flex items-center justify-center py-12 text-muted-foreground">
        {t("noTenants")}
      </div>
    );
  }

  return (
    <div className="rounded-lg border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t("name")}</TableHead>
            <TableHead>{t("slug")}</TableHead>
            <TableHead>{t("status")}</TableHead>
            <TableHead>{t("createdAt")}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {tenants.map((tenant) => (
            <TableRow key={tenant.id}>
              <TableCell>
                <Link
                  href={`/tenants/${tenant.id}`}
                  className="font-medium text-primary hover:underline"
                >
                  {tenant.name}
                </Link>
              </TableCell>
              <TableCell className="text-muted-foreground">
                {tenant.slug}
              </TableCell>
              <TableCell>
                <Badge variant={statusVariantMap[tenant.status] ?? "secondary"}>
                  {ts(tenant.status)}
                </Badge>
              </TableCell>
              <TableCell className="text-muted-foreground">
                {new Date(tenant.createdAt).toLocaleDateString()}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
      {pageInfo && pageInfo.totalPages > 1 && (
        <div className="flex items-center justify-between border-t px-4 py-3 text-sm text-muted-foreground">
          <span>{pageInfo.totalElements} total</span>
          <span>
            Page {pageInfo.page + 1} of {pageInfo.totalPages}
          </span>
        </div>
      )}
    </div>
  );
}
