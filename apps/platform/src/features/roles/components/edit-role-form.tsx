"use client";

import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
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
  useUpdateRole,
  getListRolesQueryKey,
  getGetRoleQueryKey,
  type ValidationProblemDetail,
  type ProblemDetail,
} from "@/generated/identity";
import {
  updateRoleSchema,
  type UpdateRoleFormValues,
} from "../schemas/role";

interface EditRoleFormProps {
  roleId: string;
  currentName: string;
  currentDescription?: string | null;
}

export function EditRoleForm({
  roleId,
  currentName,
  currentDescription,
}: EditRoleFormProps) {
  const queryClient = useQueryClient();
  const updateRole = useUpdateRole();

  const form = useForm<UpdateRoleFormValues>({
    resolver: zodResolver(updateRoleSchema),
    defaultValues: {
      name: currentName,
      description: currentDescription ?? "",
    },
  });

  useEffect(() => {
    form.reset({
      name: currentName,
      description: currentDescription ?? "",
    });
  }, [currentName, currentDescription, form]);

  async function onSubmit(values: UpdateRoleFormValues) {
    try {
      const response = await updateRole.mutateAsync({
        roleId,
        data: {
          name: values.name,
          description: values.description || undefined,
        },
      });
      if (response.status === 200) {
        toast.success("Rolle wurde aktualisiert.");
        queryClient.invalidateQueries({ queryKey: getListRolesQueryKey() });
        queryClient.invalidateQueries({
          queryKey: getGetRoleQueryKey(roleId),
        });
      }
    } catch (error: unknown) {
      const err = error as { status?: number } & (
        | ValidationProblemDetail
        | ProblemDetail
      );
      if (err.status === 400) {
        const validationErr = err as ValidationProblemDetail;
        if (validationErr.errors && validationErr.errors.length > 0) {
          for (const fieldError of validationErr.errors) {
            form.setError(fieldError.field as keyof UpdateRoleFormValues, {
              message: fieldError.message,
            });
          }
          return;
        }
      }
      if (err.status === 409) {
        form.setError("name", {
          message: "Eine Rolle mit diesem Namen existiert bereits.",
        });
        return;
      }
      toast.error(
        "Fehler beim Aktualisieren der Rolle. Bitte versuche es erneut."
      );
    }
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
        <FormField
          control={form.control}
          name="name"
          render={({ field }) => (
            <FormItem>
              <FormLabel>
                Name <span className="text-destructive">*</span>
              </FormLabel>
              <FormControl>
                <Input placeholder="z.B. Redakteur" {...field} />
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
              <FormLabel>Beschreibung</FormLabel>
              <FormControl>
                <Textarea
                  placeholder="Optionale Beschreibung der Rolle"
                  rows={3}
                  {...field}
                />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <div className="flex justify-end">
          <Button type="submit" aria-busy={updateRole.isPending}>
            {updateRole.isPending && (
              <Loader2 className="mr-2 size-4 animate-spin" />
            )}
            Speichern
          </Button>
        </div>
      </form>
    </Form>
  );
}
