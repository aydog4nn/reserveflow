# Inventory Domain Kuralları

## Stok
- Kullanılabilir stok = onHand - reserved.
- onHand ve reserved negatif olamaz.
- reserved, onHand değerini aşamaz.
- Rezervasyon sırasında onHand değişmez; reserved artar.

## Rezervasyon
- orderId ve productId zorunludur; quantity pozitif olmalıdır.
- Yalnız mevcut ve aktif ürün için rezervasyon yapılabilir.
- Yetersiz stokta işlem reddedilir; kayıtlar değişmez.
- İlk rezervasyonun durumu RESERVED olur.

## Tekrarlı ve Eşzamanlı İstekler
- İlk sürümde bir sipariş yalnız bir ürün için rezervasyon yapabilir.
- Aynı orderId, productId ve quantity tekrar gönderilirse
  mevcut rezervasyon döner; stok tekrar ayrılmaz.
- Aynı orderId farklı ürün veya miktarla gönderilirse reddedilir.
- Eşzamanlı istekler fazla stok ayrılmasına yol açmamalıdır.
- Stok güncellemesi ve rezervasyon kaydı birlikte tamamlanmalı;
  biri başarısız olursa ikisi de geri alınmalıdır.