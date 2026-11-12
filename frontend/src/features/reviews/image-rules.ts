import { REVIEW_IMAGE_MAX } from "@/features/reviews/schemas";

/** 후기 사진은 3장까지. 형식·크기 검사는 components/common/ImageUploader 의 validateImageFile 을 쓴다. */
export function canAddImage(count: number): boolean {
  return count < REVIEW_IMAGE_MAX;
}
