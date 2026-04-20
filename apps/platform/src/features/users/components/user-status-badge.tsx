import { Badge } from "@/components/ui/badge";
import { UserStatus } from "@/generated/identity";
import { cn } from "@/lib/utils";

interface UserStatusBadgeProps {
  status: UserStatus;
  className?: string;
}

const statusConfig: Record<
  UserStatus,
  { label: string; className: string }
> = {
  [UserStatus.INVITED]: {
    label: "Eingeladen",
    className:
      "bg-amber-100 text-amber-800 border-amber-200 dark:bg-amber-900/30 dark:text-amber-400 dark:border-amber-800",
  },
  [UserStatus.ACTIVE]: {
    label: "Aktiv",
    className:
      "bg-emerald-100 text-emerald-800 border-emerald-200 dark:bg-emerald-900/30 dark:text-emerald-400 dark:border-emerald-800",
  },
  [UserStatus.DEACTIVATED]: {
    label: "Deaktiviert",
    className:
      "bg-stone-100 text-stone-600 border-stone-200 dark:bg-stone-900/30 dark:text-stone-400 dark:border-stone-800",
  },
};

export function UserStatusBadge({ status, className }: UserStatusBadgeProps) {
  const config = statusConfig[status];

  return (
    <Badge
      variant="outline"
      className={cn(config.className, className)}
    >
      {config.label}
    </Badge>
  );
}
