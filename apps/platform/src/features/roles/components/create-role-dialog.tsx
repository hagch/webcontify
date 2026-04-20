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
        toast.success(`Rolle "${values.name}" wurde erstellt.`);
        setOpen(false);
        form.reset();
        queryClient.invalidateQueries({ queryKey: getListRolesQueryKey() });
      }
    } catch (error: unknown) {
      const err = error as { status?: number } & (ValidationProblemDetail | ProblemDetail);
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
          message: "Eine Rolle mit diesem Namen existiert bereits.",
        });
        return;
      }
      toast.error("Fehler beim Erstellen der Rolle. Bitte versuche es erneut.");
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={children as React.ReactElement} />
      <DialogContent data-testid="create-role-dialog" className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Rolle erstellen</DialogTitle>
          <DialogDescription>
            Erstelle eine neue Rolle, um Benutzerberechtigungen zu gruppieren.
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
                    Name <span className="text-destructive">*</span>
                  </FormLabel>
                  <FormControl>
                    <Input data-testid="role-name-input" placeholder="z.B. Redakteur" {...field} />
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
                      data-testid="role-description-input"
                      placeholder="Optionale Beschreibung der Rolle"
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
                Abbrechen
              </Button>
              <Button data-testid="create-role-submit" type="submit" aria-busy={createRole.isPending}>
                {createRole.isPending && (
                  <Loader2 className="mr-2 size-4 animate-spin" />
                )}
                Erstellen
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
