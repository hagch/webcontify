"use client";

import { useState, useEffect, useMemo } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { FloatingPanel } from "./floating-panel";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Checkbox } from "@/components/ui/checkbox";
import { Separator } from "@/components/ui/separator";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  useGetContentDefinition,
  useListContentDefinitions,
  useUpdateField,
  useDeleteField,
  useAddField,
  getListContentDefinitionsQueryKey,
  type FieldType,
  type ProblemDetail,
  type ValidationProblemDetail,
} from "@/generated/content";
import { getGetAppQueryKey } from "@/generated/app";
import { fieldKeySchema } from "../schemas/content-schemas";
import { FieldConfigForm } from "./field-config-form";

// Schema for editing an existing field
const editFieldSchema = z.object({
  fieldKey: fieldKeySchema,
  required: z.boolean(),
  unique: z.boolean(),
  sortable: z.boolean(),
  filterable: z.boolean(),
  config: z.any().optional(),
});

type EditFieldForm = z.infer<typeof editFieldSchema>;

// Schema for adding a new field
const addFieldSchema = z.object({
  fieldKey: fieldKeySchema,
  fieldType: z.string().min(1),
  required: z.boolean(),
  unique: z.boolean(),
  sortable: z.boolean(),
  filterable: z.boolean(),
  config: z.any().optional(),
  targetDefinitionId: z.string().optional(),
  onDelete: z.string().optional(),
});

type AddFieldForm = z.infer<typeof addFieldSchema>;

// --- Edit Field Popover ---

interface FieldPopoverProps {
  definitionId: string;
  fieldId: string;
  appSlug: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  anchorPosition?: { x: number; y: number };
}

