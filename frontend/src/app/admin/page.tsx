import Link from "next/link";
import { OldestPending } from "@/features/admin/dashboard/components/OldestPending";
import { PendingCards } from "@/features/admin/dashboard/components/PendingCards";

export default function AdminHome() {
  return (
    <div className="flex flex-col gap-6">
      <PendingCards />
      <div className="grid items-start gap-5 min-[1100px]:grid-cols-[minmax(0,1fr)_280px]">
        <OldestPending />
        <section className="card" aria-labelledby="quick-h">
          <h2 id="quick-h" className="border-b border-border px-5 py-4 text-[15px] font-bold">바로 가기</h2>
          <div className="flex flex-col gap-2 p-5">
            <Link className="btn btn-primary btn-block" href="/admin/review">검수</Link>
            <Link className="btn btn-ghost btn-block" href="/admin/categories">카테고리 관리</Link>
            <Link className="btn btn-ghost btn-block" href="/admin/sellers">판매자 관리</Link>
          </div>
        </section>
      </div>
    </div>
  );
}
