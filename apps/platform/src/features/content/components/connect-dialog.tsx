"use client";

import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import {
  Popover,
  PopoverContent,
} from "@/components/ui/popover";
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
  useAddField,
  getListContentDefinitionsQueryKey,
  type ProblemDetail,
  type ValidationProblemDetail,
} from "@/generated/content";
import { getGetAppQueryKey } from "@/generated/app";
import {
  connectReferenceSchema,
  type ConnectReferenceForm,
} from "../schemas/content-schemas";

interface ConnectDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  sourceDefinitionId: string;
  sourceDefinitionName: string;
  targetDefinitionId: string;
  appSlug: string;
  onSuccess: () => void;
}

function pluralize(name: string): string {
  if (name.endsWith("s")) return name;
  if (name.endsWith("y")) return name.slice(0, -1) + "ies";
  return name + "s";
}

export function ConnectDialog({
  open,
  onOpenChange,
  sourceDefinitionId,
  sourceDefinitionName,
  targetDefinitionId,
  appSlug,
  onSuccess,
}: ConnectDialogProps) {
  const t = useTranslations("content");
  const queryClient = useQueryClient();
  const addField = useAddField();

  const form = useForm<ConnectReferenceForm>({
    resolver: zodResolver(connectReferenceSchema),
    defaultValues: {
      referenceFieldKey: "",
      mirrorFieldKey: pluralize(sourceDefinitionName.toLowerCase()),
      relationType: "MANY_TO_ONE",
      onDelete: "SET_NULL",
    },
  });

  async function onSubmit(values: ConnectReferenceForm) {
    try {
      const response = await addField.mutateAsync({
        appSlug,
        definitionId: sourceDefinitionId,
        data: {
          fieldKey: values.referenceFieldKey,
          fieldType: "REFERENCE",
          required: false,
          unique: false,
          sortable: false,
          filterable: true,
          config: {
            fieldType: "REFERENCE" as const,
            targetDefinitionId,
            relationType: values.relationType,
            onDelete: values.onDelete,
          },
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
        onSuccess();
      }
    } catch (error: unknown) {
      const err = error as ValidationProblemDetail | ProblemDetail;
      if (err.status === 400) {
        const validationErr = err as ValidationProblemDetail;
        if (validationErr.errors) {
          for (const fieldError of validationErr.errors) {
            if (fieldError.field === "fieldKey") {
              form.setError("referenceFieldKey", {
                message: fieldError.message,
              });
            }
          }
          return;
        }
      }
      if (err.status === 409) {
        form.setError("referenceFieldKey", {
          message: t("field.keyExists"),
        });
        return;
      }
      toast.error(err.detail ?? err.title);
    }
  }

  return (
    <Popover open={open} onOpenChange={onOpenChange}>
      <PopoverContent
        className="w-[320px]"
        side="bottom"
        align="start"
        data-testid="connect-dialog"
      >
        <p className="mb-3 text-sm font-medium">{t("connect.title")}</p>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-3">
            <FormField
              control={form.control}
              name="referenceFieldKey"
              render={({ field }) => (
                <FormItem>
                  <FormLabel className="text-xs">
                    {t("connect.sourceField")}
                  </FormLabel>
                  <FormControl>
                    <Input
                      placeholder="author"
                      className="h-8 text-sm"
                      data-testid="connect-source-field"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="mirrorFieldKey"
              render={({ field }) => (
                <FormItem>
                  <FormLabel className="text-xs">
                    {t("connect.mirrorField")}
                  </FormLabel>
                  <FormControl>
                    <Input
                      className="h-8 text-sm"
                      data-testid="connect-mirror-field"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="relationType"
              render={({ field }) => (
                <FormItem>
                  <FormLabel className="text-xs">
                    {t("connect.relationType")}
                  </FormLabel>
                  <Select
                    value={field.value}
                    onValueChange={field.onChange}
                  >
                    <FormControl>
                      <SelectTrigger
                        className="w-full h-8 text-sm"
                        data-testid="connect-relation-type"
                      >
                        <SelectValue />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      <SelectItem value="ONE_TO_ONE">
                        {t("relationType.ONE_TO_ONE")}
                      </SelectItem>
                      <SelectItem value="MANY_TO_ONE">
                        {t("relationType.MANY_TO_ONE")}
                      </SelectItem>
                      <SelectItem value="MANY_TO_MANY">
                        {t("relationType.MANY_TO_MANY")}
                      </SelectItem>
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
                    {t("connect.onDelete")}
                  </FormLabel>
                  <Select
                    value={field.value}
                    onValueChange={field.onChange}
                  >
                    <FormControl>
                      <SelectTrigger
                        className="w-full h-8 text-sm"
                        data-testid="connect-on-delete"
                      >
                        <SelectValue />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      <SelectItem value="CASCADE">
                        {t("onDelete.CASCADE")}
                      </SelectItem>
                      <SelectItem value="SET_NULL">
                        {t("onDelete.SET_NULL")}
                      </SelectItem>
                      <SelectItem value="RESTRICT">
                        {t("onDelete.RESTRICT")}
                      </SelectItem>
                    </SelectContent>
                  </Select>
                  <FormMessage />
                </FormItem>
              )}
            />

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
                aria-busy={addField.isPending}
                data-testid="connect-submit"
              >
                {addField.isPending && (
                  <Loader2 className="mr-1.5 size-3.5 animate-spin" />
                )}
                {t("connect.create")}
              </Button>
            </div>
          </form>
        </Form>
      </PopoverContent>
    </Popover>
  );
}
