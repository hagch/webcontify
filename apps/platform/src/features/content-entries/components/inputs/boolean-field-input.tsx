"use client";

import type { Control, FieldValues, Path } from "react-hook-form";
import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Checkbox } from "@/components/ui/checkbox";

interface BooleanFieldInputProps<T extends FieldValues> {
  control: Control<T>;
  name: Path<T>;
  label: string;
  required?: boolean;
}

export function BooleanFieldInput<T extends FieldValues>({
  control,
  name,
  label,
  required,
}: BooleanFieldInputProps<T>) {
  return (
    <FormField
      control={control}
      name={name}
      render={({ field }) => (
        <FormItem className="flex items-center gap-2 space-y-0">
          <FormControl>
            <Checkbox
              data-testid={`field-${name}`}
              checked={field.value ?? false}
              onCheckedChange={field.onChange}
            />
          </FormControl>
          <FormLabel className="cursor-pointer">
            {label}
            {required && <span className="text-destructive"> *</span>}
          </FormLabel>
          <FormMessage />
        </FormItem>
      )}
    />
  );
}
