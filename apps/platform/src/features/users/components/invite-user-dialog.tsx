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
  useInviteUser,
  getListUsersQueryKey,
  type ValidationProblemDetail,
  type ProblemDetail,
} from "@/generated/identity";
import {
  inviteUserSchema,
  type InviteUserFormValues,
} from "../schemas/invite-user";

interface InviteUserDialogProps {
  children: React.ReactNode;
}

export function InviteUserDialog({ children }: InviteUserDialogProps) {
  const [open, setOpen] = useState(false);
  const queryClient = useQueryClient();
  const inviteUser = useInviteUser();

  const form = useForm<InviteUserFormValues>({
    resolver: zodResolver(inviteUserSchema),
    defaultValues: {
      email: "",
      firstName: "",
      lastName: "",
    },
  });

  async function onSubmit(values: InviteUserFormValues) {
    try {
      const response = await inviteUser.mutateAsync({ data: values });
      if (response.status === 201) {
        toast.success(`${values.firstName} ${values.lastName} wurde eingeladen.`);
        setOpen(false);
        form.reset();
        queryClient.invalidateQueries({ queryKey: getListUsersQueryKey() });
      }
    } catch (error: unknown) {
      const err = error as { status?: number } & (ValidationProblemDetail | ProblemDetail);
      if (err.status === 400) {
        const validationErr = err as ValidationProblemDetail;
        if (validationErr.errors && validationErr.errors.length > 0) {
          for (const fieldError of validationErr.errors) {
            form.setError(fieldError.field as keyof InviteUserFormValues, {
              message: fieldError.message,
            });
          }
          return;
        }
      }
      if (err.status === 409) {
        form.setError("email", {
          message: "Diese E-Mail-Adresse ist bereits vergeben.",
        });
        return;
      }
      toast.error("Fehler beim Einladen des Benutzers. Bitte versuche es erneut.");
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={children as React.ReactElement} />
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Benutzer einladen</DialogTitle>
          <DialogDescription>
            Lade einen neuen Benutzer zu deinem Workspace ein.
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
                    <Input type="email" placeholder="max@beispiel.de" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="firstName"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    Vorname <span className="text-destructive">*</span>
                  </FormLabel>
                  <FormControl>
                    <Input placeholder="Max" {...field} />
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
                    <Input placeholder="Mustermann" {...field} />
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
                Abbrechen
              </Button>
              <Button type="submit" aria-busy={inviteUser.isPending}>
                {inviteUser.isPending && (
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
