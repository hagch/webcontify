import { fetchTenantConfig } from "@/lib/server/fetch-tenant-config";
import { ProfileLayoutClient } from "./profile-layout-client";

export default async function ProfileLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const config = await fetchTenantConfig();

  return (
    <ProfileLayoutClient initialConfig={config}>
      {children}
    </ProfileLayoutClient>
  );
}
