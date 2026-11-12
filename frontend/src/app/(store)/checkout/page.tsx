import { Suspense } from "react";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { RequireAuth } from "@/auth/guards/RequireAuth";
import { CheckoutPage } from "@/features/checkout/components/CheckoutPage";

export default function Page() {
  return (
    <Suspense fallback={<div className="container"><PageSkeleton /></div>}>
      <RequireAuth>
        <CheckoutPage />
      </RequireAuth>
    </Suspense>
  );
}
