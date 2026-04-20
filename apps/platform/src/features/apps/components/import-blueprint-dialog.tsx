"use client";

import { useState } from "react";
import { Loader2, AlertTriangle, CheckCircle2, ArrowRight } from "lucide-react";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import {
  useAnalyzeImport,
  useExecuteImport,
  getListAppsQueryKey,
  type AppBlueprintResponse,
  type ImportPreviewResponse,
} from "@/generated/app";

interface ImportBlueprintDialogProps {
  children: React.ReactNode;
}

export function ImportBlueprintDialog({ children }: ImportBlueprintDialogProps) {
  const [open, setOpen] = useState(false);
  const [step, setStep] = useState<1 | 2>(1);
  const [jsonInput, setJsonInput] = useState("");
  const [jsonError, setJsonError] = useState<string | null>(null);
  const [blueprint, setBlueprint] = useState<AppBlueprintResponse | null>(null);
  const [preview, setPreview] = useState<ImportPreviewResponse | null>(null);

  const t = useTranslations("apps");
  const queryClient = useQueryClient();
  const analyzeMutation = useAnalyzeImport();
  const executeMutation = useExecuteImport();

  function resetState() {
    setStep(1);
    setJsonInput("");
    setJsonError(null);
    setBlueprint(null);
    setPreview(null);
  }

  function handleOpenChange(value: boolean) {
    setOpen(value);
    if (!value) {
      resetState();
    }
  }

  async function handleAnalyze() {
    setJsonError(null);

    let parsed: AppBlueprintResponse;
    try {
      parsed = JSON.parse(jsonInput) as AppBlueprintResponse;
    } catch {
      setJsonError(t("importExport.importDialog.invalidJson"));
      return;
    }

    try {
      const response = await analyzeMutation.mutateAsync({ data: parsed });
      setBlueprint(parsed);
      setPreview(response.data);
      setStep(2);
    } catch {
      toast.error(t("importExport.importDialog.invalidJson"));
    }
  }

  async function handleExecute() {
    if (!blueprint) return;

    const slugOverrides: Record<string, string> = {};
    if (preview?.apps) {
      for (const app of preview.apps) {
        if (app.hasSlugCollision && app.originalSlug && app.suggestedSlug) {
          slugOverrides[app.originalSlug] = app.suggestedSlug;
        }
      }
    }

    try {
      await executeMutation.mutateAsync({
        data: {
          blueprint,
          slugOverrides: Object.keys(slugOverrides).length > 0 ? slugOverrides : undefined,
        },
      });
      toast.success(t("importExport.importDialog.successToast"));
      await queryClient.invalidateQueries({ queryKey: getListAppsQueryKey() });
      handleOpenChange(false);
    } catch {
      toast.error(t("importExport.importDialog.invalidJson"));
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger render={children as React.ReactElement} />
      <DialogContent data-testid="import-blueprint-dialog" className="sm:max-w-lg max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{t("importExport.importDialog.title")}</DialogTitle>
          <DialogDescription>
            {t("importExport.importDialog.description")}
          </DialogDescription>
        </DialogHeader>

        {step === 1 && (
          <div className="space-y-4">
            <Textarea
              data-testid="import-json-input"
              placeholder={t("importExport.importDialog.textareaPlaceholder")}
              value={jsonInput}
              onChange={(e) => {
                setJsonInput(e.target.value);
                setJsonError(null);
              }}
              rows={10}
              className="max-h-[40vh] resize-none font-mono text-sm"
            />
            {jsonError && (
              <p className="text-sm text-destructive">{jsonError}</p>
            )}
            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => handleOpenChange(false)}
              >
                {t("importExport.importDialog.cancel")}
              </Button>
              <Button
                data-testid="import-analyze-button"
                type="button"
                onClick={handleAnalyze}
                disabled={!jsonInput.trim() || analyzeMutation.isPending}
                aria-busy={analyzeMutation.isPending}
              >
                {analyzeMutation.isPending && (
                  <Loader2 className="mr-2 size-4 animate-spin" />
                )}
                {analyzeMutation.isPending
                  ? t("importExport.importDialog.analyzing")
                  : t("importExport.importDialog.analyze")}
              </Button>
            </DialogFooter>
          </div>
        )}

        {step === 2 && preview && (
          <div className="space-y-4">
            <h4 className="text-sm font-medium">{t("importExport.preview.appsToImport")}</h4>
            <div className="space-y-2">
              {preview.apps?.map((app) => (
                <div
                  key={app.originalSlug}
                  className="flex items-center justify-between rounded-lg border p-3"
                >
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-medium">{app.originalName}</span>
                    <span className="text-xs text-muted-foreground">({app.originalSlug})</span>
                  </div>
                  {app.hasSlugCollision ? (
                    <div className="flex items-center gap-2">
                      <Badge variant="secondary" className="bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300">
                        <AlertTriangle className="mr-1 size-3" />
                        {t("importExport.preview.slugCollision")}
                      </Badge>
                      <ArrowRight className="size-3 text-muted-foreground" />
                      <span className="text-xs font-mono">{app.suggestedSlug}</span>
                    </div>
                  ) : (
                    <Badge variant="secondary" className="bg-emerald-100 text-emerald-700 dark:bg-emerald-900 dark:text-emerald-300">
                      <CheckCircle2 className="mr-1 size-3" />
                      {t("importExport.preview.noCollision")}
                    </Badge>
                  )}
                </div>
              ))}
            </div>

            {preview.canImport ? (
              <p className="text-sm text-emerald-600 dark:text-emerald-400">
                {t("importExport.preview.canImport")}
              </p>
            ) : (
              <p className="text-sm text-destructive">
                {t("importExport.preview.cannotImport")}
              </p>
            )}

            <DialogFooter>
              <Button
                type="button"
                variant="outline"
                onClick={() => {
                  setStep(1);
                  setPreview(null);
                  setBlueprint(null);
                }}
              >
                {t("importExport.importDialog.cancel")}
              </Button>
              <Button
                data-testid="import-execute-button"
                type="button"
                onClick={handleExecute}
                disabled={!preview.canImport || executeMutation.isPending}
                aria-busy={executeMutation.isPending}
              >
                {executeMutation.isPending && (
                  <Loader2 className="mr-2 size-4 animate-spin" />
                )}
                {executeMutation.isPending
                  ? t("importExport.importDialog.importing")
                  : t("importExport.importDialog.execute")}
              </Button>
            </DialogFooter>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
