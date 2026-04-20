"use client";

import Link from "next/link";
import { MoreHorizontal, ExternalLink } from "lucide-react";
import { useTranslations } from "next-intl";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import type { TenantResponse } from "@/generated/tenant";
import { TenantTableSkeleton } from "./tenant-table-skeleton";

interface TenantTableProps {
  tenants: TenantResponse[];
  isLoading: boolean;
  isRefetching: boolean;
}

export function TenantTable({
  tenants,
  isLoading,
  isRefetching,
}: TenantTableProps) {
  const t = useTranslations("tenants");

  if (isLoading) {
    return <TenantTableSkeleton />;
  }

  if (tenants.length === 0) {
    return (
      <div data-testid="empty-state" className="flex flex-col items-center justify-center rounded-lg border border-dashed p-12 text-center">
        <p className="text-lg font-medium">{t("emptyState.title")}</p>
        <p className="mt-1 text-sm text-muted-foreground">
          {t("emptyState.description")}
        </p>
      </div>
    );
  }

  return (
    <div className="relative">
      {isRefetching && (
        <div className="absolute inset-0 z-10 flex items-center justify-center rounded-lg bg-background/60">
          <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
        </div>
      )}
      <Table data-testid="tenant-table">
        <TableHeader>
          <TableRow>
            <TableHead>{t("name")}</TableHead>
            <TableHead>{t("slug")}</TableHead>
            <TableHead>{t("status")}</TableHead>
            <TableHead className="text-right">{t("actions")}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {tenants.map((tenant) => (
            <TableRow key={tenant.id} data-testid="tenant-table-row">
              <TableCell className="font-medium">
                <Link
                  href={`/tenants/${tenant.id}`}
                  className="hover:underline"
                >
                  {tenant.name}
                </Link>
              </TableCell>
              <TableCell className="text-muted-foreground">
                {tenant.slug}
              </TableCell>
              <TableCell>
                <Badge
                  variant={
                    tenant.status === "ACTIVE" ? "default" : "secondary"
                  }
                >
                  {t(`statusValues.${tenant.status}`)}
                </Badge>
              </TableCell>
              <TableCell className="text-right">
                <DropdownMenu>
                  <DropdownMenuTrigger
                    render={
                      <Button
                        variant="ghost"
                        size="icon"
                        aria-label={t("actionsMenu.label", { name: tenant.name })}
                      />
                    }
                  >
                    <MoreHorizontal className="size-4" />
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem
                      render={<Link href={`/tenants/${tenant.id}`} />}
                    >
                      <ExternalLink className="mr-2 size-4" />
                      {t("actionsMenu.viewDetails")}
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}
