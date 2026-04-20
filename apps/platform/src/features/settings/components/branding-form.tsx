"use client";

import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import {
  useGetTenantSelfConfig,
  useUpdateTenantSelfBranding,
  useUploadFile,
  useGetFile,
  getGetTenantSelfConfigQueryKey,
} from "@/generated/tenant";
import {
  updateBrandingSchema,
  UpdateBrandingFormData,
} from "@/lib/schemas/tenant-config";
import { BRANDING_DEFAULTS } from "@/providers/branding-provider";
import { Form, FormControl, FormField, FormItem, FormLabel } from "@/components/ui/form";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ColorPickerField } from "./color-picker-field";
import { LogoUpload } from "./logo-upload";
import { BrandingPreview } from "./branding-preview";
import { FontSelect } from "./font-select";

const RADIUS_OPTIONS = ["0", "0.25rem", "0.5rem", "0.75rem", "1rem"] as const;

export function BrandingForm() {
  const t = useTranslations("config.branding");
  const queryClient = useQueryClient();
  const { data, isLoading } = useGetTenantSelfConfig();
  const updateBranding = useUpdateTenantSelfBranding();
  const uploadFile = useUploadFile();

  const [logoFile, setLogoFile] = useState<File | null>(null);
  const [faviconFile, setFaviconFile] = useState<File | null>(null);
  const [logoDeleted, setLogoDeleted] = useState(false);
  const [faviconDeleted, setFaviconDeleted] = useState(false);

  const logoStorageKey = data?.data?.branding?.logoStorageKey;
  const faviconStorageKey = data?.data?.branding?.faviconStorageKey;
  const { data: logoFileData } = useGetFile({ storageKey: logoStorageKey ?? "" }, { query: { enabled: !!logoStorageKey && !logoDeleted } });
  const { data: faviconFileData } = useGetFile({ storageKey: faviconStorageKey ?? "" }, { query: { enabled: !!faviconStorageKey && !faviconDeleted } });
  const logoUrl = !logoDeleted && logoFileData?.status === 200 ? logoFileData.data.url : undefined;
  const faviconUrl = !faviconDeleted && faviconFileData?.status === 200 ? faviconFileData.data.url : undefined;

  const form = useForm<UpdateBrandingFormData>({
    resolver: zodResolver(updateBrandingSchema),
    defaultValues: {
      logoStorageKey: "",
      faviconStorageKey: "",
      primaryColor: "",
      secondaryColor: "",
      accentColor: "",
      dangerColor: "",
      successColor: "",
      warningColor: "",
      fontFamily: "",
      borderRadius: "",
    },
    values: {
      logoStorageKey: data?.data?.branding?.logoStorageKey ?? "",
      faviconStorageKey: data?.data?.branding?.faviconStorageKey ?? "",
      primaryColor: data?.data?.branding?.primaryColor ?? "",
      secondaryColor: data?.data?.branding?.secondaryColor ?? "",
      accentColor: data?.data?.branding?.accentColor ?? "",
      dangerColor: data?.data?.branding?.dangerColor ?? "",
      successColor: data?.data?.branding?.successColor ?? "",
      warningColor: data?.data?.branding?.warningColor ?? "",
      fontFamily: (data?.data?.branding?.fontFamily ?? "") as "" | "system" | "inter" | "geist" | "plus-jakarta-sans" | "dm-sans" | "roboto" | "source-sans-3" | "ibm-plex-sans" | "open-sans" | "lato" | "nunito-sans" | "poppins" | "merriweather",
      borderRadius: (data?.data?.branding?.borderRadius ?? "") as "" | "0" | "0.25rem" | "0.5rem" | "0.75rem" | "1rem",
    },
  });

  const watchedValues = useWatch({ control: form.control });

  const previewValues = {
    primaryColor: watchedValues.primaryColor || BRANDING_DEFAULTS.primaryColor,
    secondaryColor: watchedValues.secondaryColor || BRANDING_DEFAULTS.secondaryColor,
    accentColor: watchedValues.accentColor || BRANDING_DEFAULTS.accentColor,
    dangerColor: watchedValues.dangerColor || BRANDING_DEFAULTS.dangerColor,
    successColor: watchedValues.successColor || BRANDING_DEFAULTS.successColor,
    warningColor: watchedValues.warningColor || BRANDING_DEFAULTS.warningColor,
    fontFamily: watchedValues.fontFamily || BRANDING_DEFAULTS.fontFamily,
    borderRadius: watchedValues.borderRadius || BRANDING_DEFAULTS.borderRadius,
    logoStorageKey: watchedValues.logoStorageKey || null,
    faviconStorageKey: watchedValues.faviconStorageKey || null,
    tenantName: null,
    tenantSlug: null,
    resolvedLogoUrl: null,
    resolvedFaviconUrl: null,
  };

  async function onSubmit(values: UpdateBrandingFormData) {
    try {
      let logoStorageKey = logoDeleted ? "" : (values.logoStorageKey ?? "");
      let faviconStorageKey = faviconDeleted ? "" : (values.faviconStorageKey ?? "");

      if (logoFile) {
        const result = await uploadFile.mutateAsync({ data: { file: logoFile, public: true } });
        if (result.status === 201) {
          logoStorageKey = result.data.storageKey;
        }
      }

      if (faviconFile) {
        const result = await uploadFile.mutateAsync({ data: { file: faviconFile, public: true } });
        if (result.status === 201) {
          faviconStorageKey = result.data.storageKey;
        }
      }

      await updateBranding.mutateAsync({
        data: {
          ...values,
          logoStorageKey: logoStorageKey || undefined,
          faviconStorageKey: faviconStorageKey || undefined,
          primaryColor: values.primaryColor || undefined,
          secondaryColor: values.secondaryColor || undefined,
          accentColor: values.accentColor || undefined,
          dangerColor: values.dangerColor || undefined,
          successColor: values.successColor || undefined,
          warningColor: values.warningColor || undefined,
          fontFamily: (values.fontFamily || undefined) as "system" | "inter" | "geist" | "plus-jakarta-sans" | "dm-sans" | "roboto" | "source-sans-3" | "ibm-plex-sans" | "open-sans" | "lato" | "nunito-sans" | "poppins" | "merriweather" | undefined,
          borderRadius: (values.borderRadius || undefined) as "0" | "0.25rem" | "0.5rem" | "0.75rem" | "1rem" | undefined,
        },
      });

      await queryClient.invalidateQueries({
        queryKey: getGetTenantSelfConfigQueryKey(),
      });

      setLogoFile(null);
      setFaviconFile(null);
      setLogoDeleted(false);
      setFaviconDeleted(false);

      toast.success(t("saved"));
    } catch {
      toast.error(t("error"));
    }
  }

  function handleReset() {
    form.reset({
      logoStorageKey: "",
      faviconStorageKey: "",
      primaryColor: "",
      secondaryColor: "",
      accentColor: "",
      dangerColor: "",
      successColor: "",
      warningColor: "",
      fontFamily: "",
      borderRadius: "",
    });
    setLogoFile(null);
    setFaviconFile(null);
    setLogoDeleted(true);
    setFaviconDeleted(true);
    toast.success(t("resetConfirm"));
  }

  if (isLoading) {
    return (
      <div className="flex flex-col gap-6">
        <Skeleton className="h-48 w-full" />
        <Skeleton className="h-48 w-full" />
        <Skeleton className="h-36 w-full" />
      </div>
    );
  }

  const isPending = updateBranding.isPending || uploadFile.isPending;

  return (
    <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
      {/* Form Column */}
      <div className="lg:col-span-3">
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-6">
            <Tabs defaultValue="logo">
              <TabsList>
                <TabsTrigger value="logo">{t("tabLogoIdentity")}</TabsTrigger>
                <TabsTrigger value="colors">{t("tabColors")}</TabsTrigger>
                <TabsTrigger value="typography">{t("tabTypography")}</TabsTrigger>
              </TabsList>

              {/* Tab 1: Logo & Identity */}
              <TabsContent value="logo" className="flex flex-col gap-6 mt-4">
                <LogoUpload
                  label={t("logo")}
                  currentLogoUrl={logoUrl}
                  onFileSelect={setLogoFile}
                  onDelete={() => {
                    setLogoDeleted(true);
                    form.setValue("logoStorageKey", "");
                  }}
                  accept="image/svg+xml, image/png, image/jpeg"
                  maxSizeMb={2}
                  hint={t("logoHint")}
                />
                <LogoUpload
                  label={t("favicon")}
                  currentLogoUrl={faviconUrl}
                  onFileSelect={setFaviconFile}
                  onDelete={() => {
                    setFaviconDeleted(true);
                    form.setValue("faviconStorageKey", "");
                  }}
                  accept="image/svg+xml, image/png, image/x-icon"
                  maxSizeMb={0.5}
                />
              </TabsContent>

              {/* Tab 2: Colors */}
              <TabsContent value="colors" className="flex flex-col gap-4 mt-4">
                <ColorPickerField
                  name="primaryColor"
                  label={t("primaryColor")}
                  control={form.control}
                />
                <ColorPickerField
                  name="secondaryColor"
                  label={t("secondaryColor")}
                  control={form.control}
                />
                <ColorPickerField
                  name="accentColor"
                  label={t("accentColor")}
                  control={form.control}
                />
                <ColorPickerField
                  name="dangerColor"
                  label={t("dangerColor")}
                  control={form.control}
                />
                <ColorPickerField
                  name="successColor"
                  label={t("successColor")}
                  control={form.control}
                />
                <ColorPickerField
                  name="warningColor"
                  label={t("warningColor")}
                  control={form.control}
                />
              </TabsContent>

              {/* Tab 3: Typography */}
              <TabsContent value="typography" className="flex flex-col gap-4 mt-4">
                {/* Font Family */}
                <FormField
                  control={form.control}
                  name="fontFamily"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("fontFamily")}</FormLabel>
                      <FormControl>
                        <FontSelect value={field.value || ""} onChange={field.onChange} />
                      </FormControl>
                    </FormItem>
                  )}
                />

                {/* Border Radius */}
                <FormField
                  control={form.control}
                  name="borderRadius"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("borderRadius")}</FormLabel>
                      <Select onValueChange={field.onChange} value={field.value || ""}>
                        <FormControl>
                          <SelectTrigger>
                            {field.value ? t(`borderRadiusOptions.${field.value.replace(/\./g, "_")}`) : t("borderRadiusOptions.0_5rem")}
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          {RADIUS_OPTIONS.map((radius) => (
                            <SelectItem key={radius} value={radius}>
                              {t(`borderRadiusOptions.${radius.replace(/\./g, "_")}`)}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </FormItem>
                  )}
                />
              </TabsContent>
            </Tabs>

            {/* Actions (outside tabs) */}
            <div className="flex justify-between">
              <Button type="button" variant="outline" onClick={handleReset}>
                {t("resetDefaults")}
              </Button>
              <Button type="submit" disabled={isPending}>
                {isPending ? "..." : t("save")}
              </Button>
            </div>
          </form>
        </Form>
      </div>

      {/* Preview Column */}
      <div className="lg:col-span-2 lg:sticky lg:top-4 self-start">
        <BrandingPreview values={previewValues} />
      </div>
    </div>
  );
}
