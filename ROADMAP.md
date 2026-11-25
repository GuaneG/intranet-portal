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
    - HR personel oluşturma/güncelleme/pasifleştirme, ilk/normal parola değiştirme, kendi profilini görüntüleme/güncelleme ve ekip dizini backend’i hazır.
- [-] Dashboard
    - Doğum günü bölümü gerçek veriye bağlı.
    - İzin bakiyesi ve güvenli oda doluluk özeti gerçek backend verisine bağlı.
    - Duyurular, bugün izinli sayısı, takdir ve kalan talep kartları placeholder.
- [-] Takvim frontend temeli
    - `/calendar` rotası gerçek tatil API’sine bağlı; tek ve çok günlük tatil grupları listelenebiliyor ve HR/Admin tarafından yönetilebiliyor.
    - Çok günlük grup tek takvim olayı olarak gösteriliyor; grubun herhangi bir gününden bütün grup düzenlenebiliyor veya silinebiliyor.
    - Aktif çalışanların doğum günleri güvenli tarih-aralığı endpoint’i üzerinden şirket genelinde takvim olayı olarak gösteriliyor.
- [x] Ekip dizini frontend’i
    - `/ekip-dizini` gerçek personel verisi, filtreler ve role uygun görünürlük kurallarıyla çalışıyor.
    - Yönetici zinciri ve organizasyon hiyerarşisi backend sözleşmeleriyle entegre edildi.
- [-] Talepler frontend ayrımı
    - Menüde “İzin Talepleri” ve “Ekipman Talepleri” başlıkları mevcut.
    - İzin akışları ile rezervasyon günlük uygunluk/create/geçmiş/iptal ekranları hazır; ekipman ve diğer talep modülleri bekliyor.
- [-] TailAdmin ürün markalaması
    - JForce logosu bazı alanlarda kullanılıyor.
    - Demo ekranları, metinleri ve asset’leri hâlâ bulunuyor.
- [-] Test kapsamı
    - Authentication, personel/profil, ekip dizini/hiyerarşi, katalog, tatil takvimi, iş günü hesabı, izin, rezervasyon ve ekipman/zimmet akışları unit ve gerçek MySQL HTTP/integration testleriyle korunuyor.
    - Frontend’de hafif `node:test` dosyaları var; birleşik `npm test` script’i ve kapsamlı component/e2e test altyapısı henüz yok.
    - Duyuru testleri ilgili modül uygulanınca eklenecek.

### 3.3 Tamamlanmayan ana kapsam

- [x] Çalışan profil endpoint’leri
- [x] Ekip dizini API’si ve gerçek frontend ekranı
- [x] Güvenli profil fotoğrafı yükleme
- [-] Yıllık izin hakkı ve tatil takvimi — profil/dashboard bakiyesi, iş günü önizlemesi, tek/çok günlük tatil, şirket geneli doğum günleri ve izin talep/iptal/karar akışları backend/frontend’de hazır; hizmet yılı/devreden bakiye ile kişisel/takım izin olayları gelecek kapsamda
- [x] İzin talep, owner iptali ve Yönetici/Admin onay-red akışı
- [-] Ekipman talebi, envanter ve zimmet akışı — backend kataloğu, talep/karar, envanter, zimmet/iade, personel yaşam döngüsü, migration ve concurrency/security testleri hazır; frontend entegrasyonu bekliyor
- [x] Oda rezervasyonu iş akışı — backend ve frontend create/geçmiş/iptal, Admin şirket geçmişi, şirket takvimi ve dashboard doluluk entegrasyonları hazır
- [ ] Duyurular ve takım duyuruları
- [ ] Yorum, beğeni ve takdir panosu iş akışları
- [ ] Dashboard’un kalan gerçek verileri
- [x] Role göre frontend menüsü
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

