import { defineConfig } from "orval";

export default defineConfig({
  tenant: {
    input: {
      target: "../../openapi/tenant-api.yaml",
    },
    output: {
      target: "./src/generated/tenant.ts",
      client: "react-query",
      mode: "single",
      override: {
        mutator: {
          path: "./src/lib/orval-mutator.ts",
          name: "customInstance",
        },
      },
    },
  },
  identity: {
    input: {
      target: "../../openapi/identity-api.yaml",
    },
    output: {
      target: "./src/generated/identity.ts",
      client: "react-query",
      mode: "single",
      override: {
        mutator: {
          path: "./src/lib/orval-mutator.ts",
          name: "customInstance",
        },
      },
    },
  },
  platformAdmin: {
    input: {
      target: "../../openapi/platform-admin-api.yaml",
    },
    output: {
      target: "./src/generated/platform-admin.ts",
      client: "react-query",
      mode: "single",
      override: {
        mutator: {
          path: "./src/lib/orval-mutator.ts",
          name: "customInstance",
        },
      },
    },
  },
  auth: {
    input: {
      target: "../../openapi/auth-api.yaml",
    },
    output: {
      target: "./src/generated/auth.ts",
      client: "react-query",
      mode: "single",
      override: {
        mutator: {
          path: "./src/lib/orval-mutator.ts",
          name: "customInstance",
        },
      },
    },
  },
});
