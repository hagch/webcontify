import { fetchTenantConfig } from "@/lib/server/fetch-tenant-config";
import { WorkspaceLayoutClient } from "./workspace-layout-client";

export default async function WorkspaceLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const config = await fetchTenantConfig();

  return (
    <WorkspaceLayoutClient initialConfig={config}>
      {children}
    </WorkspaceLayoutClient>
  );
}
