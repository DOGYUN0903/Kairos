"use client";

import { X } from "lucide-react";
import { useState, type KeyboardEvent } from "react";
import type { FieldErrors } from "react-hook-form";
import type { ProductFormInput } from "@/features/seller/products/schemas";
import { OPTION_VALUE_MAX } from "@/features/seller/products/schemas";
import { MAX_AXES, MAX_VALUES_PER_AXIS } from "@/features/seller/products/sku-preview";

type Axis = { name: string; values: string[] };

/** 옵션 축 0~2개. 값은 태그 입력(Enter 로 추가, 빈 입력에서 Backspace 로 마지막 값 삭제). */
export function OptionAxesEditor({
  value,
  onChange,
  errors,
}: {
  value: Axis[];
  onChange: (next: Axis[]) => void;
  errors?: FieldErrors<ProductFormInput>["options"];
}) {
  const update = (i: number, axis: Axis) => onChange(value.map((a, j) => (j === i ? axis : a)));
  const rootMessage = errors?.message ?? errors?.root?.message;

  return (
    <div className="flex flex-col gap-4">
      {value.map((axis, i) => (
        <AxisRow
          key={i}
          index={i}
          axis={axis}
          onChange={(a) => update(i, a)}
          onRemove={() => onChange(value.filter((_, j) => j !== i))}
          nameError={errors?.[i]?.name?.message}
          valuesError={errors?.[i]?.values?.message ?? errors?.[i]?.values?.root?.message}
        />
      ))}
      {rootMessage && <span className="field-error">{rootMessage}</span>}
      <div>
        <button
          type="button"
          className="btn btn-ghost btn-sm"
          disabled={value.length >= MAX_AXES}
          onClick={() => onChange([...value, { name: "", values: [] }])}
        >
          + 옵션 축 추가
        </button>
        <p className="hint mt-2">옵션이 없으면 SKU 하나로 등록됩니다. 값의 모든 조합이 SKU가 됩니다.</p>
      </div>
    </div>
  );
}

function AxisRow({
  index,
  axis,
  onChange,
  onRemove,
  nameError,
  valuesError,
}: {
  index: number;
  axis: Axis;
  onChange: (a: Axis) => void;
  onRemove: () => void;
  nameError?: string;
  valuesError?: string;
}) {
  const [draft, setDraft] = useState("");
  const [note, setNote] = useState<string | null>(null);
  const nameId = `axis${index + 1}`;
  const addId = `${nameId}-add`;
  const full = axis.values.length >= MAX_VALUES_PER_AXIS;
  const label = axis.name.trim() || `옵션 ${index + 1}`;

  function onKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === "Enter") {
      e.preventDefault(); // 폼 제출 대신 값 추가
      if (e.nativeEvent.isComposing) return; // 한글 조합 중 Enter 는 무시
      const v = draft.trim();
      if (v === "") return;
      if (axis.values.includes(v)) return setNote(`"${v}"은(는) 이미 있습니다.`);
      if (v.length > OPTION_VALUE_MAX) return setNote(`옵션 값은 ${OPTION_VALUE_MAX}자 이하로 입력해 주세요.`);
      if (full) return setNote(`옵션 값은 축마다 최대 ${MAX_VALUES_PER_AXIS}개입니다.`);
      onChange({ ...axis, values: [...axis.values, v] });
      setDraft("");
      setNote(null);
    } else if (e.key === "Backspace" && draft === "" && axis.values.length > 0) {
      onChange({ ...axis, values: axis.values.slice(0, -1) });
    }
  }

  return (
    <div className="rounded border border-border p-4">
      <div className="mb-3 flex items-center gap-2">
        <label htmlFor={nameId} className="w-14 flex-none text-[13px] font-medium">
          옵션 {index + 1}
        </label>
        <input
          id={nameId}
          className="input flex-1"
          placeholder={index === 0 ? "옵션명 (예: 색상)" : "옵션명 (예: 사이즈)"}
          value={axis.name}
          aria-invalid={nameError ? true : undefined}
          aria-describedby={nameError ? `${nameId}-err` : undefined}
          onChange={(e) => onChange({ ...axis, name: e.target.value })}
        />
        <button type="button" className="btn btn-ghost btn-sm" onClick={onRemove}>
          축 삭제
        </button>
      </div>
      {nameError && (
        <span id={`${nameId}-err`} className="field-error mb-2 block">
          {nameError}
        </span>
      )}
      <div className="flex flex-wrap items-center gap-1.5">
        <ul className="contents" aria-label={`${label} 값`}>
          {axis.values.map((v) => (
            <li key={v} className="inline-flex items-center gap-1 rounded-full border border-border bg-surface px-2.5 py-1 text-[13px]">
              {v}
              <button
                type="button"
                aria-label={`${v} 삭제`}
                className="grid h-5 w-5 place-items-center rounded-full text-text-2 hover:text-danger"
                onClick={() => onChange({ ...axis, values: axis.values.filter((x) => x !== v) })}
              >
                <X className="h-3 w-3" aria-hidden />
              </button>
            </li>
          ))}
        </ul>
        <label htmlFor={addId} className="sr-only">
          {label} 값 추가
        </label>
        <input
          id={addId}
          className="input w-44"
          placeholder={full ? "값 최대 개수" : "값 입력 후 Enter"}
          value={draft}
          disabled={full}
          aria-describedby={valuesError || note ? `${addId}-err` : undefined}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={onKeyDown}
        />
      </div>
      {(note || valuesError) && (
        <span id={`${addId}-err`} className="field-error mt-1 block">
          {note ?? valuesError}
        </span>
      )}
    </div>
  );
}
