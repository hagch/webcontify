"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
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
import { useCreateTenant } from "@/generated/tenant";
import {
  createTenantSchema,
  type CreateTenantInput,
} from "../schemas/create-tenant";

interface CreateTenantDialogProps {
  children: React.ReactNode;
}

export function CreateTenantDialog({ children }: CreateTenantDialogProps) {
  const [open, setOpen] = useState(false);
  const createTenant = useCreateTenant();

  const form = useForm<CreateTenantInput>({
    resolver: zodResolver(createTenantSchema),
    defaultValues: {
      name: "",
      slug: "",
    },
  });

  async function onSubmit(values: CreateTenantInput) {
    try {
      await createTenant.mutateAsync({ data: values });
      toast.success("Tenant created successfully");
      setOpen(false);
      form.reset();
    } catch (error: unknown) {
      const err = error as {
        data?: { errors?: Array<{ field?: string; message?: string }> };
        status?: number;
      };
      if (err.status === 422 && err.data?.errors) {
        for (const fieldError of err.data.errors) {
          if (fieldError.field && fieldError.message) {
            form.setError(fieldError.field as keyof CreateTenantInput, {
              message: fieldError.message,
            });
          }
        }
      } else {
        toast.error("Failed to create tenant. Please try again.");
      }
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={children as React.ReactElement} />
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Create Tenant</DialogTitle>
          <DialogDescription>
            Create a new tenant on the platform.
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
                    <Input placeholder="Acme Corp" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="slug"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>
                    Slug <span className="text-destructive">*</span>
                  </FormLabel>
                  <FormControl>
                    <Input placeholder="acme-corp" {...field} />
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
                Cancel
              </Button>
              <Button type="submit" aria-busy={createTenant.isPending}>
                {createTenant.isPending && (
                  <Loader2 className="mr-2 size-4 animate-spin" />
                )}
                Create
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
