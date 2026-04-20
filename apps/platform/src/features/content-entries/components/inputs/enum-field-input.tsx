"use client";

import { X } from "lucide-react";
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
import { Button } from "@/components/ui/button";

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
          <div className="relative">
            <Select
              onValueChange={field.onChange}
              value={field.value as string | undefined}
            >
              <FormControl>
                <SelectTrigger
                  className="w-full"
                  data-testid={`field-${name}`}
                >
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
            {!required && field.value && (
              <Button
                type="button"
                variant="ghost"
                size="icon-xs"
                className="absolute right-8 top-1/2 -translate-y-1/2"
                onClick={() => field.onChange(null)}
              >
                <X className="size-3" />
              </Button>
            )}
          </div>
          <FormMessage />
        </FormItem>
      )}
    />
  );
}
