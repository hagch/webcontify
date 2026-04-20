import { AppHeader } from "@/components/layout/app-header";

export default function PlatformAdminsPage() {
  return (
    <>
      <AppHeader breadcrumbs={[{ label: "Platform Admins" }]} />
      <div className="flex flex-1 flex-col gap-4 p-4 md:p-6">
        <h1 className="text-2xl font-semibold tracking-tight">
          Platform Admins
        </h1>
        <div className="flex flex-col items-center justify-center rounded-lg border border-dashed p-12 text-center">
          <p className="text-lg font-medium">Admin management coming soon</p>
          <p className="mt-1 text-sm text-muted-foreground">
            Invite and manage platform administrators.
          </p>
        </div>
      </div>
    </>
  );
}
