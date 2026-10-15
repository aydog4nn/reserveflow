# Öğrenme Ortamı Altyapı Planı

## Ortam
- Önce yerel geliştirme, ardından tek Ubuntu Server VM.
- Başlangıç VM bütçesi: 2 vCPU, 4 GB RAM, 30  GB disk.
- Kaynak kullanımı ölçülerek gerekirse artırılacak.
- Şimdilik ücretli bulut kaynağı oluşturulmayacak.

## Aşamalar
1. Docker Compose ile uygulama ve bağımlılıklarını çalıştırmak.
2. Jenkins ile test, build ve image üretimini otomatikleştirmek.
3. Seçilen VM sağlayıcısında Terraform ile altyapıyı tanımlamak.
4. Ansible ile Linux yapılandırmasını otomatikleştirmek.
5. k3s üzerinde servisleri dağıtmak.
6. Prometheus ve Grafana ile gözlemlemek.

## Sınırlar
- Tek VM yüksek erişilebilirlik sağlamaz.
- Veritabanı ve yönetim arayüzleri internete açılmayacak.
- Parolalar ve erişim anahtarları Git'e eklenmeyecek.
- Terraform sağlayıcısı, VM platformu seçildiğinde belirlenecek.