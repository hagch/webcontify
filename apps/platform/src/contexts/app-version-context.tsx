"use client";

import { createContext, useContext, useMemo, useState } from "react";
import type { AppResponse, AppVersionResponse } from "@/generated/app";

interface AppVersionContextValue {
  selectedVersion: string;
  setSelectedVersion: (v: string) => void;
  isDraft: boolean;
  isActive: boolean;
  isReadOnly: boolean;
  isDataVisible: boolean;
  app: AppResponse;
  versions: AppVersionResponse[];
}

const AppVersionContext = createContext<AppVersionContextValue | null>(null);

function getDefaultSelectedVersion(
  app: AppResponse,
  versions: AppVersionResponse[],
): string {
  if (app.hasDraft) {
    return "draft";
  }
  if (versions.length > 0 && versions[0].id) {
    return versions[0].id;
  }
  return "draft";
}

interface AppVersionProviderProps {
  app: AppResponse;
  versions: AppVersionResponse[];
  children: React.ReactNode;
}

export function AppVersionProvider({
  app,
  versions,
  children,
}: AppVersionProviderProps) {
  const [selectedVersion, setSelectedVersion] = useState<string>(() =>
    getDefaultSelectedVersion(app, versions),
  );

  const activeVersionId = versions.length > 0 ? (versions[0].id ?? null) : null;

  const value = useMemo<AppVersionContextValue>(
    () => ({
      selectedVersion,
      setSelectedVersion,
      isDraft: selectedVersion === "draft",
      isActive: activeVersionId !== null && selectedVersion === activeVersionId,
      isReadOnly: selectedVersion !== "draft",
      isDataVisible:
        selectedVersion === "draft" ||
        (activeVersionId !== null && selectedVersion === activeVersionId),
      app,
      versions,
    }),
    [selectedVersion, activeVersionId, app, versions],
  );

  return (
    <AppVersionContext.Provider value={value}>
      {children}
    </AppVersionContext.Provider>
  );
}

export function useAppVersion(): AppVersionContextValue {
  const context = useContext(AppVersionContext);
  if (context === null) {
    throw new Error("useAppVersion must be used within an AppVersionProvider");
  }
  return context;
}
