# Intranet Portal — Ürün ve Geliştirme Roadmap’i

> Bu belge ürün kapsamını, onaylanmış iş kararlarını ve gelecekte yapılacak işleri takip eder.  
> Çalışma ve kodlama standartları `AGENTS.md`, tamamlanan çalışmalar `PROGRESS.md`, endpoint sözleşmeleri `API_SPEC.md` içindedir.

## 1. Görev Durumları

- `[ ]` Başlanmadı
- `[-]` Kısmen tamamlandı veya devam ediyor
- `[x]` Tamamlandı

Her modül tamamlandığında bu belge ve `PROGRESS.md` güncellenir.

Bir entity veya DTO’nun bulunması, ilgili modülün tamamlandığı anlamına gelmez. Bir özellik; gerekli repository, service, controller, güvenlik kontrolleri ve testleriyle birlikte çalıştığında tamamlanmış sayılır.

İş isteri başlangıç kapsamıdır; yeni ihtiyaçlara göre roadmap değiştirilebilir.

## 2. Projenin Hedefi

Şirket çalışanlarının günlük intranet ihtiyaçlarını tek uygulamada toplamak:

- kimlik doğrulama ve rol bazlı erişim,
- personel profili ve ekip dizini,
- yıllık izin yönetimi,
- ekipman talebi, envanter ve zimmet,
- toplantı odası rezervasyonları,
- duyurular, yorumlar, beğeniler ve takdir panosu,
- role göre dashboard,
- HR personel işlemleri,
- Admin sistem yönetimi.

## 3. Mevcut Durum

### 3.1 Tamamlanan temel

- [x] Spring Boot + MySQL backend iskeleti
- [x] React + TailAdmin frontend iskeleti
- [x] JWT login
- [x] 15 dakikalık access token
- [x] 14 günlük httpOnly refresh cookie
- [x] Refresh token rotation
- [x] Refresh token reuse detection
- [x] Login/logout/token olayları için audit altyapısı
- [x] Login ve refresh endpoint’leri için rate limit
- [x] JWT filter ve Spring Security stateless authentication temeli
- [x] Süresi dolmuş refresh token’ları temizleyen scheduled metot
- [x] Frontend `ProtectedRoute` ve `PublicRoute`
- [x] Frontend sessiz refresh ve single-flight akışı
- [x] Frontend logout temel akışı
- [x] Dashboard özet endpoint’inin temeli
- [x] Dashboard doğum günü backend sorgusu
- [x] Doğum günü kartının frontend API bağlantısı
- [x] Doğum günü kartının loading, error ve empty durumları
- [x] Personel entity alanlarının genişletilmesi:
    - mezun olunan okul,
    - mezun olunan bölüm,
    - mezuniyet yılı,
    - işe giriş tarihi,
    - işten çıkış tarihi,
    - aktif/pasif durumu
- [x] Orijinal kapsamın entity ve DTO iskeletleri:
    - personel ve profil,
    - izin,
    - ekipman ve zimmet,
    - rezervasyon,
    - duyuru,
    - yorum ve beğeni,
    - takdir panosu,
    - lookup yapıları
- [x] Mockito unit-test altyapısı ve mevcut Auth/RefreshToken/Audit testleri
- [x] MySQL 8 Testcontainers ve `AbstractIntegrationTest` altyapısı

### 3.2 Kısmen tamamlanan alanlar

- [-] Rol altyapısı
    - Rol entity’si mevcut.
    - JWT içinden Spring Security authority oluşturuluyor.
    - HR rolü ve Admin rol değiştirme akışı hazır; diğer modüllerin rol yetkilendirmeleri henüz tamamlanmadı.
- [-] Personel ve profil veri modeli
    - Entity ve DTO hazırlıkları mevcut.
    - HR personel oluşturma/güncelleme ile ilk/normal parola değiştirme akışları hazır; profil, ekip dizini ve HR pasifleştirme API’leri bekliyor.
- [-] Dashboard
    - Doğum günü bölümü gerçek veriye bağlı.
    - Duyurular, izin, oda doluluğu, takdir ve talepler placeholder.
- [-] Takvim frontend temeli
    - `/calendar` rotası ve TailAdmin takvim şablonu mevcut.
    - Gerçek API bağlantısı ve ürün verileri henüz yok.
- [-] Ekip dizini frontend temeli
    - Sidebar’da “Ekip Dizini” etiketi mevcut.
    - Sayfa hâlâ Basic Tables demo verisini gösteriyor.
- [-] Talepler frontend ayrımı
    - Menüde “İzin Talepleri” ve “Ekipman Talepleri” başlıkları mevcut.
    - Semantik rotalar ve gerçek talep sayfaları henüz yok.
- [-] TailAdmin ürün markalaması
    - JForce logosu bazı alanlarda kullanılıyor.
    - Demo ekranları, metinleri ve asset’leri hâlâ bulunuyor.
