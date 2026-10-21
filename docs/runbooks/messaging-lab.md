# Order mesaj gönderme laboratuvarı

## Devam eden aşama — 21 Ekim 2026

Order InventoryEventConsumer artık IOrderReservationService'i çağırır.
Gerçek sipariş DB'den kilitlenerek bulunur; yeni geçerli sonuç reservationId'yi
kaydeder ve PENDING_STOCK → PAYMENT_PENDING geçişi yapar. Aynı reservationId
tekrarı değişiklik yapmaz; farklı rezervasyon veya uygunsuz durum reddedilir.
Başarı logu service döndükten sonra yazılır, hata dışarı iletilir.
Gerçek createOrder artık DB kaydı tamamlandıktan sonra PENDING_STOCK siparişi
için ReserveStockCommand yayınlar. Aynı HTTP anahtarıyla tekrar çağrıda sipariş
hâlâ PENDING_STOCK ise talimat yeniden yayınlanır; PAYMENT_PENDING veya daha
ileri durumlarda yayın yapılmaz. Ödeme başlamaz.
İlgili 34 test başarılıdır: gerçek PostgreSQL kayıt/sonuç geçişleri, tekrarlar,
eş zamanlı idempotency ve mock producer/consumer davranışı. Mesaj yayınının
aktif DB transaction dışında ve order/idempotency commit'inden sonra çağrıldığı
test edildi. Broker hatasında kayıt kalır; aynı anahtarla manuel tekrar
yeniden yayınlayabilir. Bu otomatik retry, outbox veya exactly-once değildir.
Gerçek RabbitMQ kullanan otomatik uçtan uca test bu kapsamda yoktur.
Eski rastgele lab orderId'leri Order DB'de yoksa artık işlenemez. Bu nedenle
canlı deneme için önce gerçek sipariş kaydı oluşturulmalıdır.
Bu aşama henüz Docker'da denenmedi. Aşağıdaki yalnız-log açıklamaları önceki
kontrol noktasına aittir. DB commit/mesaj yayını ve hata kurtarma sınırları sürer.

Bu endpoint gerçek sipariş oluşturmaz ve Order service akışına bağlı değildir.
Inventory consumer artık reserveStock çağırır: mesaj tüketildiğinde stok rezervasyonu
gerçekten yapılır. Yalnız ayrılmış test ürünü ve yeni orderId ile dene.
Authentication yoktur; yalnız localhost geliştirme ortamında kullanılır.
Inventory consumer inventory.reserve.learning kuyruğunu dinler, mevcut
transactional stok service'ini çağırır ve başarıdan sonra log yazar.
Service hatası yutulmaz; başarı logu yazılmaz. AUTO ack, service proxy'si
commit edip listener normal döndükten sonra gönderilir.

Inventory hazırlığı: AMQP bağımlılığı, Compose bağlantı değişkenleri,
aynı queue/exchange/binding tanımları ve JSON converter eklidir.
Inventory kendi messaging.dto.ReserveStockCommand record'unu kullanır;
Order Java projesine bağımlılık yoktur. Listener metodu bu somut
record tipini alır; converter bu hedef tipi kullanarak Order'dan
gelen JSON'u Inventory modeline çevirir. Bu dönüşüm broker olmadan test edildi.
Kullanıcı Docker image'larını yeniden oluşturduktan sonra Postman'dan mesaj
gönderdi, kuyruğa ulaştığını ve Inventory logunda aynı UUID/miktarlarla
ReserveStockCommand alındığını doğruladı. Bu manuel canlı denemedir;
otomatik uçtan uca broker testi değildir.

Compose kullanıcı/parolayı zorunlu olarak Inventory'ye aktarır. Properties
içindeki guest fallback yalnız kimlik bilgisi verilmeyen, broker kullanmayan
test/context başlangıçları içindir; reserveflow broker'ına giriş bilgisi değildir.
Hatalı mesajların sonsuz dönmesini engellemek için requeue-rejected kapalıdır;
DLQ henüz yoktur, bu nedenle yalnız gözden çıkarılabilir lab mesajları kullanılır.

## Açma

Yerel, Git'e alınmayan .env dosyasına MESSAGING_LAB_ENABLED=true ekle.
RABBITMQ_USERNAME ve RABBITMQ_PASSWORD mevcut broker hesabıyla eşleşmeli.

Proje kökünde:

```bash
docker compose up -d rabbitmq
docker compose up -d --build order
docker compose up -d --build inventory
```

Postman: POST http://localhost:8081/api/v1/lab/messages/reserve-stock
Content-Type: application/json

```json
{
  "orderId": "99999999-9999-4999-8999-999999999999",
  "productId": "22222222-2222-4222-8222-222222222222",
  "quantity": 3
}
```

202 yalnız gönderim çağrısının döndüğünü gösterir; broker confirm, kalıcı teslim
veya stok rezervasyonu garantisi değildir. Broker bağlantı hatası 503,
geçersiz istek 400 olur. ID'lerin veritabanında varlığı bu labda kontrol edilmez.

