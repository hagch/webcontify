"use client";

import { useEffect } from "react";
import { useTheme } from "next-themes";
import { useGetPreferences } from "@/generated/auth";

export function usePreferencesSync() {
  const { data } = useGetPreferences();
  const { setTheme } = useTheme();

  useEffect(() => {
    if (data?.status === 200) {
      if (data.data.theme) {
        setTheme(data.data.theme);
      }
      if (data.data.locale) {
        document.cookie = `NEXT_LOCALE=${data.data.locale};path=/;SameSite=Lax;max-age=31536000`;
      }
    }
  }, [data, setTheme]);
}
