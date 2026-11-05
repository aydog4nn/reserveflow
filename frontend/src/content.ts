import type { Product, Scenario, ServiceId } from "./types";

export const scenarios: { id: Scenario; label: string; hint: string }[] = [
  {
    id: "SUCCESS",
    label: "Başarılı",
    hint: "Ödeme alınır, ayrılan stok kesinleşir.",
  },
  {
    id: "DECLINE",
    label: "Reddedilen",
    hint: "Ödeme reddedilir, ayrılan stok bırakılır.",
  },
  {
    id: "DELAY",
    label: "Gecikmeli",
    hint: "Payment yaklaşık 10 sn bekler; sonra başarı akışını tamamlar.",
  },
];
export const demoProducts = [
  {
    name: "Mekanik klavye",
    sku: "KEY",
    unitPrice: 1500,
    currency: "TRY",
    initialStock: 10,
  },
  {
    name: "Kablosuz kulaklık",
    sku: "AUDIO",
    unitPrice: 2400,
    currency: "TRY",
    initialStock: 10,
  },
  {
    name: "Kablosuz mouse",
    sku: "MOUSE",
    unitPrice: 850,
    currency: "TRY",
    initialStock: 10,
  },
];
export const guideProducts: Product[] = demoProducts.map((p, i) => ({
  ...p,
  id: `guide-${i}`,
  sku: `REHBER-${p.sku}`,
  active: true,
  onHand: 10,
  reserved: 0,
  available: 10,
}));
export const serviceDetails: Record<
  ServiceId,
  {
    label: string;
    role: string;
    why: string;
    path: string;
    code: string;
    steps: string[];
  }
> = {
  order: {
    label: "Order Service",
    role: "Sürecin koordinatörü",
    why: "Siparişin durumu Order’da tutulur. Diğer servislerin sonuçlarını dinler ve sonraki komutu üretir: bu yapı orchestration tabanlı saga’dır.",
    path: "services/order-service/src/main/java/com/aydog4nn/reserveflow/order/service/impl/OrderServiceImpl.java",
    code: "OrderController.createOrder(...)\n  → OrderServiceImpl.createOrder(...)\n  → HttpInventoryClient.getProduct(...)\n  → OrderPersistenceService.save(...)\n  → InventoryCommandProducer.sendReserveStock(...)",
    steps: [
      "HTTP isteğini ve Idempotency-Key başlığını alır.",
      "Inventory’den ürünün aktifliğini ve fiyatını HTTP ile okur.",
      "Siparişi ve tekrar isteği kaydını veritabanına yazar.",
      "Stok, ödeme ve sonlandırma sonuçlarıyla sipariş durumunu ilerletir.",
    ],
  },
  inventory: {
    label: "Inventory Service",
    role: "Ürün ve stok sahibi",
    why: "Stok kuralları tek serviste kalır. Rezervasyon ürünleri geçici ayırır; ödeme sonucuna göre commit veya release işlemi uygulanır.",
    path: "services/inventory-service/src/main/java/com/aydog4nn/reserveflow/inventory/service/impl/InventoryServiceImpl.java",
    code: "InventoryCommandConsumer\n  → InventoryServiceImpl.reserveStock(...)\n  → InventoryEventProducer.sendStockReserved(...)\n\nStockActionConsumer\n  → InventoryStockActionService\n  → StockFinalizedProducer",
    steps: [
      "Ürünün fiyatını ve stok bilgisini sunar.",
      "ReserveStock komutuyla stok ayırır.",
      "Commit komutuyla satışı kesinleştirir; release ile rezervasyonu bırakır.",
      "Sonuç olayını Order’a gönderir.",
    ],
  },
  payment: {
    label: "Payment Service",
    role: "Ödeme simülatörü",
    why: "Gerçek bir bankaya bağlanmadan başarı, ret ve gecikme davranışlarını tekrar edilebilir biçimde gösterir. Ödeme sonucu siparişin son hâli değildir; stok sonucu da beklenir.",
    path: "services/payment-service/src/main/java/com/aydog4nn/reserveflow/payment/service/impl/PaymentServiceImpl.java",
    code: "RequestPayment komutu\n  → Payment servisi\n  → SUCCESS / DECLINE / DELAY\n  → payment.succeeded.v1\n     veya payment.failed.v1\n  → Order: PaymentResultConsumer",
    steps: [
      "Order’dan ödeme talimatını alır.",
      "Seçilen senaryoya göre ödeme kaydını işler.",
      "Gecikmede scheduler tamamlayana kadar sonuç bekler.",
      "Sonucu RabbitMQ üzerinden Order’a bildirir.",
    ],
  },
  rabbitmq: {
    label: "RabbitMQ",
    role: "Komut ve olay taşıyıcısı",
    why: "Producer mesajı exchange’e verir. Binding, routing key’e göre mesajı kuyruğa yönlendirir. Consumer kuyruğu dinler; servislerin aynı HTTP çağrısında birbirini beklemesi gerekmez.",
    path: "services/order-service/src/main/java/com/aydog4nn/reserveflow/order/messaging/producer/InventoryCommandProducer.java",
    code: "rabbitTemplate.convertAndSend(\n    RabbitMqConfiguration.COMMANDS_EXCHANGE,\n    RabbitMqConfiguration.RESERVE_STOCK_ROUTING_KEY,\n    command\n);\n\nProducer → Exchange → Queue → Consumer",
    steps: [
      "reserveflow.commands: yapılacak işleri taşır.",
      "reserveflow.events: işlem sonuçlarını taşır.",
      "Kuyruk isimleri ve binding kuralları topology/config sınıflarında tanımlıdır.",
      "Bu ekran broker mesajlarını dinlemez; sipariş durumunu Order API’den sorgular.",
    ],
  },
};
export const presentationSteps = [
  {
    title: "Üç servis, üç sorumluluk",
    text: "Inventory stoktan, Payment ödeme simülasyonundan, Order siparişin ilerlemesinden sorumlu. Her servis kendi veritabanını kullanıyor; laboratuvarda aynı PostgreSQL sunucusunda duruyorlar.",
  },
  {
    title: "Önce HTTP, sonra mesajlaşma",
    text: "Order fiyat ve aktiflik bilgisini HTTP ile alıyor. Stok ayırma, ödeme ve stok sonlandırma komutları RabbitMQ üzerinden ilerliyor.",
  },
  {
    title: "Neden rezervasyon var?",
    text: "Ödeme beklenirken ürün başkasına satılmasın diye stok ayrılıyor. Ayırmak fiziksel stoğu azaltmaz; satılabilir miktarı azaltır.",
  },
  {
    title: "Başarı ve telafi",
    text: "Başarılı ödemede commit, reddedilen ödemede release uygulanıyor. Order ancak Inventory sonlandırma sonucunu aldıktan sonra CONFIRMED veya CANCELLED oluyor.",
  },
  {
    title: "Tekrar istek ve dürüst gözlem",
    text: "Aynı anahtar ve aynı içerik aynı siparişe dönüyor. Ekran düzenli GET istekleriyle gözlediği durumları kaydediyor; hızlı ara geçişleri görmeyebilir. Bu bir dağıtık tracing sistemi değil.",
  },
];
