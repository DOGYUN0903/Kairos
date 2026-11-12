import { Suspense } from "react";
import { PageSkeleton } from "@/components/common/PageSkeleton";
import { RequireAuth } from "@/auth/guards/RequireAuth";
import { FailPage } from "@/features/payment/components/FailPage";

export default function Page() {
  return (
    <Suspense fallback={<div className="container"><PageSkeleton /></div>}>
      <RequireAuth>
        <FailPage />
      </RequireAuth>
    </Suspense>
  );
}
