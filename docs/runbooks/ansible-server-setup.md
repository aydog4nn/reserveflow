# Ansible sunucu hazırlığı

## Kapsam

`infra/ansible/setup.yaml`, Ubuntu üzerinde Docker'ın resmi APT anahtarını/deposunu, gerekli paketleri ve Docker servisinin çalışma/açılış durumunu yönetir. `deploy.yaml` ise önceden hazırlanan uygulama yapılandırmasını taşıyıp Compose ortamını çalıştırır. Sunucu hazırlığı ile uygulama dağıtımı ayrı playbook'lardır.

SSH, Python ve sudo yetkisi başlangıçta mevcut olmalıdır. İnternet/DNS erişimi ve Ubuntu sürümünün Docker deposunda desteklenmesi gerekir. İlk paket listesi güncellemesi ve kurulumlar ağ erişimi gerektirebilir. Bu playbook VM oluşturmaz, GHCR kimlik bilgilerini veya uygulama parolalarını hazırlamaz, Docker grubu üyeliği eklemez.

## Kontrol makinesi ve bağımlılıklar

Komutları Mac'te repo kökünden çalıştırın. `community.docker` koleksiyonu Homebrew Ansible paketinde bulunurken ayrı ansible-lint ortamında görünmüyordu. Koleksiyon bağımlılığı `infra/ansible/requirements.yaml` içinde 5.4.0 olarak tanımlandı; lint ortamının görebileceği kullanıcı koleksiyon dizinine de kurulmalıdır.

```bash
ansible-galaxy collection install -r infra/ansible/requirements.yaml
ansible-playbook -i infra/ansible/inventory.yaml infra/ansible/setup.yaml --syntax-check
ansible-playbook -i infra/ansible/inventory.yaml infra/ansible/deploy.yaml --syntax-check
ansible-lint infra/ansible/
```

Farklı Python ortamları kullanan araçlarda koleksiyon görünürlüğünü ayrı doğrulayın. Mac'teki Homebrew araçları Python 3.15 kullanıyor ve ansible-core 2.21.5, destek üst sınırının Python 3.14 olduğunu bildiriyor. Yerel kontroller geçti ancak bu destek uyarısı çözülmüş sayılmaz. Jenkins'te hazırlanan ayrı ortam Python 3.14 kullanır; onun yerel Mac kurulumu ile aynı ortam olduğu varsayılmaz.

## Homelab bağlantısı ve sudo

Inventory, Mac'in `127.0.0.1:2222` portundan `vboxuser` ile Ubuntu'ya bağlanır. Jenkins Ubuntu üzerinde çalıştığından aynı bağlantı adresi Jenkins'ten dağıtım için doğrudan kullanılamaz.

Ubuntu 26.04 labda varsayılan `/usr/bin/sudo`, sudo-rs 0.2.13 sürümüne yönleniyordu. Ansible'ın parola ile yetki geçişi zaman aşımına uğradı; aynı kullanıcının normal SSH/sudo kontrolü başarılıydı. Mevcut `/usr/bin/sudo.ws` ile Ansible `id -u` denemesi 0 döndürdü. Bu yüzden yalnız `homelab` host'una `ansible_become_exe: /usr/bin/sudo.ws` eklendi. Sistem sudo varsayılanı değiştirilmedi, paket kaldırılmadı ve parolasız yönetici yetkisi verilmedi.

Bu yol başka sunuculara körlemesine kopyalanmamalıdır; hedefte varlığı ve uygunluğu doğrulanmalıdır. SSH ve become parolaları komut satırından etkileşimli alınır; Git'e yazılmaz.

## Önizleme ve uygulama

Jenkins build'i çalışmıyorken önce önizleyin:

```bash
ansible-playbook -i infra/ansible/inventory.yaml infra/ansible/setup.yaml \
  --ask-pass --ask-become-pass --check --diff
```

Salt okuma yapan mimari sorgusu `check_mode: false` ile önizlemede de çalışır. Diğer görevlerin check modu tam bir kurulum simülatörü değildir; özellikle `get_url` önizlemede gerçekte uygulanmayacak bir değişiklik raporlayabilir. Temiz sunucuda önizleme, önceki görevlerin gerçekten oluşturmadığı dosyalar nedeniyle ayrıca sınırlıdır.

Değişiklikleri inceleyip kabul ettikten sonra gerçek çalıştırma:

```bash
ansible-playbook -i infra/ansible/inventory.yaml infra/ansible/setup.yaml \
  --ask-pass --ask-become-pass
```

Mevcut Docker depo tanımlarını önceden inceleyin; aynı depo farklı dosyalarda veya anahtar yollarıyla tanımlanmışsa otomatik silmeyin. Bu labda `docker.sources`, resolute/arm64/stable ve `/etc/apt/keyrings/docker.asc` tanımı zaten vardı. `state: present` zorunlu sürüm yükseltmesi değildir ancak eksik paket kurulumu bağımlılık değişikliği veya servis başlangıcı oluşturabilir.

## DNS olayı ve geçici çözüm

İlk önizlemede Ubuntu `download.docker.com` adını çözemedi. Kullanıcının Ubuntu terminalinde `enp0s8` için geçici 1.1.1.1 ve 8.8.8.8 DNS ayarı yapması ve önbelleği temizlemesi sonrasında sorgu başarılı oldu. Bu ayar playbook'a veya kalıcı ağ yapılandırmasına eklenmedi; yeniden başlatma/ağ yenilemesi sonrasında korunacağı varsayılmaz.

Tekrar aynı hata olursa Ubuntu'da `resolvectl status` ve `timeout 10s resolvectl query download.docker.com` ile kontrol edin. Kurumsal/VPN ağında genel DNS sunucuları uygun olmayabilir; kalıcı DNS seçimi mevcut ağ politikasına göre ayrıca yapılmalıdır. Geçici arayüz ayarını geri almak için `sudo resolvectl revert enp0s8` kullanılabilir. Bu işlem eski DNS sorununu yeniden ortaya çıkarabilir; bir onarım komutu değildir.

## Doğrulanan ve doğrulanmayan sonuçlar

- Kullanıcının paylaştığı mevcut Ubuntu önizlemesi: `ok=9 changed=1 failed=0`.
- Aynı mevcut Ubuntu üzerinde gerçek uygulama: `ok=9 changed=0 failed=0`; Docker zaten kurulu ve hedef durumla uyumluydu.
- Yerel lint ve iki playbook için syntax kontrolleri geçti. Güncel bağımlılık/sudo değişikliklerinin Jenkins sonucu henüz doğrulanmadı.
- Temiz Ubuntu'ya ilk Docker kurulumu, ikinci sunucuda tekrar kurulum ve tam secrets/bootstrap akışı henüz test edilmedi.
- Jenkins yalnız koleksiyon hazırlığı, syntax ve lint kontrolü yapar; `setup.yaml` veya `deploy.yaml` görevlerini sunucuya uygulamaz.
