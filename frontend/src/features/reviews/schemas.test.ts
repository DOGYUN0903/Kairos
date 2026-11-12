import { describe, expect, it } from "vitest";
import { contentLength, reviewFormSchema } from "@/features/reviews/schemas";

const ok = { rating: 5, content: "아주 좋아요 추천합니다", imageFileIds: [] as number[] };
const parse = (v: Partial<typeof ok>) => reviewFormSchema.safeParse({ ...ok, ...v });

describe("reviewFormSchema", () => {
  it("공백만_20자는_실패한다", () => {
    const r = parse({ content: " ".repeat(20) });
    expect(r.success).toBe(false);
    expect(r.error?.issues[0]?.message).toBe("10자 이상 입력해 주세요");
  });
  it("앞뒤_공백을_뺀_길이로_판정한다", () => {
    expect(parse({ content: "  아주 좋아요 추천합니다  " }).success).toBe(true);
    expect(parse({ content: "가".repeat(9) }).success).toBe(false);
    expect(parse({ content: " " + "가".repeat(9) + " " }).success).toBe(false);
    expect(parse({ content: "가".repeat(10) }).success).toBe(true);
  });
  it("1000자는_통과하고_1001자는_실패한다", () => {
    expect(parse({ content: "가".repeat(1000) }).success).toBe(true);
    expect(parse({ content: "가".repeat(1001) }).success).toBe(false);
  });
  it("별점은_1에서_5의_정수만", () => {
    for (const bad of [0, 6, 2.5]) expect(parse({ rating: bad }).success).toBe(false);
    for (const good of [1, 3, 5]) expect(parse({ rating: good }).success).toBe(true);
  });
  it("별점_미선택_메시지", () => {
    expect(parse({ rating: 0 }).error?.issues[0]?.message).toBe("별점을 선택해 주세요");
  });
  it("사진은_3장까지", () => {
    expect(parse({ imageFileIds: [1, 2, 3] }).success).toBe(true);
    expect(parse({ imageFileIds: [1, 2, 3, 4] }).success).toBe(false);
  });
});

describe("contentLength", () => {
  it("앞뒤_공백을_뺀_길이", () => {
    expect(contentLength("  가나다  ")).toBe(3);
  });
  it("서버와_같은_UTF-16_단위로_센다(이모지는_2)", () => {
    expect(contentLength("😀")).toBe(2);
    expect(contentLength("😀".repeat(5))).toBe(10);
    expect(parse({ content: "😀".repeat(5) }).success).toBe(true);
  });
});