- [-] Test kapsamı
    - Authentication, rol değiştirme, HR personel oluşturma/güncelleme ve parola akışları unit ve gerçek MySQL HTTP/integration testleriyle korunuyor.
    - İzin, ekipman, rezervasyon, duyuru ve frontend testleri henüz yok.

### 3.3 Tamamlanmayan ana kapsam

- [ ] HR personel pasifleştirme
- [ ] Çalışan profil endpoint’leri
- [ ] Ekip dizini API’si ve gerçek frontend ekranı
- [ ] Güvenli profil fotoğrafı yükleme
- [ ] Yıllık izin hakkı ve tatil takvimi
- [ ] İzin talep ve onay akışı
- [ ] Ekipman talebi, envanter ve zimmet akışı
- [ ] Oda rezervasyonu iş akışı
- [ ] Duyurular ve takım duyuruları
- [ ] Yorum, beğeni ve takdir panosu iş akışları
- [ ] Dashboard’un kalan gerçek verileri
- [ ] Role göre frontend menüsü
- [ ] TailAdmin şablonunun ürün ekranlarına dönüştürülmesi

## 4. Onaylanan Ürün Kararları

### 4.1 MVP rolleri

MVP’de sabit roller:

- `PERSONEL`
- `YONETICI`
- `HR`
- `ADMIN`

MVP’de personel başına tek rol yaklaşımı korunur.

Dinamik rol ve permission sistemi MVP sonrasına bırakılır.

### 4.2 Rol atama ve oturum

- Yalnızca Admin rol atayabilir veya değiştirebilir.
- HR hiçbir rolü atayamaz.
- Rol değiştiğinde kullanıcının bütün refresh token’ları iptal edilir.
- JWT yapısı MVP’de stateless kalır.
- Eski access token en fazla kalan 15 dakikalık süresi boyunca çalışabilir.
- `tokenVersion` ile anında access token iptali MVP sonrası güvenlik iyileştirmesidir.

### 4.3 Personel ekleme ve çıkarma

HR:

- personel ekleyebilir,
- personel bilgilerini güncelleyebilir,
- departman ve yönetici atayabilir,
- personeli pasife alabilir.

Personel hard delete ile silinmez.

Pasife alma işleminde:

- `calisiyorMu = false`,
- `isCikisTarihi = işlem tarihi`,
- refresh token’ların iptali,
- yeni login ve refresh işlemlerinin engellenmesi,
- aktif ekip dizininden çıkarılması,
- geçmiş kayıtların korunması

uygulanır.

### 4.4 Takım ve takım duyurusu

- Takım, `Personel.yonetici` alanında yöneticiye doğrudan bağlı çalışanlardır.
- Yönetici yalnızca kendi doğrudan takımına duyuru oluşturabilir.
- Duyuruyu yönetici ve doğrudan çalışanları görebilir.
- Aynı departmandaki diğer çalışanlar göremez.
- Dolaylı alt ekipler MVP kapsamında değildir.
- HR ve Admin bütün takım duyurularını denetim/moderasyon amacıyla görebilir.
- Hedef kitle kontrolü backend tarafından uygulanır.

### 4.5 Yıllık izin hakkı

- İzin hakkı 1 Ocak’ta sıfırlanmaz.
- Personel her tamamladığı hizmet yılı için kıdemine göre yeni hak kazanır.
- Başlangıç kural seti 14/20/26 gündür.
- Kullanılmayan haklar sonraki yıllara devreder.
- Kıdem hesabında `isGirisTarihi` kullanılır.
- Kullanılan yıllık izin birikmiş toplam haktan düşülür.
- Hastalık izni yıllık izin hakkından düşmez.
- Hangi izin türünün bakiyeden düşeceği izin türü üzerinden belirlenir.
- İzin hakkı tek bir sabit sayaç yerine izlenebilir bir hak kazanım modeliyle yönetilir.

### 4.6 İş günü ve tatil hesabı

- MVP çalışma haftası Pazartesi–Cuma’dır.
- Cumartesi ve Pazar izin hakkından düşmez.
- Tatil tablosunda tam gün ve yarım gün ayrımı bulunur.
- Tam günlük tatil izin hakkından düşmez.
- Yarım günlük tatille çakışan izin günü `0,5` gün düşer.
- Personelin ayrıca yarım günlük izin seçmesi MVP’de bulunmaz.
- Sabit resmî tatiller yıllık olarak sistem tarafından eklenebilir.
- Dini bayramlar ve şirket özel günleri gibi değişken tarihler HR veya Admin tarafından yönetilir.
- İzin gün sayısı backend tarafından hesaplanır.
- Frontend yalnızca ön izleme gösterir.

### 4.7 Saatlik idari izin

Doktor veya banka işleri için saatlik kişisel idari izin fikri kabul edilmiştir; ancak MVP’ye dahil değildir.

Gelecekte:

