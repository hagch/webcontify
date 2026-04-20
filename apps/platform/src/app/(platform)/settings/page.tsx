import { AppHeader } from "@/components/layout/app-header";

export default function PlatformSettingsPage() {
  return (
    <>
      <AppHeader breadcrumbs={[{ label: "Settings" }]} />
      <div className="flex flex-1 flex-col gap-4 p-4 md:p-6">
        <h1 className="text-2xl font-semibold tracking-tight">
          System Settings
        </h1>
        <p className="text-sm text-muted-foreground">
          Platform-wide configuration options.
        </p>
      </div>
    </>
  );
}
