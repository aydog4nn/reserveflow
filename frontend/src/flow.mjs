export const TERMINAL = new Set(["CONFIRMED", "CANCELLED", "REJECTED"]);
export const STATES = {
  PENDING_STOCK: {
    title: "Stok bekleniyor",
    detail: "Order kaydedildi. Inventory rezervasyon sonucu bekleniyor.",
    step: 0,
    service: "inventory",
    tone: "waiting",
  },
  PAYMENT_PENDING: {
    title: "Ödeme bekleniyor",
    detail: "Stok rezervasyonu alındı. Payment sonucu bekleniyor.",
    step: 1,
    service: "payment",
    tone: "waiting",
  },
  STOCK_COMMIT_PENDING: {
    title: "Stok kesinleştiriliyor",
    detail: "Ödeme başarılı. Inventory stok kesinleştirme sonucu bekleniyor.",
    step: 2,
    service: "inventory",
    tone: "waiting",
  },
  COMPENSATION_PENDING: {
    title: "Rezervasyon bırakılıyor",
    detail: "Ödeme reddedildi. Inventory stok bırakma sonucu bekleniyor.",
    step: 2,
    service: "inventory",
    tone: "waiting",
  },
  CONFIRMED: {
    title: "Sipariş onaylandı",
    detail: "Order, stok kesinleştirme sonucunu aldı ve siparişi onayladı.",
    step: 3,
    service: "order",
    tone: "success",
  },
  CANCELLED: {
    title: "Sipariş iptal edildi",
    detail:
      "Order, ayrılan stoğun bırakıldığı sonucunu aldı ve siparişi iptal etti.",
    step: 3,
    service: "order",
    tone: "danger",
  },
  REJECTED: {
    title: "Sipariş reddedildi",
    detail: "Order API siparişin reddedildiğini bildiriyor.",
    step: 3,
    service: "order",
    tone: "danger",
  },
};
export function describeStatus(status) {
  return (
    STATES[status] || {
      title: status || "Henüz sipariş yok",
      detail: "Bir ürün seçip akışı başlatın.",
      step: -1,
      service: "order",
      tone: "idle",
    }
  );
}
export function recordObservation(
  history,
  order,
  at = new Date().toISOString(),
) {
  if (!order || history.at(-1)?.status === order.status) return history;
  return [...history, { status: order.status, at }];
}
export function guideSequence(scenario) {
  return scenario === "DECLINE"
    ? ["PENDING_STOCK", "PAYMENT_PENDING", "COMPENSATION_PENDING", "CANCELLED"]
    : ["PENDING_STOCK", "PAYMENT_PENDING", "STOCK_COMMIT_PENDING", "CONFIRMED"];
}
export function expectedStock(initial, quantity, scenario) {
  return scenario === "DECLINE" ? initial : initial - quantity;
}
