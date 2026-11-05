# Frontend demo doğrulaması — 5 Kasım 2026

## Kapsam

Türkçe React arayüzü; gerçek ürün kataloğu, sipariş oluşturma/sorgulama, ödeme senaryoları, durum açıklamaları, etkileşimli servis/sınıf diyagramı ve sunum rehberi.

## Çalıştırılan kontroller

- TypeScript kontrolü, kullanılmayan değişken/parametre kontrolü ve Vite üretim build'i başarılı.
- Akış kuralları için 3 frontend testi başarılı. Ret sonucunun stok bırakılmadan terminal sayılmaması ve gözlenmeyen durumların günlüğe eklenmemesi kontrol edildi.
- Inventory/Order yeni okuma endpoint'leri ve mevcut POST controller'ları için 44 test başarılı.
- Üç servisin Maven paketlemesi başarılı.
- Ayrı geçici PostgreSQL/RabbitMQ ortamında gerçek HTTP + mesajlaşma smoke testi: SUCCESS → CONFIRMED / 7-0-7; DECLINE → CANCELLED / 10-0-10; DELAY → CONFIRMED / 7-0-7. Her senaryoda aynı idempotency anahtarıyla tekrar, aynı sipariş kimliğini ve değişmeyen stoğu verdi.
- Tarayıcıda gerçek DELAY siparişi ve aynı isteğin tekrarı doğrulandı. Ayrıca ayrı üründe DECLINE sonrası CANCELLED ve 10-0-10 görüldü. Yenileme sonrası son sipariş kimliği tekrar sorgulanarak geri geldi.
- Rehber modunun başarı/ret/gecikme yolları ve ardışık örneklerde stok hesabı kontrol edildi. Bu mod API kullanmaz ve açıkça örnek olarak etiketlenir.
- 320 ve 390 piksel genişliklerde yatay taşma yok; masaüstü yerleşimi de görsel olarak incelendi.
- Frontend Docker image build'i başarılı. Geçici Nginx container'ında ana sayfa 200, Inventory proxy'si gerçek katalog 200 ve Order proxy'si gerçek sipariş 200 döndürdü.

## Sınırlar

Diyagram dağıtık tracing değildir: son Order durumunu açıklar. Kısa ara durumlar sorgular arasında atlanabilir. Payment gerçek para çekmez. Stok yetersizliğinde otomatik ret akışı mevcut backend'de tamamlanmamıştır. Katalog en fazla 100 ürün okur.

Frontend, Jenkins/Ansible dağıtımına bu değişiklikle otomatik eklenmedi. Compose overlay isteğe bağlıdır. Geçici lab kalıcı VM verilerini kullanmaz; kapatılınca kendi test verilerini temizler. Bu kontroller genel internete açık üretim güvenliği veya yük testi kapsamına girmez.
