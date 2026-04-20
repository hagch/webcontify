"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { ChevronDown, Undo2, FileText } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import {
  useListAppRevisions,
  type AppRevisionResponse,
} from "@/generated/app";
import { RevisionDiffView } from "./revision-diff-view";
import { RevertRevisionDialog } from "./revert-revision-dialog";

function formatDate(dateString: string): string {
  return new Intl.DateTimeFormat("de-DE", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(dateString));
}

function RevisionRow({
  revision,
  appId,
  index,
  total,
}: {
  revision: AppRevisionResponse;
  appId: string;
  index: number;
  total: number;
}) {
  const t = useTranslations("apps");
  const [isOpen, setIsOpen] = useState(false);
  const revisionNumber = total - index;

  return (
    <Collapsible open={isOpen} onOpenChange={setIsOpen}>
      <CollapsibleTrigger className="flex w-full items-center justify-between rounded-lg border p-4 hover:bg-muted/50 transition-colors cursor-pointer">
        <div className="flex items-center gap-3">
          <Badge
            variant="secondary"
            className="bg-stone-100 text-stone-700 dark:bg-stone-800 dark:text-stone-300"
          >
            #{revisionNumber}
          </Badge>
          {revision.summary && (
            <span className="text-sm text-foreground">{revision.summary}</span>
          )}
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            {revision.createdAt && (
              <span>{formatDate(revision.createdAt)}</span>
            )}
            {revision.createdByUser && (
              <span>
                — {revision.createdByUser.firstName} {revision.createdByUser.lastName}
              </span>
            )}
          </div>
        </div>
        <ChevronDown
          className={`size-4 text-muted-foreground transition-transform ${isOpen ? "rotate-180" : ""}`}
        />
      </CollapsibleTrigger>
      <CollapsibleContent className="border-x border-b rounded-b-lg px-4 pb-4 pt-2">
        <div className="space-y-3">
          {revision.patch && <RevisionDiffView patch={revision.patch} />}
          <div className="flex items-center justify-end">
            <RevertRevisionDialog
              appId={appId}
              revisionId={revision.id ?? ""}
            >
              <Button variant="outline" size="sm">
                <Undo2 className="mr-2 size-4" />
                {t("revisions.revert")}
              </Button>
            </RevertRevisionDialog>
          </div>
        </div>
      </CollapsibleContent>
    </Collapsible>
  );
}

interface AppRevisionListProps {
  appId: string;
}

export function AppRevisionList({ appId }: AppRevisionListProps) {
  const t = useTranslations("apps");
  const { data, isLoading } = useListAppRevisions(appId);
  const revisions = data?.data?.items ?? [];

  if (isLoading) {
    return (
      <div data-testid="app-revision-list" className="space-y-3">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-16 w-full rounded-lg" />
        ))}
      </div>
    );
  }

  return (
    <div data-testid="app-revision-list" className="space-y-4">
      {revisions.length === 0 ? (
        <div className="flex flex-col items-center justify-center rounded-lg border border-dashed p-8 text-center">
          <FileText className="size-10 text-muted-foreground/50" />
          <p className="mt-3 text-sm text-muted-foreground">
            {t("revisions.noRevisions")}
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {revisions.map((revision, index) => (
            <RevisionRow
              key={revision.id}
              revision={revision}
              appId={appId}
              index={index}
              total={revisions.length}
            />
          ))}
        </div>
      )}
    </div>
  );
}
