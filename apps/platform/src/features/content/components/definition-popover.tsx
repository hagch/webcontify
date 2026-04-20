"use client";

import { useEffect, useCallback, useRef } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { FloatingPanel } from "./floating-panel";
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import {
  useGetContentDefinition,
  useUpdateContentDefinition,
  useCreateContentDefinition,
  checkContentDefinitionNameAvailability,
  getListContentDefinitionsQueryKey,
  type ProblemDetail,
  type ValidationProblemDetail,
} from "@/generated/content";
import {
  createDefinitionSchema,
  updateDefinitionSchema,
  type UpdateDefinitionForm,
} from "../schemas/content-schemas";

interface DefinitionPopoverBaseProps {
  appSlug: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  anchorPosition?: { x: number; y: number };
}

interface DefinitionPopoverEditProps extends DefinitionPopoverBaseProps {
  mode?: "edit";
  definitionId: string;
  onCreated?: never;
}

interface DefinitionPopoverCreateProps extends DefinitionPopoverBaseProps {
  mode: "create";
  definitionId?: never;
  onCreated?: (id: string) => void;
}

type DefinitionPopoverProps = DefinitionPopoverEditProps | DefinitionPopoverCreateProps;

export function DefinitionPopover(props: DefinitionPopoverProps) {
  const { appSlug, open, onOpenChange } = props;
  const isCreate = props.mode === "create";

  const t = useTranslations("content");
  const queryClient = useQueryClient();
  const debounceRef = useRef<ReturnType<typeof setTimeout>>();

  const { data: defData } = useGetContentDefinition(
    appSlug,
    isCreate ? "" : props.definitionId,
    { query: { enabled: open && !isCreate && !!props.definitionId } }
  );
  const definition = isCreate ? undefined : defData?.data;

  const updateDefinition = useUpdateContentDefinition();
  const createDefinition = useCreateContentDefinition();

  const form = useForm<UpdateDefinitionForm>({
    resolver: zodResolver(isCreate ? createDefinitionSchema : updateDefinitionSchema),
    defaultValues: {
      name: "",
      description: "",
      displayFormat: "",
    },
  });

  // Populate form when definition loads (edit mode only)
  useEffect(() => {
    if (definition) {
      form.reset({
        name: definition.name,
        description: definition.description ?? "",
        displayFormat: definition.displayFormat ?? "",
      });
    }
  }, [definition, form]);

  // Reset form when opening in create mode
  useEffect(() => {
    if (open && isCreate) {
      form.reset({ name: "", description: "", displayFormat: "" });
    }
  }, [open, isCreate, form]);

  const checkNameAvailability = useCallback(
    async (name: string) => {
      if (!name || (!isCreate && name === definition?.name)) {
        form.clearErrors("name");
        return;
      }
      clearTimeout(debounceRef.current);
      debounceRef.current = setTimeout(async () => {
        try {
          const result = await checkContentDefinitionNameAvailability(
            appSlug,
            { name, excludeId: isCreate ? undefined : props.definitionId }
          );
          if (!result.data.available) {
            form.setError("name", {
              message: t("definition.nameExists"),
            });
          } else {
            form.clearErrors("name");
          }
        } catch {
          // Ignore check errors
        }
      }, 400);
    },
    [appSlug, isCreate, props, definition?.name, form, t]
  );

  async function onSubmit(values: UpdateDefinitionForm) {
    try {
      if (isCreate) {
        const response = await createDefinition.mutateAsync({
          appSlug,
          data: {
            name: values.name!,
            description: values.description || undefined,
          },
        });

        if (response.status === 201) {
          toast.success(t("definition.createSuccess"));
          queryClient.invalidateQueries({
            queryKey: getListContentDefinitionsQueryKey(appSlug),
          });
          const created = response.data as { id: string };
          props.onCreated?.(created.id);
          onOpenChange(false);
        }
      } else {
        const response = await updateDefinition.mutateAsync({
          appSlug,
          definitionId: props.definitionId,
          data: {
            name: values.name,
            description: values.description || undefined,
            displayFormat: values.displayFormat || undefined,
          },
        });

        if (response.status === 200) {
          toast.success(t("definition.editSuccess"));
          queryClient.invalidateQueries({
            queryKey: getListContentDefinitionsQueryKey(appSlug),
          });
          onOpenChange(false);
        }
      }
    } catch (error: unknown) {
      const err = error as ValidationProblemDetail | ProblemDetail;
      if (err.status === 400) {
        const validationErr = err as ValidationProblemDetail;
        if (validationErr.errors) {
          for (const fieldError of validationErr.errors) {
            form.setError(fieldError.field as keyof UpdateDefinitionForm, {
              message: fieldError.message,
            });
          }
          return;
        }
      }
      if (err.status === 409) {
        form.setError("name", {
          message: t("definition.nameExists"),
        });
        return;
      }
      toast.error(err.detail ?? err.title);
    }
  }

  const isSaving = isCreate ? createDefinition.isPending : updateDefinition.isPending;

  const formContent = (
    <>
      <p className="mb-3 text-sm font-medium">
        {isCreate ? t("spotlight.createNew") : (definition?.name ?? "...")}
      </p>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-3">
          <FormField
            control={form.control}
            name="name"
            render={({ field }) => (
              <FormItem>
                <FormLabel className="text-xs">
                  {t("definition.name")}
                </FormLabel>
                <FormControl>
                  <Input
                    className="h-8 text-sm"
                    data-testid="definition-name-input"
                    autoFocus={isCreate}
                    {...field}
                    onChange={(e) => {
                      field.onChange(e);
                      checkNameAvailability(e.target.value);
                    }}
                  />
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
                <FormLabel className="text-xs">
                  {t("definition.description")}
                </FormLabel>
                <FormControl>
                  <Textarea
                    className="min-h-[60px] text-sm"
                    data-testid="definition-description-input"
                    {...field}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          {!isCreate && (
            <FormField
              control={form.control}
              name="displayFormat"
              render={({ field }) => (
                <FormItem>
                  <FormLabel className="text-xs">
                    {t("definition.displayFormat")}
                  </FormLabel>
                  <FormControl>
                    <Input
                      className="h-8 text-sm"
                      placeholder="{title} — {author}"
                      data-testid="definition-display-format-input"
                      {...field}
                    />
                  </FormControl>
                  <FormDescription className="text-[11px]">
                    {t("definition.displayFormatHint")}
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
          )}

          <div className="flex justify-end gap-2 pt-1">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => onOpenChange(false)}
            >
              {t("connect.cancel")}
            </Button>
            <Button
              type="submit"
              size="sm"
              aria-busy={isSaving}
              data-testid="definition-save"
            >
              {isSaving && (
                <Loader2 className="mr-1.5 size-3.5 animate-spin" />
              )}
              {t("definition.save")}
            </Button>
          </div>
        </form>
      </Form>
    </>
  );

  return (
    <FloatingPanel open={open} onOpenChange={onOpenChange} anchorPosition={props.anchorPosition} data-testid="definition-popover">
      {formContent}
    </FloatingPanel>
  );
}
