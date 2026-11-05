export type Scenario = "SUCCESS" | "DECLINE" | "DELAY";
export type OrderStatus =
  | "PENDING_STOCK"
  | "PAYMENT_PENDING"
  | "STOCK_COMMIT_PENDING"
  | "COMPENSATION_PENDING"
  | "CONFIRMED"
  | "CANCELLED"
  | "REJECTED";
export type ServiceId = "order" | "inventory" | "payment" | "rabbitmq";
export interface Product {
  id: string;
  sku: string;
  name: string;
  unitPrice: number;
  currency: string;
  active: boolean;
  onHand: number;
  reserved: number;
  available: number;
}
export interface Order {
  orderId: string;
  productId: string;
  quantity: number;
  unitPrice: number;
  totalAmount: number;
  currency: string;
  paymentScenario: Scenario;
  status: OrderStatus;
  reservationId: string | null;
  paymentId: string | null;
  failureReason: string | null;
  createdAt: string;
  updatedAt: string;
}
export interface OrderRequest {
  productId: string;
  quantity: number;
  paymentScenario: Scenario;
}
export interface Attempt {
  key: string;
  body: OrderRequest;
  before: Product;
  orderId?: string;
}
export interface Observation {
  status: OrderStatus;
  at: string;
}
export interface ApiEntry {
  id: number;
  method: string;
  path: string;
  status: number | "Ağ hatası";
  at: string;
  duration: number;
}
