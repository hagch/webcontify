"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { type Control, type FieldValues, type Path } from "react-hook-form";
import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Badge } from "@/components/ui/badge";
import type { FieldType } from "@/generated/content";

interface FieldConfigFormProps<T extends FieldValues> {
  fieldType: FieldType;
  control: Control<T>;
  configPrefix: string;
  readOnly?: boolean;
}

export function FieldConfigForm<T extends FieldValues>({
  fieldType,
  control,
  configPrefix,
  readOnly = false,
}: FieldConfigFormProps<T>) {
  const t = useTranslations("content");

  const configPath = (key: string) => `${configPrefix}.${key}` as Path<T>;

  switch (fieldType) {
    case "TEXT":
      return (
        <div className="space-y-3">
          <div className="grid grid-cols-2 gap-2">
            <FormField
              control={control}
              name={configPath("minLength")}
              render={({ field }) => (
                <FormItem>
                  <FormLabel className="text-xs">
                    {t("config.minLength")}
                  </FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      className="h-8 text-sm"
                      disabled={readOnly}
                      {...field}
                      value={field.value ?? ""}
                      onChange={(e) =>
                        field.onChange(
                          e.target.value ? Number(e.target.value) : undefined
                        )
                      }
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={control}
              name={configPath("maxLength")}
              render={({ field }) => (
                <FormItem>
                  <FormLabel className="text-xs">
                    {t("config.maxLength")}
                  </FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      className="h-8 text-sm"
                      disabled={readOnly}
                      {...field}
                      value={field.value ?? ""}
                      onChange={(e) =>
                        field.onChange(
                          e.target.value ? Number(e.target.value) : undefined
                        )
                      }
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
          <FormField
            control={control}
            name={configPath("pattern")}
            render={({ field }) => (
              <FormItem>
                <FormLabel className="text-xs">
                  {t("config.pattern")}
                </FormLabel>
                <FormControl>
                  <Input
                    className="h-8 text-sm"
                    disabled={readOnly}
                    {...field}
                    value={field.value ?? ""}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={control}
            name={configPath("defaultValue")}
            render={({ field }) => (
              <FormItem>
                <FormLabel className="text-xs">
                  {t("config.defaultValue")}
                </FormLabel>
                <FormControl>
                  <Input
                    className="h-8 text-sm"
                    disabled={readOnly}
                    {...field}
                    value={field.value ?? ""}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>
      );

    case "INTEGER":
      return (
        <div className="space-y-3">
          <div className="grid grid-cols-2 gap-2">
            <FormField
              control={control}
              name={configPath("min")}
              render={({ field }) => (
                <FormItem>
                  <FormLabel className="text-xs">{t("config.min")}</FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      className="h-8 text-sm"
                      disabled={readOnly}
                      {...field}
                      value={field.value ?? ""}
                      onChange={(e) =>
                        field.onChange(
                          e.target.value ? Number(e.target.value) : undefined
                        )
                      }
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={control}
              name={configPath("max")}
              render={({ field }) => (
                <FormItem>
                  <FormLabel className="text-xs">{t("config.max")}</FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      className="h-8 text-sm"
                      disabled={readOnly}
                      {...field}
                      value={field.value ?? ""}
                      onChange={(e) =>
                        field.onChange(
                          e.target.value ? Number(e.target.value) : undefined
                        )
                      }
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
          <FormField
            control={control}
            name={configPath("defaultValue")}
            render={({ field }) => (
              <FormItem>
                <FormLabel className="text-xs">
                  {t("config.defaultValue")}
                </FormLabel>
                <FormControl>
                  <Input
                    type="number"
                    className="h-8 text-sm"
                    disabled={readOnly}
                    {...field}
                    value={field.value ?? ""}
                    onChange={(e) =>
                      field.onChange(
                        e.target.value ? Number(e.target.value) : undefined
                      )
                    }
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>
      );

    case "DECIMAL":
      return (
        <div className="space-y-3">
          <div className="grid grid-cols-2 gap-2">
            <FormField
              control={control}
              name={configPath("min")}
              render={({ field }) => (
                <FormItem>
                  <FormLabel className="text-xs">{t("config.min")}</FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      step="any"
                      className="h-8 text-sm"
                      disabled={readOnly}
                      {...field}
                      value={field.value ?? ""}
                      onChange={(e) =>
                        field.onChange(
                          e.target.value ? Number(e.target.value) : undefined
                        )
                      }
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={control}
              name={configPath("max")}
              render={({ field }) => (
                <FormItem>
                  <FormLabel className="text-xs">{t("config.max")}</FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      step="any"
                      className="h-8 text-sm"
                      disabled={readOnly}
                      {...field}
                      value={field.value ?? ""}
                      onChange={(e) =>
                        field.onChange(
                          e.target.value ? Number(e.target.value) : undefined
                        )
                      }
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
          <div className="grid grid-cols-2 gap-2">
            <FormField
              control={control}
              name={configPath("precision")}
              render={({ field }) => (
                <FormItem>
                  <FormLabel className="text-xs">
                    {t("config.precision")}
                  </FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      className="h-8 text-sm"
                      disabled={readOnly}
                      {...field}
                      value={field.value ?? ""}
                      onChange={(e) =>
                        field.onChange(
                          e.target.value ? Number(e.target.value) : undefined
                        )
                      }
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={control}
              name={configPath("scale")}
              render={({ field }) => (
                <FormItem>
                  <FormLabel className="text-xs">
                    {t("config.scale")}
                  </FormLabel>
                  <FormControl>
                    <Input
                      type="number"
                      className="h-8 text-sm"
                      disabled={readOnly}
                      {...field}
                      value={field.value ?? ""}
                      onChange={(e) =>
                        field.onChange(
                          e.target.value ? Number(e.target.value) : undefined
                        )
                      }
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
          <FormField
            control={control}
            name={configPath("defaultValue")}
            render={({ field }) => (
              <FormItem>
                <FormLabel className="text-xs">
                  {t("config.defaultValue")}
                </FormLabel>
                <FormControl>
                  <Input
                    type="number"
                    step="any"
                    className="h-8 text-sm"
                    disabled={readOnly}
                    {...field}
                    value={field.value ?? ""}
                    onChange={(e) =>
                      field.onChange(
                        e.target.value ? Number(e.target.value) : undefined
                      )
                    }
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>
      );

    case "BOOLEAN":
      return (
        <div className="space-y-3">
          <FormField
            control={control}
            name={configPath("defaultValue")}
            render={({ field }) => (
              <FormItem className="flex items-center gap-2">
                <FormControl>
                  <Checkbox
                    checked={field.value === true}
                    onCheckedChange={field.onChange}
                    disabled={readOnly}
                  />
                </FormControl>
                <FormLabel className="text-xs !mt-0">
                  {t("config.defaultValue")}
                </FormLabel>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>
      );

    case "DATE":
      return (
        <div className="space-y-3">
          <div className="grid grid-cols-2 gap-2">
            <FormField
              control={control}
              name={configPath("minDate")}
              render={({ field }) => (
                <FormItem>
                  <FormLabel className="text-xs">
                    {t("config.minDate")}
                  </FormLabel>
                  <FormControl>
                    <Input
                      type="date"
                      className="h-8 text-sm"
                      disabled={readOnly}
                      {...field}
                      value={field.value ?? ""}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={control}
              name={configPath("maxDate")}
              render={({ field }) => (
                <FormItem>
                  <FormLabel className="text-xs">
                    {t("config.maxDate")}
                  </FormLabel>
                  <FormControl>
                    <Input
                      type="date"
                      className="h-8 text-sm"
                      disabled={readOnly}
                      {...field}
                      value={field.value ?? ""}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
          <FormField
            control={control}
            name={configPath("dateFormat")}
            render={({ field }) => (
              <FormItem>
                <FormLabel className="text-xs">
                  {t("config.dateFormat")}
                </FormLabel>
                <FormControl>
                  <Input
                    className="h-8 text-sm"
                    placeholder="yyyy-MM-dd"
                    disabled={readOnly}
                    {...field}
                    value={field.value ?? ""}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={control}
            name={configPath("defaultValue")}
            render={({ field }) => (
              <FormItem>
                <FormLabel className="text-xs">
                  {t("config.defaultValue")}
                </FormLabel>
                <FormControl>
                  <Input
                    type="date"
                    className="h-8 text-sm"
                    disabled={readOnly}
                    {...field}
                    value={field.value ?? ""}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>
      );

    case "ENUM":
      return (
        <div className="space-y-3">
          <EnumAllowedValuesField
            control={control}
            name={configPath("allowedValues")}
            label={t("config.allowedValues")}
            readOnly={readOnly}
          />
          <FormField
            control={control}
            name={configPath("defaultValue")}
            render={({ field }) => (
              <FormItem>
                <FormLabel className="text-xs">
                  {t("config.defaultValue")}
                </FormLabel>
                <FormControl>
                  <Input
                    className="h-8 text-sm"
                    disabled={readOnly}
                    {...field}
                    value={field.value ?? ""}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>
      );

    case "REFERENCE":
      return (
        <div className="space-y-3">
          <div>
            <span className="text-xs text-muted-foreground">
              {t("config.targetDefinition")}
            </span>
            <Badge variant="outline" className="ml-2 text-xs">
              {/* Target is displayed as readonly label */}
              Reference
            </Badge>
          </div>
          <FormField
            control={control}
            name={configPath("relationType")}
            render={({ field }) => (
              <FormItem>
                <FormLabel className="text-xs">
                  {t("config.relationType")}
                </FormLabel>
                <Select
                  value={field.value ?? "MANY_TO_ONE"}
                  onValueChange={field.onChange}
                  disabled={readOnly}
                >
                  <FormControl>
                    <SelectTrigger className="w-full h-8 text-sm">
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
            control={control}
            name={configPath("onDelete")}
            render={({ field }) => (
              <FormItem>
                <FormLabel className="text-xs">
                  {t("config.onDelete")}
                </FormLabel>
                <Select
                  value={field.value ?? "SET_NULL"}
                  onValueChange={field.onChange}
                  disabled={readOnly}
                >
                  <FormControl>
                    <SelectTrigger className="w-full h-8 text-sm">
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
        </div>
      );

    case "MIRROR":
      return (
        <div className="space-y-2">
          <p className="text-xs text-muted-foreground italic">
            {t("field.typeImmutable")}
          </p>
        </div>
      );

    default:
      return null;
  }
}

/**
 * Separate component for enum allowedValues that keeps a raw text state
 * and only converts to array on blur. This prevents the comma from being
 * eaten on every keystroke by filter(Boolean).
 */
function EnumAllowedValuesField<T extends FieldValues>({
  control,
  name,
  label,
  readOnly,
}: {
  control: Control<T>;
  name: Path<T>;
  label: string;
  readOnly: boolean;
}) {
  const [rawText, setRawText] = useState<string | null>(null);

  return (
    <FormField
      control={control}
      name={name}
      render={({ field }) => {
        const displayValue =
          rawText !== null
            ? rawText
            : Array.isArray(field.value)
              ? field.value.join(", ")
              : (field.value ?? "");

        return (
          <FormItem>
            <FormLabel className="text-xs">{label}</FormLabel>
            <FormControl>
              <Input
                className="h-8 text-sm"
                placeholder="value1, value2, value3"
                disabled={readOnly}
                value={displayValue}
                onChange={(e) => setRawText(e.target.value)}
                onBlur={() => {
                  if (rawText !== null) {
                    const values = rawText
                      .split(",")
                      .map((v) => v.trim())
                      .filter(Boolean);
                    field.onChange(values);
                    setRawText(null);
                  }
                }}
              />
            </FormControl>
            <FormMessage />
          </FormItem>
        );
      }}
    />
  );
}
