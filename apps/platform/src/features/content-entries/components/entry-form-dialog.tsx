"use client";

import { useState, useMemo } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { useQueryClient } from "@tanstack/react-query";
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
import { Form } from "@/components/ui/form";
import type {
  ContentDefinitionResponse,
  ContentEntryResponse,
  ValidationProblemDetail,
  ProblemDetail,
} from "@/generated/content";
import {
  useCreateContentEntry,
  useUpdateContentEntry,
  getListContentEntriesQueryKey,
  getGetContentEntryQueryKey,
} from "@/generated/content";
import { buildEntrySchema } from "../schemas/build-entry-schema";
import { DynamicFieldRenderer } from "./dynamic-field-renderer";

interface EntryFormDialogProps {
  definition: ContentDefinitionResponse;
  appSlug: string;
  /** When provided, the dialog is in edit mode */
  entry?: ContentEntryResponse;
  /** All definitions for resolving reference targets */
  allDefinitions?: ContentDefinitionResponse[];
  children: React.ReactNode;
}

export function EntryFormDialog({
  definition,
  appSlug,
  entry,
  allDefinitions,
  children,
}: EntryFormDialogProps) {
  const [open, setOpen] = useState(false);
  const t = useTranslations("entries");
  const queryClient = useQueryClient();
  const createEntry = useCreateContentEntry();
  const updateEntry = useUpdateContentEntry();
  const isEdit = !!entry;

  const schema = useMemo(
    () => buildEntrySchema(definition.fields),
    [definition.fields]
  );

  const defaultValues = useMemo(() => {
    const values: Record<string, unknown> = {};
    for (const field of definition.fields) {
      if (field.fieldType === "MIRROR") continue;
      const key = `cf_${field.fieldKey}`;
      if (entry) {
        values[key] = entry.values[key] ?? null;
      } else {
        values[key] = field.fieldType === "BOOLEAN" ? false : null;
      }
    }
    return values;
  }, [definition.fields, entry]);

  const form = useForm({
    resolver: zodResolver(schema),
    defaultValues,
  });

  function invalidateQueries() {
    queryClient.invalidateQueries({
      queryKey: getListContentEntriesQueryKey(appSlug, definition.slug),
    });
    if (entry) {
      queryClient.invalidateQueries({
        queryKey: getGetContentEntryQueryKey(appSlug, definition.slug, entry.id),
      });
    }
  }

  async function onSubmit(values: Record<string, unknown>) {
    try {
      if (isEdit && entry) {
        await updateEntry.mutateAsync({
          appSlug,
          defSlug: definition.slug,
          entryId: entry.id,
          data: { version: entry.version, values },
        });
        toast.success(t("updated"));
      } else {
        await createEntry.mutateAsync({
          appSlug,
          defSlug: definition.slug,
          data: { values },
        });
        toast.success(t("created"));
      }
      invalidateQueries();
      setOpen(false);
      form.reset(defaultValues);
    } catch (error: unknown) {
      const err = error as {
        data?: ValidationProblemDetail | ProblemDetail;
        status?: number;
      };

      if (err.status === 409) {
        toast.error(t("versionConflict"));
        return;
      }

      if (err.status === 400 && err.data) {
        const validationErr = err.data as ValidationProblemDetail;
        if (validationErr.errors) {
          for (const fieldError of validationErr.errors) {
            form.setError(fieldError.field as string, {
              message: fieldError.message,
            });
          }
          return;
        }
      }

      toast.error(
        (err.data as ProblemDetail)?.detail ?? t("form.fieldInvalid")
      );
    }
  }

  const isPending = createEntry.isPending || updateEntry.isPending;

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={children as React.ReactElement} />
      <DialogContent
        data-testid="entry-form-dialog"
        className="sm:max-w-lg max-h-[80vh] overflow-y-auto"
      >
        <DialogHeader>
          <DialogTitle>{isEdit ? t("edit") : t("create")}</DialogTitle>
          <DialogDescription>
            {definition.name}
          </DialogDescription>
        </DialogHeader>
        <Form {...form}>
          <form
            onSubmit={form.handleSubmit(onSubmit)}
            className="space-y-4"
          >
            {definition.fields
              .filter((f) => f.fieldType !== "MIRROR")
              .sort((a, b) => a.position - b.position)
              .map((field) => (
                <DynamicFieldRenderer
                  key={field.id}
                  field={field}
                  control={form.control}
                  appSlug={appSlug}
                  allDefinitions={allDefinitions}
                />
              ))}
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => setOpen(false)}
              >
                {t("form.cancel")}
              </Button>
              <Button
                data-testid="entry-form-submit"
                type="submit"
                aria-busy={isPending}
              >
                {isPending && (
                  <Loader2 className="mr-2 size-4 animate-spin" />
                )}
                {t("form.save")}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
