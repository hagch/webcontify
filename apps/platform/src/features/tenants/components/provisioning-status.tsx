import { CheckCircle2, Circle, Loader2, XCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import type { ProvisioningStepInfo } from "@/generated/tenant";

const STEP_LABELS: Record<string, string> = {
  S3_BUCKET: "Speicher",
  KEYCLOAK_REALM: "Authentifizierung",
  OPENFGA_STORE: "Berechtigungssystem",
  INITIAL_ADMIN: "Initialer Admin",
};

const DEFAULT_STEPS: ProvisioningStepInfo[] = [
  { step: "S3_BUCKET", status: "PENDING" },
  { step: "KEYCLOAK_REALM", status: "PENDING" },
  { step: "OPENFGA_STORE", status: "PENDING" },
  { step: "INITIAL_ADMIN", status: "PENDING" },
];

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
  const effectiveSteps = steps ?? DEFAULT_STEPS;
  const completedCount = effectiveSteps.filter(
    (s) => s.status === "COMPLETED"
  ).length;
  const totalCount = effectiveSteps.length;
  const progressPercent =
    totalCount > 0 ? (completedCount / totalCount) * 100 : 0;
  const hasFailed = effectiveSteps.some((s) => s.status === "FAILED");

  return (
    <Card className="min-h-[300px]">
      <CardHeader>
        <CardTitle>Tenant wird eingerichtet</CardTitle>
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
            {completedCount} von {totalCount} Schritten abgeschlossen
          </p>
        </div>

        <div aria-live="polite" className="space-y-3">
          {effectiveSteps.map((step) => (
            <div key={step.step} className="flex items-start gap-3">
              <StepIcon status={step.status} />
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium">
                  {STEP_LABELS[step.step] ?? step.step}
                </p>
                <p className="text-xs text-muted-foreground">
                  <StepStatusText status={step.status} />
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
            Erneut versuchen
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

function StepStatusText({ status }: { status: string }) {
  switch (status) {
    case "COMPLETED":
      return "Abgeschlossen";
    case "IN_PROGRESS":
      return "Wird eingerichtet...";
    case "FAILED":
      return "Fehlgeschlagen";
    default:
      return "Ausstehend";
  }
}
