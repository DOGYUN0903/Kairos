import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";
import { StarRating } from "@/features/reviews/components/StarRating";
import { StarRatingInput } from "@/features/reviews/components/StarRatingInput";

function Harness({ start = 0 }: { start?: number }) {
  const [v, setV] = useState(start);
  return <StarRatingInput value={v} onChange={setV} />;
}

describe("StarRating", () => {
  it("5점_만점_라벨을_단다", () => {
    render(<StarRating rating={4} />);
    expect(screen.getByRole("img", { name: "5점 만점에 4점" })).toBeInTheDocument();
  });
});

describe("StarRatingInput", () => {
  it("라디오_그룹이고_선택한_값만_aria-checked", async () => {
    render(<Harness />);
    expect(screen.getByRole("radiogroup")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("radio", { name: "4점" }));
    expect(screen.getByRole("radio", { name: "4점" })).toHaveAttribute("aria-checked", "true");
    expect(screen.getByRole("radio", { name: "3점" })).toHaveAttribute("aria-checked", "false");
  });
  it("방향키로_값이_바뀐다", async () => {
    render(<Harness start={3} />);
    screen.getByRole("radio", { name: "3점" }).focus();
    await userEvent.keyboard("{ArrowRight}");
    expect(screen.getByRole("radio", { name: "4점" })).toHaveAttribute("aria-checked", "true");
    await userEvent.keyboard("{ArrowLeft}{ArrowLeft}");
    expect(screen.getByRole("radio", { name: "2점" })).toHaveAttribute("aria-checked", "true");
  });
  it("끝에서_넘기지_않는다", async () => {
    render(<Harness start={5} />);
    screen.getByRole("radio", { name: "5점" }).focus();
    await userEvent.keyboard("{ArrowRight}");
    expect(screen.getByRole("radio", { name: "5점" })).toHaveAttribute("aria-checked", "true");
  });
});