Pasifleştirme endpoint’i body almaz. HR/Admin `PERSONEL` ve `HR` hedefleri, yalnız Admin `YONETICI` hedefini pasifleştirebilir; `ADMIN` hedef ve öz-hedef işlemleri yasaktır. Yönetici pasifleştirilirse bütün doğrudan çalışanların yönetici ilişkisi kaldırılır. Zaten pasif hedef için yetkili istek idempotenttir ve yeni yan etki üretmez. Aktif ekip dizininden filtreleme, ekip dizini endpoint’i tamamlandığında uygulanacaktır.

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
| Hedef kitlesindeki duyuruları görüntüleme | Evet | Evet | Evet | Evet |
| Kendi izin talebini oluşturma | Evet | Evet* | Evet | Hayır |
| Kendi izin taleplerini görüntüleme | Evet | Evet* | Evet | Hayır |
| Kendi ekipman talebini oluşturma | Evet | Hayır | Evet | Hayır |
| Kendi ekipman taleplerini görüntüleme | Evet | Hayır | Evet | Hayır |
| Kendi rezervasyonunu oluşturma | Evet | Evet | Evet | Evet |
| Kendi rezervasyonlarını görüntüleme | Evet | Evet | Evet | Evet |
| Talep edilebilir ekipman kataloğunu görüntüleme | Evet | Evet | Evet | Envanter yönetimi kapsamında |
| Kendi takımının izin ve ekipman taleplerini işleme | Hayır | Evet | Hayır | Hayır |
| Şirket geneli izin/ekipman taleplerini yönetim amacıyla görüntüleme | Hayır | Hayır | Hayır | Evet |
| Genel/kişisel ürün takvimini görüntüleme | Evet | Evet | Evet | Evet |
| Kendi takımını ve takım takvimini görüntüleme | Hayır | Evet | Şirket geneli | Şirket geneli |
| Kendi takımına duyuru oluşturma | Hayır | Evet | Hayır | Hayır |
| Şirket/HR duyurusu oluşturma | Hayır | Hayır | Evet | Evet |
| Personel ekleme/güncelleme | Hayır | Hayır | Evet | Evet |
| Personeli pasife alma | Hayır | Hayır | Evet | Evet |
| Departman/yönetici atama | Hayır | Hayır | Evet | Evet |
| İzin türü ve tatil yönetimi | Hayır | Hayır | Evet | Evet |
| Rol atama/değiştirme | Hayır | Hayır | Hayır | Evet |
| Envanter ve zimmet yönetimi | Hayır | Hayır | Hayır | Evet |
| Güvenlik ayarları ve audit | Hayır | Hayır | Hayır | Evet |
| Audit log kayıtlarını görüntüleme | Hayır | Hayır | Hayır | Evet |
| Duyuru/yorum moderasyonu | Hayır | Hayır | Takım duyurularını denetler | Evet |

HR kendi izin talebini normal çalışan gibi oluşturur. `Evet*` işaretli Yönetici davranışı yalnız aktif, kendisine atanmış aktif doğrudan yöneticisi bulunan non-root Yönetici için geçerlidir; kök Yönetici kendi izin talebini oluşturamaz ve kendi izin talepleri endpoint'ine erişemez. Rutin onay ilgili yöneticidedir.

Yukarıdaki Admin satırı şirket geneli taleplerin yönetim/işleme görünürlüğünü ifade eder; `GET /api/leave-types` izin türü lookup sözleşmesi değildir. İzin türü lookup aktif Personel, HR, uygun non-root Yönetici ve Admin tarafından kullanılabilir.

Rezervasyon oluşturma, kendi geçmişini görüntüleme ve kendi başlamamış aktif rezervasyonunu iptal etme dört aktif role açıktır. Admin ayrıca şirket geneli rezervasyon geçmişini filtreleyebilir ve başkasının başlamamış aktif rezervasyonunu zorunlu güvenli nedenle iptal edebilir.

PERSONEL, YONETICI ve HR talep edilebilir ekipman kataloğunu görüntüler. PERSONEL ve HR yalnız kendi ekipman taleplerini görür; YONETICI kendi ekipman talebini oluşturmaz ve yalnız doğrudan takımının taleplerini işler. ADMIN şirket geneli izin/ekipman taleplerini bekleyen, onaylanan ve reddedilen durumlarıyla yönetim amacıyla görüntüler; ekipmanları ayrı katalog yerine daha kapsamlı envanter ve zimmet yönetimi içinde görür.

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

**Durum:** `[-]` Personel modeli, Admin rol/parola ve hedef yetenek yönetimi, HR personel oluşturma/güncelleme/pasifleştirme, Faz 6 personel yönetimi okuma sözleşmeleri, parola, kendi profilini görüntüleme/güncelleme, departman/yetenek yönetimi ve güvenli silme, ekip dizini/hiyerarşi ve güvenli profil fotoğrafı backend akışları hazır; personel/profil/ekip dizini tarafındaki ürün ekranları gerçek API’lerle entegre, kalan genel ürünleştirme işleri Aşama 6’da izleniyor.  
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
- [x] HR rolünün JWT, auth context, role guard ve frontend menü filtrelerinde taşındığını doğrula
- [x] Admin rol atama/değiştirme akışını oluştur
- [x] Rol değişikliğinde kullanıcının bütün refresh token’larını iptal et
- [x] Rol değişikliği işlemini audit et
- [x] HR/Admin personel ekleme
- [x] Yeni personeli server-controlled `PERSONEL` rolü ve BCrypt ilk parolayla oluştur
- [x] Normalize kullanıcı adı/e-posta çakışmasını engelle
- [x] İlk girişte zorunlu parola değiştirme ve `428` login akışını oluştur
- [x] Authenticated personelin istediği zaman parola değiştirme akışını oluştur
- [x] Parola değişikliğinde refresh tokenları iptal et ve işlemi audit et
- [x] Admin’in başka personelin parolasını güvenli biçimde sıfırlamasını sağla
    - Yalnız aktif Admin, `PERSONEL`, `HR` ve `YONETICI` hedefe geçici parola atayabilir; öz-hedef ve Admin hedef yasaktır.
    - BCrypt hash, zorunlu ilk parola flag’i, refresh-token iptali ve güvenli audit aynı transaction içinde uygulanır.
