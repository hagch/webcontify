"use client";

import { CheckCircle2, Circle, Loader2, XCircle } from "lucide-react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import type { ProvisioningStepInfo } from "@/generated/tenant";

const DEFAULT_STEPS: ProvisioningStepInfo[] = [
  { step: "S3_BUCKET", status: "PENDING" },
  { step: "KEYCLOAK_REALM", status: "PENDING" },
  { step: "OPENFGA_STORE", status: "PENDING" },
  { step: "INITIAL_ADMIN", status: "PENDING" },
];

function deriveDisplayStatus(steps: ProvisioningStepInfo[]): string[] {
  const hasFailed = steps.some((s) => s.status === "FAILED");
  if (hasFailed) {
    return steps.map((s) => s.status);
  }
  let foundFirstPending = false;
  return steps.map((s) => {
    if (s.status === "PENDING" && !foundFirstPending) {
      foundFirstPending = true;
      return "IN_PROGRESS";
    }
    return s.status;
  });
}

interface ProvisioningStatusProps {
  steps: ProvisioningStepInfo[] | null | undefined;
  onRetry: () => void;
  isRetrying: boolean;
}

export function ProvisioningStatus({
  steps,
  onRetry,
  isRetrying,
}: ProvisioningStatusProps) {
  const t = useTranslations("tenants");

  const effectiveSteps = steps ?? DEFAULT_STEPS;
  const completedCount = effectiveSteps.filter(
    (s) => s.status === "COMPLETED"
  ).length;
  const totalCount = effectiveSteps.length;
  const progressPercent =
    totalCount > 0 ? (completedCount / totalCount) * 100 : 0;
  const hasFailed = effectiveSteps.some((s) => s.status === "FAILED");
  const isActive = !hasFailed && completedCount < totalCount;
  const displayStatuses = deriveDisplayStatus(effectiveSteps);

  return (
    <Card data-testid="provisioning-status" className="min-h-[300px]">
      <CardHeader>
        <div className="flex items-center gap-3">
          <CardTitle>{t("provisioning.title")}</CardTitle>
          {isActive && (
            <Loader2
              className="size-4 text-primary animate-spin"
              aria-label={t("provisioning.spinnerLabel")}
            />
          )}
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="space-y-2">
          <Progress
            value={progressPercent}
            aria-valuenow={progressPercent}
            aria-valuemin={0}
            aria-valuemax={100}
          />
          <p className="text-sm text-muted-foreground">
            {t("provisioning.progress", { completed: completedCount, total: totalCount })}
          </p>
        </div>

        <div aria-live="polite" className="space-y-3">
          {effectiveSteps.map((step, index) => (
            <div key={step.step} className="flex items-start gap-3">
              <StepIcon status={displayStatuses[index]} />
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium">
                  {t(`provisioning.stepLabels.${step.step}`)}
                </p>
                <p className="text-xs text-muted-foreground">
                  {t(`provisioning.statusText.${displayStatuses[index]}`)}
                </p>
                {step.status === "FAILED" && step.error && (
                  <p className="text-xs text-destructive mt-0.5">
                    {step.error}
                  </p>
                )}
              </div>
            </div>
          ))}
        </div>

        {hasFailed && (
          <Button
            variant="default"
            onClick={onRetry}
            disabled={isRetrying}
            aria-busy={isRetrying}
          >
            {isRetrying && <Loader2 className="mr-2 size-4 animate-spin" />}
            {t("provisioning.retry")}
          </Button>
        )}
      </CardContent>
    </Card>
  );
}

function StepIcon({ status }: { status: string }) {
  switch (status) {
    case "COMPLETED":
      return (
        <CheckCircle2 className="size-4 text-emerald-500 mt-0.5 shrink-0" />
      );
    case "IN_PROGRESS":
      return (
        <Loader2 className="size-4 text-primary animate-spin mt-0.5 shrink-0" />
      );
    case "FAILED":
      return <XCircle className="size-4 text-destructive mt-0.5 shrink-0" />;
    default:
      return (
        <Circle className="size-4 text-muted-foreground mt-0.5 shrink-0" />
      );
  }
}
