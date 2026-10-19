# Servis veritabanları ve erişim sınırları

## Amaç

Tek PostgreSQL 17 sunucusunda üç ayrı uygulama veritabanı tutulur:

| Servis | Veritabanı | Kullanıcı | Parola değişkeni |
|---|---|---|---|
| Inventory | inventory_db | inventory_app | INVENTORY_DB_PASSWORD |
| Order | order_db | order_app | ORDER_DB_PASSWORD |
| Payment | payment_db | payment_app | PAYMENT_DB_PASSWORD |

DB_PASSWORD yalnız PostgreSQL yöneticisi ve db-setup içindir.
Uygulamalara yönetici parolası verilmez. Her uygulamanın kendi DB_PASSWORD
ortam değişkenine ilgili servis parolası aktarılır.

## Çalıştırma sırası

1. PostgreSQL bağlantıya hazır olur.
2. Tek seferlik db-setup işi kullanıcı, veritabanı, erişim ve sahiplik
   ayarlarını uygular.
3. db-setup başarıyla tamamlanınca uygulamalar başlar.
4. Her uygulamanın Flyway'i kendi veritabanına bağlanır.

Kurulum yalnız docker-entrypoint-initdb.d mekanizmasına bağlı değildir.
Bu mekanizma dolu volume'da tekrar çalışmadığı için mevcut ve yeni
ortamlarda aynı db-setup işi kullanılır.

## Mevcut verilerle ilk geçiş

Git'e eklenmeyen .env dosyasında .env.example içindeki dört değişkeni
ayrı, boş olmayan parolalarla tanımla. DB_PASSWORD mevcut PostgreSQL
yönetici parolasıyla aynı kalmalıdır.

Önce mevcut uygulamaları durdur; veritabanını ve volume'u silme:

```bash
docker compose stop inventory order payment
docker compose up -d --wait postgres
mkdir -p backups
(umask 077; docker compose exec -T postgres pg_dump -U postgres -d inventory_db -Fc > backups/inventory-before-service-databases.dump)
docker compose run --rm db-setup
docker compose run --rm --entrypoint /bin/sh db-setup /provision/verify-access.sh
docker compose up -d --build
```

backups/ Git dışında tutulur. Yedekleme dosya adını sonraki geçişlerde
değiştirerek eski yedekleri koru. Yedeğin alınmış olması, restore
tatbikatının tamamlandığı anlamına gelmez.

provision-role.sql eksik rol/veritabanlarını oluşturur; mevcut olanları
silmez. schema-ownership.sql yalnız ilgili veritabanının public
şemasındaki mevcut tabloların ve sequence'lerin sahipliğini değiştirir.
REASSIGN OWNED gibi başka veritabanlarının sahipliğini etkileyebilecek
genel bir komut kullanılmaz. Inventory V1 migration dosyası değiştirilmez.

Kurulum tekrar çalıştırılabilir. CREATE DATABASE ve veritabanları arası
adımlar tek bir transaction değildir; hata oluşursa uygulamalar başlamaz,
sebep düzeltildikten sonra kurulum tekrar çalıştırılır. Şema sahipliği
adımları her veritabanı için transaction içindedir.

## Kontrolün anlamı

```bash
docker compose run --rm --entrypoint /bin/sh db-setup /provision/verify-access.sh
```

Script gerçek TCP/parola bağlantısı kullanır; yerel socket trust
doğrulamasına dayanmaz. Üç başarılı kendi-DB bağlantısını, altı
reddedilmiş diğer-servis-DB bağlantısını, CONNECT yetkilerini ve
uygulama rollerinin superuser/createdb/createrole/replication/bypassrls
yetkileri bulunmadığını kontrol eder.

Veritabanları varsayılan PUBLIC CONNECT erişimi kaldırılarak ayrılır.
Bu sınır üç uygulama veritabanını kapsar; PostgreSQL yöneticisi
bilinçli olarak hepsine erişebilir. Varsayılan postgres/template
yönetim veritabanlarının erişim politikası bu kapsamda değiştirilmez.

## Flyway ve model durumu

Inventory'nin mevcut migration'ı ve verileri korunur.
Order'da V1__create_orders_table.sql ile orders tablosu hazırlanmıştır.
Payment'ta JPA, PostgreSQL ve Flyway bağlantısı hazırdır; henüz domain
tablosu veya V1 SQL migration yoktur. Payment için Flyway'in sıfır
sürümlü migration bulması beklenir.
ddl-auto=validate şema oluşturmaz ve migration yerine geçmez.

## Güvenlik sınırları

- Uygulama kullanıcıları cluster yöneticisi değildir.
- Labda Flyway ve runtime aynı servis rolünü kullanır; bu rol kendi
  veritabanı/şemasının sahibidir ve DDL yapabilir. Production için
  ayrı migration rolü ve yalnız gereken DML yetkili runtime rolü gerekir.
- Parolalar .env içinde düz metindir; secret manager değildir.
- db-setup ortam değişkenleri yöneticidir; container inceleme yetkisi
  olan kişiler bunları görebilir. Gerçek production secret çözümü yoktur.
- Şifre değişiminde .env tek başına mevcut rol parolasını değiştirmez;
  db-setup tekrar çalıştırılıp uygulamalar yeniden oluşturulmalıdır.
- Hiçbir komutta down -v veya volume silme kullanılmaz.

## Otomatik test

```bash
mvn -f services/inventory-service/pom.xml -Dtest=DatabaseProvisioningTest test
```

Testcontainers ayrı PostgreSQL açar, mevcut tablo/veri simüle eder,
kurulumu iki kere uygular, erişim matrisini ve verinin korunduğunu
doğrular. Geliştirme volume'una dokunmaz.
