import { redirect } from "next/navigation";
import { auth } from "@/auth";

export default async function RootPage() {
  const session = await auth();

  if (!session) {
    redirect("/signin");
  }

  // TODO: Check if platform admin -> /tenants, else -> /dashboard
  redirect("/tenants");
}
