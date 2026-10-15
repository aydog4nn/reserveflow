# ADR-001: Tek Repo ve Bağımsız Servisler

## Durum
Kabul edildi. Hedef mimaridir; şu an yalnız Inventory geliştiriliyor.

## Bağlam
ReserveFlow ile stok rezervasyonu üzerinden backend geliştirme,
servisler arası iletişim ve DevOps yaşam döngüsünü öğreniyoruz.

## Karar
- Inventory, Order ve Payment Simulator aynı Git reposunda tutulacak.
- Her servis bağımsız build edilip dağıtılabilecek.
- Her servis kendi veritabanına sahip olacak.
- Servisler birbirinin tablolarına doğrudan erişmeyecek.

## Alternatif
Modüler monolit geliştirmek daha basit olurdu. Ancak bağımsız
dağıtım ve dağıtık sistem davranışlarını öğrenmek için
mikroservis yaklaşımı seçildi.

## Sonuçlar
- Kod ve altyapı tanımları tek repoda takip edilebilecek.
- Servisler ayrı ayrı test edilip dağıtılabilecek.
- Ağ hataları, veri tutarlılığı ve operasyon yönetimi zorlaşacak.
- Bu yapı öğrenme amacı taşır; her proje için mikroservis önerisi değildir.