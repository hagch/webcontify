import { signIn } from "@/auth";

export default async function SignInPage({
  searchParams,
}: {
  searchParams: Promise<{ callbackUrl?: string }>;
}) {
  const params = await searchParams;
  const callbackUrl = params.callbackUrl ?? "/";

  return (
    <form
      action={async () => {
        "use server";
        await signIn("keycloak", { redirectTo: callbackUrl });
      }}
    >
      <noscript>
        <button type="submit">Sign in with Keycloak</button>
      </noscript>
      <AutoSubmit />
    </form>
  );
}

function AutoSubmit() {
  return (
    <script
      dangerouslySetInnerHTML={{
        __html: `document.forms[0].requestSubmit()`,
      }}
    />
  );
}
