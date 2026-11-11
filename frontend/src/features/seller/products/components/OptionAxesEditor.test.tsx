import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";
import { OptionAxesEditor, type Axis } from "@/features/seller/products/components/OptionAxesEditor";

function Harness({ initial, onValue }: { initial: Axis[]; onValue: (a: Axis[]) => void }) {
  const [v, setV] = useState(initial);
  return (
    <OptionAxesEditor
      value={v}
      onChange={(n) => {
        setV(n);
        onValue(n);
      }}
    />
  );
}

describe("OptionAxesEditor", () => {
  const initial: Axis[] = [{ optionId: 1, name: "색상", values: ["블랙", "화이트"], valueIds: [11, 12] }];

  it("값_이름을_바꿔도_숨은_valueId가_유지된다", async () => {
    let last: Axis[] = initial;
    render(<Harness initial={initial} onValue={(a) => (last = a)} />);
    await userEvent.click(screen.getByRole("button", { name: "블랙 이름 변경" }));
    const input = screen.getByLabelText("블랙 새 이름");
    await userEvent.clear(input);
    await userEvent.type(input, "검정{Enter}");
    expect(last[0]).toEqual({ optionId: 1, name: "색상", values: ["검정", "화이트"], valueIds: [11, 12] });
  });

  it("값을_지우면_ID도_같이_지워지고_새_값은_null", async () => {
    let last: Axis[] = initial;
    render(<Harness initial={initial} onValue={(a) => (last = a)} />);
    await userEvent.click(screen.getByRole("button", { name: "블랙 삭제" }));
    await userEvent.type(screen.getByLabelText("색상 값 추가"), "네이비{Enter}");
    expect(last[0]).toEqual({ optionId: 1, name: "색상", values: ["화이트", "네이비"], valueIds: [12, null] });
  });

  it("다른_값과_같은_이름으로는_바꿀_수_없다", async () => {
    let last: Axis[] = initial;
    render(<Harness initial={initial} onValue={(a) => (last = a)} />);
    await userEvent.click(screen.getByRole("button", { name: "블랙 이름 변경" }));
    const input = screen.getByLabelText("블랙 새 이름");
    await userEvent.clear(input);
    await userEvent.type(input, "화이트{Enter}");
    expect(last).toBe(initial);
    expect(screen.getByText(/이미 있습니다/)).toBeInTheDocument();
  });
});
