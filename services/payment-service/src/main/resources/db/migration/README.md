# Migration durumu

`V1__create_payments_table.sql`, Payment modelinin payments tablosunu oluşturur.
order_id benzersizdir; Order servisine foreign key veya JPA ilişkisi yoktur.
Tutar, para birimi, senaryo ve durum DB kısıtlarıyla korunur.
status/ready_at indeksi ileride bekleyen gecikmeli ödemelerin taranması içindir.

Model ve migration hazırdır; SUCCESS/DECLINE/DELAY service akışı,
ardışık tekrar isteği kontrolü ve gecikme scheduler'ı eklendi.
TECHNICAL_FAILURE özel exception ile simüle edilir; yeni kayıt oluşmaz.
POST/GET lab endpoint'leri eklendi. Otomatik retry, eş zamanlı ilk istek
replay'i ve RabbitMQ/outbox henüz uygulanmadı.
Migration izole PostgreSQL testinde doğrulanır; yerel Compose veritabanına
uygulamak için Payment image'ının yeniden build edilip başlatılması gerekir.
