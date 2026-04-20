"use client";

import { format } from "date-fns";
import { CalendarIcon, X } from "lucide-react";
import type { Control, FieldValues, Path } from "react-hook-form";
import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { cn } from "@/lib/utils";

interface DateFieldInputProps<T extends FieldValues> {
  control: Control<T>;
  name: Path<T>;
  label: string;
  required?: boolean;
  placeholder?: string;
  dateFormat?: string;
}

export function DateFieldInput<T extends FieldValues>({
  control,
  name,
  label,
  required,
  placeholder,
  dateFormat: displayFormat,
}: DateFieldInputProps<T>) {
  return (
    <FormField
      control={control}
      name={name}
      render={({ field }) => {
        const dateValue = field.value ? new Date(field.value as string) : undefined;

        return (
          <FormItem className="flex flex-col">
            <FormLabel>
              {label}
              {required && <span className="text-destructive"> *</span>}
            </FormLabel>
            <div className="relative">
              <Popover>
                <PopoverTrigger
                  render={
                    <Button
                      type="button"
                      variant="outline"
                      className={cn(
                        "w-full justify-start text-left font-normal",
                        !field.value && "text-muted-foreground"
                      )}
                    />
                  }
                >
                  <CalendarIcon className="mr-2 size-4" />
                  {field.value
                    ? format(new Date(field.value as string), displayFormat || "yyyy-MM-dd")
                    : (placeholder ?? "Pick a date")}
                </PopoverTrigger>
                <PopoverContent className="w-auto p-0" align="start">
                  <FormControl>
                    <Calendar
                      data-testid={`field-${name}`}
                      mode="single"
                      selected={dateValue}
                      onSelect={(date) => {
                        field.onChange(
                          date ? format(date, "yyyy-MM-dd") : null
                        );
                      }}
                      autoFocus
                    />
                  </FormControl>
                </PopoverContent>
              </Popover>
              {!required && field.value && (
                <Button
                  type="button"
                  variant="ghost"
                  size="icon-xs"
                  className="absolute right-2 top-1/2 -translate-y-1/2"
                  onClick={() => field.onChange(null)}
                >
                  <X className="size-3" />
                </Button>
              )}
            </div>
            <FormMessage />
          </FormItem>
        );
      }}
    />
  );
}
