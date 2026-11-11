"use client";

import { X } from "lucide-react";
import { useState, type KeyboardEvent } from "react";
import type { FieldErrors } from "react-hook-form";
import type { ProductFormInput } from "@/features/seller/products/schemas";
import { OPTION_VALUE_MAX } from "@/features/seller/products/schemas";
import { MAX_AXES, MAX_VALUES_PER_AXIS } from "@/features/seller/products/sku-preview";

/** optionId·valueIds 는 숨은 필드다. valueIds 는 values 와 같은 순서이며 새 값은 null. */
export type Axis = { optionId?: number | null; name: string; values: string[]; valueIds?: (number | null)[] };

const idsOf = (axis: Axis): (number | null)[] => axis.values.map((_, j) => axis.valueIds?.[j] ?? null);

/**
 * 옵션 축 0~2개. 값은 태그 입력(Enter 로 추가, 빈 입력에서 Backspace 로 마지막 값 삭제).
 * 태그 이름을 누르면 이름을 바꿀 수 있다. 숨은 ID 는 그대로라 기존 SKU 가 유지된다.
 */
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
          onClick={() => onChange([...value, { optionId: null, name: "", values: [], valueIds: [] }])}
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
  const [editing, setEditing] = useState<{ index: number; text: string } | null>(null);
  const ids = idsOf(axis);
  const removeAt = (j: number) =>
    onChange({ ...axis, values: axis.values.filter((_, k) => k !== j), valueIds: ids.filter((_, k) => k !== j) });

  function commitRename() {
    if (!editing) return;
    const v = editing.text.trim();
    const old = axis.values[editing.index];
    if (v === "" || v === old) return setEditing(null);
    if (axis.values.some((x, k) => k !== editing.index && x === v)) return setNote(`"${v}"은(는) 이미 있습니다.`);
    if (v.length > OPTION_VALUE_MAX) return setNote(`옵션 값은 ${OPTION_VALUE_MAX}자 이하로 입력해 주세요.`);
    onChange({ ...axis, values: axis.values.map((x, k) => (k === editing.index ? v : x)), valueIds: ids });
    setEditing(null);
    setNote(null);
  }
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
      onChange({ ...axis, values: [...axis.values, v], valueIds: [...ids, null] });
      setDraft("");
      setNote(null);
    } else if (e.key === "Backspace" && draft === "" && axis.values.length > 0) {
      removeAt(axis.values.length - 1);
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
          {axis.values.map((v, j) => (
            <li key={`${ids[j] ?? "new"}-${j}`} className="inline-flex items-center gap-1 rounded-full border border-border bg-surface px-2.5 py-1 text-[13px]">
              {editing?.index === j ? (
                <input
                  autoFocus
                  aria-label={`${v} 새 이름`}
                  className="w-24 bg-transparent outline-none"
                  value={editing.text}
                  onChange={(e) => setEditing({ index: j, text: e.target.value })}
                  onBlur={commitRename}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") {
                      e.preventDefault();
                      if (!e.nativeEvent.isComposing) commitRename();
                    } else if (e.key === "Escape") {
                      e.preventDefault();
                      setEditing(null);
                      setNote(null);
                    }
                  }}
                />
              ) : (
                <button
                  type="button"
                  aria-label={`${v} 이름 변경`}
                  title="이름 변경"
                  className="hover:underline"
                  onClick={() => setEditing({ index: j, text: v })}
                >
                  {v}
                </button>
              )}
              <button
                type="button"
                aria-label={`${v} 삭제`}
                className="grid h-5 w-5 place-items-center rounded-full text-text-2 hover:text-danger"
                onClick={() => removeAt(j)}
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