- [x] HR personel güncelleme
- [x] HR personeli pasife alma
- [x] HR/Admin personeli yeniden aktifleştirme
    - Body içermeyen `PATCH /api/hr/personel/{personelId}/aktiflestir` sözleşmesi ve hedef rol matrisi uygulandı.
    - Gerçek değişiklikte güvenli audit, zaten aktif hedefte yetki kontrollerinden sonra auditsiz no-op davranışı sağlandı.
    - Yönetici yeniden aktifleştirildiğinde eski doğrudan bağlıların otomatik geri atanmaması ve ekip dizini görünürlüğü test edildi.
- [x] HR departman atama
- [x] HR yönetici atama
- [x] Yönetici atamalarında iki kişilik ve uzun döngüleri service katmanında engelle
- [x] Pasife alınan personeli aktif ekip dizininden çıkar
- [x] Personel/HR/Admin endpoint’lerinde rol ve ownership kontrollerini uygula
- [x] Faz 6 HR/Admin personel yönetimi okuma sözleşmelerini oluştur
    - [x] Sayfalı ve filtrelenebilir yönetim personel listesi
    - [x] Tek personel yönetim detayı
        - [x] Admin hedefli yetenek formu için alfabetik mevcut yetenek `[{ id, ad }]` listesini döndür
    - [x] Departmana göre atanabilir yönetici lookup’u
    - [x] Mevcut mutation’larda aktif aktör ve hedef rol matrisini uygula

#### Profil ve ekip dizini

- [x] Kendi profilini görüntüleme endpoint’ini oluştur
- [x] İzin verilen profil alanlarını güncelleme endpoint’ini oluştur
- [x] Departman bilgilerini alfabetik listeleme endpoint’ini oluştur
- [x] Departman ekleme/güncelleme yönetim akışını oluştur
    - `POST /api/departments` ve `PATCH /api/departments/{departmanId}` yalnız aktif HR/Admin rollerine açıktır.
    - Case-insensitive duplicate ve database unique-constraint yarışı kontrollü `409` döner; rename mevcut personel ilişkilerini korur.
    - Exact normalize eşitlik no-op’tur; gerçek oluşturma/güncelleme audit ile aynı transaction içinde uygulanır.
- [x] Kullanılmayan departmanı güvenli biçimde sil
    - Aktif/pasif herhangi bir personele bağlı departman cascade uygulanmadan `409` ile korunur.
    - Pessimistic lock, FK/constraint fallback ve aynı-transaction `DEPARTMAN_SILINDI` auditi uygulanır.
- [x] Yetenek bilgilerini alfabetik listeleme endpoint’ini oluştur
- [x] Yetenek ekleme/güncelleme yönetim akışını oluştur
    - `POST /api/skills` ve `PATCH /api/skills/{yetenekId}` yalnız aktif HR/Admin rollerine açıktır.
    - Case-insensitive duplicate `409`; rename mevcut personel-yetenek ilişkilerini korur.
    - Oluşturma/güncelleme audit ile aynı transaction içinde uygulanır.
- [x] Kullanılmayan yeteneği güvenli biçimde sil
    - Herhangi bir personel-yetenek ilişkisi bulunan yetenek cascade uygulanmadan `409` ile korunur.
    - Pessimistic lock, constraint fallback ve aynı-transaction `YETENEK_SILINDI` auditi uygulanır.
- [x] Personel yeteneklerini güncelleme akışını oluştur
    - Endpoint `PUT /api/profil/yetenekler`; tam-değiştirme ve küme semantiği kullanılır.
    - Boş liste bütün yetenekleri kaldırır; aynı küme no-op olarak `204` döner ve audit oluşturmaz.
    - Değişiklikler yalnız JWT principal personelinde, fark tabanlı ve audit ile aynı transaction içinde uygulanır.
- [x] Ekip dizini sorgusunu oluştur
    - Endpoint `GET /api/ekip-dizini`; tüm authenticated roller erişebilir.
    - Görünürlük matrisi: PERSONEL yalnız kendi departmanındaki aktif kişileri; YONETICI ve HR, Adminler dışındaki bütün aktif kişileri; ADMIN bütün aktif kişileri görür.
    - Rol/departman sınırı içerik ve count sorgularında uygulanır; kullanıcı filtreleri bu sınırı genişletemez.
    - Sade pagination response’u `{ icerik, sayfa, sayfaBoyutu, toplamKayit, toplamSayfa }` kullanılır.
    - Aktif personel sayfası ile o sayfadaki personellerin bütün yetenekleri iki toplu sorguyla alınır; collection fetch join ile pagination yapılmaz.
    - Sabit sıralama `ad`, `soyad`, ardından response’ta gösterilmeyen `personelId` biçimindedir.
- [x] Ekip dizininde departman filtresi
    - Opsiyonel `departmanId`; bulunmayan pozitif ID boş `200 OK`, sıfır/negatif ID kontrollü `400` döndürür.
