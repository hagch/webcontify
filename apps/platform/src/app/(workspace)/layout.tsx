"use client";

import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar";
import { AppSidebar } from "@/components/layout/app-sidebar";
import { getWorkspaceNavItems } from "@/components/layout/nav-config";

export default function WorkspaceLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  // TODO: Determine isAdmin from auth context (OpenFGA in Phase 3)
  const sections = getWorkspaceNavItems({ isAdmin: true });

  return (
    <SidebarProvider>
      <AppSidebar sections={sections} />
      <SidebarInset>
        <main id="main-content">{children}</main>
      </SidebarInset>
    </SidebarProvider>
  );
}
