import { AppHeader } from "@/components/layout/app-header";

export default function DomainSettingsPage() {
  return (
    <>
      <AppHeader
        breadcrumbs={[{ label: "Settings" }, { label: "Domain" }]}
      />
      <div className="flex flex-1 flex-col gap-4 p-4 md:p-6">
        <h1 className="text-2xl font-semibold tracking-tight">
          Custom Domain
        </h1>
        <p className="text-sm text-muted-foreground">
          Configure a custom domain for your workspace.
        </p>
      </div>
    </>
  );
}
