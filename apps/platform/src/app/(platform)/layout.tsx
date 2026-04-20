"use client";

import { useTranslations } from "next-intl";
import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar";
import { AppSidebar } from "@/components/layout/app-sidebar";
import { getPlatformNavItems } from "@/components/layout/nav-config";

export default function PlatformLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const t = useTranslations("nav");
  const sections = getPlatformNavItems(t);

  return (
    <SidebarProvider>
      <AppSidebar sections={sections} />
      <SidebarInset>
        <main id="main-content">{children}</main>
      </SidebarInset>
    </SidebarProvider>
  );
}
