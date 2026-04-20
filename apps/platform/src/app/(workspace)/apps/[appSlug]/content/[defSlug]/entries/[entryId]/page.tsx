"use client";

import { use } from "react";
import { EntryDetailPage } from "@/features/content-entries/components/entry-detail-page";

export default function EntryDetailRoute({
  params,
}: {
  params: Promise<{ appSlug: string; defSlug: string; entryId: string }>;
}) {
  const { appSlug, defSlug, entryId } = use(params);

  return (
    <EntryDetailPage
      appSlug={appSlug}
      defSlug={defSlug}
      entryId={entryId}
    />
  );
}
