import { useMutation } from "@tanstack/react-query";
import { api, unwrap } from "@/api/client";
import type { components } from "@/api/schema";

export type UploadResult = components["schemas"]["UploadImageResponse"];

/** multipart `file` 하나. Content-Type(boundary 포함)은 브라우저가 FormData 로 정한다. */
export async function uploadImage(file: File): Promise<UploadResult> {
  return unwrap<UploadResult>(
    await api.POST("/api/v1/files/images", {
      // 생성 타입은 binary 를 string 으로 표기한다. 실제 직렬화는 아래 bodySerializer 가 FormData 로 한다.
      body: { file: file as unknown as string },
      bodySerializer: () => {
        const form = new FormData();
        form.append("file", file);
        return form;
      },
    }),
  );
}

export function useUploadImage() {
  return useMutation({ mutationFn: (file: File) => uploadImage(file) });
}
