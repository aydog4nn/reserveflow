# ReserveFlow Akış Laboratuvarı

Türkçe, responsive demo arayüzü. Gerçek ürünleri listeler, sipariş oluşturur ve sipariş durumunu Order API üzerinden düzenli sorgular. Servis diyagramı, kod bağlantıları ve beş adımlı sunum rehberi aynı uygulamadadır.

## 1. Frontend'i başlat

Node.js 22.12+ veya 24 kullanın. Proje kökünden:

```bash
npm --prefix frontend ci
npm --prefix frontend run dev
```

Adres: http://127.0.0.1:5173

Varsayılan mod **Canlı servisler**. Geliştirme ve preview sunucusu şu istekleri proxy eder:

| Tarayıcı yolu | Hedef |
| --- | --- |
| `/inventory-api/api/v1/products` | `http://127.0.0.1:8080/api/v1/products` |
| `/order-api/api/v1/orders` | `http://127.0.0.1:8081/api/v1/orders` |

Tarayıcı ve API'ler aynı origin üzerinden haberleştiği için servislerde genel CORS izni açılmaz. Vite/preview proxy'si geliştirme içindir; üretim Nginx yapılandırması aşağıdadır.

Hedefleri değiştirmek için `frontend/.env.example` dosyasını `frontend/.env.local` olarak kopyalayın ve adresleri düzenleyin. Değişiklikten sonra Vite'ı yeniden başlatın. Parola/token veya `VITE_*` secret tanımlamayın; tarayıcı backend parolalarını kullanmaz.

### Ubuntu VM'deki API'lere Mac'ten erişim

Ubuntu'daki backend'in bu daldaki yeni okuma endpoint'lerini içeren sürümle çalışıyor olması gerekir. Eski `ci-22` image'ları bu endpoint'leri içermez.

Mac'te ayrı terminalde:

```bash
ssh -p 2222 -N -o ExitOnForwardFailure=yes \
  -L 127.0.0.1:19000:127.0.0.1:18000 \
  -L 127.0.0.1:19001:127.0.0.1:18001 \
  vboxuser@127.0.0.1
```

`frontend/.env.local`:

```dotenv
INVENTORY_API_URL=http://127.0.0.1:19000
ORDER_API_URL=http://127.0.0.1:19001
```

Bu bağlantı için Jenkins'in açık olması gerekmez. Inventory, Order, Payment, PostgreSQL ve RabbitMQ çalışmalıdır. Gerçek ödeme servisine bağlanılmaz; Payment mevcut simülatördür.

### Mevcut sunucuya dokunmadan geçici gerçek backend

Docker Desktop ve Java 21 açıkken, proje kökünden:

```bash
mvn -DskipTests package
python3 scripts/frontend_lab.py
```

Bu yardımcı, benzersiz isimli iki geçici PostgreSQL/RabbitMQ container'ı ve üç Java servisi başlatır; üç demo ürünü oluşturur. API'ler 8080/8081/8082 portlarında, yalnız localhost üzerinde açılır. Kullanılan portlardan biri doluysa mevcut servise dokunmadan durur. Kendi rastgele lab parolalarını kullanır; projenin `.env` veya Vault dosyasını okumaz. **Ctrl+C ile bu çalıştırmanın verileri silinir**; kalıcı sunucudaki verilere bağlanılmaz.

Gerçek HTTP endpoint'leri, üç ödeme senaryosu ve tekrar isteğini otomatik sınamak için:

```bash
python3 scripts/frontend_lab.py --smoke
```

Test sonunda geçici ortam temizlenir. `--smoke --serve` verilirse kontrolden sonra açık kalır. Bu komut ayrıca üç `ZZ API doğrulama ...` ürünü oluşturur; yalnız geçici lab içindir.

## 2. Demo ürünleri ve sunum

1. **Canlı servisler** modunda Inventory bağlantısını doğrulayın.
2. **Demo ürünlerini oluştur / Yeni demo ürünleri ekle** düğmesi üç yeni ürünü, her birinde 10 başlangıç stoğuyla gerçek API'ye POST eder. SKU'lar benzersizdir. İşlem otomatik yapılmaz; mevcut stok veya ürünleri silmez/sıfırlamaz.
3. Klavyeden 3 adet ve **Gecikmeli** senaryosunu seçin. Payment beklerken fiziksel 10, ayrılan 3, satılabilir 7 değerlerini gösterebilirsiniz.
4. Tamamlandığında CONFIRMED ve 7/0/7 beklenir. Eşzamanlı başka işlemler varsa stok farklı olabilir.
5. Yeni bir demo ürünüyle **Reddedilen** senaryoyu çalıştırın. CANCELLED ve 10/0/10 beklenir. Aynı üründe önceki başarılı satışlar varsa fiziksel stok önceki seviyesine döner; tekrar 10 olmaz.
6. Tamamlanan siparişte **Aynı isteği tekrar gönder** ile aynı anahtar, aynı içerik ve aynı sipariş kimliğini gösterin.
7. Servis kutularına tıklayarak sorumlulukları ve gerçek sınıf adlarını açıklayın. **Mimari & kod** ve **Sunum rehberi** sekmeleri sunuma eşlik eder.

## 3. Rehberli örnek

Sunucular kapalıyken **Rehberli örnek** seçilebilir. Doğrudan adres: http://127.0.0.1:5173/?mode=guide

