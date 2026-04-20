"use client";

import { useTranslations } from "next-intl";

interface PatchOperation {
  op: string;
  path: string;
  value?: unknown;
  fromValue?: unknown;
}

interface RevisionDiffViewProps {
  patch: unknown;
}

function formatValue(value: unknown): string {
  if (value === null || value === undefined) return "null";
  if (typeof value === "string") return `"${value}"`;
  if (typeof value === "object") return JSON.stringify(value, null, 2);
  return String(value);
}

function formatPath(path: string): string {
  return path.replace(/\//g, " > ").replace(/^> /, "");
}

function getOperationLabel(
  op: string,
  t: ReturnType<typeof useTranslations>,
): string {
  switch (op) {
    case "add":
      return t("revisions.diffLabels.added");
    case "remove":
      return t("revisions.diffLabels.removed");
    case "replace":
      return t("revisions.diffLabels.replaced");
    default:
      return op;
  }
}

function getOperationColorClasses(op: string): {
  badge: string;
  line: string;
} {
  switch (op) {
    case "add":
      return {
        badge: "bg-emerald-900/50 text-emerald-400 border-emerald-700",
        line: "border-l-emerald-500",
      };
    case "remove":
      return {
        badge: "bg-red-900/50 text-red-400 border-red-700",
        line: "border-l-red-500",
      };
    case "replace":
      return {
        badge: "bg-amber-900/50 text-amber-400 border-amber-700",
        line: "border-l-amber-500",
      };
    default:
      return {
        badge: "bg-muted text-muted-foreground border-border",
        line: "border-l-border",
      };
  }
}

function parsePatch(patch: unknown): PatchOperation[] {
  if (Array.isArray(patch)) {
    return patch as PatchOperation[];
  }
  return [];
}

export function RevisionDiffView({ patch }: RevisionDiffViewProps) {
  const t = useTranslations("apps");
  const operations = parsePatch(patch);

  if (operations.length === 0) {
    return null;
  }

  return (
    <div className="rounded-md border bg-stone-950 p-3 font-mono text-xs">
      <div className="space-y-2">
        {operations.map((op, index) => {
          const colors = getOperationColorClasses(op.op);
          return (
            <div
              key={index}
              className={`border-l-2 pl-3 ${colors.line}`}
            >
              <div className="flex items-center gap-2">
                <span
                  className={`inline-flex items-center rounded border px-1.5 py-0.5 text-[10px] font-semibold uppercase ${colors.badge}`}
                >
                  {getOperationLabel(op.op, t)}
                </span>
                <span className="text-stone-400">{formatPath(op.path)}</span>
              </div>
              <div className="mt-1 pl-0.5">
                {op.op === "replace" && (
                  <>
                    <div className="text-red-400">
                      <span className="select-none text-red-600">- </span>
                      {formatValue(op.fromValue)}
                    </div>
                    <div className="text-emerald-400">
                      <span className="select-none text-emerald-600">+ </span>
                      {formatValue(op.value)}
                    </div>
                  </>
                )}
                {op.op === "add" && (
                  <div className="text-emerald-400">
                    <span className="select-none text-emerald-600">+ </span>
                    {formatValue(op.value)}
                  </div>
                )}
                {op.op === "remove" && (
                  <div className="text-red-400">
                    <span className="select-none text-red-600">- </span>
                    {formatValue(op.fromValue ?? op.value)}
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