- [x] Ekip dizininde yetenek filtresi
    - Opsiyonel `yetenekId`; departman filtresiyle birlikte `AND` uygulanır, bulunmayan pozitif ID boş `200 OK`, sıfır/negatif ID kontrollü `400` döndürür ve eşleşen personelin bütün yetenekleri response’ta korunur.
- [x] Ekip dizininde ad, soyad ve tam ad araması
    - Opsiyonel `arama`; departman ve yetenek filtreleriyle `AND` uygulanır, pagination ve sabit sıralama korunur.
- [x] Pasif personeli varsayılan ekip dizini sonucundan çıkar
    - Pasif personel listelenmez; pasif JWT principal kalan token ömründe `403` alır.
- [x] Güvenli yönetici hiyerarşisi okumalarını oluştur
    - Aktif HR/Admin için generic aktif organizasyon foresti; aktif PERSONEL/YONETICI için yalnız en yakın iki üst sağlanır.
    - Response güvenli görüntüleme alanlarıyla sınırlıdır; legacy döngüler `409` ile tespit edilir ve kısmi veri dönmez.
- [x] Hiyerarşi forestini arama, departman ve yetenek filtreleriyle buda
    - HR/Admin filtreli görünümünde yalnız eşleşen kişiler ve köke kadar zorunlu üst zincirler kalır; ilgisiz alt, kardeş ve yan dallar dönmez.
    - Hiyerarşi düğümleri gerçek ilişkiye dayalı `yoneticinMi` ve backend hesaplı `bugunIzinDurumu` taşır; yönetici budanmışsa bütün `yoneticinMi` değerleri `false` kalır, filtre dışı personele ait izin bilgisi sızdırılmaz.
- [x] Yöneticiye yalnız kendi güvenli hiyerarşi görünümünü sağla
    - `GET /api/ekip-dizini/yonetici-hiyerarsim`; en fazla iki üst, aktör ve yalnız aktörün aktif doğrudan/dolaylı astlarını döndürür.
    - `aktorDepartman { id, ad }` metadata’sı ve aynı filtre sözleşmesi sağlanır; PERSONEL/HR/ADMIN erişimi `403` ile engellenir.

#### Profil fotoğrafı güvenliği

- [x] İzin verilen görsel formatları için JPEG/PNG allowlist uygula
- [x] Gönderilen `Content-Type` değerine tek başına güvenme
- [x] MIME ve dosya imzasını/magic byte bilgisini doğrula
- [x] 2 MB dosya boyutu limiti uygula
- [x] 2048×2048 ve toplam piksel çözünürlük limitlerini uygula
- [x] Dosya adını sunucu tarafında UUID olarak üret ve çakışmada üzerine yazma
- [x] Path traversal girişimlerini engelle
- [x] Dosyaları çalıştırılabilir statik kaynak alanından ayrı tut
- [x] Eski fotoğrafı değiştirirken commit/rollback güvenli dosya temizliği yap
- [x] Ham görseli metadata ve ek payload taşımadan en fazla 1024 piksel uzun kenarla yeniden üret
- [x] Sahte uzantı, geçersiz imza, büyük dosya, rollback ve yetki testleri yaz
- [x] Authenticated binary GET’i `nosniff` ve private cache başlıklarıyla sun
- [x] Eşzamanlı stale personel güncellemelerini optimistic locking ile engelle

#### Sıradaki backend backlog

- [x] Güvenli profil fotoğrafı yükleme, görüntüleme ve silme akışını tamamla
- [x] Aktif Admin için güvenli, sayfalı ve filtreli audit log okuma endpoint’ini ve sorgu indeks migration’ını tamamla
- [ ] Audit ekranında aktör/hedef filtrelerini ad veya kullanıcı adıyla aranabilir yap; kesin seçimde mevcut UUID filtresini koru, kullanıcıya UUID yazdırma ve aynı isimli personelleri güvenli biçimde ayırt et
    - Yalnız aktif Admin’e açık, sayfalı ve minimum arama uzunluğu bulunan güvenli bir personel lookup sözleşmesi tasarla.
    - Audit geçmişinde bulunabilmeleri için aktif/pasif bütün roller ile Admin ve öz-aktör kayıtlarını kapsa; yalnız `{ personelId, ad, soyad, kullaniciAdi, rol, aktif }` alanlarını döndür.
    - Frontend ad veya kullanıcı adıyla arama yapsın, aynı isimli kişileri kullanıcı adı/rol ile ayırsın ve seçilen kaydın UUID’sini mevcut audit filtresine göndersin.
- [x] Production için `personel.version` kontrollü manuel schema migration’ını hazırla
- [x] Optimistic-lock çatışmasını kontrollü `409 Conflict` response’una eşle
- [ ] Bütün şemayı Flyway/Liquibase baseline migration’larına taşı ve production’da `ddl-auto=update` kullanımını kapat
- [x] Yetenek silme davranışını tasarla ve ilişkili kayıtları `409` ile koruyan güvenli silmeyi uygula
- [ ] Yetenek pasifleştirme/soft-delete ihtiyacı için ayrı ürün kararı ver
- [-] Ekipman ve duyuru backend modüllerini roadmap aşamalarına göre tamamla — ekipman backend’i tamamlandı, ekipman frontend’i ve duyuru modülü bekliyor

