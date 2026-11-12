import { Suspense } from "react";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { RequireAuth } from "@/auth/guards/RequireAuth";
import { ConfirmPage } from "@/features/payment/components/ConfirmPage";

export default function Page() {
  return (
    <Suspense fallback={<div className="container"><PageSkeleton /></div>}>
      <RequireAuth>
        <ConfirmPage />
      </RequireAuth>
    </Suspense>
  );
}
