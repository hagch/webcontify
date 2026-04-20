"use client";

import { useState } from "react";
import { ChevronsUpDown, Check, X } from "lucide-react";
import { useTranslations } from "next-intl";
import type { Control, FieldValues, Path } from "react-hook-form";
import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { Button } from "@/components/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import { cn } from "@/lib/utils";
import { useEntrySearch } from "../../hooks/use-entry-search";

interface ReferenceFieldInputProps<T extends FieldValues> {
  control: Control<T>;
  name: Path<T>;
  label: string;
  required?: boolean;
  appSlug: string;
  targetDefSlug: string;
}

export function ReferenceFieldInput<T extends FieldValues>({
  control,
  name,
  label,
  required,
  appSlug,
  targetDefSlug,
}: ReferenceFieldInputProps<T>) {
  const t = useTranslations("entries.reference");
  const [open, setOpen] = useState(false);
  const { results, isLoading, search, loadMore, hasMore } = useEntrySearch(
    appSlug,
    targetDefSlug
  );

  return (
    <FormField
      control={control}
      name={name}
      render={({ field }) => {
        const selectedDisplay = results.find(
          (r) => r.id === field.value
        )?.display;

        return (
          <FormItem className="flex flex-col">
            <FormLabel>
              {label}
              {required && <span className="text-destructive"> *</span>}
            </FormLabel>
            <div className="flex gap-1">
              <Popover open={open} onOpenChange={setOpen}>
                <PopoverTrigger
                  render={
                    <Button
                      variant="outline"
                      role="combobox"
                      aria-expanded={open}
                      className={cn(
                        "w-full justify-between font-normal",
                        !field.value && "text-muted-foreground"
                      )}
                    />
                  }
                >
                  <span className="truncate">
                    {selectedDisplay ?? field.value ?? t("search")}
                  </span>
                  <ChevronsUpDown className="ml-2 size-4 shrink-0 opacity-50" />
                </PopoverTrigger>
                <PopoverContent className="w-[var(--popover-trigger-width)] p-0" align="start">
                  <FormControl>
                    <Command shouldFilter={false}>
                      <CommandInput
                        data-testid={`field-${name}-search`}
                        placeholder={t("search")}
                        onValueChange={(value) => search(value)}
                      />
                      <CommandList>
                        {!isLoading && results.length === 0 && (
                          <CommandEmpty>{t("noResults")}</CommandEmpty>
                        )}
                        {isLoading && results.length === 0 && (
                          <CommandEmpty>{t("loading")}</CommandEmpty>
                        )}
                        <CommandGroup>
                          {results.map((entry) => (
                            <CommandItem
                              key={entry.id}
                              value={entry.id}
                              onSelect={() => {
                                field.onChange(entry.id);
                                setOpen(false);
                              }}
                            >
                              <Check
                                className={cn(
                                  "mr-2 size-4",
                                  field.value === entry.id
                                    ? "opacity-100"
                                    : "opacity-0"
                                )}
                              />
                              {entry.display}
                            </CommandItem>
                          ))}
                          {hasMore && (
                            <CommandItem
                              onSelect={() => loadMore()}
                              className="justify-center text-muted-foreground"
                            >
                              {isLoading ? t("loading") : t("search")}
                            </CommandItem>
                          )}
                        </CommandGroup>
                      </CommandList>
                    </Command>
                  </FormControl>
                </PopoverContent>
              </Popover>
              {field.value && !required && (
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  onClick={() => field.onChange(null)}
                  aria-label={t("clear")}
                >
                  <X className="size-4" />
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
