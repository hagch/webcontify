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
import { Textarea } from "@/components/ui/textarea";
import {
  useCreateApp,
  checkNewAppNameAvailability,
  type ValidationProblemDetail,
  type ProblemDetail,
} from "@/generated/app";
import {
  createAppSchema,
  type CreateAppFormValues,
} from "../schemas/create-app";

interface CreateAppDialogProps {
  children: React.ReactNode;
}

export function CreateAppDialog({ children }: CreateAppDialogProps) {
  const [open, setOpen] = useState(false);
  const router = useRouter();
  const createApp = useCreateApp();
  const t = useTranslations("apps");

  const form = useForm<CreateAppFormValues>({
    resolver: zodResolver(createAppSchema),
    defaultValues: {
      name: "",
      description: "",
    },
  });

  async function onSubmit(values: CreateAppFormValues) {
    try {
      const nameCheck = await checkNewAppNameAvailability({ name: values.name });
      if (!nameCheck.data.available) {
        form.setError("name", {
          message: t("createDialog.nameAlreadyExists"),
        });
        return;
      }
    } catch {
      // Ignore check error — backend validation will catch it
    }

    try {
      const response = await createApp.mutateAsync({ data: values });
      if (response.status === 201) {
        toast.success(t("createDialog.successToast"));
        setOpen(false);
        form.reset();
        router.push(`/apps/${response.data.id}`);
      }
    } catch (error: unknown) {
      const err = error as ValidationProblemDetail | ProblemDetail;
      if (err.status === 400) {
        const validationErr = err as ValidationProblemDetail;
        if (validationErr.errors) {
          for (const fieldError of validationErr.errors) {
            form.setError(fieldError.field as keyof CreateAppFormValues, {
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
      toast.error(t("createDialog.nameAlreadyExists"));
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={children as React.ReactElement} />
      <DialogContent data-testid="create-app-dialog" className="sm:max-w-md">
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
                    <Input data-testid="app-name-input" placeholder={t("createDialog.namePlaceholder")} {...field} />
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
                  <FormLabel>
                    {t("createDialog.descriptionLabel")}
                  </FormLabel>
                  <FormControl>
                    <Textarea
                      data-testid="app-description-input"
                      placeholder={t("createDialog.descriptionPlaceholder")}
                      {...field}
                    />
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
              <Button data-testid="create-app-submit" type="submit" aria-busy={createApp.isPending}>
                {createApp.isPending && (
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
