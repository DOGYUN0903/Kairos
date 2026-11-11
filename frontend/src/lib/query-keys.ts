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
  },
  admin: {
    queue: (kind: string, params?: Params) => ["admin", "queue", kind, params ?? {}] as const,
    queueCount: (kind: string) => ["admin", "queueCount", kind] as const,
    changeRequest: (id: number) => ["admin", "changeRequest", id] as const,
    product: (id: number) => ["admin", "product", id] as const,
    sellers: (params?: Params) => ["admin", "sellers", params ?? {}] as const,
  },
} as const;
