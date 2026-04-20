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
          path: "./src/lib/api-client.ts",
          name: "customInstance",
        },
      },
    },
  },
});
