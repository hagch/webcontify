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
  min,
  max,
  scale,
}: DecimalFieldInputProps<T>) {
  const step = scale != null ? Math.pow(10, -scale) : 0.01;

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
              type="number"
              step={step}
              min={min}
              max={max}
              {...field}
              value={field.value ?? ""}
              onChange={(e) => {
                const val = e.target.value;
                field.onChange(val === "" ? null : Number(val));
              }}
            />
          </FormControl>
          <FormMessage />
        </FormItem>
      )}
    />
  );
}
