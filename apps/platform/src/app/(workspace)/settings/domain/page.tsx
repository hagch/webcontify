"use client";

import { useEffect } from "react";
import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { AppHeader } from "@/components/layout/app-header";
import { usePermissions } from "@/hooks/use-permissions";

export default function DomainSettingsPage() {
  const tErrors = useTranslations("errors");
  const tSettings = useTranslations("settings");
  const tNav = useTranslations("nav.sidebar");
  const tConfig = useTranslations("config.domain");
  const { canAccess, isLoading } = usePermissions();
  const router = useRouter();

  useEffect(() => {
    if (!isLoading && !canAccess("tenant_config")) {
      toast.error(tErrors("noAccess"));
      router.replace("/dashboard");
    }
  }, [isLoading, canAccess, router, tErrors]);

  if (isLoading || !canAccess("tenant_config")) {
    return null;
  }

  return (
    <>
      <AppHeader
        mode="detail"
        breadcrumbs={[
          { label: tSettings("title") },
          { label: tNav("domain") },
        ]}
      />
      <div className="flex flex-1 flex-col gap-4 p-4 md:p-6">
        <p className="text-sm text-muted-foreground">
          Configure a custom domain for your workspace.
        </p>
      </div>
    </>
  );
}
