import { QuickActions } from "@/features/seller/dashboard/components/QuickActions";
import { RecentProducts } from "@/features/seller/dashboard/components/RecentProducts";
import { StatusCards } from "@/features/seller/dashboard/components/StatusCards";

export default function SellerHome() {
  return (
    <>
      <StatusCards />
      <div className="mt-6 grid grid-cols-[minmax(0,1fr)_280px] items-start gap-6">
        <RecentProducts />
        <QuickActions />
      </div>
    </>
  );
}