export function FieldPopover({
  definitionId,
  fieldId,
  appSlug,
  open,
  onOpenChange,
  anchorPosition,
}: FieldPopoverProps) {
  const t = useTranslations("content");
  const queryClient = useQueryClient();
  const [confirmDelete, setConfirmDelete] = useState(false);

  const { data: defData } = useGetContentDefinition(appSlug, definitionId, {
    query: { enabled: open && !!definitionId },
  });
  const defResponse = defData?.data && "fields" in defData.data ? defData.data : undefined;
  const field = useMemo(
    () => defResponse?.fields?.find((f) => f.id === fieldId),
    [defResponse, fieldId]
  );

  const updateField = useUpdateField();
  const deleteFieldMutation = useDeleteField();

  const form = useForm<EditFieldForm>({
    resolver: zodResolver(editFieldSchema),
    defaultValues: {
      fieldKey: "",
      required: false,
      unique: false,
      sortable: false,
      filterable: false,
      config: {},
    },
  });

  // Populate form when the selected field changes — track which fieldId the
  // form was last reset for. This avoids resetting on React Query background
  // refetches (which create new object references for the same data).
  const [resetForFieldId, setResetForFieldId] = useState<string | null>(null);
  if (field && resetForFieldId !== fieldId) {
    form.reset({
      fieldKey: field.fieldKey,
      required: field.required,
      unique: field.unique,
      sortable: field.sortable,
      filterable: field.filterable,
      config: (field.config as unknown as Record<string, unknown>) ?? {},
    });
    setConfirmDelete(false);
    setResetForFieldId(fieldId);
  }

  async function onSubmit(values: EditFieldForm) {
    try {
      const response = await updateField.mutateAsync({
        appSlug,
        definitionId,
        fieldId,
        data: {
          fieldKey: values.fieldKey,
          required: values.required,
          unique: values.unique,
          sortable: values.sortable,
          filterable: values.filterable,
          config: field ? { ...values.config, fieldType: field.fieldType } as never : values.config as never,
        },
      });

      if (response.status === 200) {
        toast.success(t("field.editSuccess"));
        queryClient.invalidateQueries({
          queryKey: getListContentDefinitionsQueryKey(appSlug),
        });
        queryClient.invalidateQueries({
          queryKey: getGetAppQueryKey(appSlug),
        });
        onOpenChange(false);
      }
    } catch (error: unknown) {
      const err = error as ValidationProblemDetail | ProblemDetail;
      if (err.status === 400) {
        const validationErr = err as ValidationProblemDetail;
        if (validationErr.errors) {
          for (const fieldError of validationErr.errors) {
            if (fieldError.field === "fieldKey") {
              form.setError("fieldKey", { message: fieldError.message });
            }
          }
          return;
        }
      }
      if (err.status === 409) {
        form.setError("fieldKey", { message: t("field.keyExists") });
        return;
      }
      toast.error(err.detail ?? err.title);
    }
  }

  async function handleDelete() {
    try {
      await deleteFieldMutation.mutateAsync({
        appSlug,
        definitionId,
        fieldId,
      });
      toast.success(t("field.deleteSuccess"));
      queryClient.invalidateQueries({
        queryKey: getListContentDefinitionsQueryKey(appSlug),
      });
      queryClient.invalidateQueries({
        queryKey: getGetAppQueryKey(appSlug),
      });
      onOpenChange(false);
    } catch (error: unknown) {
      const err = error as ProblemDetail;
      toast.error(err.detail ?? err.title);
    }
  }

  const isMirror = field?.fieldType === "MIRROR";
  const isReference = field?.fieldType === "REFERENCE";

  return (
    <FloatingPanel open={open} onOpenChange={onOpenChange} anchorPosition={anchorPosition} data-testid="field-popover">
        {/* Header with field type badge */}
        <div className="mb-3 flex items-center justify-between pr-5">
          <p className="text-sm font-medium">{field?.fieldKey ?? "..."}</p>
          {field && (
            <Badge variant="outline" className="text-[10px] uppercase">
              {t(`fieldType.${field.fieldType}` as never)}
            </Badge>
          )}
        </div>

        {isMirror ? (
          <div className="space-y-3">
            <p className="text-xs text-muted-foreground italic">
              {t("field.typeImmutable")}
            </p>
            <div className="flex justify-end">
              <Button
                variant="outline"
                size="sm"
                onClick={() => onOpenChange(false)}
              >
                {t("field.cancel")}
              </Button>
            </div>
          </div>
        ) : (
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-3">
              <FormField
                control={form.control}
                name="fieldKey"
                render={({ field: formField }) => (
                  <FormItem>
                    <FormLabel className="text-xs">
                      {t("field.fieldKey")}
                    </FormLabel>
                    <FormControl>
                      <Input
                        className="h-8 text-sm"
                        data-testid="field-key-input"
                        {...formField}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              {/* Toggles */}
              <div className="grid grid-cols-2 gap-2">
                <ToggleField
                  control={form.control}
                  name="required"
                  label={t("field.required")}
                />
                <ToggleField
                  control={form.control}
                  name="unique"
                  label={t("field.unique")}
                />
                <ToggleField
                  control={form.control}
                  name="sortable"
                  label={t("field.sortable")}
                />
                <ToggleField
                  control={form.control}
                  name="filterable"
                  label={t("field.filterable")}
                />
              </div>

              {/* Config section */}
              {field && !isMirror && (
                <>
                  <Separator />
                  <p className="text-xs font-medium text-muted-foreground">
                    {t("field.config")}
                  </p>
                  <FieldConfigForm
                    fieldType={field.fieldType}
                    control={form.control}
                    configPrefix="config"
                    readOnly={isReference}
                  />
                </>
              )}

              {confirmDelete && (
                <p className="text-xs text-destructive">
                  {t("field.deleteConfirm")}
                </p>
              )}

              <div className="flex justify-end gap-2 pt-1">
                {!confirmDelete && (
                  <Button
                    type="button"
                    variant="destructive"
                    size="icon"
                    className="size-8"
                    onClick={() => setConfirmDelete(true)}
                    data-testid="field-delete"
                  >
                    <Trash2 className="size-3.5" />
                  </Button>
                )}
                <div className="flex-1" />
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => {
                    if (confirmDelete) {
                      setConfirmDelete(false);
                    } else {
                      onOpenChange(false);
                    }
                  }}
                >
                  {t("field.cancel")}
                </Button>
                {confirmDelete ? (
                  <Button
                    type="button"
                    variant="destructive"
                    size="sm"
                    onClick={handleDelete}
                    aria-busy={deleteFieldMutation.isPending}
                    data-testid="field-confirm-delete"
                  >
                    {deleteFieldMutation.isPending && (
                      <Loader2 className="mr-1.5 size-3.5 animate-spin" />
                    )}
                    {t("field.delete")}
                  </Button>
                ) : (
                  <Button
                    type="submit"
                    size="sm"
                    aria-busy={updateField.isPending}
                    data-testid="field-save"
                  >
                    {updateField.isPending && (
                      <Loader2 className="mr-1.5 size-3.5 animate-spin" />
                    )}
                    {t("field.save")}
                  </Button>
                )}
              </div>
            </form>
          </Form>
        )}
    </FloatingPanel>
  );
}

// --- Toggle Field Helper ---

function ToggleField<T extends Record<string, unknown>>({
  control,
  name,
  label,
}: {
  control: import("react-hook-form").Control<T>;
  name: import("react-hook-form").Path<T>;
  label: string;
}) {
  return (
    <FormField
      control={control}
      name={name}
      render={({ field }) => (
        <FormItem className="flex items-center gap-2">
          <FormControl>
            <Checkbox
              checked={field.value as boolean}
              onCheckedChange={field.onChange}
            />
          </FormControl>
          <FormLabel className="text-xs !mt-0">{label}</FormLabel>
        </FormItem>
      )}
    />
  );
}

// --- Add Field Popover ---

interface AddFieldPopoverProps {
  definitionId: string;
  appSlug: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  anchorPosition?: { x: number; y: number };
  onSuccess?: () => void;
}

const FIELD_TYPES: FieldType[] = [
  "TEXT",
  "INTEGER",
  "DECIMAL",
  "BOOLEAN",
  "DATE",
  "ENUM",
];

const RELATION_TYPES = ["ONE_TO_ONE", "MANY_TO_ONE", "MANY_TO_MANY"] as const;
type RelationType = (typeof RELATION_TYPES)[number];

function isRelationType(value: string): value is RelationType {
  return RELATION_TYPES.includes(value as RelationType);
}

export function AddFieldPopover({
  definitionId,
  appSlug,
  open,
  onOpenChange,
  anchorPosition,
  onSuccess,
}: AddFieldPopoverProps) {
  const t = useTranslations("content");
  const queryClient = useQueryClient();
  const addField = useAddField();

  const { data: defsData } = useListContentDefinitions(appSlug, {
    query: { staleTime: 60 * 1000 },
  });
  const definitions = defsData?.data?.items ?? [];
  const targetDefinitions = definitions.filter((d) => d.id !== definitionId);

  const form = useForm<AddFieldForm>({
    resolver: zodResolver(addFieldSchema),
    defaultValues: {
      fieldKey: "",
      fieldType: "TEXT",
      required: false,
      unique: false,
      sortable: false,
      filterable: false,
      config: {},
      targetDefinitionId: "",
      onDelete: "SET_NULL",
    },
  });

  const selectedType = form.watch("fieldType");
  const isRelation = isRelationType(selectedType);

  // Reset form when opening
  useEffect(() => {
    if (open) {
      form.reset();
    }
  }, [open, form]);

  async function onSubmit(values: AddFieldForm) {
    try {
      const actualFieldType = isRelationType(values.fieldType) ? "REFERENCE" : values.fieldType;

      const config = isRelationType(values.fieldType)
        ? {
            fieldType: "REFERENCE",
            targetDefinitionId: values.targetDefinitionId,
            relationType: values.fieldType,
            onDelete: values.onDelete || "SET_NULL",
          }
        : { ...values.config, fieldType: actualFieldType };

      const response = await addField.mutateAsync({
        appSlug,
        definitionId,
        data: {
          fieldKey: values.fieldKey,
          fieldType: actualFieldType as FieldType,
          required: values.required,
          unique: values.unique,
          sortable: values.sortable,
          filterable: values.filterable,
          config: config as never,
        },
      });

      if (response.status === 201) {
        toast.success(t("field.createSuccess"));
        queryClient.invalidateQueries({
          queryKey: getListContentDefinitionsQueryKey(appSlug),
        });
        queryClient.invalidateQueries({
          queryKey: getGetAppQueryKey(appSlug),
        });
        form.reset();
        onOpenChange(false);
        onSuccess?.();
      }
    } catch (error: unknown) {
      const err = error as ValidationProblemDetail | ProblemDetail;
      if (err.status === 400) {
        const validationErr = err as ValidationProblemDetail;
        if (validationErr.errors) {
          for (const fieldError of validationErr.errors) {
            if (fieldError.field === "fieldKey") {
              form.setError("fieldKey", { message: fieldError.message });
            }
          }
          return;
        }
      }
      if (err.status === 409) {
        form.setError("fieldKey", { message: t("field.keyExists") });
        return;
      }
      toast.error(err.detail ?? err.title);
    }
  }

  return (
    <FloatingPanel open={open} onOpenChange={onOpenChange} anchorPosition={anchorPosition} data-testid="add-field-popover">
        <p className="mb-3 text-sm font-medium">{t("field.addField")}</p>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-3">
            <FormField
              control={form.control}
              name="fieldKey"
              render={({ field }) => (
                <FormItem>
                  <FormLabel className="text-xs">
                    {t("field.fieldKey")}
                  </FormLabel>
                  <FormControl>
                    <Input
                      className="h-8 text-sm"
                      placeholder="field_name"
                      data-testid="add-field-key-input"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="fieldType"
              render={({ field }) => (
                <FormItem>
                  <FormLabel className="text-xs">
                    {t("field.fieldType")}
                  </FormLabel>
                  <Select
                    value={field.value}
                    onValueChange={(val) => {
                      field.onChange(val);
                      if (val && isRelationType(val)) {
                        form.setValue("config", {});
                      } else {
                        form.setValue("targetDefinitionId", "");
                        form.setValue("onDelete", "SET_NULL");
                      }
                    }}
                  >
                    <FormControl>
                      <SelectTrigger
                        className="w-full h-8 text-sm"
                        data-testid="add-field-type-select"
                      >
                        <SelectValue />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      {FIELD_TYPES.map((ft) => (
                        <SelectItem key={ft} value={ft}>
                          {t(`fieldType.${ft}` as never)}
                        </SelectItem>
                      ))}
                      <Separator className="my-1" />
                      {RELATION_TYPES.map((rt) => (
                        <SelectItem key={rt} value={rt}>
                          {t(`relationType.${rt}` as never)}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />

            {/* Relation config */}
            {isRelation && (
              <>
                <FormField
                  control={form.control}
                  name="targetDefinitionId"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel className="text-xs">
                        {t("config.targetDefinition")}
                      </FormLabel>
                      <Select
                        value={field.value}
                        onValueChange={field.onChange}
                      >
                        <FormControl>
                          <SelectTrigger className="w-full h-8 text-sm" data-testid="add-field-target-select">
                            <SelectValue placeholder={t("field.selectTarget")} />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          {targetDefinitions.map((def) => (
                            <SelectItem key={def.id} value={def.id}>
                              {def.name}
                            </SelectItem>
                          ))}
                          {targetDefinitions.length === 0 && (
                            <div className="px-2 py-1.5 text-xs text-muted-foreground">
                              {t("field.noTargets")}
                            </div>
                          )}
                        </SelectContent>
                      </Select>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="onDelete"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel className="text-xs">
                        {t("config.onDelete")}
                      </FormLabel>
                      <Select
                        value={field.value ?? "SET_NULL"}
                        onValueChange={field.onChange}
                      >
                        <FormControl>
                          <SelectTrigger className="w-full h-8 text-sm" data-testid="add-field-on-delete-select">
                            <SelectValue />
                          </SelectTrigger>
                        </FormControl>
                        <SelectContent>
                          <SelectItem value="CASCADE">{t("onDelete.CASCADE" as never)}</SelectItem>
                          <SelectItem value="SET_NULL">{t("onDelete.SET_NULL" as never)}</SelectItem>
                          <SelectItem value="RESTRICT">{t("onDelete.RESTRICT" as never)}</SelectItem>
                        </SelectContent>
                      </Select>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </>
            )}

            {/* Toggles — only for non-relation fields */}
            {!isRelation && (
              <div className="grid grid-cols-2 gap-2">
                <ToggleField
                  control={form.control}
                  name="required"
                  label={t("field.required")}
                />
                <ToggleField
                  control={form.control}
                  name="unique"
                  label={t("field.unique")}
                />
                <ToggleField
                  control={form.control}
                  name="sortable"
                  label={t("field.sortable")}
                />
                <ToggleField
                  control={form.control}
                  name="filterable"
                  label={t("field.filterable")}
                />
              </div>
            )}

            {/* Config section — only for non-relation fields */}
            {!isRelation && (
              <>
                <Separator />
                <p className="text-xs font-medium text-muted-foreground">
                  {t("field.config")}
                </p>
                <FieldConfigForm
                  fieldType={selectedType as FieldType}
                  control={form.control}
                  configPrefix="config"
                />
              </>
            )}

            <div className="flex justify-end gap-2 pt-1">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => onOpenChange(false)}
              >
                {t("field.cancel")}
              </Button>
              <Button
                type="submit"
                size="sm"
                aria-busy={addField.isPending}
                data-testid="add-field-submit"
              >
                {addField.isPending && (
                  <Loader2 className="mr-1.5 size-3.5 animate-spin" />
                )}
                {t("field.addField")}
              </Button>
            </div>
          </form>
        </Form>
    </FloatingPanel>
  );
}
