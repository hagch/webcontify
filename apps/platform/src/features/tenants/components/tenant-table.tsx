"use client";

import Link from "next/link";
import { MoreHorizontal, ExternalLink } from "lucide-react";
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
  if (isLoading) {
    return <TenantTableSkeleton />;
  }

  if (tenants.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center rounded-lg border border-dashed p-12 text-center">
        <p className="text-lg font-medium">No tenants yet</p>
        <p className="mt-1 text-sm text-muted-foreground">
          Create your first tenant to get started.
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
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Name</TableHead>
            <TableHead>Slug</TableHead>
            <TableHead>Status</TableHead>
            <TableHead className="text-right">Actions</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {tenants.map((tenant) => (
            <TableRow key={tenant.id}>
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
                  {tenant.status}
                </Badge>
              </TableCell>
              <TableCell className="text-right">
                <DropdownMenu>
                  <DropdownMenuTrigger
                    render={
                      <Button
                        variant="ghost"
                        size="icon"
                        aria-label={`Actions for ${tenant.name}`}
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
                      View details
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
