"use client";

import { useTranslations } from "next-intl";
import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar";
import { AppSidebar } from "@/components/layout/app-sidebar";
import {
  getPlatformNavItems,
  getWorkspaceNavItems,
} from "@/components/layout/nav-config";
import { useAuth } from "@/hooks/use-auth";
import { usePermissions } from "@/hooks/use-permissions";
import { BrandingProvider, type ServerTenantConfig } from "@/providers/branding-provider";

interface ProfileLayoutClientProps {
  children: React.ReactNode;
  initialConfig: ServerTenantConfig | null;
}

export function ProfileLayoutClient({ children, initialConfig }: ProfileLayoutClientProps) {
  const { isPlatformAdmin } = useAuth();
  const { canAccess } = usePermissions();
  const t = useTranslations("nav");
  const sections = isPlatformAdmin
    ? getPlatformNavItems(t)
    : getWorkspaceNavItems(
        {
          canAccessUserManagement: canAccess("user_management"),
          canAccessRoleManagement: canAccess("role_management"),
          canAccessTenantConfig: canAccess("tenant_config"),
          canAccessApps: canAccess("app_management"),
        },
        t
      );

  const content = (
    <SidebarProvider>
      <AppSidebar sections={sections} />
      <SidebarInset>
        <main id="main-content">{children}</main>
      </SidebarInset>
    </SidebarProvider>
  );

  if (isPlatformAdmin) {
    return content;
  }

  return <BrandingProvider initialConfig={initialConfig}>{content}</BrandingProvider>;
}
