# Migration durumu

V1__create_orders_table.sql sipariş tablosunu, veri kısıtlarını ve
status/updated_at indeksini oluşturur. Ürün, rezervasyon ve ödeme
kimlikleri başka servislere ait UUID referanslarıdır; servisler arası
foreign key yoktur. Outbox ve HTTP idempotency tabloları henüz eklenmedi.