### Aşama 2 — Yıllık izin ve şirket takvimi

**Durum:** `[-]` Tatil takvimi ile izin talebi oluşturma, owner iptali, Yönetici/Admin kuyrukları ve onay/red backend ile frontend’de çalışıyor; izin türü yönetimi, hizmet yılı/devreden bakiye modeli ve takvimde izin olayları açık.  
**Tahmin:** 8–12 gün  
**Zorluk:** Zor

#### Tamamlanan hazırlıklar

- [x] Temel izin entity’sini oluştur
- [x] Temel izin türü entity’sini oluştur
- [x] İzin talep request/response DTO iskeletlerini oluştur
- [x] İzin durum ilişkisinin veri modeli temelini oluştur
- [x] Sabit talep durumlarını `TalepDurum` enum kodlarıyla eşle
- [x] İzin türüne yıllık haktan düşme davranışını ekle

#### Veri modeli ve migration

- [x] İzin türüne “yıllık haktan düşer mi?” alanı ekle
- [x] Tam/yarım gün ve çok günlük grup destekli resmî/şirket tatili tablosu oluştur
- [x] Tatil türünü sabit/değişken/şirket günü olarak ayırt et
- [ ] İzin hakkı kazanımlarını hizmet yılı bazında izlenebilir tutacak modeli oluştur
- [ ] Mevcut sabit `yillikIzinHakki` alanından birikmiş hak modeline geçiş planı oluştur
- [ ] Mevcut personel verileri için izin hakkı migration yaklaşımını belirle
- [x] `IzinBilgileri.isGunuSayisi` alanını `0,5` günü güvenle temsil edecek `BigDecimal` tabanlı tipe dönüştür
- [x] İzin response DTO’larındaki gün sayısı alanlarını yarım gün destekleyecek şekilde güncelle
- [x] Sabit talep durumları, başlangıç izin türleri, sorgu indeksleri ve create audit ENUM değeri için idempotent MySQL migration ekle
- [x] Yönetici/Admin karar alanı, sorgu indeksleri ve onay/red audit ENUM değerleri için idempotent MySQL migration ekle
- [x] Owner iptal audit ENUM değeri için karar migration’ından sonra çalıştırılan idempotent MySQL migration ekle
- [ ] Sabit tatiller için yıllık üretim/seed yaklaşımı oluştur

Gelecekte değerlendirilecek hak modeli:

- Mevcut `Personel.yillikIzinHakki` alanı bu aşamada korunur.
- Hizmet yılına göre 14/20/26 gün tahakkuk, yıl bazlı hak kayıtları, devreden bakiye ve mevcut verilerin migration’ı ayrı bir mimari/veri tasarımı olarak ele alınacaktır; mevcut teslimin parçası değildir.

#### İş kuralları

- [ ] Tamamlanan hizmet yılına göre 14/20/26 gün hak hesapla
- [ ] Kullanılmayan izin hakkını sonraki yıllara devret
- [x] Hafta sonlarını iş günü hesabından çıkar
- [x] Tam günlük tatilleri izin hesabından çıkar
- [x] Yarım günlük ve çok günlük tatilleri kapsanan her iş gününde `0,5` gün olarak hesapla
- [x] Hastalık/mazeret gibi yıllık haktan düşmeyen türleri bakiye kontrolünden muaf tut
- [x] Yalnızca “yıllık haktan düşer” işaretli izin türlerini bakiyeden düş
- [x] Profil kalan bakiyesinde kullanılan miktarı yalnızca onaylanmış izinlerden hesapla
- [x] Yeni talepte onaylanmış tüketim ile bekleyen rezervasyonu birlikte değerlendirip bakiyeyi aşmayı engelle
- [x] Geçmiş, ters, 366 günü aşan veya sıfır iş günü üreten talep aralığını engelle
- [ ] Bekleyen talep için düzenleme kurallarını uygula
- [x] Owner’ın geçmiş tarihli olsa dahi bekleyen kendi talebini iptal etmesini sağla; son durumlar geri alınamaz
- [x] Onay/reddetmeyi işlem anındaki aktif doğrudan yöneticiye ve şirket geneli Admin’e aç
- [x] Başlamış veya geçmiş bekleyen izin talebinin sonradan onaylanmasını karar anı tarih doğrulamasıyla engelle
- [x] Aynı kişinin bekleyen/onaylı taleplerinde etkili iş günü çakışmasını kontrol et
- [x] Talep oluşturmayı `IZIN_TALEBI_OLUSTURULDU` olayıyla aynı transaction içinde audit et
- [x] Owner iptalini `IZIN_TALEBI_IPTAL_EDILDI` olayıyla audit et
- [x] Yetkili yönetici/Admin onayını `IZIN_TALEBI_ONAYLANDI` olayıyla audit et
- [x] Yetkili yönetici/Admin reddini `IZIN_TALEBI_REDDEDILDI` olayıyla audit et
- [ ] Talep düzenleme ayrıca onaylanırsa `IZIN_TALEBI_GUNCELLENDI` olayını ekle

