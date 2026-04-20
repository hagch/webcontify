"use client";

import type { Control, FieldValues, Path } from "react-hook-form";
import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

interface EnumFieldInputProps<T extends FieldValues> {
  control: Control<T>;
  name: Path<T>;
  label: string;
  required?: boolean;
  allowedValues: string[];
  placeholder?: string;
}

export function EnumFieldInput<T extends FieldValues>({
  control,
  name,
  label,
  required,
  allowedValues,
  placeholder,
}: EnumFieldInputProps<T>) {
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
          <Select
            onValueChange={field.onChange}
            defaultValue={field.value as string | undefined}
          >
            <FormControl>
              <SelectTrigger data-testid={`field-${name}`}>
                <SelectValue placeholder={placeholder ?? label} />
              </SelectTrigger>
            </FormControl>
            <SelectContent>
              {allowedValues.map((value) => (
                <SelectItem key={value} value={value}>
                  {value}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <FormMessage />
        </FormItem>
      )}
    />
  );
}
