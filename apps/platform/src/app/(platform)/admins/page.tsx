"use client";

import { useTranslations } from "next-intl";
import { Plus } from "lucide-react";
import { AppHeader } from "@/components/layout/app-header";
import { Button } from "@/components/ui/button";
import { useListPlatformAdmins } from "@/generated/platform-admin";
import { AdminTable } from "@/features/admins/components/admin-table";
import { InviteAdminDialog } from "@/features/admins/components/invite-admin-dialog";

export default function PlatformAdminsPage() {
  const { data, isLoading, isRefetching } = useListPlatformAdmins();
  const admins = (data?.status === 200 ? data.data.admins : undefined) ?? [];
  const t = useTranslations("admins");

  return (
    <>
      <AppHeader mode="list" title={t("title")} actions={
        <InviteAdminDialog>
          <Button data-testid="invite-admin-button">
            <Plus className="mr-2 size-4" />
            {t("invite")}
          </Button>
        </InviteAdminDialog>
      } />
      <div className="flex flex-1 flex-col gap-4 p-4 md:p-6">
        <AdminTable admins={admins} isLoading={isLoading} isRefetching={isRefetching} />
      </div>
    </>
  );
}
