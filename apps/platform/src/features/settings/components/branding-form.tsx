"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  useGetTenantSelfConfig,
  useUpdateTenantSelfBranding,
  useUploadFile,
  getGetTenantSelfConfigQueryKey,
} from "@/generated/tenant";
import {
  updateBrandingSchema,
  UpdateBrandingFormData,
} from "@/lib/schemas/tenant-config";
import { Form } from "@/components/ui/form";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { ColorPickerField } from "./color-picker-field";
import { LogoUpload } from "./logo-upload";

export function BrandingForm() {
  const queryClient = useQueryClient();
  const { data, isLoading } = useGetTenantSelfConfig();
  const updateBranding = useUpdateTenantSelfBranding();
  const uploadFile = useUploadFile();

  const [logoFile, setLogoFile] = useState<File | null>(null);
  const [faviconFile, setFaviconFile] = useState<File | null>(null);
  const [logoDeleted, setLogoDeleted] = useState(false);
  const [faviconDeleted, setFaviconDeleted] = useState(false);

  const form = useForm<UpdateBrandingFormData>({
    resolver: zodResolver(updateBrandingSchema),
    defaultValues: {
      logoStorageKey: "",
      faviconStorageKey: "",
      primaryColor: "",
      secondaryColor: "",
    },
    values: {
      logoStorageKey: data?.data?.branding?.logoStorageKey ?? "",
      faviconStorageKey: data?.data?.branding?.faviconStorageKey ?? "",
      primaryColor: data?.data?.branding?.primaryColor ?? "",
      secondaryColor: data?.data?.branding?.secondaryColor ?? "",
    },
  });

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
        },
      });

      await queryClient.invalidateQueries({
        queryKey: getGetTenantSelfConfigQueryKey(),
      });

      setLogoFile(null);
      setFaviconFile(null);
      setLogoDeleted(false);
      setFaviconDeleted(false);

      toast.success("Branding gespeichert");
    } catch {
      toast.error("Fehler beim Speichern des Brandings");
    }
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
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-6">
        {/* Logo */}
        <Card>
          <CardHeader>
            <CardTitle>Logo</CardTitle>
            <CardDescription>
              Das Logo wird in der Navigationsleiste und auf der Login-Seite angezeigt.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <LogoUpload
              label="Logo"
              currentLogoUrl={
                data?.data?.branding?.logoStorageKey && !logoDeleted
                  ? `/api/v1/tenant/files/${data.data.branding.logoStorageKey}`
                  : undefined
              }
              onFileSelect={setLogoFile}
              onDelete={() => {
                setLogoDeleted(true);
                form.setValue("logoStorageKey", "");
              }}
              accept="image/png, image/jpeg"
              maxSizeMb={2}
            />
          </CardContent>
        </Card>

        {/* Favicon */}
        <Card>
          <CardHeader>
            <CardTitle>Favicon</CardTitle>
            <CardDescription>
              Das Favicon wird im Browser-Tab angezeigt.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <LogoUpload
              label="Favicon"
              currentLogoUrl={
                data?.data?.branding?.faviconStorageKey && !faviconDeleted
                  ? `/api/v1/tenant/files/${data.data.branding.faviconStorageKey}`
                  : undefined
              }
              onFileSelect={setFaviconFile}
              onDelete={() => {
                setFaviconDeleted(true);
                form.setValue("faviconStorageKey", "");
              }}
              accept="image/png, image/x-icon"
              maxSizeMb={0.5}
            />
          </CardContent>
        </Card>

        {/* Farben */}
        <Card>
          <CardHeader>
            <CardTitle>Farben</CardTitle>
            <CardDescription>
              Passe die Primär- und Sekundärfarbe deines Workspaces an.
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            <ColorPickerField
              name="primaryColor"
              label="Primärfarbe"
              control={form.control}
            />
            <ColorPickerField
              name="secondaryColor"
              label="Sekundärfarbe"
              control={form.control}
            />
          </CardContent>
        </Card>

        <div className="flex justify-end">
          <Button type="submit" disabled={isPending}>
            {isPending ? "Wird gespeichert…" : "Speichern"}
          </Button>
        </div>
      </form>
    </Form>
  );
}
