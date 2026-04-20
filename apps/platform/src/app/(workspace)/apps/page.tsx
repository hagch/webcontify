"use client";

import { Plus, Upload } from "lucide-react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { AppHeader } from "@/components/layout/app-header";
import { AppTable } from "@/features/apps/components/app-table";
import { CreateAppDialog } from "@/features/apps/components/create-app-dialog";
import { ImportBlueprintDialog } from "@/features/apps/components/import-blueprint-dialog";
import { useListApps } from "@/generated/app";

export default function AppsPage() {
  const t = useTranslations("apps");
  const { data, isLoading, isRefetching } = useListApps();
  const apps = data?.data?.items ?? [];

  return (
    <>
      <AppHeader mode="list" title={t("title")} description={t("description")} actions={
        <div className="flex items-center gap-2">
          <ImportBlueprintDialog>
            <Button variant="outline">
              <Upload className="mr-2 size-4" />
              {t("importExport.import")}
            </Button>
          </ImportBlueprintDialog>
          <CreateAppDialog>
            <Button>
              <Plus className="mr-2 size-4" />
              {t("create")}
            </Button>
          </CreateAppDialog>
        </div>
      } />
      <div data-testid="apps-page" className="flex flex-1 flex-col gap-4 p-4 md:p-6">
        <AppTable
          apps={apps}
          isLoading={isLoading}
          isRefetching={isRefetching}
        />
      </div>
    </>
  );
}
