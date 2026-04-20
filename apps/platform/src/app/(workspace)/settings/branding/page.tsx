import { AppHeader } from "@/components/layout/app-header";

export default function BrandingSettingsPage() {
  return (
    <>
      <AppHeader
        breadcrumbs={[{ label: "Settings" }, { label: "Branding" }]}
      />
      <div className="flex flex-1 flex-col gap-4 p-4 md:p-6">
        <h1 className="text-2xl font-semibold tracking-tight">Branding</h1>
        <p className="text-sm text-muted-foreground">
          Customize the look and feel of your workspace.
        </p>
      </div>
    </>
  );
}
