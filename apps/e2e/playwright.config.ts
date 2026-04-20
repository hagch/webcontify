import { defineConfig, devices } from "@playwright/test";
import dotenv from "dotenv";
import path from "path";

dotenv.config({ path: path.resolve(__dirname, ".env.local") });

export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: process.env.CI ? "github" : "html",
  timeout: 120000,
  use: {
    baseURL: process.env.BASE_URL ?? "http://platform.localhost",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: {
        ...devices["Desktop Chrome"],
        // Force IPv4 — Traefik listens on 0.0.0.0:80 but not on [::]:80
        launchOptions: {
          args: ["--disable-ipv6"],
        },
      },
    },
  ],
});
