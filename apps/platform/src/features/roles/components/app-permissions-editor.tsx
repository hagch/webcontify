"use client";

import { useState, useCallback, useMemo } from "react";
import { useTranslations } from "next-intl";
import { Info, X } from "lucide-react";
import { toast } from "sonner";
import { useQueryClient } from "@tanstack/react-query";
import { Checkbox } from "@/components/ui/checkbox";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useListApps } from "@/generated/app";
import {
  useSetRolePermissions,
  useGetRoleAppPermissions,
  useSetRoleAppPermissions,
  getGetRoleQueryKey,
  getGetRoleAppPermissionsQueryKey,
} from "@/generated/identity";
import type {
  RolePermissionPreviewResponse,
  AppPermissionEntry,
} from "@/generated/identity";
import { usePermissions } from "@/hooks/use-permissions";

const APP_RESOURCE_ACTIONS: Record<string, string[]> = {
  app_management: ["viewer", "editor", "admin"],
};

type PermissionState = Record<string, Record<string, boolean>>;

function buildInitialState(
  permissions: RolePermissionPreviewResponse[]
): PermissionState {
  const state: PermissionState = {};
  for (const resourceType of Object.keys(APP_RESOURCE_ACTIONS)) {
    state[resourceType] = {};
    const existing = permissions.find((p) => p.resourceType === resourceType);
    for (const action of APP_RESOURCE_ACTIONS[resourceType]) {
      state[resourceType][action] = existing?.actions.includes(action) ?? false;
    }
  }
  return state;
}

function statesEqual(a: PermissionState, b: PermissionState): boolean {
  for (const resource of Object.keys(a)) {
    for (const action of Object.keys(a[resource])) {
      if (a[resource][action] !== b[resource]?.[action]) return false;
    }
  }
  return true;
}

const PER_APP_ACTIONS = ["viewer", "editor", "admin"] as const;

type PerAppPermissionState = Record<string, Record<string, boolean>>;

function buildPerAppInitialState(
  items: AppPermissionEntry[]
): PerAppPermissionState {
  const state: PerAppPermissionState = {};
  for (const item of items) {
    state[item.appId] = {};
    for (const action of PER_APP_ACTIONS) {
      state[item.appId][action] = item.actions.includes(action);
    }
  }
  return state;
}

function perAppStatesEqual(
  a: PerAppPermissionState,
  b: PerAppPermissionState
): boolean {
  const allKeys = new Set([...Object.keys(a), ...Object.keys(b)]);
  for (const appId of allKeys) {
    for (const action of PER_APP_ACTIONS) {
      if ((a[appId]?.[action] ?? false) !== (b[appId]?.[action] ?? false))
        return false;
    }
  }
  return true;
}

interface AppPermissionsEditorProps {
  roleId: string;
  permissions: RolePermissionPreviewResponse[];
  allPermissions: RolePermissionPreviewResponse[];
  isSystemRole: boolean;
}

