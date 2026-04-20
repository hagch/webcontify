import { redirect } from "next/navigation";

export default async function AppDetailPage({
  params,
}: {
  params: Promise<{ appSlug: string }>;
}) {
  const { appSlug } = await params;
  redirect(`/apps/${appSlug}/definitions`);
}