- günlük izin modelinden ayrı,
- saat cinsinden,
- şirket yönetmeliğine bağlı kotası bulunan

bir özellik olarak tasarlanacaktır.

## 5. Yetki Matrisi

| İşlem | Personel | Yönetici | HR | Admin |
|---|---:|---:|---:|---:|
| Kendi profilini görüntüleme/düzenleme | Evet | Evet | Evet | Evet |
| Ekip dizinini görüntüleme | Evet | Evet | Evet | Evet |
| Kendi izin talebini oluşturma | Evet | Hayır | Evet | Hayır |
| Kendi ekipman talebini oluşturma | Evet | Hayır | Evet | Hayır |
| Kendi takımının taleplerini işleme | Hayır | Evet | Hayır | Gerekirse müdahale |
| Kendi takımını ve takvimini görüntüleme | Hayır | Evet | Şirket geneli | Şirket geneli |
| Kendi takımına duyuru oluşturma | Hayır | Evet | Hayır | Gerekirse |
| Şirket/HR duyurusu oluşturma | Hayır | Hayır | Evet | Evet |
| Personel ekleme/güncelleme | Hayır | Hayır | Evet | Evet |
| Personeli pasife alma | Hayır | Hayır | Evet | Evet |
| Departman/yönetici atama | Hayır | Hayır | Evet | Evet |
| İzin türü ve tatil yönetimi | Hayır | Hayır | Evet | Evet |
| Rol atama/değiştirme | Hayır | Hayır | Hayır | Evet |
| Envanter ve zimmet yönetimi | Hayır | Hayır | Hayır | Evet |
| Güvenlik ayarları ve audit | Hayır | Hayır | Hayır | Evet |
| Duyuru/yorum moderasyonu | Hayır | Hayır | HR kapsamı | Evet |

HR kendi izin talebini normal çalışan gibi oluşturur. Rutin onay ilgili yöneticidedir.

## 6. MVP Geliştirme Aşamaları

### Aşama 0 — Mevcut authentication temelini kapatma

**Durum:** `[x]` Authentication temeli tamamlandı; tam backend test paketi Testcontainers üzerinde 23/23 geçti.  
**Tahmin:** 1–2 gün  
**Zorluk:** Kolay–Orta

- [x] Scheduled expired refresh-token temizliğinin Testcontainers integration testini tamamla
- [x] Pasif personelin login sırasında yeni token almasını engelle
- [x] Pasif personelin refresh sırasında yeni access/refresh token almasını engelle
- [x] Pasif personel login ve refresh senaryolarının testlerini yaz
- [x] Spring Security tarafından üretilen 401 cevaplarını ortak `ErrorResponse` formatıyla eşitle
- [x] 401 ve 403 status/body biçimleri için HTTP integration testleri yaz
- [x] Auth tarafındaki kontrollü hata mesajları ile iç exception mesajlarını yeniden gözden geçir
- [x] `API_SPEC.md` içindeki dashboard endpoint’inin mevcut `/api/dashboard/ozet` yolu ile uyumlu olduğunu doğrula
- [x] `SecurityConfig` için satır satır araştırma çalışmasını tamamla
- [x] Integration sınıfları arasında kapanmış container’a bağlı Spring context’in yeniden kullanılmasını engelle ve tam backend test paketini doğrula

#### SecurityConfig araştırmasında incelenecekler

- [x] Stateless session yaklaşımı
- [x] CSRF’nin neden kapalı olduğu
- [x] `permitAll` ve `authenticated` temel farkı
- [x] JWT ve rate-limit filter sırası
- [x] JWT claim’lerinden authority oluşturulması
- [x] 401 ve 403 üretim yollarının ayrıntılı incelenmesi
- [x] Authentication entry point
- [x] Access denied handler
- [x] Method security ve `@PreAuthorize`
- [x] CORS davranışı ve ortam bazlı ayarlar
- [x] Araştırma sonucunun `SECURITY.md` içinde kalıcı hâle getirilmesi

> Backend, frontend ve `API_SPEC.md` dashboard yolu `/api/dashboard/ozet` üzerinde uyumludur.

### Aşama 1 — Personel, profil ve HR

**Durum:** `[-]` Personel modeli, Admin rol değiştirme, HR personel oluşturma/güncelleme ve parola akışları hazır; HR pasifleştirme, profil ve ekip dizini bekliyor.  
**Tahmin:** 5–8 gün  
**Zorluk:** Orta–Zor

#### Tamamlanan model hazırlıkları