export function AppPermissionsEditor({
  roleId,
  permissions,
  allPermissions,
  isSystemRole,
}: AppPermissionsEditorProps) {
  const t = useTranslations("roles");
  const queryClient = useQueryClient();
  const { can } = usePermissions();
  const canEditPermissions = can("role_management", "editor");
  const { mutate, isPending } = useSetRolePermissions();

  const [state, setState] = useState<PermissionState>(() =>
    buildInitialState(permissions)
  );
  const [savedState, setSavedState] = useState<PermissionState>(() =>
    buildInitialState(permissions)
  );

  const isDirty = !statesEqual(state, savedState);

  // Per-app permissions
  const { data: appPermissionsData } = useGetRoleAppPermissions(roleId);
  const appPermissionItems = appPermissionsData?.data?.items ?? [];
  const { mutate: mutateAppPermissions, isPending: isAppPending } =
    useSetRoleAppPermissions();

  const [perAppState, setPerAppState] = useState<PerAppPermissionState>({});
  const [perAppSavedState, setPerAppSavedState] =
    useState<PerAppPermissionState>({});
  const [perAppInitialized, setPerAppInitialized] = useState(false);

  // Initialize per-app state when data arrives
  if (appPermissionItems.length > 0 && !perAppInitialized) {
    const initial = buildPerAppInitialState(appPermissionItems);
    setPerAppState(initial);
    setPerAppSavedState(initial);
    setPerAppInitialized(true);
  }

  const isPerAppDirty = !perAppStatesEqual(perAppState, perAppSavedState);

  const handleCancel = useCallback(() => {
    setState(savedState);
  }, [savedState]);

  function handleCheckboxChange(
    resourceType: string,
    action: string,
    checked: boolean
  ) {
    setState((prev) => {
      const updated = {
        ...prev,
        [resourceType]: {
          ...prev[resourceType],
          [action]: checked,
        },
      };
      // viewer is implied when any higher action is set
      const otherActions = APP_RESOURCE_ACTIONS[resourceType].filter(
        (a) => a !== "viewer"
      );
      const anyOtherChecked = otherActions.some(
        (a) => (a === action ? checked : updated[resourceType][a])
      );
      if (anyOtherChecked) {
        updated[resourceType]["viewer"] = true;
      }
      return updated;
    });
  }

  const handlePerAppCancel = useCallback(() => {
    setPerAppState(perAppSavedState);
  }, [perAppSavedState]);

  function handlePerAppCheckboxChange(
    appId: string,
    action: string,
    checked: boolean
  ) {
    setPerAppState((prev) => {
      const updated = {
        ...prev,
        [appId]: {
          ...prev[appId],
          [action]: checked,
        },
      };
      // viewer is implied when any higher action is set
      const otherActions = PER_APP_ACTIONS.filter((a) => a !== "viewer");
      const anyOtherChecked = otherActions.some((a) =>
        a === action ? checked : updated[appId]?.[a]
      );
      if (anyOtherChecked) {
        updated[appId] = { ...updated[appId], viewer: true };
      }
      return updated;
    });
  }

  function handlePerAppSave() {
    const appPermissions = Object.entries(perAppState)
      .map(([appId, actions]) => ({
        appId,
        actions: Object.entries(actions)
          .filter(([, checked]) => checked)
          .map(([action]) => action) as Array<"viewer" | "editor" | "admin">,
      }))
      .filter((p) => p.actions.length > 0);

    mutateAppPermissions(
      { roleId, data: { appPermissions } },
      {
        onSuccess: () => {
          toast.success(t("permissions.appManagement.perAppSaveSuccess"));
          setPerAppSavedState(perAppState);
          queryClient.invalidateQueries({
            queryKey: getGetRoleAppPermissionsQueryKey(roleId),
          });
          queryClient.invalidateQueries({
            queryKey: getGetRoleQueryKey(roleId),
          });
        },
        onError: () => {
          toast.error(t("permissions.appManagement.perAppSaveError"));
        },
      }
    );
  }

  function isPerAppViewerImplied(appId: string): boolean {
    const otherActions = PER_APP_ACTIONS.filter((a) => a !== "viewer");
    return otherActions.some((a) => perAppState[appId]?.[a]);
  }

  function handleSave() {
    // Merge app permissions with existing non-app permissions
    const appPermissions = Object.entries(state)
      .map(([resourceType, actions]) => ({
        resourceType,
        actions: Object.entries(actions)
          .filter(([, checked]) => checked)
          .map(([action]) => action),
      }))
      .filter((p) => p.actions.length > 0);

    const nonAppPermissions = allPermissions
      .filter((p) => !APP_RESOURCE_ACTIONS[p.resourceType])
      .map((p) => ({
        resourceType: p.resourceType,
        actions: [...p.actions],
      }));

    const mergedPermissions = [...nonAppPermissions, ...appPermissions];

    mutate(
      { roleId, data: { permissions: mergedPermissions } },
      {
        onSuccess: () => {
          toast.success(t("permissions.saveSuccess"));
          setSavedState(state);
          queryClient.invalidateQueries({
            queryKey: getGetRoleQueryKey(roleId),
          });
        },
        onError: () => {
          toast.error(t("permissions.saveError"));
        },
      }
    );
  }

  function getActionLabel(action: string): string {
    const key = `permissions.actions.${action}` as const;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    return t(key as any);
  }

  function isViewerImplied(resourceType: string): boolean {
    const otherActions = APP_RESOURCE_ACTIONS[resourceType].filter(
      (a) => a !== "viewer"
    );
    return otherActions.some((a) => state[resourceType]?.[a]);
  }

  return (
      <div className="space-y-4">
        {isSystemRole ? (
          <div
            data-testid="system-role-permissions-notice"
            className="flex items-center gap-2 rounded-lg border border-stone-200 bg-stone-50 px-4 py-3 text-sm text-stone-600 dark:border-stone-800 dark:bg-stone-900/30 dark:text-stone-400"
          >
            <Info className="size-4 shrink-0" />
            <span>{t("permissions.systemRoleNotice")}</span>
          </div>
        ) : null}

        <AppPermissionsTable
          globalState={state}
          onGlobalCheckboxChange={handleCheckboxChange}
          isViewerImplied={isViewerImplied}
          isGlobalDirty={isDirty}
          onGlobalCancel={handleCancel}
          onGlobalSave={handleSave}
          isGlobalPending={isPending}
          appPermissionItems={appPermissionItems}
          perAppState={perAppState}
          isSystemRole={isSystemRole}
          canEditPermissions={canEditPermissions}
          can={can}
          isPerAppViewerImplied={isPerAppViewerImplied}
          onPerAppCheckboxChange={handlePerAppCheckboxChange}
          onPerAppStateChange={setPerAppState}
          isPerAppDirty={isPerAppDirty}
          onPerAppCancel={handlePerAppCancel}
          onPerAppSave={handlePerAppSave}
          isAppPending={isAppPending}
          getActionLabel={getActionLabel}
        />
      </div>
  );
}

