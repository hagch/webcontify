"use client";

import { useTranslations } from "next-intl";
import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar";
import { AppSidebar } from "@/components/layout/app-sidebar";
import { getWorkspaceNavItems } from "@/components/layout/nav-config";
import { usePermissions } from "@/hooks/use-permissions";
import { BrandingProvider, type ServerTenantConfig } from "@/providers/branding-provider";

interface WorkspaceLayoutClientProps {
  children: React.ReactNode;
  initialConfig: ServerTenantConfig | null;
}

export function WorkspaceLayoutClient({ children, initialConfig }: WorkspaceLayoutClientProps) {
  const t = useTranslations("nav");
  const { canAccess, isLoading } = usePermissions();

  const sections = getWorkspaceNavItems(
    {
      canAccessUserManagement: canAccess("user_management"),
      canAccessRoleManagement: canAccess("role_management"),
      canAccessTenantConfig: canAccess("tenant_config"),
      canAccessApps: canAccess("app_management"),
    },
    t
  );

  return (
    <BrandingProvider initialConfig={initialConfig}>
      <SidebarProvider>
        <AppSidebar sections={sections} isLoading={isLoading} />
        <SidebarInset>
          <main id="main-content">{children}</main>
        </SidebarInset>
      </SidebarProvider>
    </BrandingProvider>
  );
}
