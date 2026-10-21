# Payment mesajlaşma hazırlığı

## Güncel durum — üçüncü parça tamamlandı

StockFinalizedEvent(orderId, reservationId, outcome) iki serviste yerel
record'dur. Inventory stok transaction'ı tamamlandıktan sonra
inventory.committed.v1 veya inventory.released.v1 anahtarıyla
reserveflow.events exchange'ine yayınlar.
Order order.stock-finalized.learning kuyruğunu dinler; rezervasyon/ödeme
kimliği ve beklenen durum kontrolünden sonra CONFIRMED/CANCELLED yapar.
Aynı sonuç tekrarı değişiklik yapmaz; ters veya erken sonuç reddedilir.

Başarı, ret ve DELAY otomatik smoke denemesinde gerçek HTTP, RabbitMQ ve
PostgreSQL üzerinden uçtan uca doğrulandı. 10 stok/3 miktar için başarı ve
DELAY sonunda onHand=7/reserved=0; rette onHand=10/reserved=0.
Aynı HTTP anahtarının terminal tekrarında stok ve orderId değişmedi.
Deneme üç Java uygulamasını host'ta, DB/broker'ı ayrı Docker container'larında
çalıştırdı. Yerel Compose servisleri ve verileri değiştirilmedi.

Komutlar: mvn -DskipTests package; python3 scripts/smoke_message_flow.py.
Script yalnız oluşturduğu izole test container'larını temizler.
Geniş regresyon: 81 Inventory + 113 Order + 69 Payment = 263 test başarılı.
Komut:

```bash
mvn -o -B '-Dtest=*,!InventoryServiceApplicationTests' \
  -Dspring.rabbitmq.listener.simple.auto-startup=false \
  -Dspring.rabbitmq.username=test -Dspring.rabbitmq.password=test test
```

Eski InventoryServiceApplicationTests harici DB ortamı beklediği için kapsam
dışıdır; bu komut tam suite olarak sunulmaz. Otomatik broker smoke ayrıca çalıştırıldı.
Bu son sürümle kullanıcının mevcut Compose image'ları yeniden oluşturulmadı.
Canlı labda denemeden önce eski kuyruk mesajlarını incele; stok değiştirebilirler.
Sıralı başlatma: rabbitmq → inventory → payment → order; mevcut stock-reserved
kuyruğunun ilk tanımı Inventory'dedir.

DB commit/yayın arası kopukluk ve hata kurtarma eksikleri devam eder.
TECHNICAL_FAILURE ve yetersiz stok otomatik sonlandırma kapsamı dışındadır.
Aşağıdaki parça notları geliştirme geçmişidir; güncel kapsam bu bölümdür.

Order ve Payment kendi RequestPaymentCommand record'larını kullanır:
orderId, amount (BigDecimal), currency, scenario. Java servis bağımlılığı yoktur.
Tutar Order'ın sakladığı totalAmount değerinden alınacaktır.

Payment AMQP bağımlılığı, JSON converter, Compose bağlantı değişkenleri
ve şu topolojiyle hazırlanmıştır:

reserveflow.commands → payment.request.v1 → payment.request.learning

Order PaymentCommandProducer artık gerçek stok sonucu akışından çağrılır.
IOrderReservationService rezervasyonu kaydedip OrderResponse döndürür;
consumer PAYMENT_PENDING ise snapshot totalAmount/currency/paymentScenario
ile talimat yayınlar. Yayın, service transaction'ı commit edildikten sonradır.
İleri durumdaki siparişte yeniden yayın yapılmaz; PAYMENT_PENDING durumunda
aynı stok sonucu tekrarı yeniden ödeme talimatı yayınlayabilir.
PaymentCommandConsumer, gelen command'i açık Bean Validation
kontrolünden geçirip CreatePaymentRequest'e çevirir ve mevcut IPaymentService'i
çağırır. Geçersiz veri service'e ulaşmaz; teknik hata dışarı iletilir.
Consumer sonucu yalnız loglar; DELAY için bu sonuç hâlâ PENDING olabilir.
Bu bağlantı yeni image'larla çalıştırıldığında ödeme başlatır.
Kullanıcı Order → Payment talimatını Docker loglarında SUCCEEDED ile
gördüğünü bildirdi. Yeni sonuç dönüşü henüz canlı Docker'da denenmedi.

## Parça 1 — Payment sonucu Order'a döner

