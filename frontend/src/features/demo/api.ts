import { api, unwrap } from "@/api/client";
import type { components } from "@/api/schema";

export type DemoReseedResult = components["schemas"]["DemoReseedResult"];

export async function reseedDemo(): Promise<DemoReseedResult> {
  return unwrap<DemoReseedResult>(await api.POST("/api/v1/demo/reseed"));
}
