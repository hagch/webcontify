"use client";

import { use } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { toast } from "sonner";
import { Loader2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { AppHeader } from "@/components/layout/app-header";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  useGetAdminTenantConfig,
  useUpdateAdminBrandingConfig,
} from "@/generated/tenant";
import {
  updateBrandingSchema,
  type UpdateBrandingFormData,
} from "@/lib/schemas/tenant-config";

export default function TenantConfigPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  const t = useTranslations("tenants");
  const tConfig = useTranslations("config");

  const { data: configData } = useGetAdminTenantConfig(id);
  const branding =
    configData?.status === 200 ? configData.data.branding : null;

  const updateBranding = useUpdateAdminBrandingConfig();

  const brandingForm = useForm<UpdateBrandingFormData>({
    resolver: zodResolver(updateBrandingSchema),
    defaultValues: {
      primaryColor: branding?.primaryColor ?? "",
      secondaryColor: branding?.secondaryColor ?? "",
      logoStorageKey: branding?.logoStorageKey ?? "",
      faviconStorageKey: branding?.faviconStorageKey ?? "",
    },
  });

  async function onSaveBranding(values: UpdateBrandingFormData) {
    try {
      await updateBranding.mutateAsync({
        tenantId: id,
        data: {
          primaryColor: values.primaryColor || undefined,
          secondaryColor: values.secondaryColor || undefined,
          logoStorageKey: values.logoStorageKey || undefined,
          faviconStorageKey: values.faviconStorageKey || undefined,
        },
      });
      toast.success(tConfig("branding.saved"));
    } catch {
      toast.error(tConfig("branding.error"));
    }
  }

  return (
    <>
      <AppHeader
        mode="detail"
        breadcrumbs={[
          { label: t("title"), href: "/tenants" },
          { label: id, href: `/tenants/${id}` },
          { label: t("detail.config") },
        ]}
      />
      <div className="flex flex-1 flex-col gap-4 p-4 md:p-6">

        <Tabs defaultValue="branding">
          <TabsList>
            <TabsTrigger value="branding">{tConfig("branding.title")}</TabsTrigger>
            <TabsTrigger value="email">{tConfig("email.title")}</TabsTrigger>
            <TabsTrigger value="domain">{tConfig("domain.title")}</TabsTrigger>
          </TabsList>

          <TabsContent value="branding">
            <Card>
              <CardHeader>
                <CardTitle>{tConfig("branding.title")}</CardTitle>
              </CardHeader>
              <CardContent>
                <Form {...brandingForm}>
                  <form
                    onSubmit={brandingForm.handleSubmit(onSaveBranding)}
                    className="space-y-4 max-w-md"
                  >
                    <FormField
                      control={brandingForm.control}
                      name="primaryColor"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel htmlFor="primary-color-input">{tConfig("branding.primaryColor")}</FormLabel>
                          <FormControl>
                            <Input
                              id="primary-color-input"
                              aria-label={tConfig("branding.primaryColor")}
                              placeholder="#6366F1"
                              {...field}
                            />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={brandingForm.control}
                      name="secondaryColor"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel htmlFor="secondary-color-input">{tConfig("branding.secondaryColor")}</FormLabel>
                          <FormControl>
                            <Input
                              id="secondary-color-input"
                              aria-label={tConfig("branding.secondaryColor")}
                              placeholder="#10B981"
                              {...field}
                            />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <Button
                      type="submit"
                      aria-busy={updateBranding.isPending}
                    >
                      {updateBranding.isPending && (
                        <Loader2 className="mr-2 size-4 animate-spin" />
                      )}
                      {tConfig("branding.save")}
                    </Button>
                  </form>
                </Form>
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="email">
            <Card>
              <CardHeader>
                <CardTitle>{tConfig("email.title")}</CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-sm text-muted-foreground">
                  {tConfig("email.comingSoon")}
                </p>
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="domain">
            <Card>
              <CardHeader>
                <CardTitle>{tConfig("domain.title")}</CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-sm text-muted-foreground">
                  {tConfig("domain.comingSoon")}
                </p>
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>
      </div>
    </>
  );
}
