import type { ApiEntry, Order, OrderRequest, Product } from "./types";

export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
  ) {
    super(message);
  }
}
let sequence = 0;
let onEntry: (entry: ApiEntry) => void = () => {};
export function setApiObserver(observer: typeof onEntry) {
  onEntry = observer;
}

async function request<T>(
  service: "inventory" | "order",
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const started = performance.now();
  const method = init.method || "GET";
  let status: ApiEntry["status"] = "Ağ hatası";
  try {
    const response = await fetch(`/${service}-api${path}`, {
      ...init,
      signal: AbortSignal.timeout(10000),
    });
    status = response.status;
    const text = await response.text();
    let data: unknown;
    try {
      data = text ? JSON.parse(text) : null;
    } catch {
      data = null;
    }
    if (!response.ok) {
      const problem = data as {
        detail?: string;
        errors?: Record<string, string>;
      } | null;
      const detail =
        problem?.detail ||
        (response.status === 404
          ? "Endpoint veya kayıt bulunamadı. Frontend için eklenen okuma API’lerinin sunucuda güncel olduğundan emin olun."
          : `${service === "inventory" ? "Inventory" : "Order"} yanıtı: HTTP ${response.status}. Servis adresini ve sunucu loglarını kontrol edin.`);
      throw new ApiError(
        [detail, ...Object.values(problem?.errors || {})].join(" "),
        response.status,
      );
    }
    if (data === null)
      throw new ApiError(
        "Servisten beklenen JSON yanıtı alınamadı.",
        response.status,
      );
    return data as T;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    throw new ApiError(
      "Servise ulaşılamadı. Backend’i başlatın ve frontend proxy adreslerini kontrol edin.",
      0,
    );
  } finally {
    onEntry({
      id: ++sequence,
      method,
      path: `${service}${path}`,
      status,
      at: new Date().toISOString(),
      duration: Math.round(performance.now() - started),
    });
  }
}
export const api = {
  products: () => request<Product[]>("inventory", "/api/v1/products?limit=100"),
  product: (id: string) =>
    request<Product>("inventory", `/api/v1/products/${encodeURIComponent(id)}`),
  order: (id: string) =>
    request<Order>("order", `/api/v1/orders/${encodeURIComponent(id)}`),
  createOrder: (body: OrderRequest, key: string) =>
    request<Order>("order", "/api/v1/orders", {
      method: "POST",
      headers: { "Content-Type": "application/json", "Idempotency-Key": key },
      body: JSON.stringify(body),
    }),
  createProduct: (body: {
    sku: string;
    name: string;
    unitPrice: number;
    currency: string;
    initialStock: number;
  }) =>
    request<Product>("inventory", "/api/v1/products", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    }),
};
