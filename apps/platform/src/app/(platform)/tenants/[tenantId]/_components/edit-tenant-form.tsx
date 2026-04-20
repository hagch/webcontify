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
import type { TenantResponse, UpdateTenantRequest } from "@/generated/tenant";
import {
  updateTenantSchema,
  type UpdateTenantFormData,
} from "@/lib/schemas/tenant";

interface EditTenantFormProps {
  tenant: TenantResponse;
  onSubmit: (data: UpdateTenantRequest) => Promise<void>;
  onCancel: () => void;
  isSubmitting: boolean;
}

export function EditTenantForm({
  tenant,
  onSubmit,
  onCancel,
  isSubmitting,
}: EditTenantFormProps) {
  const t = useTranslations("tenants");
  const td = useTranslations("tenants.detail");
  const tc = useTranslations("tenants.createDialog");
  const form = useForm<UpdateTenantFormData>({
    resolver: zodResolver(updateTenantSchema),
    defaultValues: {
      name: tenant.name,
      slug: tenant.slug,
    },
  });

  const handleSubmit = async (data: UpdateTenantFormData) => {
    await onSubmit(data);
  };

  return (
    <Card>
      <CardContent className="pt-6">
        <Form {...form}>
          <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="name"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("name")}</FormLabel>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="slug"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("slug")}</FormLabel>
                  <FormControl>
                    <Input
                      {...field}
                      onChange={(e) => {
                        field.onChange(
                          e.target.value.toLowerCase().replaceAll(/[^a-z0-9-]/g, "")
                        );
                      }}
                    />
                  </FormControl>
                  <FormDescription>{tc("slugHint")}</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            <div className="flex justify-end gap-2 pt-2">
              <Button type="button" variant="outline" onClick={onCancel}>
                {td("cancel")}
              </Button>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? "..." : td("save")}
              </Button>
            </div>
          </form>
        </Form>
      </CardContent>
    </Card>
  );
}
