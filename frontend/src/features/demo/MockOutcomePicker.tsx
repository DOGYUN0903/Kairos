"use client";

import type { MockOutcome, MockOutcomePrefixes } from "@/features/payment/payment-key";

// MockPgClient 의 paymentKey 접두사 규칙. 이 상수는 이 파일에만 둔다:
// 데모 빌드에서만 동적으로 불러오므로 운영 번들에는 실패 접두사가 없다(pnpm check:bundle 이 확인).
const PREFIXES: MockOutcomePrefixes = { reject: "fail-", unknown: "unknown-", lost: "lost-" };

const OPTIONS: { value: MockOutcome; label: string; hint: string }[] = [
  { value: "approve", label: "정상 승인", hint: "결제가 승인되고 주문이 완료됩니다" },
  { value: "reject", label: "카드사 거절", hint: "결제가 거절되고 주문이 취소됩니다" },
  { value: "unknown", label: "응답 없음 — 실제로는 승인됨", hint: "\"결제 확인 중\"을 거쳐 결제 완료로 바뀝니다" },
  { value: "lost", label: "응답 없음 — 승인도 안 됨", hint: "\"결제 확인 중\"을 거쳐 주문이 취소됩니다" },
];

export default function MockOutcomePicker({
  value,
  onChange,
}: {
  value: MockOutcome;
  onChange(v: MockOutcome, prefixes: MockOutcomePrefixes): void;
}) {
  return (
    <fieldset className="m-0 rounded border border-dashed border-[#D9BE6A] bg-[#FFFCF1] px-4 pb-2 pt-3.5">
      <legend className="sr-only">결제 결과 선택 (시연용)</legend>
      <div aria-hidden className="mb-1.5 flex items-center gap-2 text-[13px] font-bold">
        <span className="inline-flex h-5 items-center rounded-sm border border-dashed border-[#D9BE6A] bg-[#FFF6DD] px-1.5 text-[11px] font-bold leading-none text-[#7A5600]">
          시연용
        </span>
        결제 결과 선택
      </div>
      {OPTIONS.map((o, i) => (
        <label key={o.value} className={`flex cursor-pointer items-start gap-2.5 py-2 text-sm ${i > 0 ? "border-t border-[#F0E5BF]" : ""}`}>
          <input
            type="radio"
            name="mock-outcome"
            className="mt-0.5 flex-none"
            value={o.value}
            checked={value === o.value}
            onChange={() => onChange(o.value, PREFIXES)}
          />
          <span>
            <b className="block font-medium">{o.label}</b>
            <span className="block text-xs text-text-2">{o.hint}</span>
          </span>
        </label>
      ))}
    </fieldset>
  );
}
