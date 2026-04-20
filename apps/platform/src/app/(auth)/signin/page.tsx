import { signIn } from "@/auth";

export default function SignInPage() {
  return (
    <div className="flex min-h-dvh items-center justify-center">
      <form
        action={async () => {
          "use server";
          await signIn("keycloak");
        }}
      >
        <button
          type="submit"
          className="rounded-lg bg-primary px-6 py-3 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90"
        >
          Sign in with Keycloak
        </button>
      </form>
    </div>
  );
}
