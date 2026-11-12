import type { OrderShippingAddress } from "@/features/checkout/api";

export function ShippingAddressCard({ address }: { address: OrderShippingAddress }) {
  return (
    <section aria-labelledby="addr-h" className="rounded-lg border border-border bg-bg p-6">
      <h2 id="addr-h" className="mb-4 text-base font-bold">
        배송지
      </h2>
      <dl className="grid grid-cols-[64px_minmax(0,1fr)] gap-x-3 gap-y-2.5 text-sm">
        <dt className="text-text-2">수령인</dt>
        <dd>{address.receiverName}</dd>
        <dt className="text-text-2">연락처</dt>
        <dd className="tabular-nums">{address.phone}</dd>
        <dt className="text-text-2">주소</dt>
        <dd>
          ({address.zipCode}) {address.address1}
          {address.address2 ? `, ${address.address2}` : ""}
        </dd>
        {address.memo ? (
          <>
            <dt className="text-text-2">배송 메모</dt>
            <dd>{address.memo}</dd>
          </>
        ) : null}
      </dl>
    </section>
  );
}