- [x] Personel entity’sine mezun olunan okul alanını ekle
- [x] Personel entity’sine mezun olunan bölüm alanını ekle
- [x] Personel entity’sine mezuniyet yılı alanını ekle
- [x] Personel entity’sine işe giriş tarihi alanını ekle
- [x] Personel entity’sine işten çıkış tarihi alanını ekle
- [x] Personel entity’sinde aktif/pasif alanını oluştur
- [x] Yönetici ilişkisini personel modeli üzerinde tanımla
- [x] Profil request/response DTO iskeletlerini oluştur
- [x] Ekip dizini response DTO iskeletini oluştur
- [x] Profil fotoğrafı dosya adı alanını personel modelinde oluştur
- [x] İlk giriş parola değişikliği flag’ini personel modelinde oluştur
- [x] Sicil numarasını MySQL `AUTO_INCREMENT` ile sunucu tarafında üret

#### Bekleyen rol ve personel işlemleri

- [x] `HR` değerini sabit rol enum’una ekle
- [x] HR rol kaydını veritabanına ekle
- [-] HR rolünün JWT ve frontend tarafında taşınmasını doğrula — backend JWT doğrulandı, frontend bekliyor
- [x] Admin rol atama/değiştirme akışını oluştur
- [x] Rol değişikliğinde kullanıcının bütün refresh token’larını iptal et
- [x] Rol değişikliği işlemini audit et
- [x] HR/Admin personel ekleme
- [x] Yeni personeli server-controlled `PERSONEL` rolü ve BCrypt ilk parolayla oluştur
- [x] Normalize kullanıcı adı/e-posta çakışmasını engelle
- [x] İlk girişte zorunlu parola değiştirme ve `428` login akışını oluştur
- [x] Authenticated personelin istediği zaman parola değiştirme akışını oluştur
- [x] Parola değişikliğinde refresh tokenları iptal et ve işlemi audit et
- [x] HR personel güncelleme
- [ ] HR personeli pasife alma
- [x] HR departman atama
- [x] HR yönetici atama
- [ ] Pasife alınan personeli aktif ekip dizininden çıkar
- [ ] Personel/HR/Admin endpoint’lerinde rol ve ownership kontrollerini uygula

#### Profil ve ekip dizini

- [ ] Kendi profilini görüntüleme endpoint’ini oluştur
- [ ] İzin verilen profil alanlarını güncelleme endpoint’ini oluştur
- [ ] Profil response’una mezuniyet yılını aktar
- [ ] Departman bilgilerini listeleme/yönetme akışını oluştur
- [ ] Yetenek bilgilerini listeleme/yönetme akışını oluştur
- [ ] Personel yeteneklerini güncelleme akışını oluştur
- [ ] Ekip dizini sorgusunu oluştur
- [ ] Ekip dizininde departman filtresi
- [ ] Ekip dizininde yetenek filtresi
- [ ] Pasif personeli varsayılan ekip dizini sonucundan çıkar

#### Profil fotoğrafı güvenliği

- [ ] İzin verilen görsel formatları için allowlist uygula
- [ ] Gönderilen `Content-Type` değerine tek başına güvenme
- [ ] MIME ve dosya imzasını/magic byte bilgisini doğrula
- [ ] Dosya boyutu limiti uygula
- [ ] Gerekirse görsel çözünürlük limiti uygula
- [ ] Dosya adını sunucu tarafında rastgele üret
- [ ] Path traversal girişimlerini engelle
- [ ] Dosyaları çalıştırılabilir statik kaynak alanından ayrı tut
- [ ] Eski fotoğrafı değiştirirken güvenli dosya temizliği yap
- [ ] Sahte uzantı, geçersiz imza ve büyük dosya testleri yaz

### Aşama 2 — Yıllık izin ve şirket takvimi

**Durum:** `[-]` Entity/DTO iskeletleri var; çalışan izin modülü yok.  
**Tahmin:** 8–12 gün  
**Zorluk:** Zor

#### Tamamlanan hazırlıklar

- [x] Temel izin entity’sini oluştur
- [x] Temel izin türü entity’sini oluştur
- [x] İzin talep request/response DTO iskeletlerini oluştur
- [x] İzin durum ilişkisinin veri modeli temelini oluştur

#### Veri modeli ve migration

- [ ] İzin türüne “yıllık haktan düşer mi?” alanı ekle
- [ ] Tam/yarım gün destekli resmî ve şirket tatili tablosu oluştur
- [ ] Tatil türünü sabit/değişken/şirket günü olarak ayırt et
- [ ] İzin hakkı kazanımlarını hizmet yılı bazında izlenebilir tutacak modeli oluştur
- [ ] Mevcut sabit `yillikIzinHakki` alanından birikmiş hak modeline geçiş planı oluştur
- [ ] Mevcut personel verileri için izin hakkı migration yaklaşımını belirle
- [ ] `IzinBilgileri.isGunuSayisi` alanını `0,5` günü güvenle temsil edecek `BigDecimal` tabanlı tipe dönüştür
- [ ] İzin response DTO’larındaki gün sayısı alanlarını yarım gün destekleyecek şekilde güncelle
- [ ] Sabit tatiller için yıllık üretim/seed yaklaşımı oluştur

#### İş kuralları