Bu mod açıkça etiketlenir, yalnız bellekte örnek veri kullanır ve API çağrısı yapmaz. **Sonraki adımı göster** ile ilerler. Payment gecikmesi bu modda gerçek bir 10 saniyelik bekleme değildir. Gerçek backend kesilirse uygulama kendiliğinden rehber moduna geçmez.

## 4. Bu dalda eklenen backend endpoint'leri

| Servis | Endpoint | Davranış |
| --- | --- | --- |
| Inventory | `GET /api/v1/products?limit=50` | Varsayılan 50, en fazla 100 ürün; ad ve kimliğe göre sıralı. Stoklar toplu okunur. |
| Order | `GET /api/v1/orders/{orderId}` | Kalıcı kayıttaki güncel OrderResponse; bulunamazsa 404 / ORDER_NOT_FOUND. İş akışı başlatmaz. |

Sipariş oluşturma ve ürün oluşturma için mevcut POST endpoint'leri kullanılır. Order'ın mevcut client-id/idempotency kapsamı değişmez. GET endpoint'leri de mevcut localhost laboratuvar API'leri gibi authentication içermez; genel internete açılacak bir erişim modeli değildir.

## 5. Gözlemin sınırları

- Canlı mod yaklaşık 1,25 saniyede bir sipariş durumunu okur. Yalnız gözlenen durum değişimleri günlüğe eklenir; atlanan ara durumlar veya mesaj zamanları uydurulmaz.
- Diyagramdaki servis vurgusu son sipariş durumundan türetilen bir açıklamadır; dağıtık tracing, broker mesajı veya servis sağlığı ölçümü değildir.
- Bekleyen siparişte en fazla 45 saniyelik değişimsiz izleme veya üç ardışık okuma hatası sonunda izleme duraklar. Bu, siparişi iptal etmez. Kullanıcı yeniden izlemeyi başlatabilir.
- POST hatasında istek gövdesi ve Idempotency-Key korunur; sessizce yeni sipariş yaratılmaz. Son istek bilgisi sekme oturumunda saklanır. Sayfa yenilendiyse önceki sipariş kimliğiyle izlemeye dönülebilir. Bu, backend idempotency kaydının yerini tutmaz.
- Stok yetersizliğinde Order'a otomatik ret olayı mevcut backend'de tamamlanmamıştır. UI bilinen satılabilir stoğu aşmayı engeller; eşzamanlı stok hareketleri bu kontrolü geçersiz kılabilir. Bekleyen sipariş durumunda servis logları incelenmelidir.
- `GET` sorguları ve HTTP kayıt paneli, yalnız bu tarayıcının yaptığı istekleri gösterir.

## 6. Kontroller

```bash
npm --prefix frontend test
npm --prefix frontend run build
mvn -B -pl services/inventory-service,services/order-service \
  '-Dtest=ProductCatalogControllerTest,ProductControllerTest,OrderQueryControllerTest,OrderControllerTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Build TypeScript kontrolünü ve Vite üretim build'ini içerir. Derlenmiş dosyalar `frontend/dist` içinde, Git dışında tutulur. `npm --prefix frontend run preview` aynı API proxy ayarlarını kullanarak http://127.0.0.1:4173 adresinde build'i gösterir.

## 7. Docker Compose ile isteğe bağlı çalıştırma

Backend image'ları güncel okuma endpoint'lerini içermelidir. Mevcut dağıtım `.env` dosyası ve uygun `IMAGE_TAG` hazırlanmışken proje kökünden:

```bash
docker compose -f compose.deploy.yaml -f compose.frontend.yaml up -d --build frontend
```

Frontend http://127.0.0.1:18002 üzerinde açılır. Nginx, container ağı üzerinden `inventory:8080` ve `order:8081` adreslerine proxy eder. Veritabanı bilgileri frontend image'ına veya JavaScript'e eklenmez.

Frontend image'ı ve bu overlay mevcut Jenkins/Ansible hattına otomatik eklenmemiştir. Bu komut isteğe bağlı ayrı çalıştırmadır; mevcut backend dağıtımını değiştiren bir pipeline değişikliği yapılmamıştır. Docker image build'i Docker daemon açıkken ayrıca doğrulanmalıdır.

## Kodda nereden başlamalı?

- `src/App.tsx`: uygulama kabuğu, sekmeler ve ortak seçim durumu.
- `src/components/Composer.tsx`: ürün ve ödeme senaryosu seçimi; sipariş formu.
- `src/components/Results.tsx`: stok, sipariş kaydı ve gözlenen durumlar.
- `src/components/Presentation.tsx`: beş adımlı sunum rehberi.
- `src/components/HttpLog.tsx`: tarayıcı HTTP kayıt paneli.
- `src/useDemo.ts`: gerçek istekler, anahtarın korunması, sayfa yenilendiğinde son siparişin tekrar sorgulanması, polling ve rehber modu.
- `src/api.ts`: Inventory/Order HTTP bağlantısı ve güvenli hata gösterimi.
- `src/components/FlowMap.tsx`: servislere göre etkileşimli mimari diyagramı.
- `src/components/Inspector.tsx`, `src/content.ts`: servis sorumlulukları ve sınıf bağlantıları.
- `src/flow.mjs`: durum açıklamaları ve saf akış kuralları.
- `src/styles.css`: responsive yerleşim, tasarım token'ları ve reduced-motion desteği.
