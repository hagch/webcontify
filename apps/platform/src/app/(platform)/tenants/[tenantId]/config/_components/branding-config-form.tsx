"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslations } from "next-intl";
import {
  Button,
  Card,
  CardContent,
  Input,
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@webcontify/ui";
import { toast } from "sonner";
import type {
  BrandingConfigResponse,
  UpdateBrandingConfigRequest,
} from "@/generated/tenant";
import {
  updateBrandingSchema,
  type UpdateBrandingFormData,
} from "@/lib/schemas/tenant-config";

interface BrandingConfigFormProps {
  config?: BrandingConfigResponse;
  onSubmit: (data: UpdateBrandingConfigRequest) => Promise<unknown>;
  isSubmitting: boolean;
}

export function BrandingConfigForm({
  config,
  onSubmit,
  isSubmitting,
}: BrandingConfigFormProps) {
  const t = useTranslations("config.branding");
  const form = useForm<UpdateBrandingFormData>({
    resolver: zodResolver(updateBrandingSchema),
    defaultValues: {
      primaryColor: config?.primaryColor ?? "",
      secondaryColor: config?.secondaryColor ?? "",
    },
  });

  const handleSubmit = async (data: UpdateBrandingFormData) => {
    try {
      await onSubmit({
        primaryColor: data.primaryColor || undefined,
        secondaryColor: data.secondaryColor || undefined,
      });
      toast.success(t("saved"));
    } catch {
      toast.error(t("error"));
    }
  };

  return (
    <Card>
      <CardContent className="pt-6">
        <Form {...form}>
          <form
            onSubmit={form.handleSubmit(handleSubmit)}
            className="space-y-4"
          >
            <FormField
              control={form.control}
              name="primaryColor"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("primaryColor")}</FormLabel>
                  <FormControl>
                    <Input placeholder="#3b82f6" {...field} />
                  </FormControl>
                  <FormDescription>{t("primaryColorHint")}</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="secondaryColor"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("secondaryColor")}</FormLabel>
                  <FormControl>
                    <Input placeholder="#64748b" {...field} />
                  </FormControl>
                  <FormDescription>{t("secondaryColorHint")}</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className="flex justify-end pt-2">
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? "..." : t("save")}
              </Button>
            </div>
          </form>
        </Form>
      </CardContent>
    </Card>
  );
}
