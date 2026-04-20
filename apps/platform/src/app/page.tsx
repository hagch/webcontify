import { useTranslations } from "next-intl";

export default function Home() {
  const t = useTranslations("home");

  return (
    <div className="flex min-h-screen items-center justify-center">
      <main className="flex flex-col items-center gap-4">
        <h1 className="text-4xl font-bold text-primary">
          {t("title")}
        </h1>
        <p className="text-lg text-muted-foreground">
          {t("description")}
        </p>
      </main>
    </div>
  );
}
