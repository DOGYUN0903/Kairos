export const checkoutHref = {
  fromCart: (cartItemIds: number[]): string => `/checkout?cartItemIds=${cartItemIds.join(",")}`,
  direct: (skuId: number, qty: number): string => `/checkout?skuId=${skuId}&qty=${qty}`,
};
