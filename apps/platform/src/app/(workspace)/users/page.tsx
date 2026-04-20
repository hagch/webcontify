"use client";

import { UserPlus } from "lucide-react";
import { AppHeader } from "@/components/layout/app-header";
import { Button } from "@/components/ui/button";
import { useListUsers } from "@/generated/identity";
import { InviteUserDialog } from "@/features/users/components/invite-user-dialog";
import { UserTable } from "@/features/users/components/user-table";

export default function UsersPage() {
  const { data, isLoading, isRefetching } = useListUsers();
  const users = (data?.status === 200 ? data.data.items : undefined) ?? [];

  return (
    <>
      <AppHeader breadcrumbs={[{ label: "Benutzer" }]} />
      <div className="flex flex-1 flex-col gap-4 p-4 md:p-6">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-semibold tracking-tight">Benutzer</h1>
          <InviteUserDialog>
            <Button>
              <UserPlus className="mr-2 size-4" />
              Einladen
            </Button>
          </InviteUserDialog>
        </div>
        <UserTable
          users={users}
          isLoading={isLoading}
          isRefetching={isRefetching}
        />
      </div>
    </>
  );
}
