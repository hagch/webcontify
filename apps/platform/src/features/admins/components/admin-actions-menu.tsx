"use client";

import { MoreHorizontal, UserCheck, UserX, KeyRound, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import type { PlatformAdminResponse } from "@/generated/platform-admin";
import { useAdminActions } from "../hooks/use-admin-actions";

interface AdminActionsMenuProps {
  admin: PlatformAdminResponse;
  onDeleteRequest?: (admin: PlatformAdminResponse) => void;
}

export function AdminActionsMenu({
  admin,
  onDeleteRequest,
}: AdminActionsMenuProps) {
  const { activateAdmin, deactivateAdmin, resetPassword, isUpdatingStatus, isResettingPassword } =
    useAdminActions();

  const fullName = `${admin.firstName} ${admin.lastName}`;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <Button
            data-testid={`actions-menu-${admin.id}`}
            variant="ghost"
            size="icon"
            aria-label={`Aktionen fuer ${fullName}`}
          />
        }
      >
        <MoreHorizontal className="size-4" />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        {admin.enabled ? (
          <DropdownMenuItem
            data-testid={`deactivate-admin-${admin.id}`}
            disabled={isUpdatingStatus}
            onClick={() => deactivateAdmin(admin.id)}
          >
            <UserX className="mr-2 size-4" />
            Deaktivieren
          </DropdownMenuItem>
        ) : (
          <DropdownMenuItem
            data-testid={`activate-admin-${admin.id}`}
            disabled={isUpdatingStatus}
            onClick={() => activateAdmin(admin.id)}
          >
            <UserCheck className="mr-2 size-4" />
            Aktivieren
          </DropdownMenuItem>
        )}
        <DropdownMenuItem
          data-testid={`reset-password-admin-${admin.id}`}
          disabled={isResettingPassword}
          onClick={() => resetPassword(admin.id)}
        >
          <KeyRound className="mr-2 size-4" />
          Passwort-Reset erzwingen
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem
          data-testid={`delete-admin-${admin.id}`}
          variant="destructive"
          onClick={() => onDeleteRequest?.(admin)}
        >
          <Trash2 className="mr-2 size-4" />
          Entfernen
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
