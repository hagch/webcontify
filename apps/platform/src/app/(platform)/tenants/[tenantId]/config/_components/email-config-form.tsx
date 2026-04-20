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
  EmailConfigResponse,
  UpdateEmailConfigRequest,
  TestEmailRequest,
} from "@/generated/tenant";
import {
  updateEmailSchema,
  testEmailSchema,
  type UpdateEmailFormData,
  type TestEmailFormData,
} from "@/lib/schemas/tenant-config";

interface EmailConfigFormProps {
  config?: EmailConfigResponse;
  onSubmit: (data: UpdateEmailConfigRequest) => Promise<unknown>;
  isSubmitting: boolean;
  onTestEmail: (data: TestEmailRequest) => Promise<unknown>;
  isTestingEmail: boolean;
}

export function EmailConfigForm({
  config,
  onSubmit,
  isSubmitting,
  onTestEmail,
  isTestingEmail,
}: EmailConfigFormProps) {
  const t = useTranslations("config.email");

  const form = useForm<UpdateEmailFormData>({
    resolver: zodResolver(updateEmailSchema),
    defaultValues: {
      smtpHost: config?.smtpHost ?? "",
      smtpPort: config?.smtpPort ?? 587,
      smtpUser: config?.smtpUser ?? "",
      smtpPassword: "",
      fromAddress: config?.fromAddress ?? "",
      fromName: config?.fromName ?? "",
    },
  });

  const testForm = useForm<TestEmailFormData>({
    resolver: zodResolver(testEmailSchema),
    defaultValues: {
      recipientEmail: "",
    },
  });

  const handleSubmit = async (data: UpdateEmailFormData) => {
    try {
      await onSubmit({
        smtpHost: data.smtpHost,
        smtpPort: data.smtpPort,
        smtpUser: data.smtpUser || undefined,
        smtpPassword: data.smtpPassword || undefined,
        fromAddress: data.fromAddress,
        fromName: data.fromName,
      });
      toast.success(t("saved"));
    } catch {
      toast.error(t("error"));
    }
  };

  const handleTestEmail = async (data: TestEmailFormData) => {
    try {
      await onTestEmail({ recipientEmail: data.recipientEmail });
      toast.success(t("testSent"));
    } catch {
      toast.error(t("testError"));
    }
  };

  return (
    <div className="space-y-6">
      <Card>
        <CardContent className="pt-6">
          <Form {...form}>
            <form
              onSubmit={form.handleSubmit(handleSubmit)}
              className="space-y-4"
            >
              <FormField
                control={form.control}
                name="smtpHost"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("smtpHost")}</FormLabel>
                    <FormControl>
                      <Input placeholder="smtp.example.com" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="smtpPort"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("smtpPort")}</FormLabel>
                    <FormControl>
                      <Input
                        type="number"
                        placeholder="587"
                        {...field}
                        onChange={(e) =>
                          field.onChange(
                            e.target.value
                              ? Number(e.target.value)
                              : undefined
                          )
                        }
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="smtpUser"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("smtpUser")}</FormLabel>
                    <FormControl>
                      <Input {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="smtpPassword"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("smtpPassword")}</FormLabel>
                    <FormControl>
                      <Input type="password" {...field} />
                    </FormControl>
                    <FormDescription>{t("smtpPasswordHint")}</FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="fromAddress"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("fromAddress")}</FormLabel>
                    <FormControl>
                      <Input
                        type="email"
                        placeholder="noreply@example.com"
                        {...field}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="fromName"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("fromName")}</FormLabel>
                    <FormControl>
                      <Input placeholder="My App" {...field} />
                    </FormControl>
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

      <Card>
        <CardContent className="pt-6">
          <h3 className="text-lg font-medium text-foreground">
            {t("testTitle")}
          </h3>
          <p className="mt-1 text-sm text-muted-foreground">
            {t("testDescription")}
          </p>
          <Form {...testForm}>
            <form
              onSubmit={testForm.handleSubmit(handleTestEmail)}
              className="mt-4 flex items-end gap-3"
            >
              <FormField
                control={testForm.control}
                name="recipientEmail"
                render={({ field }) => (
                  <FormItem className="flex-1">
                    <FormLabel>{t("recipientEmail")}</FormLabel>
                    <FormControl>
                      <Input
                        type="email"
                        placeholder="test@example.com"
                        {...field}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <Button type="submit" variant="outline" disabled={isTestingEmail}>
                {isTestingEmail ? "..." : t("sendTest")}
              </Button>
            </form>
          </Form>
        </CardContent>
      </Card>
    </div>
  );
}
