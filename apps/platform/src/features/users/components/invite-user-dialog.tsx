"use client";

import { useState, useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
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
  useListRoles,
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
  const t = useTranslations("users");
  const te = useTranslations("errors");

  const { data: usersData } = useListUsers();
  const users = usersData?.data?.items ?? [];

  const { data: rolesData } = useListRoles();
  const roles = (rolesData?.status === 200 ? rolesData.data.roles : undefined) ?? [];

  const form = useForm<InviteUserFormValues>({
    resolver: zodResolver(inviteUserSchema),
    defaultValues: {
      email: "",
      firstName: "",
      lastName: "",
      roleIds: [],
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

  // Pre-fill roleIds when copy-from user's roles are loaded
  useEffect(() => {
    if (previewRoles.length > 0) {
      form.setValue("roleIds", previewRoles.map((r) => r.id));
    }
  }, [previewRoles, form]);

  async function onSubmit(values: InviteUserFormValues) {
    try {
      const { copyRolesFromUserId: _ignored, ...payload } = values;
      const response = await inviteUser.mutateAsync({ data: payload });
      if (response.status === 201) {
        toast.success(t("toasts.invited", { name: `${values.firstName} ${values.lastName}` }));
        setOpen(false);
        form.reset({
          email: "",
          firstName: "",
          lastName: "",
          roleIds: [],
          copyRolesFromUserId: undefined,
        });
        queryClient.invalidateQueries({ queryKey: getListUsersQueryKey() });
      }
    } catch (error: unknown) {
      const err = error as { status?: number; code?: string } & (ValidationProblemDetail | ProblemDetail);
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
          message: te.has(err.code ?? "") ? te(err.code!) : te("user.email_already_exists"),
        });
        return;
      }
      toast.error(te.has(err.code ?? "") ? te(err.code!) : te("generic"));
    }
  }

  function handleOpenChange(nextOpen: boolean) {
    setOpen(nextOpen);
    if (!nextOpen) {
      form.reset({
        email: "",
        firstName: "",
        lastName: "",
        roleIds: [],
        copyRolesFromUserId: undefined,
      });
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger render={children as React.ReactElement} />
      <DialogContent data-testid="invite-user-dialog" className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{t("inviteDialog.title")}</DialogTitle>
          <DialogDescription>
            {t("inviteDialog.description")}
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
                    {t("inviteDialog.emailLabel")} <span className="text-destructive">*</span>
                  </FormLabel>
                  <FormControl>
                    <Input data-testid="user-email-input" type="email" placeholder={t("inviteDialog.emailPlaceholder")} {...field} />
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
                    {t("inviteDialog.firstNameLabel")} <span className="text-destructive">*</span>
                  </FormLabel>
                  <FormControl>
                    <Input data-testid="user-firstname-input" placeholder={t("inviteDialog.firstNamePlaceholder")} {...field} />
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
                    {t("inviteDialog.lastNameLabel")} <span className="text-destructive">*</span>
                  </FormLabel>
                  <FormControl>
                    <Input data-testid="user-lastname-input" placeholder={t("inviteDialog.lastNamePlaceholder")} {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="roleIds"
              render={() => (
                <FormItem>
                  <FormLabel>
                    {t("inviteDialog.rolesLabel")} <span className="text-destructive">*</span>
                  </FormLabel>
                  {roles.length === 0 ? (
                    <p className="text-sm text-muted-foreground">{t("inviteDialog.rolesEmptyState")}</p>
                  ) : (
                    <div className="space-y-2">
                      {roles.map((role) => (
                        <FormField
                          key={role.id}
                          control={form.control}
                          name="roleIds"
                          render={({ field }) => (
                            <FormItem className="flex items-center space-x-2 space-y-0">
                              <FormControl>
                                <Checkbox
                                  data-testid={`role-checkbox-${role.id}`}
                                  checked={field.value?.includes(role.id)}
                                  onCheckedChange={(checked) => {
                                    const current = field.value ?? [];
                                    field.onChange(
                                      checked
                                        ? [...current, role.id]
                                        : current.filter((id: string) => id !== role.id)
                                    );
                                  }}
                                />
                              </FormControl>
                              <FormLabel className="font-normal cursor-pointer">{role.name}</FormLabel>
                            </FormItem>
                          )}
                        />
                      ))}
                    </div>
                  )}
                  <FormMessage />
                </FormItem>
              )}
            />
            {!initialCopyFromUserId && (
              <FormField
                control={form.control}
                name="copyRolesFromUserId"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>{t("inviteDialog.copyRolesLabel")}</FormLabel>
                    <Select
                      value={field.value ?? "__none__"}
                      onValueChange={(val) =>
                        field.onChange(val === "__none__" ? undefined : val)
                      }
                    >
                      <FormControl>
                        <SelectTrigger data-testid="copy-roles-select">
                          <SelectValue>
                            {field.value
                              ? (() => {
                                  const u = users.find((u) => u.id === field.value);
                                  return u ? `${u.firstName} ${u.lastName} (${u.email})` : t("inviteDialog.copyRolesPlaceholder");
                                })()
                              : t("inviteDialog.copyRolesNone")}
                          </SelectValue>
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        <SelectItem value="__none__">{t("inviteDialog.copyRolesNone")}</SelectItem>
                        {users.map((user) => (
                          <SelectItem key={user.id} value={user.id}>
                            {user.firstName} {user.lastName} ({user.email})
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />
            )}
            <DialogFooter>
              <Button
                data-testid="invite-user-cancel"
                type="button"
                variant="outline"
                onClick={() => handleOpenChange(false)}
              >
                {t("inviteDialog.cancel")}
              </Button>
              <Button data-testid="invite-user-submit" type="submit" aria-busy={inviteUser.isPending}>
                {inviteUser.isPending && (
                  <Loader2 className="mr-2 size-4 animate-spin" />
                )}
                {t("inviteDialog.submit")}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
