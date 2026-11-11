import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/api/client";
import { ImageUploader } from "./ImageUploader";

const upload = vi.fn();
vi.mock("@/features/files/api", () => ({
  useUploadImage: () => ({ mutateAsync: (f: File) => upload(f) }),
}));

const img = (n: number) => ({ fileId: n, url: `/img/${n}.jpg` });
const file = (name: string, type: string, size = 1024) => {
  const f = new File(["x"], name, { type });
  Object.defineProperty(f, "size", { value: size });
  return f;
};

function setup(value: { fileId: number; url: string }[] = [], max = 3) {
  const onChange = vi.fn();
  render(<ImageUploader value={value} onChange={onChange} max={max} label="상품 이미지" />);
  // accept 필터를 끄고 클라이언트 검증 자체를 시험한다(드래그·OS 선택기는 accept 를 우회할 수 있다).
  return { onChange, user: userEvent.setup({ applyAccept: false }) };
}

describe("ImageUploader", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("6MB_파일은_업로드하지_않고_오류를_보인다", async () => {
    const { onChange, user } = setup();
    await user.upload(screen.getByLabelText("상품 이미지"), file("big.jpg", "image/jpeg", 6 * 1024 * 1024));
    expect(await screen.findByRole("alert")).toHaveTextContent("5MB 이하");
    expect(upload).not.toHaveBeenCalled();
    expect(onChange).not.toHaveBeenCalled();
  });

  it("gif는_거부한다", async () => {
    const { onChange, user } = setup();
    await user.upload(screen.getByLabelText("상품 이미지"), file("a.gif", "image/gif"));
    expect(await screen.findByRole("alert")).toHaveTextContent("JPG, PNG, WEBP");
    expect(upload).not.toHaveBeenCalled();
    expect(onChange).not.toHaveBeenCalled();
  });

  it("정상_파일은_업로드_후_value에_추가된다", async () => {
    upload.mockResolvedValue(img(9));
    const { onChange, user } = setup([img(1)]);
    await user.upload(screen.getByLabelText("상품 이미지"), file("ok.png", "image/png"));
    await waitFor(() => expect(onChange).toHaveBeenCalledWith([img(1), img(9)]));
  });

  it("서버_FILE_UNSUPPORTED_FORMAT은_항목_오류로_보인다", async () => {
    upload.mockRejectedValue(new ApiError(400, "FILE_UNSUPPORTED_FORMAT", "서버 메시지", null));
    const { onChange, user } = setup();
    await user.upload(screen.getByLabelText("상품 이미지"), file("fake.png", "image/png"));
    expect(await screen.findByRole("alert")).toHaveTextContent("지원하지 않는");
    expect(onChange).not.toHaveBeenCalled();
  });

  it("아래로_버튼은_순서를_바꾼다", async () => {
    const { onChange, user } = setup([img(1), img(2)]);
    await user.click(screen.getByRole("button", { name: "1번째 이미지 아래로 이동" }));
    expect(onChange).toHaveBeenCalledWith([img(2), img(1)]);
  });

  it("첫_장에_대표_배지가_있고_삭제하면_빠진다", async () => {
    const { onChange, user } = setup([img(1), img(2)]);
    expect(screen.getAllByText("대표")).toHaveLength(1);
    await user.click(screen.getByRole("button", { name: "2번째 이미지 삭제" }));
    expect(onChange).toHaveBeenCalledWith([img(1)]);
  });

  it("max를_넘으면_입력이_disabled", () => {
    setup([img(1), img(2)], 2);
    expect(screen.getByLabelText("상품 이미지")).toBeDisabled();
  });
});