RabbitMQ UI'de reserveflow vhost'u, inventory.reserve.learning kuyruğunu kontrol et.
Exchange reserveflow.commands, routing key inventory.reserve.v1 kullanılır.
Inventory consumer çalışırken Ready hızla sıfıra düşebilir. Mesajın alındığını
`docker compose logs --tail=50 inventory` çıktısındaki
"Stok rezervasyonu tamamlandı: ReserveStockCommand[...]" satırıyla doğrula.
Yalnız Ready azalması işleme kanıtı değildir. Gerçek iş verisi gönderme.

Bitirince MESSAGING_LAB_ENABLED=false yapıp order container'ını yeniden oluştur.
Varsayılan false'tur; kapalıyken endpoint kayıt edilmez (404).
Bu labda publisher confirm/return, retry ve outbox/inbox yoktur.
Service DB commit'i sonrası ACK öncesi crash olursa mesaj tekrar gelebilir.
Ardışık aynı sipariş/içerik service düzeyinde mevcut rezervasyona döner.
Eş zamanlı ilk istek yarışı ve başarısız mesajın kurtarılması henüz çözülmedi.
requeue-rejected=false ve DLQ yokluğu nedeniyle hata alan mesaj kaybolabilir.
Sonuç dönüşü hazırlığı: consumer artık service'ten dönen gerçek orderId ve
reservationId ile StockReservedEvent oluşturup InventoryEventProducer çağırır.
Exchange reserveflow.events, routing key inventory.reserved.v1, kuyruk
order.stock-reserved.learning olarak tanımlıdır. Order InventoryEventConsumer
kendi StockReservedEvent tipiyle sonucu alıp yalnız loga yazar; sipariş DB'si
güncellenmez ve ödeme başlamaz.
20 Ekim 2026'da kullanıcı Inventory ve Order image'larını yeniden oluşturup
önceki başarılı rezervasyon komutunu aynı orderId/productId/quantity ile
tekrar gönderdi. Order logunda "Stok ayrıldı sonucu geldi:
StockReservedEvent[orderId=..., reservationId=...]" görüldüğünü doğruladı.
Bu, lab endpoint → Inventory rezervasyon service'i → sonuç yayını → Order
log akışının manuel canlı kanıtıdır; gerçek sipariş/ödeme saga akışı değildir.
Bu son denemede stok GET değeri ayrıca raporlanmadı; önceki ardışık tekrar
canlı denemesi ve izole PostgreSQL testleri çift rezervasyon kontrolünü kapsar.
İlgili 22 test başarıyla tamamlandı; otomatik gerçek broker uçtan uca testi yoktur.

Yeni tanımlar önce Inventory tarafından oluşturulduğundan canlı deneme
başlatma sırası: rabbitmq çalışır durumda olmalı, ardından Inventory image'ı
build edilip başlatılmalı. Inventory loglarında broker bağlantısı başarılı
olmalı ve RabbitMQ'da order.stock-reserved.learning kuyruğu görünmeli.
Sonra Order image'ı build edilip başlatılır. Bu manuel sıra Compose tarafından
henüz zorunlu kılınmaz. Order consumer kuyruk yokken başlatılırsa hata alabilir.

Lab komutundan sonra Order loglarında "Stok ayrıldı sonucu geldi:
StockReservedEvent[...]" beklenir. Önceki test ürününün aynı orderId/miktarı
ile tekrar komut mevcut rezervasyona döner ve tekrar sonuç yayınlayabilir.
Bu tekrar sonuçları Order şimdilik yalnız logladığından business deduplication
kanıtı sayılmaz.
Rezervasyon commit'i ile mesaj yayınlama atomik değildir. Broker gönderimi
hata verirse rezervasyon DB'de kalır, hata listener'a çıkar. Mevcut ayarda
komut kaybolabilir; outbox/retry olmadan otomatik kurtarma garantisi yoktur.
İzole PostgreSQL testinde bu arıza ve elle tekrar çağrıda çift rezervasyon
olmaması doğrulandı; gerçek broker arızası veya otomatik retry testi değildir.
Unit/MockMvc testleri gerçek broker teslimi testi değildir.

Consumer → gerçek service → izole PostgreSQL testleri rezervasyon, ardışık
tekrar, çelişkili içerik ve yetersiz stokta rollback'i doğrular. Bu testlerde
Rabbit listener otomatik başlangıcı kapalıdır; geliştirme kuyruğu tüketilmez.
## Canlı stok rezervasyonu kanıtı — 20 Ekim 2026

Kullanıcı Inventory image'ını yeniden oluşturup API üzerinden 10 stoklu
test ürünü oluşturdu: a993c58b-02f1-48f0-a9ed-3ceaf7c7fa41.
Lab endpoint'ine orderId=aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa,
bu productId ve quantity=3 gönderildi.
Ürün GET yanıtında onHand=10, reserved=3, available=7 doğrulandı.
Aynı mesaj ardışık tekrar gönderildiğinde GET değerleri aynı kaldı.
Bu manuel Docker/Postman denemesidir; otomatik broker/crash testi değildir.
Yeni denemelerde ayrı ürün/orderId kullan; yukarıdaki mevcut kaydın
aynı olacağını veya stoğun değişmediğini varsayma.