- [ ] Tamamlanan hizmet yılına göre 14/20/26 gün hak hesapla
- [ ] Kullanılmayan izin hakkını sonraki yıllara devret
- [ ] Hafta sonlarını iş günü hesabından çıkar
- [ ] Tam günlük tatilleri izin hesabından çıkar
- [ ] Yarım günlük tatilleri `0,5` gün olarak hesapla
- [ ] Hastalık iznini yıllık izin hakkından düşme
- [ ] Yalnızca “yıllık haktan düşer” işaretli izin türlerini bakiyeden düş
- [ ] Kullanılan bakiyeyi yalnızca onaylanmış izinlerden hesapla
- [ ] Bakiyeyi aşan talebi engelle
- [ ] Bitiş tarihi başlangıç tarihinden önce olan talebi engelle
- [ ] Bekleyen talep için düzenleme kurallarını uygula
- [ ] Bekleyen talep için iptal kurallarını uygula
- [ ] Onay/reddetmeyi yalnızca doğrudan yöneticiye aç
- [ ] Çakışan izin taleplerini kontrol et
- [ ] Kritik durum değişikliklerini audit et

#### API ve ekranlar

- [ ] Personel/HR izin talebi oluşturma
- [ ] Personel/HR kendi izin geçmişini görüntüleme
- [ ] Yönetici doğrudan takımının izin taleplerini görüntüleme
- [ ] Yönetici izin onaylama/reddetme
- [ ] HR şirket geneli izin kayıtlarını görüntüleme
- [ ] HR/Admin izin türü yönetimi
- [ ] HR/Admin tatil takvimi yönetimi
- [ ] Takvimde kişisel izinleri gösterme
- [ ] Takvimde yönetici için takım izinlerini gösterme
- [ ] Takvimde resmî ve şirket tatillerini gösterme

### Aşama 3 — Ekipman, envanter ve zimmet

**Durum:** `[-]` Entity/DTO iskeletleri var; çalışan iş akışı yok.  
**Tahmin:** 5–8 gün  
**Zorluk:** Orta–Zor

#### Tamamlanan hazırlıklar

- [x] Ekipman entity’sini oluştur
- [x] Ekipman tipi entity’sini oluştur
- [x] Ekipman talep entity’sini oluştur
- [x] Zimmet entity’sini oluştur
- [x] Ekipman ve zimmet DTO iskeletlerini oluştur

#### Bekleyen işler

- [ ] Personel/HR ekipman talebi oluşturma
- [ ] Personel/HR kendi ekipman taleplerini görüntüleme
- [ ] Bekleyen ekipman talebini düzenleme
- [ ] Bekleyen ekipman talebini iptal etme
- [ ] Yöneticinin yalnızca doğrudan ekibinin taleplerini görmesi
- [ ] Yönetici ekipman talebi onaylama/reddetme
- [ ] Admin ekipman tipi yönetimi
- [ ] Admin ekipman envanteri yönetimi
- [ ] Onaylanan talep için zimmet kaydı oluşturma
- [ ] Zimmet sırasında uygun stok kontrolü
- [ ] İade işleminde ekipmanı tekrar depoya alma
- [ ] Zimmet geçmişini silmeden koruma
- [ ] Stok, onay, zimmet ve iade testleri

### Aşama 4 — Oda rezervasyonu ve ürün takvimi

**Durum:** `[-]` Veri modeli, route ve takvim şablonu var; çalışan rezervasyon modülü ve takvim entegrasyonu yok.  
**Tahmin:** 4–6 gün  
**Zorluk:** Orta

#### Tamamlanan hazırlıklar

- [x] Oda entity’sini oluştur
- [x] Rezervasyon entity’sini oluştur
- [x] Rezervasyon request/response DTO iskeletlerini oluştur
- [x] `/calendar` frontend rotasını oluştur
- [x] TailAdmin takvim şablonunu projede koru

#### Bekleyen rezervasyon işleri

- [ ] Oda repository/service/controller akışını oluştur
- [ ] Oda listesini getir
- [ ] Rezervasyon repository/service/controller akışını oluştur
- [ ] Tarih ve odaya göre rezervasyonları listele
- [ ] Saatlik rezervasyon oluştur
- [ ] Bitiş saatinin başlangıç saatinden sonra olmasını doğrula
- [ ] Çakışan zaman aralıklarını backend’de engelle
- [ ] Kullanıcının yalnızca kendi rezervasyonunu silebilmesi
- [ ] Rezervasyon ownership ve çakışma testleri

#### Ürün takvimi

- [ ] TailAdmin demo event’lerini kaldır
- [ ] Takvimi gerçek backend verilerine bağla
- [ ] Resmî/şirket tatili filtresi
- [ ] Kişisel izin filtresi
- [ ] Yönetici takım izinleri filtresi
- [ ] Oda rezervasyonları filtresi
- [ ] Farklı olay türlerini tutarlı renk ve açıklamalarla göster
- [ ] Takvim loading, error ve empty durumlarını oluştur

