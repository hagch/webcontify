"use client";

import type { Control, FieldValues, Path } from "react-hook-form";
import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";

interface DecimalFieldInputProps<T extends FieldValues> {
  control: Control<T>;
  name: Path<T>;
  label: string;
  required?: boolean;
  min?: number;
  max?: number;
  scale?: number;
}

export function DecimalFieldInput<T extends FieldValues>({
  control,
  name,
  label,
  required,
}: DecimalFieldInputProps<T>) {
  return (
    <FormField
      control={control}
      name={name}
      render={({ field }) => (
        <FormItem>
          <FormLabel>
            {label}
            {required && <span className="text-destructive"> *</span>}
          </FormLabel>
          <FormControl>
            <Input
              data-testid={`field-${name}`}
              inputMode="decimal"
              {...field}
              value={field.value ?? ""}
              onChange={(e) => {
                const val = e.target.value;
                if (val === "") {
                  field.onChange(null);
                  return;
                }
                const normalized = val.replace(",", ".");
                const num = Number(normalized);
                field.onChange(Number.isNaN(num) ? field.value : num);
              }}
            />
          </FormControl>
          <FormMessage />
        </FormItem>
      )}
    />
  );
}
