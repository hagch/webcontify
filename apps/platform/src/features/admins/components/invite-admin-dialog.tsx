"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
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
import {
  useInvitePlatformAdmin,
  getListPlatformAdminsQueryKey,
  type ValidationProblemDetail,
  type ProblemDetail,
} from "@/generated/platform-admin";
import {
  inviteAdminSchema,
  type InviteAdminFormValues,
} from "../schemas/invite-admin";

interface InviteAdminDialogProps {
  children: React.ReactNode;
}

export function InviteAdminDialog({ children }: InviteAdminDialogProps) {
  const [open, setOpen] = useState(false);
  const queryClient = useQueryClient();
  const inviteAdmin = useInvitePlatformAdmin();

  const form = useForm<InviteAdminFormValues>({
    resolver: zodResolver(inviteAdminSchema),
    defaultValues: {
      email: "",
      firstName: "",
      lastName: "",
    },
  });

  async function onSubmit(values: InviteAdminFormValues) {
    try {
      const response = await inviteAdmin.mutateAsync({
        data: {
          email: values.email,
          firstName: values.firstName,
          lastName: values.lastName,
        },
      });
      if (response.status === 201) {
        toast.success(
          `Administrator "${values.firstName} ${values.lastName}" wurde eingeladen.`
        );
        setOpen(false);
        form.reset();
        queryClient.invalidateQueries({
          queryKey: getListPlatformAdminsQueryKey(),
        });
      }
    } catch (error: unknown) {
      const err = error as { status?: number } & (ValidationProblemDetail | ProblemDetail);
      if (err.status === 400) {
        const validationErr = err as ValidationProblemDetail;
        if (validationErr.errors && validationErr.errors.length > 0) {
          for (const fieldError of validationErr.errors) {
            form.setError(fieldError.field as keyof InviteAdminFormValues, {
              message: fieldError.message,
            });
          }
          return;
        }
      }
      if (err.status === 409) {
        form.setError("email", {
          message: "Ein Administrator mit dieser E-Mail-Adresse existiert bereits.",
        });
        return;
      }
      toast.error(
        "Fehler beim Einladen des Administrators. Bitte versuche es erneut."
      );
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={children as React.ReactElement} />
      <DialogContent data-testid="invite-admin-dialog" className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Administrator einladen</DialogTitle>
          <DialogDescription>
            Lade eine Person als Platform Administrator ein. Sie erhaelt eine
            E-Mail mit einem Einladungslink.
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="email"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    E-Mail <span className="text-destructive">*</span>
                  </FormLabel>
                  <FormControl>
                    <Input
                      data-testid="admin-email-input"
                      type="email"
                      placeholder="name@beispiel.de"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <div className="grid grid-cols-2 gap-4">
              <FormField
                control={form.control}
                name="firstName"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>
                      Vorname <span className="text-destructive">*</span>
                    </FormLabel>
                    <FormControl>
                      <Input data-testid="admin-firstname-input" placeholder="Max" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="lastName"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>
                      Nachname <span className="text-destructive">*</span>
                    </FormLabel>
                    <FormControl>
                      <Input data-testid="admin-lastname-input" placeholder="Mustermann" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>
            <DialogFooter>
              <Button
                data-testid="invite-admin-cancel"
                type="button"
                variant="outline"
                onClick={() => setOpen(false)}
              >
                Abbrechen
              </Button>
              <Button data-testid="invite-admin-submit" type="submit" aria-busy={inviteAdmin.isPending}>
                {inviteAdmin.isPending && (
                  <Loader2 className="mr-2 size-4 animate-spin" />
                )}
                Einladen
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
