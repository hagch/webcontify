"use client";

import React, { useState, useCallback, useMemo } from "react";
import { useTranslations } from "next-intl";
import { Info, X, ChevronRight, Database } from "lucide-react";
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
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import { useListApps } from "@/generated/app";
import { useListContentDefinitions } from "@/generated/content";
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
const DEFINITION_ACTIONS = ["viewer", "editor", "deleter"] as const;

type DefinitionActionsState = Record<string, boolean>;
type PerAppEntryState = {
  actions: Record<string, boolean>;
  isRestricted: boolean;
  /** defId → action → bool. Only consulted when isRestricted=true. */
  definitions: Record<string, DefinitionActionsState>;
};
type PerAppPermissionState = Record<string, PerAppEntryState>;

function buildPerAppInitialState(
  items: AppPermissionEntry[]
): PerAppPermissionState {
  const state: PerAppPermissionState = {};
  for (const item of items) {
    const actions: Record<string, boolean> = {};
    for (const action of PER_APP_ACTIONS) {
      actions[action] = item.actions.includes(action);
    }
    const definitions: Record<string, DefinitionActionsState> = {};
    for (const def of item.definitionPermissions ?? []) {
      const defActions: DefinitionActionsState = {};
      for (const action of DEFINITION_ACTIONS) {
        defActions[action] = (def.actions as readonly string[]).includes(action);
      }
      definitions[def.definitionId] = defActions;
    }
    state[item.appId] = {
      actions,
      isRestricted: Boolean(item.isRestricted),
      definitions,
    };
  }
  return state;
}

