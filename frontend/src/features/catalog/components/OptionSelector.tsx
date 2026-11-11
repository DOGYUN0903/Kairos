import { disabledValues, selectionLabel, type ProductOption, type Selection, type Sku } from "@/features/catalog/sku-match";

type Props = {
  options: ProductOption[];
  skus: Sku[];
  selected: Selection;
  /** 상품 전체가 품절이면 모든 값을 막는다. */
  soldOut: boolean;
  onSelect: (optionId: number, valueId: number | null) => void;
};

/** 축별 버튼 그룹. 이미 고른 값을 다시 누르면 선택이 풀린다. 막힌 값은 disabled + "품절". */
export function OptionSelector({ options, skus, selected, soldOut, onSelect }: Props) {
  const disabled = disabledValues(skus, options, selected);
  return (
    <>
      {options.map((option) => {
        const headingId = `option-${option.optionId}`;
        const chosen = selectionLabel([option], selected);
        return (
          <div key={option.optionId} className="border-b border-border py-5">
            <h2 id={headingId} className="mb-2.5 flex justify-between text-[13px] font-bold">
              {option.name}
              <span className="font-normal text-text-2">{chosen ? `선택: ${chosen}` : "선택하세요"}</span>
            </h2>
            <div role="group" aria-labelledby={headingId} className="flex flex-wrap gap-2">
              {option.values.map((v) => {
                const isSelected = selected[option.optionId] === v.valueId;
                const isDisabled = soldOut || (disabled.has(v.valueId) && !isSelected);
                return (
                  <button
                    key={v.valueId}
                    type="button"
                    aria-pressed={isSelected}
                    disabled={isDisabled}
                    aria-label={isDisabled ? `${v.name} 품절` : undefined}
                    onClick={() => onSelect(option.optionId, isSelected ? null : v.valueId)}
                    className="relative min-h-[44px] min-w-14 rounded border border-border bg-bg px-3.5 font-medium hover:border-navy aria-pressed:border-navy aria-pressed:bg-navy aria-pressed:text-white disabled:cursor-not-allowed disabled:border-dashed disabled:bg-surface disabled:text-text-3 disabled:hover:border-border"
                  >
                    {v.name}
                    {isDisabled && (
                      <span aria-hidden className="absolute -right-1 -top-2 bg-bg px-0.5 text-[10px] font-bold text-text-3">
                        품절
                      </span>
                    )}
                  </button>
                );
              })}
            </div>
          </div>
        );
      })}
    </>
  );
}
