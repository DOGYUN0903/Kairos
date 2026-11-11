"use client";

import { createContext, useContext } from "react";

/** 서버 409 SELLER_NOT_ACTIVE 같은 콘솔 공통 안내를 ConsoleShell 배너 슬롯에 띄운다. */
export const SellerBannerContext = createContext<(message: string | null) => void>(() => {});

export function useSellerBanner(): (message: string | null) => void {
  return useContext(SellerBannerContext);
}
