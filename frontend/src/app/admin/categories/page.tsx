import { CategoryTreeEditor } from "@/features/admin/categories/components/CategoryTreeEditor";

export default function AdminCategoriesPage() {
  return (
    <div className="flex flex-col gap-5">
      <p className="text-sm text-text-2">대분류와 소분류(2단계)를 관리합니다. 순서는 위·아래 버튼으로 바꿉니다.</p>
      <CategoryTreeEditor />
    </div>
  );
}
