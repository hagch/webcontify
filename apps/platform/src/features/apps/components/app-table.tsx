"use client";

import Link from "next/link";
import { MoreHorizontal, ExternalLink, Pencil } from "lucide-react";
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
import type { AppResponse } from "@/generated/app";
import { AppTableSkeleton } from "./app-table-skeleton";

interface AppTableProps {
  apps: AppResponse[];
  isLoading: boolean;
  isRefetching: boolean;
}

function statusBadgeVariant(status: string): "default" | "outline" | "secondary" {
  switch (status) {
    case "DRAFT":
      return "outline";
    case "ACTIVE":
      return "default";
    case "INACTIVE":
      return "secondary";
    default:
      return "outline";
  }
}

export function AppTable({
  apps,
  isLoading,
  isRefetching,
}: AppTableProps) {
  const t = useTranslations("apps");

  if (isLoading) {
    return <AppTableSkeleton />;
  }

  if (apps.length === 0) {
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
      <Table data-testid="app-table">
        <TableHeader>
          <TableRow>
            <TableHead>{t("name")}</TableHead>
            <TableHead>{t("status")}</TableHead>
            <TableHead>{t("version")}</TableHead>
            <TableHead>{t("hasDraft")}</TableHead>
            <TableHead>{t("description_label")}</TableHead>
            <TableHead className="text-right">{t("actions")}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {apps.map((app) => (
            <TableRow key={app.id} data-testid="app-table-row">
              <TableCell className="font-medium">
                <Link
                  href={`/apps/${app.id}`}
                  className="hover:underline"
                >
                  {app.name}
                </Link>
              </TableCell>
              <TableCell>
                <Badge variant={statusBadgeVariant(app.status)}>
                  {t(`statusValues.${app.status}`)}
                </Badge>
              </TableCell>
              <TableCell className="font-mono text-sm text-muted-foreground">
                {app.currentVersion ?? "—"}
              </TableCell>
              <TableCell>
                {app.hasDraft && (
                  <Badge variant="outline" className="border-amber-500 text-amber-600">
                    {t("hasDraft")}
                  </Badge>
                )}
              </TableCell>
              <TableCell className="max-w-[200px] truncate text-muted-foreground">
                {app.description || ""}
              </TableCell>
              <TableCell className="text-right">
                <DropdownMenu>
                  <DropdownMenuTrigger
                    render={
                      <Button
                        variant="ghost"
                        size="icon"
                        aria-label={`Actions for ${app.name}`}
                      />
                    }
                  >
                    <MoreHorizontal className="size-4" />
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end">
                    <DropdownMenuItem
                      render={<Link href={`/apps/${app.id}`} />}
                    >
                      <ExternalLink className="mr-2 size-4" />
                      {t("viewDetails")}
                    </DropdownMenuItem>
                    <DropdownMenuItem
                      render={<Link href={`/apps/${app.id}`} />}
                    >
                      <Pencil className="mr-2 size-4" />
                      {t("edit")}
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
