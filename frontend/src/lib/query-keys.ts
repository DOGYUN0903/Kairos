type Params = Record<string, unknown> | undefined;

export const queryKeys = {
  auth: {
    me: ["auth", "me"] as const,
  },
  catalog: {
    categories: ["catalog", "categories"] as const,
    brands: ["catalog", "brands"] as const,
    productsRoot: ["catalog", "products"] as const,
    products: (params?: Params) => ["catalog", "products", params ?? {}] as const,
    product: (id: number) => ["catalog", "product", id] as const,
    brand: (id: number) => ["catalog", "brand", id] as const,
  },
  seller: {
    me: ["seller", "me"] as const,
    brands: ["seller", "brands"] as const,
    products: (params?: Params) => ["seller", "products", params ?? {}] as const,
    product: (id: number) => ["seller", "product", id] as const,
    changeRequest: (productId: number, id: number) => ["seller", "product", productId, "changeRequest", id] as const,
  },
  cart: ["cart"] as const,
  checkout: {
    /** 모든 견적(접두사). 장바구니가 바뀌면 통째로 무효화한다. */
    quotes: ["checkout", "quote"] as const,
    /** benefit 이 없으면 넷째 원소를 넣지 않는다(`quotes`·기존 키의 접두사가 계속 맞는다). */
    quote: (items: unknown, benefit?: unknown) =>
      (benefit === undefined ? ["checkout", "quote", items] : ["checkout", "quote", items, benefit]) as readonly unknown[],
  },
  orders: {
    root: ["orders"] as const,
    list: (params?: Params) => ["orders", "list", params ?? {}] as const,
    detail: (id: number) => ["orders", "detail", id] as const,
    cancelPreview: (orderId: number, itemId: number) => ["orders", orderId, "cancelPreview", itemId] as const,
  },
  sellerOrders: {
    /** 모든 목록·건수(접두사). */
    lists: ["sellerOrders", "list"] as const,
    counts: ["sellerOrders", "count"] as const,
    list: (params?: Params) => ["sellerOrders", "list", params ?? {}] as const,
    detail: (id: number) => ["sellerOrders", "detail", id] as const,
    count: (status: string) => ["sellerOrders", "count", status] as const,
  },
  adminOrders: {
    /** 모든 목록(접두사). */
    lists: ["adminOrders", "list"] as const,
    list: (params?: Params) => ["adminOrders", "list", params ?? {}] as const,
    detail: (id: number) => ["adminOrders", "detail", id] as const,
  },
  admin: {
    queue: (kind: string, params?: Params) => ["admin", "queue", kind, params ?? {}] as const,
    queueCount: (kind: string) => ["admin", "queueCount", kind] as const,
    changeRequest: (id: number) => ["admin", "changeRequest", id] as const,
    seller: (id: number) => ["admin", "seller", id] as const,
    brand: (id: number) => ["admin", "brand", id] as const,
    product: (id: number) => ["admin", "product", id] as const,
    sellers: (params?: Params) => ["admin", "sellers", params ?? {}] as const,
  },
  coupons: {
    policiesRoot: ["coupons", "policies"] as const,
    policies: (productId?: number) => ["coupons", "policies", productId ?? null] as const,
    mineRoot: ["coupons", "mine"] as const,
    mine: (params?: Params) => ["coupons", "mine", params ?? {}] as const,
  },
  points: {
    root: ["points"] as const,
    summary: ["points", "summary"] as const,
    transactions: (params?: Params) => ["points", "transactions", params ?? {}] as const,
  },
  reviews: {
    productRoot: (productId: number) => ["reviews", "product", productId] as const,
    product: (productId: number, page: number) => ["reviews", "product", productId, page] as const,
    mineRoot: ["reviews", "mine"] as const,
    mine: (params?: Params) => ["reviews", "mine", params ?? {}] as const,
  },
  likes: {
    root: ["likes"] as const,
    list: (params?: Params) => ["likes", "list", params ?? {}] as const,
  },
  sellerCoupons: {
    lists: ["sellerCoupons", "list"] as const,
    list: (params?: Params) => ["sellerCoupons", "list", params ?? {}] as const,
  },
  sellerReviews: {
    lists: ["sellerReviews", "list"] as const,
    list: (params?: Params) => ["sellerReviews", "list", params ?? {}] as const,
  },
  adminCoupons: {
    lists: ["adminCoupons", "list"] as const,
    list: (params?: Params) => ["adminCoupons", "list", params ?? {}] as const,
  },
  adminMemberPoints: (memberId: number, params?: Params) => ["adminMemberPoints", memberId, params ?? {}] as const,
} as const;