// ---------------------------------------------------------------------------
// Unified App Permissions Table (Global + Per-App)
// ---------------------------------------------------------------------------

interface AppPermissionsTableProps {
  globalState: PermissionState;
  onGlobalCheckboxChange: (resourceType: string, action: string, checked: boolean) => void;
  isViewerImplied: (resourceType: string) => boolean;
  isGlobalDirty: boolean;
  onGlobalCancel: () => void;
  onGlobalSave: () => void;
  isGlobalPending: boolean;
  appPermissionItems: AppPermissionEntry[];
  perAppState: PerAppPermissionState;
  isSystemRole: boolean;
  canEditPermissions: boolean;
  can: (resource: string, action: string) => boolean;
  isPerAppViewerImplied: (appId: string) => boolean;
  onPerAppCheckboxChange: (appId: string, action: string, checked: boolean) => void;
  onPerAppStateChange: React.Dispatch<React.SetStateAction<PerAppPermissionState>>;
  isPerAppDirty: boolean;
  onPerAppCancel: () => void;
  onPerAppSave: () => void;
  isAppPending: boolean;
  getActionLabel: (action: string) => string;
}

function AppPermissionsTable({
  globalState,
  onGlobalCheckboxChange,
  isViewerImplied,
  isGlobalDirty,
  onGlobalCancel,
  onGlobalSave,
  isGlobalPending,
  appPermissionItems,
  perAppState,
  isSystemRole,
  canEditPermissions,
  can,
  isPerAppViewerImplied,
  onPerAppCheckboxChange,
  onPerAppStateChange,
  isPerAppDirty,
  onPerAppCancel,
  onPerAppSave,
  isAppPending,
  getActionLabel,
}: AppPermissionsTableProps) {
  const t = useTranslations("roles");
  const [searchQuery, setSearchQuery] = useState("");
  const [showSuggestions, setShowSuggestions] = useState(false);

  const { data: appsData } = useListApps(
    { page: 0, size: 100 },
    { query: { staleTime: 60 * 1000 } }
  );

  const allApps = appsData?.data?.items ?? [];
  const canModify = !isSystemRole && canEditPermissions;
  const isDirty = isGlobalDirty || isPerAppDirty;
  const isSaving = isGlobalPending || isAppPending;

  const assignedAppIds = useMemo(
    () => new Set(Object.keys(perAppState)),
    [perAppState]
  );

  const appNameMap = useMemo(() => {
    const map = new Map<string, string>();
    for (const item of appPermissionItems) {
      map.set(item.appId, item.appName);
    }
    for (const app of allApps) {
      map.set(app.id, app.name);
    }
    return map;
  }, [appPermissionItems, allApps]);

  const suggestions = useMemo(() => {
    if (!searchQuery.trim()) return [];
    const query = searchQuery.toLowerCase();
    return allApps.filter(
      (app) =>
        !assignedAppIds.has(app.id) &&
        app.name.toLowerCase().includes(query)
    );
  }, [allApps, assignedAppIds, searchQuery]);

  function handleAddApp(appId: string) {
    onPerAppStateChange((prev) => ({
      ...prev,
      [appId]: { viewer: true, editor: false, admin: false },
    }));
    setSearchQuery("");
    setShowSuggestions(false);
  }

  function handleRemoveApp(appId: string) {
    onPerAppStateChange((prev) => {
      const next = { ...prev };
      delete next[appId];
      return next;
    });
  }

  function handleSaveAll() {
    if (isGlobalDirty) onGlobalSave();
    if (isPerAppDirty) onPerAppSave();
  }

  function handleCancelAll() {
    if (isGlobalDirty) onGlobalCancel();
    if (isPerAppDirty) onPerAppCancel();
  }

  const appRows = useMemo(() => {
    return Object.keys(perAppState)
      .map((appId) => ({
        appId,
        appName: appNameMap.get(appId) ?? appId,
      }))
      .sort((a, b) => a.appName.localeCompare(b.appName));
  }, [perAppState, appNameMap]);

  return (
    <div className="space-y-3">
      <div className="rounded-lg border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-[40%]">{t("permissions.appManagement.appColumn")}</TableHead>
              {PER_APP_ACTIONS.map((action) => (
                <TableHead key={action} className="text-center w-[15%]">
                  {getActionLabel(action)}
                </TableHead>
              ))}
              {canModify && <TableHead className="w-[3rem]" />}
            </TableRow>
          </TableHeader>
          <TableBody>
            {/* Global row */}
            <TableRow className="bg-muted/30">
              <TableCell className="text-sm font-medium">
                <div>
                  {t("permissions.appManagement.global")}
                  <p className="text-xs font-normal text-muted-foreground">
                    {t("permissions.appManagement.globalDescription")}
                  </p>
                </div>
              </TableCell>
              {PER_APP_ACTIONS.map((action) => {
                const isViewer = action === "viewer";
                const viewerImplied = isViewer && isViewerImplied("app_management");
                const userHasPermission = can("app_management", action);
                const isDisabled =
                  isSystemRole || !canEditPermissions || viewerImplied || !userHasPermission;
                const isChecked = globalState["app_management"]?.[action] ?? false;

                return (
                  <TableCell key={action} className="text-center">
                    <Checkbox
                      data-testid={`permission-app_management-${action}`}
                      checked={isChecked}
                      disabled={isDisabled}
                      onCheckedChange={(checked) => {
                        if (!isDisabled) {
                          onGlobalCheckboxChange("app_management", action, checked === true);
                        }
                      }}
                    />
                  </TableCell>
                );
              })}
              {canModify && <TableCell />}
            </TableRow>

            {/* Per-app rows */}
            {appRows.map(({ appId, appName }) => (
              <TableRow key={appId}>
                <TableCell className="text-sm font-medium">{appName}</TableCell>
                {PER_APP_ACTIONS.map((action) => {
                  const isViewer = action === "viewer";
                  const viewerImplied = isViewer && isPerAppViewerImplied(appId);
                  const userHasPermission = can("app_management", action);
                  const isDisabled =
                    isSystemRole || !canEditPermissions || viewerImplied || !userHasPermission;
                  const isChecked = perAppState[appId]?.[action] ?? false;

                  return (
                    <TableCell key={action} className="text-center">
                      <Checkbox
                        data-testid={`permission-app-${appId}-${action}`}
                        checked={isChecked}
                        disabled={isDisabled}
                        onCheckedChange={(checked) => {
                          if (!isDisabled) {
                            onPerAppCheckboxChange(appId, action, checked === true);
                          }
                        }}
                      />
                    </TableCell>
                  );
                })}
                {canModify && (
                  <TableCell className="text-center">
                    <button
                      className="text-muted-foreground hover:text-destructive transition-colors"
                      onClick={() => handleRemoveApp(appId)}
                      data-testid={`remove-app-permission-${appId}`}
                    >
                      <X className="size-4" />
                    </button>
                  </TableCell>
                )}
              </TableRow>
            ))}

            {/* Add app row */}
            {canModify && (
              <TableRow>
                <TableCell colSpan={PER_APP_ACTIONS.length + 2} className="p-0">
                  <div className="relative">
                    <Input
                      placeholder={t("permissions.appManagement.searchApp")}
                      value={searchQuery}
                      onChange={(e) => {
                        setSearchQuery(e.target.value);
                        setShowSuggestions(true);
                      }}
                      onFocus={() => searchQuery.trim() && setShowSuggestions(true)}
                      onBlur={() => setTimeout(() => setShowSuggestions(false), 200)}
                      className="border-0 shadow-none focus-visible:ring-0 rounded-none"
                      data-testid="per-app-search-input"
                    />
                    {showSuggestions && suggestions.length > 0 && (
                      <div className="absolute left-0 right-0 top-full z-10 max-h-48 overflow-y-auto rounded-b-lg border border-t-0 bg-popover shadow-md">
                        {suggestions.map((app) => (
                          <button
                            key={app.id}
                            className="flex w-full items-center px-3 py-2 text-sm hover:bg-accent hover:text-accent-foreground transition-colors"
                            onMouseDown={(e) => e.preventDefault()}
                            onClick={() => handleAddApp(app.id)}
                            data-testid={`add-app-suggestion-${app.id}`}
                          >
                            {app.name}
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      {canModify && isDirty && (
        <div className="flex items-center gap-2">
          <Button
            data-testid="cancel-app-permissions-button"
            variant="outline"
            onClick={handleCancelAll}
            disabled={isSaving}
          >
            {t("permissions.cancel")}
          </Button>
          <Button
            data-testid="save-app-permissions-button"
            onClick={handleSaveAll}
            disabled={isSaving}
          >
            {t("permissions.save")}
          </Button>
        </div>
      )}
    </div>
  );
}
