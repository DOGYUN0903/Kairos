import Link from "next/link";

export default function ForbiddenPage() {
  return (
    <main className="container flex flex-col items-center gap-4 py-24 text-center">
      <h1 className="text-2xl font-bold">권한이 없습니다</h1>
      <p className="text-text-2">이 페이지에 접근할 권한이 없습니다.</p>
      <Link href="/" className="btn btn-primary">
        홈으로
      </Link>
    </main>
  );
}
