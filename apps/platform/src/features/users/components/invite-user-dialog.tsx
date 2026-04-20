"use client";

import { useState, useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
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
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  useInviteUser,
  useListUsers,
  useGetUserRoles,
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
  initialCopyFromUserId?: string;
}

export function InviteUserDialog({
  children,
  initialCopyFromUserId,
}: InviteUserDialogProps) {
  const [open, setOpen] = useState(!!initialCopyFromUserId);
  const queryClient = useQueryClient();
  const inviteUser = useInviteUser();

  const { data: usersData } = useListUsers();
  const users = usersData?.data?.items ?? [];

  const form = useForm<InviteUserFormValues>({
    resolver: zodResolver(inviteUserSchema),
    defaultValues: {
      email: "",
      firstName: "",
      lastName: "",
      copyRolesFromUserId: initialCopyFromUserId ?? undefined,
    },
  });

  const selectedCopyFromUserId = form.watch("copyRolesFromUserId");

  const { data: userRolesData } = useGetUserRoles(selectedCopyFromUserId ?? "", {
    query: { enabled: !!selectedCopyFromUserId },
  });
  const previewRoles = (userRolesData?.status === 200 ? userRolesData.data.roles : undefined) ?? [];

  // When initialCopyFromUserId changes externally (controlled from table), sync the form
  useEffect(() => {
    if (initialCopyFromUserId) {
      form.setValue("copyRolesFromUserId", initialCopyFromUserId);
      setOpen(true);
    }
  }, [initialCopyFromUserId, form]);

  async function onSubmit(values: InviteUserFormValues) {
    try {
      const response = await inviteUser.mutateAsync({ data: values });
      if (response.status === 201) {
        toast.success(`${values.firstName} ${values.lastName} wurde eingeladen.`);
        setOpen(false);
        form.reset({
          email: "",
          firstName: "",
          lastName: "",
          copyRolesFromUserId: undefined,
        });
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

  function handleOpenChange(nextOpen: boolean) {
    setOpen(nextOpen);
    if (!nextOpen) {
      form.reset({
        email: "",
        firstName: "",
        lastName: "",
        copyRolesFromUserId: undefined,
      });
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
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
            <FormField
              control={form.control}
              name="copyRolesFromUserId"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Berechtigungen uebernehmen von</FormLabel>
                  <Select
                    value={field.value ?? ""}
                    onValueChange={(val) =>
                      field.onChange(val === "__none__" ? undefined : val)
                    }
                  >
                    <FormControl>
                      <SelectTrigger>
                        <SelectValue placeholder="Benutzer auswaehlen (optional)" />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      <SelectItem value="__none__">Keinen auswaehlen</SelectItem>
                      {users.map((user) => (
                        <SelectItem key={user.id} value={user.id}>
                          {user.firstName} {user.lastName} ({user.email})
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                  {previewRoles.length > 0 && (
                    <div className="mt-2 flex flex-wrap gap-1">
                      {previewRoles.map((role) => (
                        <Badge key={role.id} variant="secondary">
                          {role.name}
                        </Badge>
                      ))}
                    </div>
                  )}
                </FormItem>
              )}
            />
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => handleOpenChange(false)}
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
