"use client";

import { use } from "react";
import Link from "next/link";
import { ArrowLeft, Shield, UserPlus } from "lucide-react";
import { AppHeader } from "@/components/layout/app-header";
import { Badge } from "@/components/ui/badge";
import { Button, buttonVariants } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Separator } from "@/components/ui/separator";
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@/components/ui/tabs";
import { cn } from "@/lib/utils";
import { useGetRole } from "@/generated/identity";
import { EditRoleForm } from "@/features/roles/components/edit-role-form";
import { RoleAssigneesTable } from "@/features/roles/components/role-assignees-table";
import { AddAssigneesDialog } from "@/features/roles/components/add-assignees-dialog";
import { RolePermissionsPreview } from "@/features/roles/components/role-permissions-preview";

interface RoleDetailPageProps {
  params: Promise<{ roleId: string }>;
}

function RoleDetailSkeleton() {
  return (
    <div className="flex flex-1 flex-col gap-6 p-4 md:p-6">
      <div className="flex items-start gap-4">
        <div className="space-y-2">
          <Skeleton className="h-8 w-48" />
          <Skeleton className="h-4 w-72" />
        </div>
      </div>
      <Skeleton className="h-px w-full" />
      <Skeleton className="h-8 w-64" />
      <div className="space-y-3">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-12 w-full" />
        ))}
      </div>
    </div>
  );
}

export default function RoleDetailPage({ params }: RoleDetailPageProps) {
  const { roleId } = use(params);
  const { data, isLoading } = useGetRole(roleId);

  const role = data?.status === 200 ? data.data : undefined;

  if (isLoading) {
    return (
      <>
        <AppHeader
          breadcrumbs={[
            { label: "Rollen", href: "/roles" },
            { label: "Laden..." },
          ]}
        />
        <RoleDetailSkeleton />
      </>
    );
  }

  if (!role) {
    return (
      <>
        <AppHeader
          breadcrumbs={[
            { label: "Rollen", href: "/roles" },
            { label: "Nicht gefunden" },
          ]}
        />
        <div className="flex flex-1 flex-col items-center justify-center gap-4 p-4 md:p-6">
          <p className="text-lg font-medium">Rolle nicht gefunden</p>
          <p className="text-sm text-muted-foreground">
            Die gesuchte Rolle existiert nicht oder wurde geloescht.
          </p>
          <Link
            href="/roles"
            className={cn(buttonVariants({ variant: "outline" }))}
          >
            <ArrowLeft className="mr-2 size-4" />
            Zurueck zu Rollen
          </Link>
        </div>
      </>
    );
  }

  const assigneeIds = role.assignees.map((a) => a.userId);

  return (
    <>
      <AppHeader
        breadcrumbs={[
          { label: "Rollen", href: "/roles" },
          { label: role.name },
        ]}
      />
      <div className="flex flex-1 flex-col gap-6 p-4 md:p-6">
        <div className="flex items-start justify-between gap-4">
          <div className="flex items-center gap-3">
            <Link
              href="/roles"
              aria-label="Zurueck zu Rollen"
              className={cn(buttonVariants({ variant: "ghost", size: "icon" }))}
            >
              <ArrowLeft className="size-4" />
            </Link>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-2xl font-semibold tracking-tight">
                  {role.name}
                </h1>
                {role.systemRole && (
                  <Badge
                    variant="outline"
                    className="bg-indigo-100 text-indigo-800 border-indigo-200 dark:bg-indigo-900/30 dark:text-indigo-400 dark:border-indigo-800"
                  >
                    <Shield className="mr-1 size-3" />
                    System
                  </Badge>
                )}
              </div>
              {role.description && (
                <p className="mt-1 text-sm text-muted-foreground">
                  {role.description}
                </p>
              )}
            </div>
          </div>
        </div>

        <Separator />

        {!role.systemRole && (
          <>
            <div>
              <h2 className="mb-4 text-lg font-medium">Rolle bearbeiten</h2>
              <div className="max-w-lg">
                <EditRoleForm
                  roleId={role.id}
                  currentName={role.name}
                  currentDescription={role.description}
                />
              </div>
            </div>
            <Separator />
          </>
        )}

        <Tabs defaultValue="assignees">
          <TabsList>
            <TabsTrigger value="assignees">
              Zugewiesene Benutzer ({role.assignees.length})
            </TabsTrigger>
            <TabsTrigger value="permissions">Berechtigungen</TabsTrigger>
          </TabsList>

          <TabsContent value="assignees" className="mt-4">
            <div className="space-y-4">
              {!role.systemRole && (
                <div className="flex justify-end">
                  <AddAssigneesDialog
                    roleId={role.id}
                    currentAssigneeIds={assigneeIds}
                  >
                    <Button data-testid="add-assignee-button">
                      <UserPlus className="mr-2 size-4" />
                      Benutzer hinzufuegen
                    </Button>
                  </AddAssigneesDialog>
                </div>
              )}
              <RoleAssigneesTable
                assignees={role.assignees}
                roleId={role.id}
                isSystemRole={role.systemRole}
              />
            </div>
          </TabsContent>

          <TabsContent value="permissions" className="mt-4">
            <RolePermissionsPreview permissions={role.permissions} />
          </TabsContent>
        </Tabs>
      </div>
    </>
  );
}
