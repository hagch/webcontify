"use client";

import { useTheme } from "next-themes";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  useGetPreferences,
  useUpdatePreferences,
  UpdateUserPreferencesRequestTheme,
  UpdateUserPreferencesRequestLocale,
} from "@/generated/auth";

const localeLabels: Record<string, string> = { de: "Deutsch", en: "English" };

export function PreferencesForm() {
  const t = useTranslations("profile");
  const { theme, setTheme } = useTheme();
  const { data: preferencesData } = useGetPreferences();
  const updatePreferences = useUpdatePreferences();

  const preferences = preferencesData?.status === 200 ? preferencesData.data : undefined;
  // Read locale from cookie (source of truth for next-intl) instead of backend
  const cookieLocale = typeof document !== "undefined"
    ? document.cookie.split("; ").find(c => c.startsWith("NEXT_LOCALE="))?.split("=")[1]
    : undefined;
  const currentLocale = cookieLocale ?? preferences?.locale ?? "en";
  const currentTheme = theme ?? preferences?.theme ?? "system";

  async function handleLocaleChange(locale: string | null) {
    if (!locale) return;
    document.cookie = `NEXT_LOCALE=${locale};path=/;SameSite=Lax;max-age=31536000`;
    // Sync to backend (fire-and-forget), then reload to apply
    updatePreferences.mutate({
      data: { locale: locale as UpdateUserPreferencesRequestLocale },
    });
    // Reload immediately — cookie is already set, backend sync is best-effort
    window.location.reload();
  }

  async function handleThemeChange(newTheme: string | null) {
    if (!newTheme) return;
    setTheme(newTheme);
    try {
      await updatePreferences.mutateAsync({
        data: { theme: newTheme as UpdateUserPreferencesRequestTheme },
      });
      toast.success(t("preferences.saved"));
    } catch {
      // Theme is already applied locally; silently ignore backend failure
    }
  }

  const isPending = updatePreferences.isPending;

  return (
    <Card data-testid="preferences-form">
      <CardHeader>
        <CardTitle>{t("preferences.title")}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor="locale-select">{t("preferences.language")}</Label>
          <Select
            value={currentLocale}
            onValueChange={handleLocaleChange}
            disabled={isPending}
          >
            <SelectTrigger
              id="locale-select"
              data-testid="locale-select"
              className="w-48"
              aria-busy={isPending}
            >
              <SelectValue>{localeLabels[currentLocale] ?? currentLocale}</SelectValue>
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={UpdateUserPreferencesRequestLocale.de}>
                {localeLabels[UpdateUserPreferencesRequestLocale.de]}
              </SelectItem>
              <SelectItem value={UpdateUserPreferencesRequestLocale.en}>
                {localeLabels[UpdateUserPreferencesRequestLocale.en]}
              </SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-2">
          <Label htmlFor="theme-select">{t("preferences.theme")}</Label>
          <Select
            value={currentTheme}
            onValueChange={handleThemeChange}
            disabled={isPending}
          >
            <SelectTrigger
              id="theme-select"
              data-testid="theme-select"
              className="w-48"
              aria-busy={isPending}
            >
              <SelectValue>{t(`themes.${currentTheme}`)}</SelectValue>
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={UpdateUserPreferencesRequestTheme.light}>
                {t("themes.light")}
              </SelectItem>
              <SelectItem value={UpdateUserPreferencesRequestTheme.dark}>
                {t("themes.dark")}
              </SelectItem>
              <SelectItem value={UpdateUserPreferencesRequestTheme.system}>
                {t("themes.system")}
              </SelectItem>
            </SelectContent>
          </Select>
        </div>
      </CardContent>
    </Card>
  );
}
