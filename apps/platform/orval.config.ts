import { defineConfig } from "orval";

export default defineConfig({
  health: {
    input: {
      target: "../../openapi/health-api.yaml",
    },
    output: {
      target: "./src/generated/health.ts",
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