#### API ve ekranlar

- [x] Aktif Personel, HR, uygun non-root Yönetici ve Admin için izin türü lookup
- [x] Aktif Personel, HR ve uygun non-root Yönetici için iş günü önizleme
- [x] Aktif Personel, HR ve uygun non-root Yönetici için izin talebi oluşturma
- [x] Talep oluşturabilen roller için owner-only, sayfalı ve durum filtreli kendi izin geçmişi
- [x] Owner’ın kendi bekleyen izin talebini iptal etmesi
- [x] Yönetici doğrudan takımının izin taleplerini görüntüleme
- [x] Yönetici izin onaylama/reddetme
- [x] Admin şirket geneli izin kayıtlarını görüntüleme ve işleme
- [ ] HR/Admin izin türü yönetimi
    - Gelecekte ayrıca tasarlanacaktır. Kullanılmış türü fiziksel silmek yerine geçmiş kayıtları koruyan pasifleştirme/yeniden adlandırma yaklaşımı değerlendirilmelidir; mevcut kapsamda uygulanmayacaktır.
- [x] HR/Admin tek ve çok günlük tatil takvimi backend yönetimi
- [ ] Takvimde kişisel izinleri gösterme
    - Gelecekte yalnız owner’ın `ONAYLANDI` izinlerini, görüntülenen tarih aralığıyla kesişim mantığı kullanarak güvenli takvim olayı biçiminde döndüren salt-okunur backend sözleşmesi eklenecektir.
- [ ] Takvimde yönetici için takım izinlerini gösterme
    - Gelecekte Yönetici yalnız işlem anındaki aktif doğrudan çalışanlarının `ONAYLANDI` izinlerini görecektir; dolaylı astlar ve başka ekipler kapsama alınmayacaktır.
    - Takım görünümünde çalışan adı, tarih aralığı ve tam/yarım gün bilgisi yeterlidir; izin türü, talep/karar notu ve gereksiz personel alanları açılmayacaktır. Okuma işlemleri audit üretmeyecektir.
- [x] Takvimde tek ve çok günlük resmî/şirket tatillerini grup halinde gösterme
- [x] Takvimde aktif çalışanların doğum günlerini şirket genelinde gösterme
  - [x] Güvenli tarih-aralığı backend endpoint’i; doğum yılı ve gereksiz personel alanları response’a eklenmiyor
  - [x] Frontend takvim event entegrasyonu

### Aşama 3 — Ekipman, envanter ve zimmet

**Durum:** `[-]` Backend iş akışı, manuel MySQL migration ve güvenlik/concurrency testleri tamamlandı; frontend ekranları bekliyor.  
**Tahmin:** 5–8 gün  
**Zorluk:** Orta–Zor

#### Tamamlanan hazırlıklar

- [x] Ekipman entity’sini oluştur
- [x] Ekipman tipi entity’sini oluştur
- [x] Ekipman talep entity’sini oluştur
- [x] Zimmet entity’sini oluştur
- [x] Ekipman ve zimmet DTO iskeletlerini oluştur

#### Tamamlanan backend işleri

- [x] Personel/HR ekipman talebi oluşturma
- [x] Personel/HR kendi ekipman taleplerini görüntüleme
- [x] Bekleyen ekipman talebini düzenleme
- [x] Bekleyen ekipman talebini iptal etme
- [x] Yöneticinin yalnızca doğrudan aktif ekibinin taleplerini görmesi
- [x] Yönetici/Admin ekipman talebi onaylama/reddetme
- [x] Admin ekipman tipi yönetimi
- [x] Admin ekipman envanteri yönetimi
- [x] Onaylanan talep için atomik zimmet kaydı oluşturma
- [x] Zimmet sırasında uygun stok ve eşzamanlılık kontrolü
- [x] İade işleminde ekipmanı tekrar depoya alma
- [x] Zimmet geçmişini silmeden koruma
- [x] Personel pasifleştirme/rol geçişinde bekleyen talepleri güvenli iptal etme
- [x] MySQL migration, rollback, rol/ownership, concurrency ve HTTP security testleri

#### Bekleyen frontend ve MVP sonrası işler

- [ ] Personel/HR talep oluşturma, geçmiş, güncelleme ve iptal ekranları
- [ ] Yönetici takım karar kuyruğu ve Admin şirket geneli karar ekranı
- [ ] Admin ekipman tipi, envanter ve zimmet/iade ekranları
- [ ] Doğrudan zimmet atama, bakım süreci, çoklu depo, QR/barkod ve tip-bazlı dinamik özellikler için ayrı ürün/veri modeli kararı

### Aşama 4 — Oda rezervasyonu ve ürün takvimi