### Aşama 5 — Duyurular ve takdir panosu

**Durum:** `[-]` Entity/DTO iskeletleri var; çalışan API ve hedef kitle modeli yok.  
**Tahmin:** 4–7 gün  
**Zorluk:** Orta–Zor

#### Tamamlanan hazırlıklar

- [x] Duyuru entity’sini oluştur
- [x] Yorum entity’sini oluştur
- [x] Beğeni entity’sini oluştur
- [x] Takdir panosu entity’sini oluştur
- [x] İlgili request/response DTO iskeletlerini oluştur

#### Hedef kitle modeli

- [ ] Duyuru modeline hedef kitle türü ekle
- [ ] Şirket geneli duyuruyu temsil et
- [ ] HR duyurusunu temsil et
- [ ] Doğrudan takım duyurusunu temsil et
- [ ] Takım duyurusunun hedef yöneticisini/takımını modelle
- [ ] Dolaylı alt ekipleri kapsam dışında tut
- [ ] HR/Admin denetim sorgularını tasarla

#### Duyuru iş akışı

- [ ] Admin şirket geneli duyuru oluşturma/düzenleme/silme
- [ ] HR, HR/şirket duyurusu oluşturma
- [ ] Yönetici yalnızca doğrudan takımına duyuru oluşturma
- [ ] Duyuru sorgularında hedef kitle filtrelemesi
- [ ] HR/Admin takım duyurusu denetimi
- [ ] Yetkisiz takım duyurusu erişimini engelleme
- [ ] Takım duyurusu görünürlük testleri

#### Yorum, beğeni ve takdir

- [ ] Yorum oluşturma
- [ ] Yorum sahibinin yorumunu düzenlemesi
- [ ] Yorum sahibinin yorumunu silmesi
- [ ] Admin yorum moderasyonu
- [ ] İdempotent beğeni ekleme
- [ ] Beğeni kaldırma
- [ ] Takdir mesajı oluşturma
- [ ] Takdir mesajlarını listeleme
- [ ] Dashboard için son takdirleri getirme

### Aşama 6 — Dashboard ve frontend ürünleştirme

**Durum:** `[-]` Authentication ekranları ve doğum günü kartı gerçek; dashboard’un kalanı ve ürün ekranları bekliyor.  
**Tahmin:** 5–8 gün  
**Zorluk:** Orta

#### Dashboard

- [x] Doğum günü verisini gerçek backend sorgusundan getir
- [x] Doğum günü kartını backend’e bağla
- [x] Doğum günü kartında loading/error/empty durumlarını göster
- [ ] Son duyuruları role ve hedef kitleye göre göster
- [ ] Bekleyen talepleri role göre göster
- [ ] Bugün izinli personel özetini göster
- [ ] Oda doluluk özetini göster
- [ ] Son takdirleri göster
- [ ] Kalan izin bilgisini göster
- [ ] Dashboard response’unu role göre düzenle
- [ ] Dashboard için backend testleri yaz

#### Kalan izin kartı

TailAdmin `MonthlyTarget` bileşeni dönüştürülecek:

- [ ] Bileşeni `KalanIzinKarti` olarak yeniden adlandır
- [ ] Başlığı “Kalan İzin Hakkın” yap
- [ ] Yüzdeyi `kalan / toplam hak × 100` ile hesapla
- [ ] Alt alanları `Toplam Hak / Kullanılan / Kalan` yap
- [ ] `+10%` alanını bekleyen izin bilgisine dönüştür veya kaldır
- [ ] Gelir/hedef metinlerini kaldır
- [ ] Kartı izin sayfasına bağla
- [ ] Yönetici/Admin için kartı gizle veya role uygun bir özetle değiştir

#### Navigasyon ve sayfalar

- [-] `/calendar` rotası ve demo takvim mevcut; ürün dönüşümü Aşama 4’te takip ediliyor
- [x] Sidebar’da “Ekip Dizini” ana etiketini oluştur
- [-] Basic Tables ekranı ekip dizini için korunuyor; içerik hâlâ demo
- [ ] Ekip dizini içeriğini gerçek personel verisine dönüştür
- [ ] Ekip dizinini `/ekip-dizini` rotasına taşı
- [x] Talepler menüsünde izin ve ekipman başlıklarını ayır
- [ ] `/talepler/izin` rotasını ve sayfasını oluştur
- [ ] `/talepler/ekipman` rotasını ve sayfasını oluştur
- [ ] `/form-elements` ve `/basic-tables` gibi demo route adlarını kaldır
- [ ] Menüyü role göre filtrele
- [ ] HR menü seçeneklerini ekle
- [ ] Yönetici menü seçeneklerini kendi takım yetkilerine göre düzenle
- [ ] Route seviyesinde role uygun kullanıcı deneyimi sağla

#### Frontend authentication sağlamlaştırması

