"use client";

import { Control } from "react-hook-form";
import { useTranslations } from "next-intl";
import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { ColorPicker } from "@/components/ui/color-picker";

interface ColorPickerFieldProps {
  name: string;
  label: string;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  control: Control<any>;
}

export function ColorPickerField({ name, label, control }: ColorPickerFieldProps) {
  const t = useTranslations("config.branding");

  return (
    <FormField
      control={control}
      name={name}
      render={({ field }) => (
        <FormItem>
          <FormLabel>{label}</FormLabel>
          <FormControl>
            <div className="flex items-center gap-2">
              <Input
                {...field}
                value={field.value ?? ""}
                placeholder="#000000"
                className="font-mono"
                onChange={(e) => {
                  field.onChange(e.target.value);
                }}
              />
              <ColorPicker
                value={field.value || "#000000"}
                onChange={(hex) => field.onChange(hex)}
                aria-label={t("colorPickerLabel", { label })}
              />
            </div>
          </FormControl>
          <FormMessage />
        </FormItem>
      )}
    />
  );
}
