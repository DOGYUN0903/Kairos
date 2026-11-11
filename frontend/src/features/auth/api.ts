import { useMutation } from "@tanstack/react-query";
import { api, REQUESTED_WITH, unwrap } from "@/api/client";
import type { components } from "@/api/schema";
import { IDEMPOTENCY_HEADER } from "@/api/idempotency";
import type { SignupValues } from "@/features/auth/schemas";

export type Me = components["schemas"]["MeResponse"];

export async function fetchMe(): Promise<Me> {
  return unwrap<Me>(await api.GET("/api/v1/auth/me"));
}

export async function loginRequest(email: string, password: string): Promise<string> {
  const token = unwrap<components["schemas"]["TokenResponse"]>(
    await api.POST("/api/v1/auth/login", { body: { email, password } }),
  );
  return token.accessToken;
}

/** 실패해도 클라이언트 상태는 비운다(호출자가 처리). */
export async function logoutRequest(): Promise<void> {
  await api.POST("/api/v1/auth/logout", { headers: { ...REQUESTED_WITH } });
}

export function useSignup() {
  return useMutation({
    mutationFn: async ({ values, key }: { values: SignupValues; key: string }) =>
      unwrap<components["schemas"]["SignupResponse"]>(
        await api.POST("/api/v1/auth/signup", { body: values, headers: { [IDEMPOTENCY_HEADER]: key } }),
      ),
  });
}
