"use client";

import Link from "next/link";
import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export default function WorkspaceNotFoundPage() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-stone-50">
      <div className="flex flex-col items-center gap-6 text-center">
        <div className="flex flex-col gap-2">
          <h1 className="text-2xl font-semibold text-stone-900">
            Workspace nicht gefunden
          </h1>
          <p className="text-stone-500">
            Dieser Workspace existiert nicht oder wurde entfernt.
          </p>
        </div>
        <Link
          href="http://platform.localhost"
          className={cn(buttonVariants({ variant: "default" }))}
        >
          Zur Platform
        </Link>
      </div>
    </div>
  );
}
