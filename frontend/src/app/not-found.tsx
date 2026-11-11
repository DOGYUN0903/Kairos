import Link from "next/link";

export default function NotFound() {
  return (
    <main className="container flex flex-col items-center gap-4 py-24 text-center">
      <h1 className="text-2xl font-bold">페이지를 찾을 수 없습니다</h1>
      <p className="text-text-2">주소가 바뀌었거나 삭제된 페이지입니다.</p>
      <Link href="/" className="btn btn-primary">
        홈으로
      </Link>
    </main>
  );
}