**Durum:** `[-]` Backend ve frontend oda rezervasyonu, geçmiş/iptal, Admin şirket geçmişi, pasifleştirme, şirket takviminde rezervasyon gösterimi ve dashboard doluluk akışları tamamlandı; ürün takviminin olay filtreleri ile kişisel/takım izin olayları bekliyor.  
**Tahmin:** 4–6 gün  
**Zorluk:** Orta

#### Tamamlanan hazırlıklar

- [x] Oda entity’sini oluştur
- [x] Rezervasyon entity’sini oluştur
- [x] Rezervasyon request/response DTO iskeletlerini oluştur
- [x] `/calendar` frontend rotasını oluştur
- [x] TailAdmin takvim şablonunu projede koru

#### Backend rezervasyon işleri

- [x] Oda repository/service/controller akışını oluştur
- [x] Oda listesini ve kapasiteleri getir
- [x] Rezervasyon repository/service/controller akışını oluştur
- [x] Tarih aralığı ve odaya göre aktif rezervasyonları listele
- [x] Saatlik rezervasyon oluştur
- [x] Çalışma saati, tam/yarım gün tatil, süre ve ileri tarih sınırlarını doğrula
- [x] Aynı oda ve aynı owner interval çakışmalarını backend’de engelle
- [x] Owner/Admin başlamamış aktif rezervasyon iptalini soft-state ile uygula
- [x] Kendi ve Admin rezervasyon geçmişini filtreli/sayfalı sun
- [x] Personel pasifleştirmeyi başlamamış rezervasyon iptaliyle atomik bağla
- [x] Ownership, audit, rollback ve gerçek MySQL concurrency testlerini ekle
- [x] Aktif rezervasyonla çakışan tatil oluşturma/güncellemeyi atomik `409` ile engelle; iptal edilen rezervasyon sonrasında yeniden denemeye izin ver

#### Ürün takvimi

- [x] TailAdmin demo event’lerini kaldır
- [x] Takvimi gerçek backend verilerine bağla
- [ ] Resmî/şirket tatili filtresi
- [ ] Kişisel izin filtresi
- [ ] Yönetici takım izinleri filtresi
- [ ] Oda rezervasyonları filtresi
- [x] Oda rezervasyonlarını güvenli alanlarla şirket takviminde göster
- [ ] Farklı olay türlerini tutarlı renk ve açıklamalarla göster
- [x] Takvim loading, error ve empty durumlarını oluştur

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

**Durum:** `[-]` Authentication, ekip dizini, tatil/doğum günü takvimi, izin ve rezervasyon backend/frontend akışları hazır; ekipman backend’i hazır, ekipman frontend’i ile duyuru, takdir ve kalan dashboard ürünleştirmeleri bekliyor.  
**Tahmin:** 5–8 gün  
**Zorluk:** Orta

#### Dashboard

- [x] Doğum günü verisini gerçek backend sorgusundan getir
- [x] Doğum günü kartını backend’e bağla
- [x] Doğum günü kartında loading/error/empty durumlarını göster
- [x] Role göre görünür aktif ekip üyesi sayısını dashboard özetine ekle
- [x] Aynı görünür kümeden ilk 5 güvenli ekip dizini kaydını dashboard özetine ekle
- [ ] Son duyuruları role ve hedef kitleye göre göster
- [ ] Bekleyen talepleri role göre göster
- [x] Dashboard ekip özeti satırlarında bugünün tam/yarım gün izin durumunu göster
- [x] Dashboard response’una güvenli oda doluluk özetini ekle
- [x] Oda doluluk özetini frontend kartında göster
- [ ] Son takdirleri göster
- [x] Backend hesaplı toplam/kullanılan/onay bekleyen/kalan izin bilgisini göster
- [x] Dashboard response’unu role göre düzenle
- [x] Dashboard için backend testleri yaz

#### Kalan izin kartı

TailAdmin `MonthlyTarget` bileşeni dönüştürülecek:

- [x] Bileşeni `KalanIzinKarti` olarak yeniden adlandır
- [x] Başlığı “Kalan İzin Hakkın” yap
- [x] Yüzdeyi backend’in kullanılabilir kalan bakiyesine göre hesapla
- [x] Alt alanları `Toplam Hak / Kullanılan / Kalan` yap
- [x] `+10%` alanını onay bekleyen izin bilgisine dönüştür
- [x] Gelir/hedef metinlerini kaldır
- [x] Kartı izin sayfasına bağla
- [x] Backend `izinBakiyesi=null` döndürdüğünde kartı gizle

#### Navigasyon ve sayfalar

