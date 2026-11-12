// SweetTrackerDeliveryTracker 가 code 로 그대로 보내는 값이다.
export const CARRIERS: readonly { code: string; name: string }[] = [
  { code: "04", name: "CJ대한통운" },
  { code: "05", name: "한진택배" },
  { code: "08", name: "롯데택배" },
  { code: "01", name: "우체국택배" },
  { code: "06", name: "로젠택배" },
];

/** 모르는 코드는 코드 그대로, 없으면 빈 문자열. */
export function carrierName(code: string | null | undefined): string {
  if (code == null) return "";
  return CARRIERS.find((c) => c.code === code)?.name ?? code;
}
