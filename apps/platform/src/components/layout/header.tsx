"use client";

import { useTranslations } from "next-intl";

export function Header() {
  const t = useTranslations("header");

  return (
    <header className="flex h-14 items-center border-b border-border bg-card px-6">
      <h2 className="text-sm font-medium text-muted-foreground">
        {t("platform")}
      </h2>
    </header>
  );
}
