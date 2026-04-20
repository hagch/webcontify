"use client";

import { useTranslations } from "next-intl";
import { AlertCircle, AlertTriangle } from "lucide-react";
import { useListAppProblems } from "@/generated/app";

interface AppProblemsPanelProps {
  appId: string;
}

export function AppProblemsPanel({ appId }: AppProblemsPanelProps) {
  const t = useTranslations("apps");
  const { data } = useListAppProblems(appId);

  const rawData = data?.data;
  if (!rawData || !("items" in rawData)) return null;

  const { items, errorCount, warningCount } = rawData;

  if (errorCount === 0 && warningCount === 0) return null;

  return (
    <div
      data-testid="app-problems-panel"
      className="rounded-lg border border-destructive/50 bg-destructive/5 p-4"
    >
      <div className="mb-3 flex items-center gap-2">
        <AlertCircle className="size-4 text-destructive" />
        <h3 className="text-sm font-medium">
          {t("problems.title")} ({errorCount + warningCount})
        </h3>
        {errorCount > 0 && (
          <span className="text-xs text-destructive font-medium">
            {errorCount} errors
          </span>
        )}
        {warningCount > 0 && (
          <span className="text-xs text-amber-600 dark:text-amber-400 font-medium">
            {warningCount} warnings
          </span>
        )}
      </div>
      <ul className="space-y-1.5">
        {items.map((problem, index) => (
          <li key={index} className="flex items-start gap-2 text-sm">
            {problem.severity === "ERROR" ? (
              <AlertCircle className="mt-0.5 size-3.5 shrink-0 text-destructive" />
            ) : (
              <AlertTriangle className="mt-0.5 size-3.5 shrink-0 text-amber-600 dark:text-amber-400" />
            )}
            <span className="text-muted-foreground">{problem.message}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
