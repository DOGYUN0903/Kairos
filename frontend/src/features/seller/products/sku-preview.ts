/** 옵션 축 최대 개수(백엔드 ProductContent.MAX_OPTIONS). */
export const MAX_AXES = 2;
/** 축당 값 최대 개수. 백엔드에는 한도가 없어 프론트에서 조합 폭발(20 × 20 = 400 SKU)을 막는다. */
export const MAX_VALUES_PER_AXIS = 20;
export const MAX_SKUS = 400;

type Axis = { name: string; values: string[] };

/**
 * 축 값의 데카르트 곱. 축 순서·값 순서를 그대로 따라 결정적이다(백엔드 SKU 생성 순서와 같은 방향).
 * 값이 없는 축은 건너뛴다(편집 중 상태). 축이 0개면 SKU 하나([[]]).
 */
export function cartesian(options: Axis[]): string[][] {
  return options
    .filter((o) => o.values.length > 0)
    .reduce<string[][]>((rows, axis) => rows.flatMap((row) => axis.values.map((v) => [...row, v])), [[]]);
}

/** 백엔드 displayName 과 같은 " / " 연결. 옵션이 없으면 빈 문자열 1행. */
export function previewRows(options: Axis[]): { displayName: string }[] {
  return cartesian(options).map((combo) => ({ displayName: combo.join(" / ") }));
}
