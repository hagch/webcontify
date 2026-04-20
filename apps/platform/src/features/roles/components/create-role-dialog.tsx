"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
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
import { Textarea } from "@/components/ui/textarea";
import {
  useCreateRole,
  getListRolesQueryKey,
  type ValidationProblemDetail,
  type ProblemDetail,
} from "@/generated/identity";
import {
  createRoleSchema,
  type CreateRoleFormValues,
} from "../schemas/role";

interface CreateRoleDialogProps {
  children: React.ReactNode;
}

export function CreateRoleDialog({ children }: CreateRoleDialogProps) {
  const [open, setOpen] = useState(false);
  const queryClient = useQueryClient();
  const createRole = useCreateRole();
  const t = useTranslations("roles");
  const te = useTranslations("errors");

  const form = useForm<CreateRoleFormValues>({
    resolver: zodResolver(createRoleSchema),
    defaultValues: {
      name: "",
      description: "",
    },
  });

  async function onSubmit(values: CreateRoleFormValues) {
    try {
      const response = await createRole.mutateAsync({
        data: {
          name: values.name,
          description: values.description || undefined,
        },
      });
      if (response.status === 201) {
        toast.success(t("toasts.created", { name: values.name }));
        setOpen(false);
        form.reset();
        queryClient.invalidateQueries({ queryKey: getListRolesQueryKey() });
      }
    } catch (error: unknown) {
      const err = error as { status?: number; code?: string } & (ValidationProblemDetail | ProblemDetail);
      if (err.status === 400) {
        const validationErr = err as ValidationProblemDetail;
        if (validationErr.errors && validationErr.errors.length > 0) {
          for (const fieldError of validationErr.errors) {
            form.setError(fieldError.field as keyof CreateRoleFormValues, {
              message: fieldError.message,
            });
          }
          return;
        }
      }
      if (err.status === 409) {
        form.setError("name", {
          message: te.has(err.code ?? "") ? te(err.code!) : te("role.name_already_exists"),
        });
        return;
      }
      toast.error(te.has(err.code ?? "") ? te(err.code!) : te("generic"));
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={children as React.ReactElement} />
      <DialogContent data-testid="create-role-dialog" className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t("createDialog.title")}</DialogTitle>
          <DialogDescription>
            {t("createDialog.description")}
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="name"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    {t("createDialog.nameLabel")} <span className="text-destructive">*</span>
                  </FormLabel>
                  <FormControl>
                    <Input data-testid="role-name-input" placeholder={t("createDialog.namePlaceholder")} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="description"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("createDialog.descriptionLabel")}</FormLabel>
                  <FormControl>
                    <Textarea
                      data-testid="role-description-input"
                      placeholder={t("createDialog.descriptionPlaceholder")}
                      rows={3}
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <DialogFooter>
              <Button
                data-testid="create-role-cancel"
                type="button"
                variant="outline"
                onClick={() => setOpen(false)}
              >
                {t("createDialog.cancel")}
              </Button>
              <Button data-testid="create-role-submit" type="submit" aria-busy={createRole.isPending}>
                {createRole.isPending && (
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
