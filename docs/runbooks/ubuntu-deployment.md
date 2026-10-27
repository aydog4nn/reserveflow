# Ubuntu üzerinde manuel GHCR dağıtımı

## Amaç ve sınırlar

Jenkins'in test edip GHCR'ye yayımladığı image'ları Ubuntu ARM64 laboratuvarında yeniden derlemeden çalıştırmak. Bu manuel dağıtımdır; Ansible veya otomatik CD değildir. Aynı VM'de Jenkins de çalışır: yaklaşık 3.3 GiB RAM ile CI ve kalıcı ortamı aynı anda çalıştırmayın. Dağıtım öncesinde `free -h`, `df -h /` ve `docker ps` ile kaynakları/portları kontrol edin.

## Dosya yerleşimi

Repodaki `compose.deploy.yaml`, `infra/postgres/` ve secretsiz `.env.deploy.example` dosyalarını kullanın. Mevcut Ubuntu ortamına ilk denemede `postgres/` doğrudan kopyalanmıştı; repodaki sürümle standart yerleşim aşağıdaki gibidir:

```text
~/reserveflow-deploy/
  compose.deploy.yaml
  .env
  infra/postgres/
```

Mac'teki repo kökünden, SSH port yönlendirmesi 2222 ise:

```bash
ssh -p 2222 vboxuser@127.0.0.1 'mkdir -p ~/reserveflow-deploy/infra && chmod 700 ~/reserveflow-deploy'
scp -P 2222 compose.deploy.yaml vboxuser@127.0.0.1:~/reserveflow-deploy/
scp -P 2222 -r infra/postgres vboxuser@127.0.0.1:~/reserveflow-deploy/infra/
```

Mevcut `.env` dosyasını değiştirmeyin veya üzerine yazmayın. Yeni kurulumda Ubuntu'daki bu dosyayı `.env.deploy.example` alanlarına göre oluşturun; ayrı rastgele parolalar kullanın ve `chmod 600 .env` uygulayın. Dosya Git'e gönderilmez. Veritabanı/RabbitMQ ilk kurulum parolalarının dosyada değiştirilmesi mevcut hesapların parolalarını kendiliğinden güncellemez.

## Image seçimi ve indirme

Üç image için GHCR'de bulunan aynı `IMAGE_TAG` değerini seçin. İlk doğrulanan dağıtım `ci-22-debc7c777f9b` sürümüdür; `latest` kullanılmaz. Etiket sabitliği registry tarafından zorlanmaz; daha sıkı sürüm sabitlemesi için digest kullanımı sonraki adımdır. Bu image'lar ARM64 labda üretildi; amd64 uyumluluğu/multi-platform yayın doğrulanmadı.

Ubuntu'da `read:packages` yetkili ayrı GitHub classic token ile giriş yapın; token'ı parola sorusuna girin, komut argümanına veya Git'e yazmayın:

```bash
docker login ghcr.io --username aydog4nn
cd ~/reserveflow-deploy
docker compose -f compose.deploy.yaml config --quiet
docker compose -f compose.deploy.yaml pull
docker compose -f compose.deploy.yaml up -d --no-build
docker compose -f compose.deploy.yaml ps -a
docker compose -f compose.deploy.yaml logs --tail 60 inventory order payment
```

`db-setup` için `Exited (0)` beklenir. PostgreSQL/RabbitMQ healthy ve her uygulama için `Started ...Application` görülmelidir. Bu kontroller tek başına iş akışını kanıtlamaz. `config --quiet` kullanın; normal `config` çıktısı çözümlenmiş parolaları gösterebilir. Docker credential helper yoksa giriş dosyası şifrelenmez; indirme sonrasında `docker logout ghcr.io` ile kayıtlı giriş kaldırılabilir, tekrar pull için yeniden giriş gerekir.

## Ağ ve veri

- Inventory: Ubuntu `127.0.0.1:18000`; Order: Ubuntu `127.0.0.1:18001`.
- Payment, PostgreSQL ve RabbitMQ için host portu açılmaz. Uygulamalar Compose ağı üzerinde servis adlarıyla konuşur.
- Mac'teki localhost Ubuntu'nun localhost'u değildir. Mac Postman erişimi için ayrı terminalde tünel açın:

```bash
ssh -N -o ExitOnForwardFailure=yes -p 2222 \
  -L 127.0.0.1:18000:127.0.0.1:18000 \
  -L 127.0.0.1:18001:127.0.0.1:18001 \
  vboxuser@127.0.0.1
```

- Proje adı `reserveflow-deploy`; volume'lar bu proje altında ayrılır. CI ortamından bağımsızdır.
- `restart: unless-stopped` kullanılır. Bu, Docker çalışırken yeniden başlatma politikasıdır; uygulama readiness veya otomatik yedekleme sağlamaz.
- JVM heap `-Xmx256m` ile sınırlandırılır; toplam container belleği 256 MiB ile sınırlanmış değildir. Log rotasyonu dosya başına 10 MiB, üç dosya olarak ayarlıdır.

## İş akışı kontrolü

Ubuntu'da veya SSH tüneli açık Mac'te yeni ve benzersiz SKU ile ürün oluşturun:

```bash
curl -i http://127.0.0.1:18000/api/v1/products \
  -H 'Content-Type: application/json' \
  -d '{"sku":"VM-DEMO-002","name":"Ubuntu Test Klavyesi","unitPrice":1500,"currency":"TRY","initialStock":10}'
```

Dönen ürün kimliğini aşağıda `PRODUCT_ID` yerine yazın:

```bash
curl -i http://127.0.0.1:18001/api/v1/orders \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: vm-demo-success-002' \
  -d '{"productId":"PRODUCT_ID","quantity":3,"paymentScenario":"SUCCESS"}'
```

Birkaç saniye sonra aynı gövde ve anahtarla tekrar gönderin: aynı orderId ve `CONFIRMED` beklenir. Ürünü GET ile sorgulayın:

```bash
curl -s http://127.0.0.1:18000/api/v1/products/PRODUCT_ID
```

Beklenen `onHand=7`, `reserved=0`, `available=7`. Ardından `docker compose -f compose.deploy.yaml restart` çalıştırın; servislerin açılmasını bekleyip aynı ürünü tekrar sorgulayın. Bu yalnız restart sonrası korunumu gösterir; volume silinmesi, makine kaybı veya yedekten dönüş testi değildir.

## Gözlenen sonuç — 27 Ekim 2026

Kullanıcının paylaştığı çıktılarda üç uygulamanın açılışı, Flyway migration'ları ve RabbitMQ bağlantıları doğrulandı. `a0abb649-e569-40ce-922f-2944f3cb36f4` ürünüyle miktar 3 SUCCESS siparişi önce PENDING_STOCK, ardından CONFIRMED oldu. Kullanıcı stok sonucunun 7/0/7 olduğunu ve restart sonrasında aynı kaldığını bildirdi. Bu Ubuntu ortamında DECLINE/DELAY, yeniden oluşturma, VM reboot ve restore henüz doğrulanmadı; bu senaryoların CI smoke testinde geçmesi ayrı kanıttır.

## Durdurma

```bash
docker compose -f compose.deploy.yaml stop
```

Bu veri silmez. `down` container/ağı kaldırır ama named volume'ları korur. **`down -v` verileri siler; bu dağıtımda kullanmayın.** Henüz yedekleme ve geri yükleme prosedürü uygulanmadı.
