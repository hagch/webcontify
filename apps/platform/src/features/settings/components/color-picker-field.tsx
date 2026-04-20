"use client";

import { Control } from "react-hook-form";
import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";

interface ColorPickerFieldProps {
  name: string;
  label: string;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  control: Control<any>;
}

export function ColorPickerField({ name, label, control }: ColorPickerFieldProps) {
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
              <input
                type="color"
                aria-label={`${label} Farbwähler`}
                value={field.value || "#000000"}
                onChange={(e) => {
                  field.onChange(e.target.value);
                }}
                className="h-9 w-10 cursor-pointer rounded-md border border-input bg-transparent p-1"
              />
            </div>
          </FormControl>
          <FormMessage />
        </FormItem>
      )}
    />
  );
}
