"use client";

import { useTranslations } from "next-intl";
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { FONT_CSS_VAR_MAP } from "@/lib/fonts";

const FONT_CATEGORIES = {
  modern: ["inter", "geist", "dm-sans", "plus-jakarta-sans"],
  classic: ["roboto", "source-sans-3", "ibm-plex-sans"],
  friendly: ["open-sans", "lato", "nunito-sans", "poppins"],
  serif: ["merriweather"],
} as const;

interface FontSelectProps {
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
}

export function FontSelect({ value, onChange, disabled }: FontSelectProps) {
  const t = useTranslations("config.branding");

  const fontStyle = (key: string): React.CSSProperties => ({
    fontFamily: FONT_CSS_VAR_MAP[key] || "inherit",
  });

  return (
    <Select value={value} onValueChange={(v) => { if (v !== null) onChange(v); }} disabled={disabled}>
      <SelectTrigger style={fontStyle(value)}>
        <SelectValue>
          {value ? t(`fontFamilyOptions.${value}` as Parameters<typeof t>[0]) : t("fontFamilyOptions.system")}
        </SelectValue>
      </SelectTrigger>
      <SelectContent>
        <SelectItem value="system">{t("fontFamilyOptions.system")}</SelectItem>

        {Object.entries(FONT_CATEGORIES).map(([category, fonts]) => (
          <SelectGroup key={category}>
            <SelectLabel>
              {t(
                `fontCategory${category.charAt(0).toUpperCase() + category.slice(1)}` as Parameters<typeof t>[0],
              )}
            </SelectLabel>
            {fonts.map((font) => (
              <SelectItem
                key={font}
                value={font}
                style={fontStyle(font)}
              >
                {t(`fontFamilyOptions.${font}` as Parameters<typeof t>[0])}
              </SelectItem>
            ))}
          </SelectGroup>
        ))}
      </SelectContent>
    </Select>
  );
}
