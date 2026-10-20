# Order mesaj gönderme laboratuvarı

Bu endpoint gerçek sipariş oluşturmaz, stok ayırmaz ve Order service akışına bağlı değildir.
Authentication yoktur; yalnız localhost geliştirme ortamında kullanılır.
Inventory consumer inventory.reserve.learning kuyruğunu dinler ve yalnız log yazar.
Stok service'ini çağırmaz; stok ve rezervasyon tablolarını değiştirmez.

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
"Stok ayırma mesajı geldi: ReserveStockCommand[...]" satırıyla doğrula.
Yalnız Ready azalması işleme kanıtı değildir. Gerçek iş verisi gönderme.

Bitirince MESSAGING_LAB_ENABLED=false yapıp order container'ını yeniden oluştur.
Varsayılan false'tur; kapalıyken endpoint kayıt edilmez (404).
Bu labda publisher confirm/return, retry ve outbox/inbox yoktur.
Consumer logdan döndükten sonra listener AUTO ack kullanır; bu,
projedeki stok işleminin tamamlandığı anlamına gelmez.
Unit/MockMvc testleri gerçek broker teslimi testi değildir.
