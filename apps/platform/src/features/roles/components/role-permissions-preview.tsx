"use client";

import { Info } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { RolePermissionPreviewResponse } from "@/generated/identity";

const RESOURCE_TYPE_LABELS: Record<string, string> = {
  user_management: "Benutzerverwaltung",
  role_management: "Rollenverwaltung",
  tenant_config: "Tenant-Konfiguration",
};

const ACTION_LABELS: Record<string, string> = {
  reader: "Lesen",
  editor: "Bearbeiten",
  creator: "Erstellen",
  deleter: "Loeschen",
  inviter: "Einladen",
};

function getResourceLabel(resourceType: string): string {
  return RESOURCE_TYPE_LABELS[resourceType] ?? resourceType;
}

function getActionLabel(action: string): string {
  return ACTION_LABELS[action] ?? action;
}

interface RolePermissionsPreviewProps {
  permissions: RolePermissionPreviewResponse[];
}

export function RolePermissionsPreview({
  permissions,
}: RolePermissionsPreviewProps) {
  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2 rounded-lg border border-stone-200 bg-stone-50 px-4 py-3 text-sm text-stone-600 dark:border-stone-800 dark:bg-stone-900/30 dark:text-stone-400">
        <Info className="size-4 shrink-0" />
        <span>Berechtigungen koennen aktuell nicht bearbeitet werden.</span>
      </div>

      {permissions.length === 0 ? (
        <div className="flex flex-col items-center justify-center rounded-lg border border-dashed p-12 text-center">
          <p className="text-lg font-medium">Keine Berechtigungen</p>
          <p className="mt-1 text-sm text-muted-foreground">
            Dieser Rolle sind keine Berechtigungen zugewiesen.
          </p>
        </div>
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {permissions.map((permission) => (
            <Card key={permission.resourceType}>
              <CardHeader className="pb-2 pt-4">
                <CardTitle className="text-sm font-medium">
                  {getResourceLabel(permission.resourceType)}
                </CardTitle>
              </CardHeader>
              <CardContent className="pb-4">
                <div className="flex flex-wrap gap-1.5">
                  {permission.actions.map((action) => (
                    <Badge
                      key={action}
                      variant="secondary"
                      className="text-xs"
                    >
                      {getActionLabel(action)}
                    </Badge>
                  ))}
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
