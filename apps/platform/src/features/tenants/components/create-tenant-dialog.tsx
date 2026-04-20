"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Separator } from "@/components/ui/separator";
import {
  useCreateTenant,
  checkTenantNameAvailability,
  type ValidationProblemDetail,
  type ProblemDetail,
} from "@/generated/tenant";
import {
  createTenantSchema,
  type CreateTenantFormValues,
} from "../schemas/create-tenant";

interface CreateTenantDialogProps {
  children: React.ReactNode;
}

export function CreateTenantDialog({ children }: CreateTenantDialogProps) {
  const [open, setOpen] = useState(false);
  const router = useRouter();
  const createTenant = useCreateTenant();
  const t = useTranslations("tenants");

  const form = useForm<CreateTenantFormValues>({
    resolver: zodResolver(createTenantSchema),
    defaultValues: {
      name: "",
      adminEmail: "",
      adminFirstName: "",
      adminLastName: "",
    },
  });

  async function onSubmit(values: CreateTenantFormValues) {
    try {
      const nameCheck = await checkTenantNameAvailability({ name: values.name });
      if (!nameCheck.data.available) {
        form.setError("name", {
          message: t("createDialog.nameAlreadyExists"),
        });
        return;
      }
    } catch {
      // Fehler bei der Prüfung ignorieren — Backend-Validierung schlägt dann fehl
    }

    try {
      const response = await createTenant.mutateAsync({ data: values });
      if (response.status === 201) {
        toast.success(t("createDialog.creatingToast"));
        setOpen(false);
        form.reset();
        router.push(`/tenants/${response.data.id}`);
      }
    } catch (error: unknown) {
      const err = error as ValidationProblemDetail | ProblemDetail;
      if (err.status === 400) {
        const validationErr = err as ValidationProblemDetail;
        if (validationErr.errors) {
          for (const fieldError of validationErr.errors) {
            form.setError(fieldError.field as keyof CreateTenantFormValues, {
              message: fieldError.message,
            });
          }
          return;
        }
      }
      if (err.status === 409) {
        form.setError("name", {
          message: t("createDialog.nameAlreadyExists"),
        });
        return;
      }
      toast.error(t("createDialog.createError"));
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={children as React.ReactElement} />
      <DialogContent data-testid="create-tenant-dialog" className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t("createDialog.title")}</DialogTitle>
          <DialogDescription>
            {t("createDialog.description")}
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            {/* Sektion 1: Tenant */}
            <FormField
              control={form.control}
              name="name"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    {t("createDialog.nameLabel")} <span className="text-destructive">*</span>
                  </FormLabel>
                  <FormControl>
                    <Input data-testid="tenant-name-input" placeholder="Acme Corp" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <Separator />
            <p className="text-sm font-medium text-muted-foreground">
              {t("createDialog.adminSection")}
            </p>

            {/* Sektion 2: Administrator */}
            <FormField
              control={form.control}
              name="adminEmail"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    {t("createDialog.adminEmailLabel")} <span className="text-destructive">*</span>
                  </FormLabel>
                  <FormControl>
                    <Input data-testid="admin-email-input" type="email" placeholder={t("createDialog.adminEmailPlaceholder")} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="adminFirstName"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    {t("createDialog.adminFirstNameLabel")} <span className="text-destructive">*</span>
                  </FormLabel>
                  <FormControl>
                    <Input data-testid="admin-firstname-input" placeholder={t("createDialog.adminFirstNamePlaceholder")} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="adminLastName"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    {t("createDialog.adminLastNameLabel")} <span className="text-destructive">*</span>
                  </FormLabel>
                  <FormControl>
                    <Input data-testid="admin-lastname-input" placeholder={t("createDialog.adminLastNamePlaceholder")} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setOpen(false)}
              >
                {t("createDialog.cancel")}
              </Button>
              <Button data-testid="create-tenant-submit" type="submit" aria-busy={createTenant.isPending}>
                {createTenant.isPending && (
                  <Loader2 className="mr-2 size-4 animate-spin" />
                )}
                {t("createDialog.submit")}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