- [x] Protected route temeli
- [x] Public route temeli
- [x] Sessiz refresh ve single-flight temeli
- [x] Logout temel akışı
- [ ] Logout isteği ağ hatası verse de local access token ve kullanıcı bilgisini temizle
- [ ] Login formuna submitting/loading durumu ekle
- [ ] Login isteğindeki ağ hatalarını kontrollü biçimde göster

#### Frontend temizliği

Temizlik iki aşamalı yapılır:

1. Ürüne dönüştürülebilecek template component’ları korunur.
2. Ürün dönüşümü tamamlandıktan sonra kullanılmayan demo içerikleri temizlenir.

Görevler:

- [x] JForce logosunu sidebar/auth alanlarında kullanmaya başla
- [-] TailAdmin şablonunun bazı alanlarını JForce görünümüne uyarla
- [ ] Kullanılmayan demo sayfalarını kaldır
- [ ] Kullanılmayan demo rotalarını kaldır
- [ ] Kullanılmayan örnek verileri kaldır
- [ ] Kullanılmayan import’ları kaldır
- [ ] Kullanılmayan asset’leri kaldır
- [ ] TailAdmin ecommerce metinlerini kaldır
- [ ] Sayfa title ve metadata değerlerini ürün adına göre güncelle
- [ ] Yeni veri kullanan bütün ekranlarda loading/error/empty durumlarını oluştur

### Aşama 7 — MVP doğrulama ve teslim

**Durum:** `[ ]` Yeni modüller tamamlandıktan sonra yürütülecek.  
**Tahmin:** 3–6 gün  
**Zorluk:** Orta

#### Backend doğrulama

- [ ] Her endpoint için rol ve ownership kontrolü
- [ ] Personel–Yönetici–HR–Admin yetki matrisi integration testleri
- [x] Pasif personel login testi
- [x] Pasif personel refresh testi
- [x] Rol değişikliği ve refresh token iptali testi
- [ ] Tatil ve iş günü hesabı sınır testleri
- [ ] Yarım gün `0,5` hesaplama testleri
- [ ] Kıdem ve devreden bakiye testleri
- [ ] Takım duyurusu görünürlük testleri
- [ ] Rezervasyon çakışma ve ownership testleri
- [ ] Ekipman stok, zimmet ve iade testleri
- [ ] Dosya yükleme kötüye kullanım testleri
- [x] Security 401/403 response testleri

#### Frontend doğrulama

- [ ] Frontend test yaklaşımını ve gerekli test araçlarını belirle
- [ ] Auth guard davranışlarını test et
- [ ] Silent refresh davranışını test et
- [ ] Role göre menü görünürlüğünü test et
- [ ] Kritik dashboard kartlarını test et
- [ ] Loading, error ve empty durumlarını kontrol et
- [ ] Frontend production build doğrulaması

#### Dokümantasyon ve demo

- [ ] Swagger/OpenAPI çıktısını güncelle
- [ ] `API_SPEC.md` dosyasını gerçek endpoint’lerle eşitle
- [x] Auth bölümündeki refresh/logout açıklamalarını güncelle
- [x] Dashboard endpoint yolunun `/api/dashboard/ozet` olduğunu doğrula
- [ ] Eski kararlarla çelişen doküman maddelerini temizle
- [ ] README içindeki proje durumunu güncelle
- [ ] Manuel uçtan uca MVP senaryosu
- [ ] Demo verileri
- [ ] Sunum akışı

Test ve doğrulama standartları kök ve ilgili nested `AGENTS.md` dosyaları ile `CONTRIBUTING.md` kontrol listelerine göre uygulanır.
## 7. MVP Sonrası

### 7.1 Saatlik idari izin

**Tahmin:** 4–8 gün  
**Zorluk:** Orta–Zor

- [ ] Saatlik izin veri modelini tasarla
- [ ] Saatlik izin kotasını belirle
- [ ] Kota periyodunu belirle
- [ ] Doktor/banka gibi nedenleri tanımla
- [ ] Saat çakışması kontrolü
- [ ] Yönetici onay akışı
- [ ] Yenilenme veya devretme politikası

Şirket yönetmeliği netleşmeden uygulanmaz.

### 7.2 Dinamik rol ve permission sistemi

**Tahmin:** 10–18 gün  
**Zorluk:** Çok Zor

- [ ] Admin’in yeni rol oluşturması
- [ ] Permission kataloğu
- [ ] Role read/create/update/delete izinleri atanması
- [ ] Kullanıcıya rol atanması
- [ ] Permission bazlı frontend
- [ ] Permission bazlı endpoint güvenliği
- [ ] Privilege escalation kontrolleri
- [ ] Rol/yetki değişikliklerinin audit edilmesi
- [ ] Sabit enum ve tek rol modelinden güvenli migration
- [ ] Kapsamlı permission testleri

### 7.3 Anında JWT iptali

**Tahmin:** 2–4 gün  
**Zorluk:** Orta–Zor

