"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslations } from "next-intl";
import {
  Badge,
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
  DomainConfigResponse,
  UpdateDomainConfigRequest,
} from "@/generated/tenant";
import {
  updateDomainSchema,
  type UpdateDomainFormData,
} from "@/lib/schemas/tenant-config";

interface DomainConfigFormProps {
  config?: DomainConfigResponse;
  onSubmit: (data: UpdateDomainConfigRequest) => Promise<unknown>;
  isSubmitting: boolean;
}

const sslStatusVariant = (
  status?: string
): "default" | "secondary" | "destructive" => {
  switch (status) {
    case "ACTIVE":
      return "default";
    case "PENDING":
      return "secondary";
    case "FAILED":
      return "destructive";
    default:
      return "secondary";
  }
};

export function DomainConfigForm({
  config,
  onSubmit,
  isSubmitting,
}: DomainConfigFormProps) {
  const t = useTranslations("config.domain");
  const form = useForm<UpdateDomainFormData>({
    resolver: zodResolver(updateDomainSchema),
    defaultValues: {
      customDomain: config?.customDomain ?? "",
    },
  });

  const handleSubmit = async (data: UpdateDomainFormData) => {
    try {
      await onSubmit({
        customDomain: data.customDomain || undefined,
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
              name="customDomain"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("customDomain")}</FormLabel>
                  <FormControl>
                    <Input placeholder="app.example.com" {...field} />
                  </FormControl>
                  <FormDescription>{t("customDomainHint")}</FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />

            {config?.sslStatus && (
              <div className="flex items-center gap-2">
                <span className="text-sm font-medium text-foreground">
                  {t("sslStatus")}:
                </span>
                <Badge variant={sslStatusVariant(config.sslStatus)}>
                  {config.sslStatus}
                </Badge>
              </div>
            )}

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
