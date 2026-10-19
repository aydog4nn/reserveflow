# Migration durumu

V1__create_orders_table.sql sipariş tablosunu, veri kısıtlarını ve
status/updated_at indeksini oluşturur. Ürün, rezervasyon ve ödeme
kimlikleri başka servislere ait UUID referanslarıdır; servisler arası
foreign key yoktur. V2__create_request_idempotency_table.sql, müşteri ve
anahtarın birlikte benzersiz olduğu request_idempotency tablosunu ekler;
order_id aynı servis içindeki orders tablosuna foreign key'dir.
Service entegrasyonu ve outbox henüz tamamlanmadı.