function perAppStatesEqual(
  a: PerAppPermissionState,
  b: PerAppPermissionState
): boolean {
  const allKeys = new Set([...Object.keys(a), ...Object.keys(b)]);
  for (const appId of allKeys) {
    const ea = a[appId];
    const eb = b[appId];
    if (!ea || !eb) return false;
    for (const action of PER_APP_ACTIONS) {
      if ((ea.actions[action] ?? false) !== (eb.actions[action] ?? false)) return false;
    }
    if (ea.isRestricted !== eb.isRestricted) return false;
    const defIds = new Set([
      ...Object.keys(ea.definitions),
      ...Object.keys(eb.definitions),
    ]);
    for (const defId of defIds) {
      for (const action of DEFINITION_ACTIONS) {
        if (
          (ea.definitions[defId]?.[action] ?? false) !==
          (eb.definitions[defId]?.[action] ?? false)
        ) {
          return false;
        }
      }
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
      const prevEntry = prev[appId] ?? {
        actions: { viewer: false, editor: false, admin: false },
        isRestricted: false,
        definitions: {},
      };
      const nextActions = { ...prevEntry.actions, [action]: checked };
      // viewer is implied when any higher action is set
      const otherActions = PER_APP_ACTIONS.filter((a) => a !== "viewer");
      const anyOtherChecked = otherActions.some((a) =>
        a === action ? checked : nextActions[a]
      );
      if (anyOtherChecked) nextActions.viewer = true;
      return {
        ...prev,
        [appId]: { ...prevEntry, actions: nextActions },
      };
    });
  }

  function handleRestrictedToggle(appId: string, includeAll: boolean) {
    // Toggle semantic: "include all definitions automatically".
    // includeAll=true  → cascade mode (isRestricted=false, definitions cleared)
    // includeAll=false → override mode (isRestricted=true, definitions kept)
    setPerAppState((prev) => {
      const prevEntry = prev[appId] ?? {
        actions: { viewer: false, editor: false, admin: false },
        isRestricted: false,
        definitions: {},
      };
      return {
        ...prev,
        [appId]: {
          ...prevEntry,
          isRestricted: !includeAll,
          definitions: includeAll ? {} : prevEntry.definitions,
        },
      };
    });
  }

  function handleDefinitionCheckboxChange(
    appId: string,
    definitionId: string,
    action: string,
    checked: boolean
  ) {
    setPerAppState((prev) => {
      const prevEntry = prev[appId];
      if (!prevEntry) return prev;
      const prevDef = prevEntry.definitions[definitionId] ?? {};
      const nextDef = { ...prevDef, [action]: checked };
      // viewer is implied when any higher action is set on the definition
      const otherActions = DEFINITION_ACTIONS.filter((a) => a !== "viewer");
      const anyOtherChecked = otherActions.some((a) =>
        a === action ? checked : nextDef[a]
      );
      if (anyOtherChecked) nextDef.viewer = true;
      return {
        ...prev,
        [appId]: {
          ...prevEntry,
          definitions: { ...prevEntry.definitions, [definitionId]: nextDef },
        },
      };
    });
  }

  function handlePerAppSave() {
    const appPermissions = Object.entries(perAppState)
      .map(([appId, entry]) => {
        const actions = Object.entries(entry.actions)
          .filter(([, checked]) => checked)
          .map(([a]) => a) as Array<"viewer" | "editor" | "admin">;
        const definitionPermissions = entry.isRestricted
          ? Object.entries(entry.definitions)
              .map(([definitionId, defActions]) => ({
                definitionId,
                actions: Object.entries(defActions)
                  .filter(([, checked]) => checked)
                  .map(([a]) => a) as Array<
                  "viewer" | "editor" | "deleter"
                >,
              }))
              .filter((d) => d.actions.length > 0)
          : [];
        return {
          appId,
          actions,
          restricted: entry.isRestricted,
          definitionPermissions,
        };
      })
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
    return otherActions.some((a) => perAppState[appId]?.actions?.[a]);
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
          onRestrictedToggle={handleRestrictedToggle}
          onDefinitionCheckboxChange={handleDefinitionCheckboxChange}
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
// Content Definition Permissions per App (read-only display)
// ---------------------------------------------------------------------------

const CONTENT_DEFINITION_ACTIONS = ["viewer", "editor", "deleter"] as const;

interface ContentDefinitionRowsProps {
  appId: string;
  appSlug: string;
  appName: string;
  isRestricted: boolean;
  /** App-level granted actions (viewer/editor/admin). Used in cascade mode
   *  to compute which per-definition columns appear implicitly granted. */
  appActions: Record<string, boolean>;
  definitions: Record<string, DefinitionActionsState>;
  onRestrictedToggle: (appId: string, includeAll: boolean) => void;
  onDefinitionCheckboxChange: (
    appId: string,
    definitionId: string,
    action: string,
    checked: boolean
  ) => void;
  canEdit: boolean;
}

/**
 * Maps app-level granted actions to the per-definition actions that are
 * implied via the OpenFGA cascade when the role is in cascade mode.
 *
 * OpenFGA model (see infra/openfga/model.fga):
 *   content_definition.viewer  ← app.viewer  (direct) OR app.editor OR app.admin
 *   content_definition.editor  ← app.editor OR app.admin
 *   content_definition.deleter ← app.editor OR app.admin  (deleter uses editor)
 *   content_definition.admin   ← app.admin
 *   content_definition.creator ← app.admin  (creator uses admin)
 */
function definitionActionImpliedByApp(
  action: string,
  appActions: Record<string, boolean>
): boolean {
  switch (action) {
    case "viewer":
      return Boolean(appActions.viewer || appActions.editor || appActions.admin);
    case "editor":
    case "deleter":
      return Boolean(appActions.editor || appActions.admin);
    default:
      return false;
  }
}

function ContentDefinitionRows({
  appId,
  appSlug,
  isRestricted,
  appActions,
  definitions: definitionState,
  onRestrictedToggle,
  onDefinitionCheckboxChange,
  canEdit,
}: ContentDefinitionRowsProps) {
  const t = useTranslations("roles");
  const [isOpen, setIsOpen] = useState(false);
  const { data } = useListContentDefinitions(appSlug, {
    query: { staleTime: 60 * 1000, enabled: isOpen },
  });
  const definitions = data?.data?.items ?? [];

  // Toggle semantic: checkbox checked = "include all automatically" = cascade
  // mode = !isRestricted. When the grid renders but toggle is ON, checkboxes
  // appear all-checked and disabled to visualise implicit grant.
  const includeAll = !isRestricted;

  return (
    <>
      <TableRow>
        <TableCell colSpan={PER_APP_ACTIONS.length + 2} className="p-0">
          <Collapsible open={isOpen} onOpenChange={setIsOpen}>
            <CollapsibleTrigger className="flex w-full items-center gap-2 px-4 py-2 text-xs text-muted-foreground hover:text-foreground transition-colors cursor-pointer">
              <ChevronRight className={`size-3 transition-transform ${isOpen ? "rotate-90" : ""}`} />
              <Database className="size-3" />
              <span>{t("permissions.contentDefinitions.title")}</span>
              {definitions.length > 0 && (
                <span className="text-muted-foreground">({definitions.length})</span>
              )}
            </CollapsibleTrigger>
            <CollapsibleContent>
              <div className="border-t">
                <div className="flex items-center gap-2 px-4 py-2 text-xs">
                  <Checkbox
                    data-testid={`permission-cascade-toggle-${appId}`}
                    checked={includeAll}
                    disabled={!canEdit}
                    onCheckedChange={(checked) =>
                      onRestrictedToggle(appId, checked === true)
                    }
                  />
                  <span>{t("permissions.contentDefinitions.cascadeToggleLabel")}</span>
                  <span className="text-muted-foreground">
                    — {t("permissions.contentDefinitions.cascadeToggleHelp")}
                  </span>
                </div>
                {definitions.length === 0 ? (
                  <p className="px-4 pb-2 text-xs text-muted-foreground">
                    {t("permissions.contentDefinitions.noDefinitions")}
                  </p>
                ) : (
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead className="w-[40%] pl-10 text-xs">
                          {t("permissions.contentDefinitions.definitionColumn")}
                        </TableHead>
                        {CONTENT_DEFINITION_ACTIONS.map((action) => (
                          <TableHead key={action} className="text-center text-xs w-[12%]">
                            {t(`permissions.actions.${action}` as never)}
                          </TableHead>
                        ))}
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {definitions.map((def) => (
                        <TableRow key={def.id} className="bg-muted/10">
                          <TableCell className="pl-10 text-xs">{def.name}</TableCell>
                          {CONTENT_DEFINITION_ACTIONS.map((action) => {
                            // In cascade mode (includeAll), each checkbox shows
                            // what the OpenFGA cascade actually grants based on
                            // the app-level role (e.g. editor:app implies
                            // viewer+editor+deleter on every definition, but
                            // NOT admin or creator). In override mode, render
                            // the real per-def state and allow edits.
                            const checked = includeAll
                              ? definitionActionImpliedByApp(action, appActions)
                              : definitionState[def.id]?.[action] ?? false;
                            const disabled = !canEdit || includeAll;
                            return (
                              <TableCell key={action}>
                                <div className="flex justify-center">
                                  <Checkbox
                                    data-testid={`permission-content_definition-${def.id}-${action}`}
                                    disabled={disabled}
                                    checked={checked}
                                    onCheckedChange={(v) => {
                                      if (disabled) return;
                                      onDefinitionCheckboxChange(
                                        appId,
                                        def.id,
                                        action,
                                        v === true
                                      );
                                    }}
                                  />
                                </div>
                              </TableCell>
                            );
                          })}
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </div>
            </CollapsibleContent>
          </Collapsible>
        </TableCell>
      </TableRow>
    </>
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
  onRestrictedToggle: (appId: string, includeAll: boolean) => void;
  onDefinitionCheckboxChange: (
    appId: string,
    definitionId: string,
    action: string,
    checked: boolean
  ) => void;
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
  onRestrictedToggle,
  onDefinitionCheckboxChange,
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

  const appSlugMap = useMemo(() => {
    const map = new Map<string, string>();
    for (const item of appPermissionItems) {
      if (item.appSlug) map.set(item.appId, item.appSlug);
    }
    for (const app of allApps) {
      map.set(app.id, app.slug);
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
      [appId]: {
        actions: { viewer: true, editor: false, admin: false },
        isRestricted: false,
        definitions: {},
      },
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
        appSlug: appSlugMap.get(appId),
      }))
      .sort((a, b) => a.appName.localeCompare(b.appName));
  }, [perAppState, appNameMap, appSlugMap]);

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
                  <TableCell key={action}>
                    <div className="flex justify-center">
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
                    </div>
                  </TableCell>
                );
              })}
              {canModify && <TableCell />}
            </TableRow>

            {/* Per-app rows */}
            {appRows.map(({ appId, appName, appSlug }) => (
              <React.Fragment key={appId}>
                <TableRow>
                  <TableCell className="text-sm font-medium">{appName}</TableCell>
                  {PER_APP_ACTIONS.map((action) => {
                    const isViewer = action === "viewer";
                    const viewerImplied = isViewer && isPerAppViewerImplied(appId);
                    const userHasPermission = can("app_management", action);
                    const isDisabled =
                      isSystemRole || !canEditPermissions || viewerImplied || !userHasPermission;
                    const isChecked = perAppState[appId]?.actions?.[action] ?? false;

                    return (
                      <TableCell key={action}>
                        <div className="flex justify-center">
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
                        </div>
                      </TableCell>
                    );
                  })}
                  {canModify && (
                    <TableCell>
                      <div className="flex justify-center">
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          className="hover:bg-destructive/10 hover:text-destructive"
                          onClick={() => handleRemoveApp(appId)}
                          data-testid={`remove-app-permission-${appId}`}
                        >
                          <X className="size-4" />
                        </Button>
                      </div>
                    </TableCell>
                  )}
                </TableRow>
                {appSlug && (
                  <ContentDefinitionRows
                    appId={appId}
                    appSlug={appSlug}
                    appName={appName}
                    isRestricted={perAppState[appId]?.isRestricted ?? false}
                    appActions={perAppState[appId]?.actions ?? {}}
                    definitions={perAppState[appId]?.definitions ?? {}}
                    onRestrictedToggle={onRestrictedToggle}
                    onDefinitionCheckboxChange={onDefinitionCheckboxChange}
                    canEdit={canModify}
                  />
                )}
              </React.Fragment>
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
                          <Button
                            key={app.id}
                            variant="ghost"
                            className="w-full justify-start rounded-none"
                            onMouseDown={(e) => e.preventDefault()}
                            onClick={() => handleAddApp(app.id)}
                            data-testid={`add-app-suggestion-${app.id}`}
                          >
                            {app.name}
                          </Button>
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
