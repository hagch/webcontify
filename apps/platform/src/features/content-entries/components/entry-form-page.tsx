"use client";

import { useState, useMemo, useEffect } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Save, Trash2, Clock, Loader2 } from "lucide-react";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { Form } from "@/components/ui/form";
import { Separator } from "@/components/ui/separator";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import {
  Tooltip,
  TooltipTrigger,
  TooltipContent,
} from "@/components/ui/tooltip";
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
import { EntryDeleteDialog } from "./entry-delete-dialog";
import { EntryHistoryList } from "./entry-history-list";
import { formatAuditDate } from "./entry-table-columns";

export interface EntryFormActionsState {
  isEdit: boolean;
  isPending: boolean;
  onHistoryOpen: () => void;
  onDeleteOpen: () => void;
}

interface EntryFormPageProps {
  definition: ContentDefinitionResponse;
  appSlug: string;
  /** When provided, the page is in edit mode */
  entry?: ContentEntryResponse;
  /** All definitions for resolving reference targets */
  allDefinitions?: ContentDefinitionResponse[];
  /** Called whenever action state changes so the parent can render actions in AppHeader */
  onActionsReady?: (state: EntryFormActionsState) => void;
}

export function EntryFormActions({
  isEdit,
  isPending,
  onHistoryOpen,
  onDeleteOpen,
}: EntryFormActionsState) {
  const t = useTranslations("entries");

  return (
    <div className="flex items-center gap-1">
      {isEdit && (
        <Tooltip>
          <TooltipTrigger
            render={
              <Button
                type="button"
                variant="ghost"
                size="icon"
                data-testid="entry-history-button"
                onClick={onHistoryOpen}
              />
            }
          >
            <Clock className="size-4" />
          </TooltipTrigger>
          <TooltipContent>{t("history.title")}</TooltipContent>
        </Tooltip>
      )}
      <Tooltip>
        <TooltipTrigger
          render={
            <Button
              form="entry-form"
              type="submit"
              variant="ghost"
              size="icon"
              data-testid="entry-save-button"
              disabled={isPending}
            />
          }
        >
          {isPending ? (
            <Loader2 className="size-4 animate-spin" />
          ) : (
            <Save className="size-4" />
          )}
        </TooltipTrigger>
        <TooltipContent>{t("form.save")}</TooltipContent>
      </Tooltip>
      {isEdit && (
        <Tooltip>
          <TooltipTrigger
            render={
              <Button
                type="button"
                variant="ghost"
                size="icon"
                data-testid="entry-delete-button"
                onClick={onDeleteOpen}
              />
            }
          >
            <Trash2 className="size-4" />
          </TooltipTrigger>
          <TooltipContent>{t("delete")}</TooltipContent>
        </Tooltip>
      )}
    </div>
  );
}

