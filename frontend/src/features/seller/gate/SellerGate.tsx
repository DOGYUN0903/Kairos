"use client";

import { CircleAlert, Info } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState, type ReactNode } from "react";
import { useAuth } from "@/auth/useAuth";
import { ErrorState } from "@/components/common/ErrorState";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { ConsoleShell, type NavItem } from "@/components/layout/ConsoleShell";
import { ApplicationSummary } from "@/features/seller/apply/components/ApplicationSummary";
import { ApplyForm } from "@/features/seller/apply/components/ApplyForm";
import { SellerBannerContext } from "@/features/seller/gate/banner";
import { useSellerMe } from "@/features/seller/gate/useSellerMe";

export { useSellerCapabilities } from "@/features/seller/gate/useSellerCapabilities";

const FULL_NAV: NavItem[] = [
  { href: "/seller", label: "대시보드" },
  { href: "/seller/products", label: "상품 관리" },
  { href: "/seller/brands", label: "브랜드" },
  { href: "/seller/settlements", label: "정산", disabled: true, soonLabel: "준비 중" },
];
// 미신청·심사 중·반려에서는 시안대로 "대시보드"만 보인다.
const LIMITED_NAV: NavItem[] = [{ href: "/seller", label: "대시보드" }];

function titleOf(pathname: string): string {
  if (pathname.startsWith("/seller/apply")) return "입점 신청";
  if (pathname.startsWith("/seller/products")) return "상품 관리";
  if (pathname.startsWith("/seller/brands")) return "브랜드";
  return "대시보드";
}

function Notice({ tone, children }: { tone: "info" | "warn"; children: ReactNode }) {
  return (
    <div className={tone === "warn" ? "notice notice-warn" : "notice"} role={tone === "warn" ? "alert" : undefined}>
      {tone === "warn" ? <CircleAlert className="h-4 w-4 flex-none" aria-hidden /> : <Info className="h-4 w-4 flex-none text-navy" aria-hidden />}
      <span>{children}</span>
    </div>
  );
}

function Onboarding() {
  return (
    <div className="card p-5">
      <h2 className="text-xl font-bold">KAIROS에 입점하고 브랜드 상품을 판매하세요</h2>
      <p className="mt-2 text-text-2">사업자 정보와 정산 계좌를 등록하면 관리자 심사 후 판매자 센터를 모두 쓸 수 있습니다.</p>
      <ol className="my-4 mb-6 grid grid-cols-3 gap-4">
        {[
          ["1. 입점 신청", "회사명 · 사업자등록번호 · 대표자 · 연락처 · 정산 계좌"],
          ["2. 관리자 심사", "보통 영업일 기준 1–2일이 걸립니다"],
          ["3. 브랜드 · 상품 등록", "브랜드 승인 후 상품을 등록하고 검수를 받습니다"],
        ].map(([b, t]) => (
          <li key={b} className="list-none rounded-lg bg-surface p-4 text-[13px] text-text-2">
            <b className="mb-1 block text-sm text-navy">{b}</b>
            {t}
          </li>
        ))}
      </ol>
      <Link href="/seller/apply" className="btn btn-primary btn-lg">
        입점 신청
      </Link>
    </div>
  );
}

/** `GET /seller/me` 의 status 로 콘솔 진입을 고른다. */
export function SellerGate({ children }: { children: ReactNode }) {
  const { me } = useAuth();
  const pathname = usePathname();
  const { status, seller, error, refetch } = useSellerMe();
  const [banner, setBanner] = useState<string | null>(null);

  const full = status === "APPROVED" || status === "SUSPENDED";
  const userLabel = seller?.companyName ?? me?.name ?? "";

  let body: ReactNode;
  if (error) {
    body = <ErrorState error={error} onRetry={refetch} />;
  } else if (status === "loading") {
    body = <PageSkeleton />;
  } else if (full) {
    body = children;
  } else if (pathname.startsWith("/seller/apply")) {
    // 신청·수정 화면 자체는 미신청·심사 중·반려에서도 열려 있어야 한다.
    body = children;
  } else if (status === "none") {
    body = <Onboarding />;
  } else if (status === "PENDING" && seller) {
    body = (
      <>
        <Notice tone="info">심사 중입니다. 내용을 고치면 다시 심사합니다.</Notice>
        <div className="mt-4">
          <ApplicationSummary seller={seller} />
        </div>
      </>
    );
  } else if (status === "REJECTED" && seller) {
    body = (
      <>
        <Notice tone="warn">
          <strong>입점이 반려되었습니다.</strong> 사유: {seller.rejectReason ?? "사유가 등록되지 않았습니다."} 고쳐서 다시 신청해 주세요.
        </Notice>
        <div className="mt-4">
          <ApplyForm mode="update" seller={seller} />
        </div>
      </>
    );
  } else {
    body = null;
  }

  const bannerNode =
    status === "SUSPENDED" ? (
      <Notice tone="warn">
        <strong>판매자 정지 중:</strong> {seller?.suspendReason ?? "사유가 등록되지 않았습니다."} 판매 중인 상품은 노출되지 않으며, 새 상품·브랜드를 등록할 수 없습니다.
      </Notice>
    ) : banner ? (
      <Notice tone="warn">{banner}</Notice>
    ) : null;

  return (
    <SellerBannerContext.Provider value={setBanner}>
      <ConsoleShell role="판매자 센터" nav={full ? FULL_NAV : LIMITED_NAV} userLabel={userLabel} title={titleOf(pathname)} banner={bannerNode}>
        {body}
      </ConsoleShell>
    </SellerBannerContext.Provider>
  );
}
