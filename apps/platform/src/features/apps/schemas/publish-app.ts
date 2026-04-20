import { z } from "zod"

export const publishAppSchema = z.object({
  version: z
    .string()
    .min(1)
    .regex(/^\d+\.\d+\.\d+$/, "Must be a valid semver version (e.g. 1.0.0)"),
  releaseNotes: z.string().max(5000).optional().or(z.literal("")),
})

export type PublishAppFormValues = z.infer<typeof publishAppSchema>