export function EntryFormPage({
  definition,
  appSlug,
  entry,
  allDefinitions,
  onActionsReady,
}: EntryFormPageProps) {
  const t = useTranslations("entries");
  const router = useRouter();
  const queryClient = useQueryClient();
  const createEntry = useCreateContentEntry();
  const updateEntry = useUpdateContentEntry();
  const isEdit = !!entry;

  const [historyOpen, setHistoryOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);

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
        const raw = entry.values[key];
        // Reference fields come as { id, display } — extract the UUID for the form
        if (field.fieldType === "REFERENCE" && typeof raw === "object" && raw !== null && "id" in raw) {
          values[key] = (raw as { id: string }).id;
        } else {
          values[key] = raw ?? null;
        }
      } else {
        const config = field.config as Record<string, unknown> | undefined;
        const configDefault = config?.defaultValue;
        if (configDefault != null) {
          values[key] = configDefault;
        } else {
          values[key] = field.fieldType === "BOOLEAN" ? false : null;
        }
      }
    }
    return values;
  }, [definition.fields, entry]);

  const form = useForm({
    resolver: zodResolver(schema),
    defaultValues,
  });

  const isPending = createEntry.isPending || updateEntry.isPending;

  useEffect(() => {
    onActionsReady?.({
      isEdit,
      isPending,
      onHistoryOpen: () => setHistoryOpen(true),
      onDeleteOpen: () => setDeleteOpen(true),
    });
  }, [isEdit, isPending, onActionsReady]);

  function invalidateQueries() {
    queryClient.invalidateQueries({
      queryKey: getListContentEntriesQueryKey(appSlug, definition.slug),
    });
    if (entry) {
      queryClient.invalidateQueries({
        queryKey: getGetContentEntryQueryKey(
          appSlug,
          definition.slug,
          entry.id
        ),
      });
    }
  }

  const entriesPath = `/apps/${appSlug}/data/${definition.slug}`;

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
        invalidateQueries();
      } else {
        const created = await createEntry.mutateAsync({
          appSlug,
          defSlug: definition.slug,
          data: { values },
        });
        toast.success(t("created"));
        invalidateQueries();
        const newEntry = created.data as ContentEntryResponse;
        router.push(`${entriesPath}/${newEntry.id}`);
      }
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
            const translatedMessage = t.has(fieldError.message as never)
              ? t(fieldError.message as never)
              : fieldError.message;
            form.setError(fieldError.field as string, {
              message: translatedMessage,
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

  function handleDeleteSuccess() {
    setDeleteOpen(false);
    router.push(entriesPath);
  }

  return (
    <>
      <Form {...form}>
        <form id="entry-form" onSubmit={form.handleSubmit(onSubmit)}>
          {/* Form Fields Grid */}
          <div
            data-testid="entry-form-grid"
            className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4"
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
                  entryValues={entry?.values}
                />
              ))}
          </div>

          {/* System Fields */}
          {isEdit && entry && (
            <>
              <Separator className="my-6" />
              <div
                data-testid="system-fields"
                className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4"
              >
                <SystemField label={t("detail.fields.id")} value={entry.id} />
                <SystemField
                  label={t("detail.fields.version")}
                  value={String(entry.version)}
                />
                {entry.createdAt && (
                  <SystemField
                    label={t("detail.fields.createdAt")}
                    value={formatAuditDate(entry.createdAt)}
                  />
                )}
                {entry.updatedAt && (
                  <SystemField
                    label={t("detail.fields.updatedAt")}
                    value={formatAuditDate(entry.updatedAt)}
                  />
                )}
                {entry.createdBy && (
                  <SystemField
                    label={t("detail.fields.createdBy")}
                    value={entry.createdByName ?? entry.createdBy}
                  />
                )}
                {entry.updatedBy && (
                  <SystemField
                    label={t("detail.fields.updatedBy")}
                    value={entry.updatedByName ?? entry.updatedBy}
                  />
                )}
              </div>
            </>
          )}
        </form>
      </Form>

      {/* History Sheet */}
      {isEdit && entry && (
        <Sheet open={historyOpen} onOpenChange={setHistoryOpen}>
          <SheetContent side="right">
            <SheetHeader>
              <SheetTitle>{t("history.title")}</SheetTitle>
            </SheetHeader>
            <div className="overflow-y-auto p-4">
              <EntryHistoryList
                appSlug={appSlug}
                defSlug={definition.slug}
                entryId={entry.id}
              />
            </div>
          </SheetContent>
        </Sheet>
      )}

      {/* Delete Dialog */}
      {isEdit && entry && (
        <EntryDeleteDialog
          appSlug={appSlug}
          defSlug={definition.slug}
          entry={entry}
          open={deleteOpen}
          onOpenChange={(open) => {
            setDeleteOpen(open);
            if (!open) handleDeleteSuccess();
          }}
        />
      )}
    </>
  );
}

function SystemField({ label, value }: { label: string; value: string }) {
  return (
    <div className="space-y-1">
      <p className="text-xs font-medium text-muted-foreground">{label}</p>
      <p className="text-sm font-mono">{value}</p>
    </div>
  );
}
