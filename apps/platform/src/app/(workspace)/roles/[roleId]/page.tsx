"use client";

import { use } from "react";
import Link from "next/link";
import { useTranslations } from "next-intl";
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
import { usePermissions } from "@/hooks/use-permissions";
import { EditRoleForm } from "@/features/roles/components/edit-role-form";
import { RoleAssigneesTable } from "@/features/roles/components/role-assignees-table";
import { AddAssigneesDialog } from "@/features/roles/components/add-assignees-dialog";
import { RolePermissionsEditor } from "@/features/roles/components/role-permissions-editor";

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
  const t = useTranslations("roles");
  const { can } = usePermissions();
  const canEditRole = can("role_management", "editor");
  const canManageAssignees = can("role_management", "editor");

  const role = data?.status === 200 ? data.data : undefined;

  if (isLoading) {
    return (
      <>
        <AppHeader
          mode="detail"
          breadcrumbs={[
            { label: t("detailPage.breadcrumbList"), href: "/roles" },
            { label: t("detailPage.breadcrumbLoading") },
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
          mode="detail"
          breadcrumbs={[
            { label: t("detailPage.breadcrumbList"), href: "/roles" },
            { label: t("detailPage.breadcrumbNotFound") },
          ]}
        />
        <div className="flex flex-1 flex-col items-center justify-center gap-4 p-4 md:p-6">
          <p className="text-lg font-medium">{t("detailPage.notFoundTitle")}</p>
          <p className="text-sm text-muted-foreground">
            {t("detailPage.notFoundDescription")}
          </p>
          <Link
            href="/roles"
            className={cn(buttonVariants({ variant: "outline" }))}
          >
            <ArrowLeft className="mr-2 size-4" />
            {t("detailPage.backToRoles")}
          </Link>
        </div>
      </>
    );
  }

  const assigneeIds = role.assignees.map((a) => a.userId);

  return (
    <>
      <AppHeader
        mode="detail"
        breadcrumbs={[
          { label: t("detailPage.breadcrumbList"), href: "/roles" },
          {
            label: role.name,
            suffix: role.systemRole ? (
              <Badge
                variant="outline"
                className="bg-indigo-100 text-indigo-800 border-indigo-200 dark:bg-indigo-900/30 dark:text-indigo-400 dark:border-indigo-800"
              >
                <Shield className="mr-1 size-3" />
                {t("typeLabels.system")}
              </Badge>
            ) : undefined,
          },
        ]}
      />
      <div className="flex flex-1 flex-col gap-6 p-4 md:p-6">

        {!role.systemRole && canEditRole && (
          <>
            <div>
              <h2 className="mb-4 text-lg font-medium">{t("detailPage.editSection")}</h2>
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
              {t("detailPage.tabAssignees", { count: role.assignees.length })}
            </TabsTrigger>
            <TabsTrigger value="permissions" data-testid="role-permissions-tab">{t("detailPage.tabPermissions")}</TabsTrigger>
          </TabsList>

          <TabsContent value="assignees" className="mt-4">
            <div className="space-y-4">
              {!role.systemRole && canManageAssignees && (
                <div className="flex justify-end">
                  <AddAssigneesDialog
                    roleId={role.id}
                    currentAssigneeIds={assigneeIds}
                  >
                    <Button data-testid="add-assignee-button">
                      <UserPlus className="mr-2 size-4" />
                      {t("detailPage.addUsers")}
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
            <RolePermissionsEditor
              roleId={role.id}
              permissions={role.permissions}
              isSystemRole={role.systemRole}
            />
          </TabsContent>
        </Tabs>
      </div>
    </>
  );
}
