"use client";

import { use } from "react";
import Link from "next/link";
import { useTranslations } from "next-intl";
import { Button, Tabs, TabsList, TabsTrigger, TabsContent } from "@webcontify/ui";
import { useTenantConfigFacade } from "@/facades/use-tenant-config-facade";
import { BrandingConfigForm } from "./_components/branding-config-form";
import { EmailConfigForm } from "./_components/email-config-form";
import { DomainConfigForm } from "./_components/domain-config-form";

export default function TenantConfigPage({
  params,
}: {
  params: Promise<{ tenantId: string }>;
}) {
  const { tenantId } = use(params);
  const t = useTranslations("config");
  const tDetail = useTranslations("tenants.detail");
  const facade = useTenantConfigFacade(tenantId);

  if (facade.isLoading && !facade.error) {
    return (
      <div className="flex items-center justify-center py-12 text-muted-foreground">
        Loading...
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-foreground">{t("title")}</h1>
        <Button variant="outline" size="sm" asChild>
          <Link href={`/tenants/${tenantId}`}>{tDetail("title")}</Link>
        </Button>
      </div>
      <Tabs defaultValue="branding">
        <TabsList>
          <TabsTrigger value="branding">{t("branding.title")}</TabsTrigger>
          <TabsTrigger value="email">{t("email.title")}</TabsTrigger>
          <TabsTrigger value="domain">{t("domain.title")}</TabsTrigger>
        </TabsList>
        <TabsContent value="branding">
          <BrandingConfigForm
            config={facade.config?.branding}
            onSubmit={facade.updateBranding}
            isSubmitting={facade.isUpdatingBranding}
          />
        </TabsContent>
        <TabsContent value="email">
          <EmailConfigForm
            config={facade.config?.email}
            onSubmit={facade.updateEmail}
            isSubmitting={facade.isUpdatingEmail}
            onTestEmail={facade.testEmail}
            isTestingEmail={facade.isTestingEmail}
          />
        </TabsContent>
        <TabsContent value="domain">
          <DomainConfigForm
            config={facade.config?.domain}
            onSubmit={facade.updateDomain}
            isSubmitting={facade.isUpdatingDomain}
          />
        </TabsContent>
      </Tabs>
    </div>
  );
}
