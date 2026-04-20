"use client";

import { useRef, useEffect } from "react";
import { useTranslations } from "next-intl";
import { applyBrandingToElement, type BrandingValues } from "@/providers/branding-provider";
import { FONT_CSS_VAR_MAP } from "@/lib/fonts";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";

interface BrandingPreviewProps {
  values: Partial<BrandingValues>;
}

export function BrandingPreview({ values }: BrandingPreviewProps) {
  const t = useTranslations("config.branding");
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (containerRef.current) {
      applyBrandingToElement(containerRef.current, values);
    }
  }, [values]);

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("preview")}</CardTitle>
      </CardHeader>
      <CardContent>
        <div ref={containerRef} className="flex flex-col gap-4 p-4 rounded-lg border bg-background">
          {/* Buttons */}
          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              className="inline-flex items-center justify-center rounded-md text-sm font-medium h-9 px-4 py-2 text-white"
              style={{ backgroundColor: "var(--primary)", borderRadius: "var(--radius)" }}
            >
              {t("previewButton")}
            </button>
            <button
              type="button"
              className="inline-flex items-center justify-center rounded-md text-sm font-medium h-9 px-4 py-2 border"
              style={{
                borderColor: values.secondaryColor || "#10B981",
                color: values.secondaryColor || "#10B981",
                borderRadius: "var(--radius)",
              }}
            >
              {t("previewSecondaryButton")}
            </button>
            <span
              className="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold text-white"
              style={{ backgroundColor: "var(--accent)", borderRadius: "9999px" }}
            >
              {t("previewBadge")}
            </span>
          </div>

          {/* Severity Badges */}
          <div className="flex flex-wrap gap-2">
            <span
              className="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold text-white"
              style={{ backgroundColor: "var(--destructive)" }}
            >
              {t("previewDanger")}
            </span>
            <span
              className="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold text-white"
              style={{ backgroundColor: "var(--success)" }}
            >
              {t("previewSuccess")}
            </span>
            <span
              className="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold text-white"
              style={{ backgroundColor: "var(--warning)" }}
            >
              {t("previewWarning")}
            </span>
          </div>

          {/* Sample Card */}
          <div
            className="border p-4 bg-card"
            style={{ borderRadius: "var(--radius)" }}
          >
            <div className="font-semibold text-sm">{t("previewCard")}</div>
            <div className="text-xs text-muted-foreground mt-1">
              {t("previewCardDescription")}
            </div>
            <div className="mt-3 flex items-center gap-2">
              <div className="h-2 flex-1 rounded-full bg-muted overflow-hidden">
                <div
                  className="h-full rounded-full"
                  style={{ backgroundColor: "var(--primary)", width: "65%" }}
                />
              </div>
              <span className="text-xs font-medium" style={{ color: "var(--primary)" }}>
                65%
              </span>
            </div>
          </div>

          {/* Login Mockup */}
          <div
            className="border p-4"
            style={{
              borderRadius: "var(--radius)",
              background: `linear-gradient(135deg, ${values.primaryColor || "#6366F1"}22, ${values.primaryColor || "#6366F1"}08)`,
            }}
          >
            <div className="text-xs font-medium text-muted-foreground mb-2">Login Page</div>
            <div className="flex flex-col gap-2">
              <div
                className="h-7 w-full border bg-background"
                style={{ borderRadius: "var(--radius)" }}
              />
              <div
                className="h-7 w-full border bg-background"
                style={{ borderRadius: "var(--radius)" }}
              />
              <div
                className="h-7 w-full text-white text-xs flex items-center justify-center font-medium"
                style={{
                  backgroundColor: "var(--primary)",
                  borderRadius: "var(--radius)",
                }}
              >
                Sign in
              </div>
            </div>
          </div>

          {/* Font Preview */}
          {values.fontFamily && values.fontFamily !== "system" && (
            <div className="border p-4" style={{ borderRadius: "var(--radius)" }}>
              <div className="text-xs font-medium text-muted-foreground mb-2">
                {t("previewFont")}
              </div>
              <div style={{ fontFamily: FONT_CSS_VAR_MAP[values.fontFamily || "system"] || "inherit" }}>
                <div className="text-lg font-semibold">The quick brown fox</div>
                <div className="text-sm">jumps over the lazy dog. 0123456789</div>
              </div>
            </div>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
