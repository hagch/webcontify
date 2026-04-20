"use client";

import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar";
import { AppSidebar } from "@/components/layout/app-sidebar";
import { getPlatformNavItems } from "@/components/layout/nav-config";

export default function PlatformLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const sections = getPlatformNavItems();

  return (
    <SidebarProvider>
      <AppSidebar sections={sections} />
      <SidebarInset>
        <main id="main-content">{children}</main>
      </SidebarInset>
    </SidebarProvider>
  );
}