PaymentResultEvent, iki serviste bağımsız record olarak tanımlıdır:
orderId, paymentId, amount, currency, outcome (SUCCEEDED/FAILED), failureReason.
Payment service yerel Spring olayı üretir; PaymentResultPublisher bunu
AFTER_COMMIT aşamasında RabbitMQ'ya gönderir. PENDING sonuç yayınlamaz.
SUCCESS/DECLINE ve terminal replay sonuç üretir; DELAY scheduler gerçekten
SUCCEEDED yaptığında sonuç üretir. Rollback olan transaction yayın yapmaz.

reserveflow.events → payment.succeeded.v1/payment.failed.v1 →
order.payment-results.learning. Her iki servis bu topolojiyi tanımlar.
OrderPaymentService kilitli siparişte kimlik, tutar, para birimi ve durum
uyumunu kontrol eder. Başarı STOCK_COMMIT_PENDING, ret COMPENSATION_PENDING
yapar. Aynı sonuç tekrarı değişiklik yapmaz; çelişkili sonuç reddedilir.
Stok kararı ikinci parçada aşağıdaki gibi eklendi. CONFIRMED/CANCELLED
geçişi henüz eklenmedi.

## Parça 2 — ödeme sonucu stok kararını tetikler

Order PaymentResultConsumer, ödeme sonucu service transaction'ı tamamlanınca
STOCK_COMMIT_PENDING için inventory.commit.v1, COMPENSATION_PENDING için
inventory.release.v1 talimatı yayınlar. İleri terminal durumdaki tekrar
sonucu yeni stok talimatı oluşturmaz. DB güncellemesi/yayın atomik değildir.

StockActionCommand(orderId, reservationId) her iki serviste yerel record'dur.
reserveflow.commands exchange'i bu iki anahtarı inventory.commit.learning
ve inventory.release.learning kuyruklarına yönlendirir. Topoloji Order ve
Inventory'de ayrı tanımlıdır.

InventoryStockActionService aynı transaction içinde rezervasyonu kilitler,
orderId/reservationId eşleşmesini doğrular ve mevcut commitReservation veya
releaseReservation metodunu çağırır. Yanlış rezervasyon kimliği stok
değişikliğinden önce reddedilir.

Commit: onHand ve reserved aynı miktarda azalır. Release: yalnız reserved
azalır, onHand korunur. Ardışık tekrar işlemi ikinci kez uygulamaz; commit
sonrası release ve release sonrası commit reddedilir.

Stok işlemi sonrası Order'a bitiş olayı bu aşamada henüz gönderilmez.
Bu nedenle Order ara durumda kalır; bu beklenen geçici kapsam sınırıdır.
Yeni stok komutlarıyla canlı Docker denemesi henüz yapılmadı. Testlerde
listener kapalı, PostgreSQL izoledir; gerçek broker teslimi iddia edilmez.

AFTER_COMMIT yalnız DB commit sonrasına ertelemedir, outbox değildir.
Commit sonrası uygulama/broker hatasında sonuç kaybolabilir, DB geri alınmaz.
Otomatik retry, publisher confirm ve DLQ henüz yoktur.

DB durum geçişi ve mesaj yayını atomik değildir. Yayın hatasında
PAYMENT_PENDING ve rezervasyon DB'de kalır; otomatik kurtarma/outbox yoktur.
Önceki regresyon 38 Order + 64 Payment idi. Yeni sonuç dönüşü için
Payment modülündeki 69 test ve seçili Order testleri başarıyla çalıştırıldı.
Bu sayı tam monorepo suite'i veya gerçek broker uçtan uca testi değildir.

Compose kullanıcı/parolayı zorunlu aktarır; guest fallback broker
kullanmayan context testleri için varsayılandır, bu broker'ın hesabı değildir.
default-requeue-rejected=false; DLQ/retry olmadığından hatalı mesaj kaybolabilir.
Yalnız lab verisi kullanılmalı; DTO annotation'ları tek başına listener
validation başlatmadığından consumer'a Validator enjekte edilmiştir.

Testler JSON'u Order type header'ına rağmen Payment'ın somut hedef
record'una çevirme ve topoloji sözleşmesini broker olmadan doğrular.
Bu gerçek broker teslimi testi değildir.

Payment modülündeki 69 test başarılıdır. Consumer → gerçek service →
izole PostgreSQL testleri kayıt, ardışık tekrar, geçersiz veri ve teknik
hatada kayıtsız kalma davranışını doğrular. Testlerde Rabbit listener
başlangıcı kapalıdır; geliştirme kuyruğuna bağlanılmaz. Sonuç dönüşü
değişikliğinden sonra Docker image henüz yeniden oluşturulmadı.
