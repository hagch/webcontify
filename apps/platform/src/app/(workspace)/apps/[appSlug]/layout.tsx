import { AppLayoutClient } from "./app-layout-client";

export default async function AppLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ appSlug: string }>;
}) {
  const { appSlug } = await params;

  return <AppLayoutClient appSlug={appSlug}>{children}</AppLayoutClient>;
}