Araştırılacak seçenekler:

- [ ] `tokenVersion`
- [ ] Denylist
- [ ] Merkezi session/cache

MVP’de refresh token iptali ve en fazla 15 dakikalık access token penceresi kabul edilir.

## 8. Opsiyonel ve Özel Takip İşleri

Bu tablo yeni veya tekrarlanan görev oluşturmaz. İşlerin asıl görevleri belirtilen roadmap bölümünde takip edilir.

| İş | Durum | Zorluk | Tahmin | Roadmap konumu / karar |
|---|---|---|---:|---|
| Login timing attack düzeltmesi | `[ ]` Opsiyonel | Kolay | 0,5–1 gün | Olmayan kullanıcı için dummy BCrypt çalıştır |
| SecurityConfig araştırması | `[x]` | Orta | 0,5–1 gün | Aşama 0 |
| Profil fotoğrafı güvenliği | `[ ]` MVP | Orta–Zor | 2–4 gün | Aşama 1; güvenli upload olmadan tamamlanmış sayılmaz |
| Frontend demo temizliği | `[-]` | Kolay–Orta | 1–3 gün | Aşama 6; ürün dönüşümlerinden sonra |
| JWT anında iptal | `[ ]` MVP sonrası | Orta–Zor | 2–4 gün | Bölüm 7.3 |
| Dinamik RBAC | `[ ]` MVP sonrası | Çok Zor | 10–18 gün | Bölüm 7.2 |

## 9. Bağımlılık Sırası

1. Authentication sağlamlaştırma ve aktif personel kontrolü
2. Personel modeli, HR rolü ve rol atama akışı
3. Personel–Yönetici ilişkisini kullanan erişim kuralları
4. Profil ve ekip dizini
5. Tatil takvimi ve izin hakkı kazanım modeli
6. İzin talep ve onay akışları
7. Ekipman talep, envanter ve zimmet akışları
8. Rezervasyon backend’i ve ürün takvimi
9. Duyuru hedef kitle modeli ve takım duyuruları
10. Dashboard’un gerçek verilerle tamamlanması
11. Frontend role göre menü ve ürünleştirme
12. Frontend temizliği ve uçtan uca doğrulama

Takım ilişkisi tamamlanmadan yönetici onayları ve takım duyuruları tamamlanmış sayılmaz.

Tatil takvimi ve yarım gün destekli veri tipi tamamlanmadan doğru izin bakiyesi hesaplanmış sayılmaz.

Backend sözleşmesi tamamlanmadan ilgili frontend ekranı tamamlanmış sayılmaz.

Yeni modülün testleri yazılmadan ilgili modül tamamlanmış sayılmaz.

## 10. MVP Tamamlanma Kriterleri

- [ ] Dört sabit rol doğru endpoint ve ekranlara erişebiliyor.
- [x] Admin rol atayabiliyor.
- [ ] HR rol atayamıyor.
- [x] Rol değişikliğinde refresh token’lar iptal ediliyor.
- [x] Pasif personel login veya refresh ile yeni token alamıyor.
- [x] HR personel ekleyebiliyor.
- [x] HR personel güncelleyebiliyor.
- [ ] HR personeli geçmişini silmeden pasife alabiliyor.
- [ ] Personel ve HR yıllık izin talebi oluşturabiliyor.
- [ ] Personel ve HR ekipman talebi oluşturabiliyor.
- [ ] Yönetici yalnızca doğrudan takımının taleplerini işleyebiliyor.
- [ ] İzin bakiyesi kıdem ve devreden haklara göre hesaplanıyor.
- [ ] Hafta sonları izin bakiyesinden düşmüyor.
- [ ] Tam günlük tatiller izin bakiyesinden düşmüyor.
- [ ] Yarım günlük tatiller `0,5` gün olarak hesaplanıyor.
- [ ] Hastalık izni yıllık izin hakkından düşmüyor.
- [ ] Yönetici yalnızca kendi doğrudan takımına duyuru gönderebiliyor.
- [ ] HR/Admin takım duyurularını denetleyebiliyor.
- [ ] Admin envanter ve zimmetleri yönetebiliyor.
- [ ] Oda rezervasyonlarında çakışma engelleniyor.
- [ ] Kullanıcı yalnızca kendi rezervasyonunu silebiliyor.
- [ ] Dashboard gerçek ve role uygun veriler gösteriyor.
- [ ] Profil fotoğrafı güvenli biçimde yükleniyor.
- [ ] Frontend menüsü role göre şekilleniyor.
- [ ] Kritik yetki ve iş kuralları otomatik testlerle korunuyor.
- [ ] `API_SPEC.md`, Swagger ve frontend rotaları gerçek uygulamayla uyumlu.
- [ ] Kullanılmayan TailAdmin demo içerikleri temizlenmiş.
- [ ] Manuel uçtan uca MVP senaryosu başarıyla doğrulanmış.