- [x] `/calendar` gerçek tatil API’sine bağlı; tek ve çok günlük tatil grubu görüntüleme/yönetme akışları hazır
- [x] Sidebar’da “Ekip Dizini” ana etiketini oluştur
- [x] Ekip dizini içeriğini gerçek personel verisine dönüştür
- [x] Ekip dizinini `/ekip-dizini` rotasına taşı
- [x] Talepler menüsünde izin ve ekipman başlıklarını ayır
- [x] Talepler menüsüne role göre rezervasyon, takım izin talepleri ve tüm izin talepleri seçeneklerini ekle
- [ ] Duyurular menüsünü bütün rollere görünür, oluşturma ve moderasyon eylemlerini role göre sayfa içinde sınırlı tasarla
- [ ] PERSONEL, YONETICI ve HR için talep edilebilir ekipman kataloğu menüsünü ekle
- [-] HR ve ADMIN yönetim menülerini ürünleştir — personel/katalog ve tatil eylemleri, ADMIN izin karar kuyruğu ve audit ekranı hazır; envanter/zimmet bekliyor
- [x] ADMIN için audit log menüsünü, role-protected route’u ve filtreli/sayfalı ekranı ekle
- [ ] ADMIN için rol yönetimi ve envanter/zimmet menülerini tamamla
- [x] Kendi izin oluşturma/geçmiş/iptal sayfasını ve Yönetici/Admin karar listelerini route’lara bağla
- [ ] `/talepler/ekipman` rotasını ve sayfasını oluştur
- [ ] Duyuru ve kalan yönetim menüleri için gerçek route ve sayfaları backend sözleşmeleri hazır olduğunda oluştur
- [ ] `/form-elements` ve `/basic-tables` gibi demo route adlarını kaldır
- [x] Menüyü role göre filtrele
- [x] Route seviyesinde role uygun kullanıcı deneyimi sağla
- [x] `RoleRoute` guard’ını oluştur ve personel/katalog yönetim rotalarında kullan

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
- [x] Tatil ve iş günü hesabı sınır testleri
- [x] Yarım gün `0,5` hesaplama testleri
- [ ] Kıdem ve devreden bakiye testleri
- [ ] Takım duyurusu görünürlük testleri
- [x] Rezervasyon çakışma, ownership, rollback ve concurrency testleri
- [x] Ekipman stok, talep/karar, zimmet/iade, rollback ve concurrency testleri
- [ ] Dosya yükleme kötüye kullanım testleri
- [x] Security 401/403 response testleri

#### Frontend doğrulama

- [ ] Frontend test yaklaşımını ve gerekli test araçlarını belirle
- [ ] Auth guard davranışlarını test et
- [ ] Silent refresh davranışını test et
- [ ] Role göre menü görünürlüğünü test et
- [ ] Kritik dashboard kartlarını test et
- [ ] Loading, error ve empty durumlarını kontrol et
- [x] Frontend production build doğrulaması

#### Dokümantasyon ve demo

- [ ] Swagger/OpenAPI çıktısını güncelle
- [ ] `API_SPEC.md` dosyasını gerçek endpoint’lerle eşitle
- [x] Auth bölümündeki refresh/logout açıklamalarını güncelle
- [x] Dashboard endpoint yolunun `/api/dashboard/ozet` olduğunu doğrula
- [ ] Eski kararlarla çelişen doküman maddelerini temizle
- [x] README içindeki proje durumunu güncelle
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
| Profil fotoğrafı güvenliği | `[x]` | Orta–Zor | 2–4 gün | Aşama 1; upload, authenticated GET, silme ve rollback tamamlandı |
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
- [x] HR personeli geçmişini silmeden pasife alabiliyor.
- [x] Personel, HR ve uygun non-root Yönetici yıllık izin talebi oluşturabiliyor.
- [x] Personel ve HR ekipman talebi oluşturabiliyor.
- [x] Yönetici yalnızca doğrudan takımının ekipman taleplerini işleyebiliyor.
- [ ] İzin bakiyesi kıdem ve devreden haklara göre hesaplanıyor.
- [ ] Hafta sonları izin bakiyesinden düşmüyor.
- [x] Tam günlük tatiller iş günü hesabında izin bakiyesine tüketim yazmıyor.
- [x] Yarım günlük tatiller kapsadıkları her hafta içi için `0,5` gün etkisiyle hesaplanıyor.
- [ ] Hastalık izni yıllık izin hakkından düşmüyor.
- [ ] Yönetici yalnızca kendi doğrudan takımına duyuru gönderebiliyor.
- [ ] HR/Admin takım duyurularını denetleyebiliyor.
- [x] Admin envanter ve zimmetleri yönetebiliyor.
- [x] Oda rezervasyonlarında aynı oda ve aynı owner çakışması engelleniyor.
- [x] Owner kendi başlamamış rezervasyonunu, Admin yetkili kapsamda başkasının rezervasyonunu iptal edebiliyor; kayıt geçmişte korunuyor.
- [ ] Dashboard gerçek ve role uygun veriler gösteriyor.
- [x] Profil fotoğrafı güvenli biçimde yükleniyor, authenticated sunuluyor ve silinebiliyor.
- [x] Frontend menüsü role göre şekilleniyor.
- [ ] Kritik yetki ve iş kuralları otomatik testlerle korunuyor.
- [ ] `API_SPEC.md`, Swagger ve frontend rotaları gerçek uygulamayla uyumlu.
- [ ] Kullanılmayan TailAdmin demo içerikleri temizlenmiş.
- [ ] Manuel uçtan uca MVP senaryosu başarıyla doğrulanmış.
