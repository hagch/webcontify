import { z } from "zod"

export const createAppSchema = z.object({
  name: z.string().min(1).max(255),
  description: z.string().max(2000).optional().or(z.literal("")),
})

export type CreateAppFormValues = z.infer<typeof createAppSchema>
