# API Specification

> Planlama dokümanı — canlı OpenAPI spec'i SpringDoc tarafından `/v3/api-docs` üzerinden otomatik üretilir (Swagger UI: `/swagger-ui.html`).
> Bu dosya endpoint yüzeyini, rol kurallarını ve tasarım notlarını tutar.

### Durum ayrımı

- **Uygulandı** olarak belirtilen bölümler mevcut controller/service koduyla çalışan sözleşmeleri anlatır.
- Başlığında **planlanan** yazan bölümler ürün tasarımıdır; bu endpoint’ler henüz backend’de yoktur ve frontend tarafından canlı sözleşme gibi kullanılmamalıdır.
- Kod ile belge çelişirse çalışan backend ve `/v3/api-docs` gerçek kaynak kabul edilir; belge aynı teslimde düzeltilir.

## Conventions

- Base path: `/api`
- Auth: login, refresh, logout ve ilk parola değiştirme endpoint’leri Spring Security açısından `permitAll` durumundadır. Login ve ilk parola değiştirme credential; refresh/logout ise `httpOnly` refresh cookie üzerinden kendi doğrulamasını yapar. Diğer endpoint’ler JWT Bearer token ister.
- Roller: `Personel`, `Yönetici`, `HR`, `Admin` | Admin tüm menülere yetkili
- `(owner)` = kaydı oluşturan kişi; service katmanında `kayit.personelId == token.personelId` kontrolü

## Status Codes

| Code | Anlamı | Örnek |
|------|--------|-------|
| 200 | OK | Listeleme, güncelleme başarılı |
| 201 | Created | POST ile kayıt oluştu |
| 204 | No Content | İşlem başarılı, response body yok |
| 400 | Bad Request | Validation hatası (bitiş < başlangıç gibi) |
| 401 | Unauthorized | Token yok / geçersiz / süresi dolmuş |
| 403 | Forbidden | Token geçerli ama rol/ownership yetmiyor |
| 404 | Not Found | Kayıt yok |
| 409 | Conflict | Çakışan rezervasyon, duplicate/kullanımdaki kayıt veya optimistic-lock çatışması |
| 413 | Payload Too Large | Profil fotoğrafı 2 MB sınırını aşıyor |
| 415 | Unsupported Media Type | Profil fotoğrafı formatı veya içerik türü desteklenmiyor |
| 428 | Precondition Required | İlk girişte parola değişikliği zorunlu |

- Optimistic-lock çatışmasında bütün endpoint’ler güvenli ortak response olarak `409 Conflict` ve `Kayıt başka bir işlem tarafından güncellendi. Lütfen bilgileri yenileyip tekrar deneyin.` mesajını döner. Hibernate entity adı, SQL, version değeri veya stack trace istemciye verilmez.

Authentication ve authorization hataları ortak `ErrorResponse` gövdesini kullanır: `status`, kontrollü `message`, `timestamp` ve istek `path` değeri.

---

## Auth

| Method | Endpoint | Role |
|--------|----------|------|
| POST | `/api/auth/login` | herkes |
| POST | `/api/auth/refresh` | refresh cookie sahibi |
| POST | `/api/auth/logout` | herkes; cookie yoksa da idempotent |
| PATCH | `/api/auth/ilk-parola` | herkes; credential doğrulaması ve rate limit uygulanır |

- Login response: `{ token, ad, soyad, rol }` frontend menüyü role göre kurar
- Başarılı login response body’sinde access token döner; ham refresh token body’ye yazılmaz, `httpOnly` cookie olarak gönderilir.
- Pasif personel doğru parola kullansa bile login ile yeni token alamaz.
- Doğru credential kullanan fakat `parolaDegistirmeliMi=true` olan personelin login isteği `428` döner; access token, refresh token/cookie ve başarılı/başarısız login audit kaydı üretilmez.
- İlk parola değiştirme request’i `{ kullaniciAdi, mevcutParola, yeniParola }` biçimindedir. Başarılı işlem flag’i kapatır, personele ait refresh tokenları siler, audit kaydı oluşturur ve `204 No Content` döner.
- `/api/auth/ilk-parola` endpoint’i IP ve path bazında dakikada 5 istekle sınırlandırılır.
- Refresh isteği `refreshToken` cookie’sini kullanır; geçerli token rotate edilir, eski token revoked olur, yeni access token body’de ve yeni refresh token cookie’de döner.
- Pasif personelin refresh isteği reddedilir ve personele ait refresh token kayıtları silinir.
- Logout, cookie’deki refresh token kaydını sunucuda siler, `LOGOUT` audit olayı üretir ve cookie’yi `maxAge=0` ile temizler. Cookie yoksa da 204 döner.

**Örnek — login:**

```
POST /api/auth/login
Content-Type: application/json

{ "kullaniciAdi": "can.gere", "parola": "****" }

-> 200 OK
{ "token": "eyJhbGciOiJIUzI1...", "ad": "Can", "soyad": "Gere", "rol": "PERSONEL" }

-> 401 Unauthorized (kullanıcı adı/parola yanlış)
```


---

## Profil

| Method | Endpoint | Role |
|--------|----------|------|
| GET | `/api/profil` | kimliği doğrulanmış tüm roller |
| PATCH | `/api/profil` | kimliği doğrulanmış tüm roller |
| PUT | `/api/profil/yetenekler` | kimliği doğrulanmış tüm roller |
| PATCH | `/api/profil/parola` | kimliği doğrulanmış tüm roller |
| PUT | `/api/profil/fotograf` | aktif ve kimliği doğrulanmış tüm roller |
| DELETE | `/api/profil/fotograf` | aktif ve kimliği doğrulanmış tüm roller |
| GET | `/api/profil-fotograflari/{dosyaAnahtari}` | aktif ve kimliği doğrulanmış tüm roller |

- `GET /api/profil` request body veya personel ID parametresi almaz; personel kimliği yalnız JWT principal’dan (`Authentication.getName()`) alınır.
- Başarılı profil isteği `200 OK` ile mevcut `ProfilBilgileriResponse` sözleşmesini döner.
- Response; kullanıcı adı, ad-soyad, doğum tarihi, e-posta, sicil numarası, departman, rol, opsiyonel yönetici, yıllık izin hakkı, profil fotoğrafı, eğitim/iletişim bilgileri, işe giriş tarihi ve alfabetik yetenek listesini içerir.
- `PERSONEL` ve `HR` için `kalanIzin`, yalnız `yillikHaktanDuserMi=true` olan ve durumu `ONAYLANDI` olan izinlerin iş günü toplamı düşülerek hesaplanır.
- `ADMIN` ve `YONETICI` için `kalanIzin=null` döner.
- Yönetici yoksa `yonetici` alanı response’tan çıkar; yetenek yoksa `yetenekler=[]` döner.
- Profil görüntüleme read-only’dir ve audit kaydı üretmez.
- Token yoksa/geçersizse `401`; token personeli veritabanında bulunamazsa kontrollü `404` döner.
- `mezuniyetYili` profil response’una dahil değildir; entity alanı HR güncelleme akışı için korunur.
- `PATCH /api/profil` request veya path üzerinde personel ID almaz; yalnız JWT principal’daki personelin kendi profilini günceller.
- Güncellenebilir alanlar `{ telNo, mezunOkul, mezunBolum, mezuniyetYili }` ile sınırlıdır. Kimlik, kullanıcı adı, e-posta, rol, departman, yönetici, çalışma durumu, profil fotoğrafı ve yetenekler bu endpoint’ten değiştirilemez.
- Kısmi PATCH semantiğinde gönderilmeyen alan mevcut değerini korur; izin verilen alanlar açıkça `null` gönderilerek temizlenebilir. Metin alanlarında boş veya yalnız boşluk içeren değerler `400 Bad Request` döner.
- `mezuniyetYili` pozitif olmalı ve mevcut yıldan büyük olmamalıdır; `null` gönderilirse temizlenir.
- Gerçek değişiklik `204 No Content` döner ve yalnız değişen alan adlarını içeren `PROFIL_GUNCELLENDI` audit kaydı oluşturur. Audit aktörü ve hedefi aynı personeldir; kişisel alanların eski veya yeni değerleri audit detayına yazılmaz.
- Mevcut değerlerin tekrar gönderildiği no-op istek `204 No Content` döner ve audit yan etkisi oluşturmaz. Profil değişikliği ile audit kaydı aynı transaction’a katılır.
- Pasif personelin kalan ömrü bulunan access token ile güncelleme denemesi `403`; JWT principal’daki personel bulunamazsa `404`; kimlik doğrulanmamış istek `401` döner.
- `PUT /api/profil/yetenekler` request veya path üzerinde personel ID almaz; yalnız JWT principal’daki personelin kendi yetenek kümesini tam-değiştirme semantiğiyle günceller. Request `{ "yetenekIdleri": [1, 3, 7] }` biçimindedir; boş liste bütün yetenekleri kaldırır.
- `yetenekIdleri` listesindeki `null`, sıfır, negatif veya tekrarlanan değerler `400 Bad Request` döner. İstenen ID’lerden biri bile bulunamazsa bütün istek `404 Yetenek bulunamadı.` ile reddedilir ve hiçbir ilişki değiştirilmez.
- İstenen küme mevcut kümeden farklıysa yalnız kaldırılan ve eklenen ilişkiler güncellenir; başarılı işlem `204 No Content` döner ve aynı transaction içinde `PROFIL_GUNCELLENDI` audit kaydı oluşturur. Audit detayı yalnız `Güncellenen alanlar: yetenekler` olur; yetenek adı veya ID içermez.
- Aynı kümenin farklı sırada gönderildiği no-op istek `204 No Content` döner; persistence veya audit yan etkisi oluşturmaz. Pasif principal `403`, bulunamayan principal `404`, kimlik doğrulanmamış istek `401` alır.

### Profil fotoğrafı

- `PUT /api/profil/fotograf`, `multipart/form-data` içinde zorunlu `dosya` part’ını alır; request veya path üzerinde personel ID kabul etmez. Aktör yalnız JWT principal’dan belirlenir.
- Yalnız JPEG ve PNG kabul edilir. Normalize uzantı, bildirilen `Content-Type`, dosya imzası ve decode edilen gerçek format birbiriyle uyumlu olmalıdır. Boş veya bozuk görsel `400`, desteklenmeyen/uyumsuz format `415 Unsupported Media Type` döner.
- Dosya en fazla 2 MB, genişlik ve yükseklik en fazla 2048 piksel, toplam piksel sayısı en fazla 4.194.304 olabilir. Dosya sınırı aşılırsa `413 Payload Too Large`; boyut veya piksel sınırı aşılırsa `400 Bad Request` döner.
- Kabul edilen görsel ham biçimde saklanmaz. Decode edilip metadata’sız JPEG/PNG olarak yeniden üretilir; en uzun kenar en fazla 1024 piksele küçültülür, en-boy oranı korunur ve küçük görsel büyütülmez.
- Başarılı yükleme/değiştirme `200 OK` ve `{ "profilFoto": "<uuid>.jpg" }` veya `{ "profilFoto": "<uuid>.png" }` döner. Veritabanında yalnız sunucunun ürettiği UUID dosya anahtarı tutulur; orijinal dosya adı, URL veya fiziksel yol saklanmaz.
- Yeni fotoğraf ve `PROFIL_FOTOGRAFI_GUNCELLENDI` auditi aynı veritabanı transaction’ına bağlanır. Rollback olursa yeni dosya telafi işlemiyle silinir; commit olursa önceki özel UUID dosyası güvenli biçimde temizlenir. Audit detayı orijinal ad, UUID veya fiziksel yol içermez.
- `DELETE /api/profil/fotograf` başarılı gerçek silme ve no-op için `204 No Content` döner. Gerçek silmede DB anahtarı `def_pfp.png` olur, `PROFIL_FOTOGRAFI_SILINDI` auditi transaction’a katılır ve eski özel dosya yalnız commit sonrasında silinir. Değer zaten sentinel/null/geçersiz ise persistence, audit veya dosya sistemi yan etkisi oluşmaz.
- `GET /api/profil-fotograflari/{dosyaAnahtari}` yalnız kanonik `<uuid>.jpg` veya `<uuid>.png` anahtarlarını sunar. Başarılı response gerçek `image/jpeg` veya `image/png` içeriğini, `X-Content-Type-Options: nosniff` ve `Cache-Control: private, max-age=3600` başlıklarını taşır; `Content-Disposition: attachment` eklenmez ve audit oluşmaz.
- Uygulama katmanına ulaşan geçersiz/eksik fotoğraf anahtarı veya bulunamayan dosya kontrollü `404` döner. Encoded traversal isteği Spring Security firewall tarafından controller’a ulaşmadan `400` ile reddedilebilir. `def_pfp.png` fiziksel fotoğraf endpoint’inden istenmez; frontend bu değerde baş harfli avatar gösterir.
- Üç endpoint de aktif authenticated principal ister. Token yoksa/geçersizse `401`, pasif principal `403`, principal personeli bulunamazsa `404 Personel bulunamadı.` döner. Fotoğraf GET’inde bu sürümde anahtar başına ayrıca ownership/rol filtresi uygulanmaz.
- Aynı anahtar `GET /api/profil`, görünür ekip dizini, dashboard özeti ve izin verilen HR/Admin personel yönetimi liste/detay DTO’larına taşınır. Özel fotoğrafı olmayan kayıtlar `def_pfp.png` döndürür.

**Örnek:**

```json
{
  "kullaniciAdi": "test.personel",
  "ad": "Test",
  "soyad": "Personel",
  "dogumTarihi": "1995-05-10",
  "ePosta": "test.personel@test.local",
  "sicilNo": 1,
  "departman": "Bilgi Teknolojileri",
  "rol": "PERSONEL",
  "yillikIzinHakki": 20,
  "profilFoto": "def_pfp.png",
  "kalanIzin": 16,
  "mezunOkul": "Test Üniversitesi",
  "mezunBolum": "Bilgisayar Mühendisliği",
  "telNo": "05550000000",
  "isGirisTarihi": "2022-01-10",
  "yetenekler": [
    { "id": 2, "ad": "Angular" },
    { "id": 1, "ad": "Java" }
  ]
}
```

---

## Oda rezervasyonu

| Method | Endpoint | Erişim |
|--------|----------|--------|
| GET | `/api/rooms` | Aktif PERSONEL, YONETICI, HR, ADMIN |
| GET | `/api/reservations?baslangicTarihi=&bitisTarihi=&odaId=` | Aktif PERSONEL, YONETICI, HR, ADMIN |
| POST | `/api/reservations` | Aktif PERSONEL, YONETICI, HR, ADMIN; yalnız principal adına |
| GET | `/api/reservations/my?page=&size=&durum=&odaId=&baslangic=&bitis=` | Aktif PERSONEL, YONETICI, HR, ADMIN; yalnız kendi geçmişi |
| GET | `/api/admin/reservations?page=&size=&durum=&odaId=&olusturanId=&baslangic=&bitis=` | Yalnız aktif ve DB rolü de ADMIN |
| PATCH | `/api/reservations/{rezervasyonId}/cancel` | Owner veya gerçek DB Admin |

Oda lookup’ı varsayılan seed kayıtlarını `{ id, ad, kapasite }` biçiminde döndürür: `Büyük Salon (12)` ve `Küçük Oda (6)`. Takvim sorgusu iki uç dahil en fazla 366 günlük aralık kabul eder; `odaId` opsiyoneldir. Aktif rezervasyonlarda oluşturanın yalnız ad/soyadı görünür. Başlık ve katılımcı sayısı yalnız owner veya gerçek DB Admin için dolu, diğer kullanıcılarda `null` döner.

Create body yalnız şu alanları taşır:

```json
{
  "odaId": 1,
  "tarih": "2026-08-28",
  "baslangicSaat": 10,
  "bitisSaat": 12,
  "baslik": "Sprint planlama",
  "katilimciSayisi": 6
}
```

- Saatler tam sayıdır; varsayılan çalışma aralığı `08:00–18:00`, maksimum süre 4 saat ve ileri tarih sınırı 90 gündür.
- Bugün için yalnız sonraki tam saatten başlayan rezervasyon kabul edilir. Hafta sonu ve tam gün tatilde rezervasyon yapılamaz. Yarım gün tatilde bitiş en geç `13:00` olabilir.
- Katılımcı sayısı owner dahil en az 1 ve oda kapasitesini aşmayacak biçimde backend’de doğrulanır.
- Oda çakışması ve aynı owner’ın farklı odadaki çakışması `yeni.baslangic < mevcut.bitis AND yeni.bitis > mevcut.baslangic` koşuluyla engellenir; bitişik aralıklar çakışma sayılmaz.
- Create owner Personel satırını, ardından oda satırını pessimistic lock ile alır. Aynı owner veya oda yarışları seri hale gelir; farklı owner/farklı oda işlemleri global kilitle gereksiz yere bloke edilmez.
- Başarılı create `201 Created` ve güvenli `RezervasyonResponse` döner.

`/my` ve Admin geçmişi `{ icerik, sayfa, sayfaBoyutu, toplamKayit, toplamSayfa }` biçiminde, `tarih DESC`, `baslangicSaat DESC`, `rezervasyonId DESC` kararlı sırasıyla döner. Varsayılan `page=0`, `size=20`; maksimum boyut 100’dür. `baslangic` ile `bitis` birlikte gönderilir. Admin `olusturanId` filtresi canonical UUID olmalıdır; bilinmeyen geçerli UUID boş sayfa üretir.

İptal body’si opsiyoneldir:

```json
{ "iptalNedeni": "Toplantı iptal edildi." }
```

Owner yalnız kendi başlamamış `AKTIF` rezervasyonunu, neden vermeden iptal edebilir. Admin hem kendi hem başkasının başlamamış aktif rezervasyonunu iptal edebilir; başkasının kaydında trim sonrası `1..500` karakter neden zorunludur. Kayıt silinmez, `IPTAL_EDILDI` durumunda geçmişte tutulur. Başlamış, geçmiş veya zaten iptal kayıt `409 Rezervasyon artık iptal edilemez.` döner. Personel pasifleştirilirken bütün başlamamış aktif rezervasyonları aynı transaction içinde iptal edilir.

Create ve iptal işlemleri sırasıyla `REZERVASYON_OLUSTURULDU` ve `REZERVASYON_IPTAL_EDILDI` auditlerini ana transaction içinde yazar. Audit detayları yalnız `Rezervasyon oluşturuldu.` veya `Rezervasyon iptal edildi.` olur; oda, tarih/saat, başlık, katılımcı veya iptal nedeni audit detayına yazılmaz. Okuma endpoint’leri auditsizdir.

---

## Takdir Panosu (planlanan)

| Method | Endpoint | Role |
|--------|----------|------|
| GET | `/api/kudos?limit=` | tüm roller |
| POST | `/api/kudos` | tüm roller |
| DELETE | `/api/kudos/{id}` | admin (moderasyon) |

- POST body: `{ aliciPersonelId, mesaj }` gönderen token'dan alınır, body'den değil

---

## Ekipman, envanter ve zimmet (uygulandı)

### Endpoint ve rol matrisi

| Method | Endpoint | Erişim |
|--------|----------|--------|
| GET | `/api/equipment-types` | Aktif PERSONEL, YONETICI, HR, ADMIN |
| GET | `/api/admin/equipment-types?page=&size=&aktif=&arama=` | Yalnız aktif ve DB rolü de ADMIN |
| POST | `/api/admin/equipment-types` | Yalnız aktif ve DB rolü de ADMIN |
| PATCH | `/api/admin/equipment-types/{ekipmanTipiId}` | Yalnız aktif ve DB rolü de ADMIN |
| GET | `/api/admin/equipment?page=&size=&ekipmanTipiId=&durum=&arama=` | Yalnız aktif ve DB rolü de ADMIN |
| POST | `/api/admin/equipment` | Yalnız aktif ve DB rolü de ADMIN |
| PATCH | `/api/admin/equipment/{ekipmanId}` | Yalnız aktif ve DB rolü de ADMIN |
| PATCH | `/api/admin/equipment/{ekipmanId}/status` | Yalnız aktif ve DB rolü de ADMIN |
| POST | `/api/equipment-requests` | Aktif PERSONEL ve HR; yalnız principal adına |
| GET | `/api/equipment-requests/my?page=&size=&durum=&ekipmanTipiId=` | Aktif PERSONEL ve HR; yalnız kendi talepleri |
| PATCH | `/api/equipment-requests/{talepId}` | Aktif PERSONEL ve HR; yalnız kendi bekleyen talebi |
| PATCH | `/api/equipment-requests/{talepId}/cancel` | Aktif PERSONEL ve HR; yalnız kendi bekleyen talebi |
| GET | `/api/equipment-requests/team?durum=&ekipmanTipiId=&page=&size=` | Yalnız aktif ve DB rolü de YONETICI; doğrudan aktif çalışanları |
| GET | `/api/admin/equipment-requests?durum=&ekipmanTipiId=&personelId=&page=&size=` | Yalnız aktif ve DB rolü de ADMIN; şirket geneli |
| GET | `/api/equipment-requests/{talepId}/available-equipment?page=&size=` | Aktif YONETICI veya ADMIN; yetkili olduğu talep |
| PATCH | `/api/equipment-requests/{talepId}/approve` | Aktif YONETICI veya ADMIN; yetkili olduğu bekleyen talep |
| PATCH | `/api/equipment-requests/{talepId}/reject` | Aktif YONETICI veya ADMIN; yetkili olduğu bekleyen talep |
| GET | `/api/assignments/my?page=&size=` | Aktif PERSONEL, YONETICI, HR, ADMIN; yalnız kendi geçmişi |
| GET | `/api/admin/assignments?aktif=&personelId=&ekipmanTipiId=&page=&size=` | Yalnız aktif ve DB rolü de ADMIN |
| PATCH | `/api/admin/assignments/{zimmetId}/return` | Yalnız aktif ve DB rolü de ADMIN |

Kimliği doğrulanmamış istek `401`; izin verilmeyen JWT rolü, pasif aktör veya JWT–DB rol uyuşmazlığı `403`; principal veritabanında bulunamazsa kontrollü `404` döner. Yetki ve ownership kararları service katmanında güncel veritabanı rolü/ilişkisiyle tekrar doğrulanır.

### Ekipman tipi ve envanter

`GET /api/equipment-types`, yalnız aktif tipleri alfabetik `[{ "id": 1, "ad": "Laptop" }]` biçiminde döndürür. Admin tipi aşağıdaki body ile oluşturur; PATCH body’sinde alanlar opsiyoneldir fakat en az biri gönderilmelidir:

```json
{ "ad": "Laptop" }
```

```json
{ "ad": "Dizüstü Bilgisayar", "aktifMi": false }
```

- Tip adı trim sonrası `1..50` karakterdir. Case-insensitive duplicate veya unique-constraint yarışı kontrollü `409 Ekipman tipi zaten mevcut.` döner.
- Admin tipi `arama` ve `aktif` filtreleriyle listeler. Bekleyen talebi bulunan tip pasife alınamaz ve `409` döner. No-op PATCH `204` döner ve audit üretmez.
- Envanter create body’si `{ "ekipmanTipiId": 1, "marka": "Dell", "model": "Latitude 5450", "seriNo": "SN-001" }` biçimindedir. Başarılı create `201` ile `{ id, ekipmanTipiId, ekipmanTipi, marka, model, seriNo, durum, kullanimDisiNedeni, acikZimmetVarMi }` döner; ilk durum `DEPODA`dır.
- Envanter listesi `ekipmanTipiId`, `durum=DEPODA|PERSONELDE|KULLANIM_DISI` ve `arama` filtrelerini `AND` uygular. Seri numarası benzersizdir; duplicate `409` döner.
- Metadata PATCH body’si `{ marka?, model?, seriNo? }` alanlarından en az birini taşımalıdır. Durum PATCH body’si `{ "durum": "KULLANIM_DISI", "neden": "Bakım bekliyor" }` biçimindedir.
- Admin yalnız `DEPODA ↔ KULLANIM_DISI` geçişini yapabilir. `PERSONELDE` cihaz iade endpoint’i kullanılmadan değiştirilemez; açık zimmetli cihaz kullanım dışına alınamaz. `KULLANIM_DISI` için neden zorunlu, `DEPODA` için neden yasaktır. İhlaller kontrollü `400` veya `409` döner.
- Ekipman fiziksel olarak silinmez. Bu yaklaşım zimmet geçmişinin referanslarını korur.

### Talep oluşturma, kendi talepleri ve iptal

Create ve güncelleme body’si:

```json
{ "ekipmanTipiId": 1, "aciklama": "Proje geliştirme için gerekli." }
```

- Aktör/owner request’ten alınmaz; yalnız JWT principal belirler. Tip aktif olmalıdır. Açıklama opsiyoneldir, trim edilir ve en fazla 255 karakterdir.
- Aynı owner ve ekipman tipi için ikinci `ONAY_BEKLIYOR` talep oluşturulamaz; yarış dahil kontrollü `409` döner.
- Başarılı create `201` ve güvenli `EkipmanTalepResponse` döner. Talep durumları `ONAY_BEKLIYOR`, `ONAYLANDI`, `REDDEDILDI`, `IPTAL_EDILDI` değerleridir.
- `/my` yalnız principal’a ait kayıtları, `durum` ve `ekipmanTipiId` filtrelerini `AND` uygulayarak döndürür. Başka owner’ın canonical UUID’si endpoint sözleşmesinde kabul edilmez.
- Güncelleme ve iptal yalnız owner’ın `ONAY_BEKLIYOR` talebinde mümkündür. Terminal talep `409 Talep artık işlem beklemiyor.` döner; başka owner’ın veya bulunmayan kaydın ayrımı bilgi sızıntısını azaltmak için güvenli `404` olarak gizlenir.
- Güncelleme aynı normalize değerlerle no-op ise `204` ve yan etkisizdir. İptal kaydı silmez; `IPTAL_EDILDI`, işlem yapan ve server zamanı ile geçmişte korunur.

Örnek talep response’u:

```json
{
  "id": "4fd94d50-88e5-45aa-823e-e91bf932cb5c",
  "ekipmanTipiId": 1,
  "ekipmanTipi": "Laptop",
  "aciklama": "Proje geliştirme için gerekli.",
  "durum": "ONAY_BEKLIYOR",
  "durumAdi": "Onay bekliyor",
  "talepTarihi": "2026-08-31T14:30:00",
  "kararNotu": null,
  "islemTarihi": null,
  "kararVeren": null,
  "zimmet": null
}
```

### Yönetici/Admin karar akışı

- Yönetici takım kuyruğunda yalnız işlem anındaki aktif doğrudan çalışanlarını görür; dolaylı çalışan, başka ekip, pasif owner, aktörün kendisi ve legacy self-manager kaydı kapsam dışıdır. Admin şirket genelinde aktif/pasif owner geçmişini görebilir.
- Karar listesi talep sahibinin yalnız `{ ad, soyad, departman, profilFoto, aktifMi }` alanlarını; karar verenin yalnız ad/soyadını taşır. Personel UUID’si, kullanıcı adı ve iletişim alanları dönmez.
- Uygun cihaz endpoint’i yalnız yetkili talebin tipindeki `DEPODA` cihazları `{ id, marka, model, seriNo }` olarak döndürür.
- Onay body’si `{ "ekipmanId": "<canonical-uuid>" }`; red body’si `{ "kararNotu": "Stok planlaması nedeniyle reddedildi." }` biçimindedir. Red notu trim sonrası `1..500` karakterdir.
- Yönetici yalnız aktif doğrudan çalışanının, Admin yalnız başka bir kişinin bekleyen talebini karara bağlar. Öz-hedef ve kapsam dışı kayıt terminal durum kontrolünden önce güvenli `404` ile gizlenir.
- Onay, aynı tipte ve hâlâ `DEPODA` olan cihazı talebe bağlar; talebi `ONAYLANDI`, cihazı `PERSONELDE` yapar ve tek açık zimmet oluşturur. Doğrudan zimmet oluşturma endpoint’i yoktur.
- Owner → talep → cihaz → zimmet kilit sırası ve veritabanı unique constraint’leri aynı cihazın veya talebin iki kez zimmetlenmesini engeller. Yarışın kaybedeni ham SQL ayrıntısı olmadan kontrollü `409` alır.
- Red talebi `REDDEDILDI` yapar ve cihaz/zimmet yan etkisi oluşturmaz. Onay, red ve owner iptali aynı talepte yarışırsa yalnız ilk commit kazanır; diğerleri kontrollü conflict alır ve karar geri çevrilmez.

### Zimmet ve iade

- `/api/assignments/my`, principal’ın açık ve kapanmış zimmet geçmişini `{ id, ekipmanTipiId, ekipmanTipi, marka, model, seriNo, teslimTarihi, iadeTarihi }` DTO’suyla döndürür.
- Admin zimmet listesinde `aktif=true|false`, canonical `personelId` ve pozitif `ekipmanTipiId` filtreleri `AND` uygulanır. Personel özeti güvenli ad/soyad/departman/profil fotoğrafı/aktiflik alanlarıyla sınırlıdır.
- İade owner → zimmet → cihaz kilit sırasıyla çalışır. Açık zimmetin `iadeTarihi` alanı server zamanıyla doldurulur, cihaz `DEPODA` yapılır ve zimmet satırı silinmez. Pasif owner’ın cihazı da iade alınabilir.
- Daha önce kapanmış zimmet yeniden iade edilirse `409 Zimmet artık açık değil.`; geçersiz canonical UUID `400`; bulunamayan veya tutarsız zimmet `404` döner.

### Ortak sayfalama, audit ve migration

Sayfalı response `{ icerik, sayfa, sayfaBoyutu, toplamKayit, toplamSayfa }` biçimindedir. Varsayılan `page=0`, `size=20`; `page >= 0` ve `1 <= size <= 100` zorunludur. Pozitif olmayan tip ID veya canonical olmayan UUID `400`; bulunmayan fakat geçerli filtre kimliği boş `200` sayfa üretir.

Gerçek mutation’lar aynı transaction içinde şu güvenli audit olaylarını üretir: `EKIPMAN_TIPI_EKLENDI`, `EKIPMAN_TIPI_GUNCELLENDI`, `EKIPMAN_EKLENDI`, `EKIPMAN_GUNCELLENDI`, `EKIPMAN_TALEBI_OLUSTURULDU`, `EKIPMAN_TALEBI_IPTAL_EDILDI`, `EKIPMAN_TALEBI_ONAYLANDI`, `EKIPMAN_TALEBI_REDDEDILDI`, `ZIMMET_IADE_EDILDI`. Audit detaylarına talep/cihaz UUID’si, seri numarası, açıklama veya karar notu yazılmaz. Salt okumalar, no-op ve başarısız işlemler audit üretmez.

Personel pasifleştirme ve `PERSONEL|HR → YONETICI` rol geçişi, hedefin bütün bekleyen ekipman taleplerini aynı dış transaction içinde iptal eder. Açık zimmet ve cihazın `PERSONELDE` durumu korunur; cihazın fiziksel iadesi Admin tarafından ayrıca yapılır.

Production ortamında endpoint’ler açılmadan önce `backend/db/manual-migrations/2026-08-28-add-equipment-workflow.sql` uygulanmalıdır. Script mevcut ekipman tablolarını geriye uyumlu genişletir, gerekli kolon/constraint/index ve audit ENUM değerlerini ekler, tanım uyuşmazlığında fail-fast davranır ve tekrar çalıştırılabilirlik için gerçek MySQL üzerinde doğrulanmıştır. Hibernate `ddl-auto=update`, mevcut MySQL ENUM listesini güvenilir biçimde genişletmediği için bu migration’ın yerine geçmez.

---

## İzin Yönetimi

### İzin türü lookup (uygulandı)

| Method | Endpoint | Rol |
|--------|----------|-----|
| GET | `/api/leave-types` | aktif personel, aktif HR, uygun aktif yönetici, aktif admin |

| PERSONEL | Uygun YONETICI | Kök YONETICI | HR | ADMIN |
|---:|---:|---:|---:|---:|
| 200 | 200 | 403 | 200 | 200 |

- Response alfabetik ve kararlı sıralı `[{ "id": 1, "ad": "Yıllık İzin", "yillikHaktanDuserMi": true }]` listesidir; entity veya kapsam dışı alan dönmez.
- Uygun Yönetici; veritabanında atanmış, aktif ve DB rolü `YONETICI` olan bir üst yöneticisi bulunan aktif `YONETICI` aktördür. Üst yöneticisi olmayan kök Yönetici `403` alır.
- Lookup salt okunurdur ve audit üretmez. Admin lookup'ı okuyabilir ancak kendi adına izin talebi oluşturamaz.

### İş günü önizlemesi (uygulandı)

| Method | Endpoint | Role |
|--------|----------|------|
| GET | `/api/leave-requests/workday-preview?baslangicTarihi=&bitisTarihi=` | aktif personel, aktif HR, uygun aktif yönetici |

| PERSONEL | Uygun YONETICI | Kök YONETICI | HR | ADMIN |
|---:|---:|---:|---:|---:|
| 200 | 200 | 403 | 200 | 403 |

- Response `{ "isGunuSayisi": 4.5 }` biçimindedir. Hesap hafta sonlarını ve tarih aralığıyla kesişen tek/çok günlük tatil gruplarını dikkate alır.
- Tam günlük tatilin kapsadığı hafta içi günler `0`, yarım günlük tatilin kapsadığı hafta içi günler `0,5` katkı yapar.
- Başlangıç/bitiş zorunludur; ters aralık ve iki uç dahil 366 günü aşan aralık kontrollü `400` döner.
- Kök Yönetici ve Admin `403`; pasif aktör veya JWT–DB rol uyuşmazlığı `403`; principal veritabanında yoksa `404`; token yoksa `401` döner.
- Endpoint read-only’dir; izin talebi veya audit kaydı oluşturmaz.

### İzin talebi oluşturma (uygulandı)

| Method | Endpoint | Rol |
|--------|----------|-----|
| POST | `/api/leave-requests` | aktif personel, aktif HR; atanmış aktif yöneticisi bulunan aktif yönetici |

| PERSONEL | Uygun YONETICI | Kök YONETICI | HR | ADMIN |
|---:|---:|---:|---:|---:|
| 201 | 201 | 403 | 201 | 403 |

- Request yalnız `{ izinTuruId, baslangicTarihi, bitisTarihi, yoneticiyeNot }` alanlarını kabul eder. Personel, rol, durum, iş günü, timestamp veya audit alanı istemciden alınmaz.
- Başlangıç ve bitiş bugün veya gelecekte olmalı, bitiş başlangıçtan önce olmamalı ve aralık iki uç dahil en fazla 366 gün olmalıdır. `yoneticiyeNot` trim edilir; trim sonrası boş değer `null`, uzunluk en fazla 1000 karakterdir.
- İş günü backend'de yeniden hesaplanır. Sonuç `0.0` ise `400 İzin talebi en az bir iş günü içermelidir.` döner.
- Yıllık haktan düşen türlerde kullanılabilir bakiye `yillikIzinHakki - ONAYLANDI toplamı - ONAY_BEKLIYOR toplamı`dır. Aşım `400 Yetersiz izin bakiyesi.` döner. Bekleyen rezervasyon `GET /api/profil` içindeki `kalanIzin` alanını değiştirmez; bu alan yalnız onaylanmış tüketimi düşmeye devam eder.
- Yalnız aynı personele ait `ONAY_BEKLIYOR` ve `ONAYLANDI` talepler etkili iş günü çakışmasına girer. Ortak aralıkta `0.5` veya daha fazla iş günü varsa `409 Seçilen tarih aralığında mevcut bir izin talebiniz bulunuyor.` döner; yalnız hafta sonu/tam tatil kesişimi engel değildir. `REDDEDILDI` ve `IPTAL_EDILDI` talepler dikkate alınmaz.
- Üst yöneticisi bulunmayan kök Yönetici ve Admin `403` alır. PERSONEL ve HR için yönetici ataması oluşturma ön koşulu değildir.
- Başarılı işlem `201 Created` döner; talep `ONAY_BEKLIYOR` durumuyla kaydedilir ve aynı transaction içinde `IZIN_TALEBI_OLUSTURULDU` auditi oluşur.

**Örnek — izin talebi:**

```http
POST /api/leave-requests
Content-Type: application/json

{
  "izinTuruId": 1,
  "baslangicTarihi": "2026-09-01",
  "bitisTarihi": "2026-09-05",
  "yoneticiyeNot": "Aile ziyareti"
}

201 Created
{
  "id": "8f198ffe-3334-4ec0-9dc6-a28c14ca966f",
  "izinTuru": "Yıllık İzin",
  "baslangicTarihi": "2026-09-01",
  "bitisTarihi": "2026-09-05",
  "isGunuSayisi": 4.0,
  "yoneticiyeNot": "Aile ziyareti",
  "durum": "ONAY_BEKLIYOR",
  "durumAdi": "Onay bekliyor",
  "talepTarihi": "2026-08-24T12:00:00"
}
```

### Kendi izin taleplerini listeleme (uygulandı)

| Method | Endpoint | Rol |
|--------|----------|-----|
| GET | `/api/leave-requests/my?page=0&size=10&durum=ONAY_BEKLIYOR` | talep oluşturabilen roller |

| PERSONEL | Uygun YONETICI | Kök YONETICI | HR | ADMIN |
|---:|---:|---:|---:|---:|
| 200 | 200 | 403 | 200 | 403 |

- `page` varsayılan `0` ve minimum `0`; `size` varsayılan `10` ve `1..100`; `durum` opsiyonel `TalepDurum` kodudur. Geçersiz parametre veya enum `400` döner.
- Sonuç `{ icerik, sayfa, sayfaBoyutu, toplamKayit, toplamSayfa }` alanlı `SayfaliResponse<IzinTalepResponse>` sözleşmesidir.
- Sıralama `talepTarihi DESC`, eşitlikte `izinId DESC` olur. Ownership zorunlu principal ID predicate'iyle repository sorgusunda uygulanır; başka personelin talebi veya sayısı dönmez.
- Liste salt okunur ve audit üretmez. Rol/aktiflik/JWT–DB uyumu talep oluşturma ile aynıdır.

### Hata sözleşmesi

| Koşul | Status | Güvenli `message` |
|-------|-------:|-------------------|
| Token yok, geçersiz veya süresi dolmuş | `401` | `Kimlik doğrulaması gerekli.` |
| Yasak rol, pasif aktör, JWT–DB rol uyuşmazlığı veya uygun aktif üstü olmayan Yönetici | `403` | `Bu işlem için yetkiniz bulunmuyor.` |
| JWT principal'ına ait personel kaydı yok | `404` | `Personel bulunamadı.` |
| POST'taki pozitif izin türü ID'si bulunamadı | `404` | `İzin türü bulunamadı.` |
| POST için zorunlu `ONAY_BEKLIYOR` durum kaydı bulunamadı | `404` | `İzin durumu bulunamadı.` |
| Eksik/bozuk JSON body; null zorunlu alan; sıfır/negatif izin türü ID'si | `400` | `Geçersiz istek` |
| POST tarihi geçmişte veya bitiş başlangıçtan önce | `400` | `Geçersiz tarih aralığı.` |
| POST tarih aralığı dahil 366 günü aşıyor | `400` | `İzin talebi en fazla 366 gün olabilir.` |
| Önizlemede zorunlu tarih query'si eksik veya tarih biçimi bozuk | `400` | `Geçersiz istek` |
| Önizlemede bitiş başlangıçtan önce | `400` | `Bitiş tarihi başlangıç tarihinden önce olamaz.` |
| Önizleme tarih aralığı dahil 366 günü aşıyor | `400` | `Tarih aralığı en fazla 366 gün olabilir.` |
| Trim edilmiş yönetici notu 1000 karakteri aşıyor | `400` | `Yönetici notu en fazla 1000 karakter olabilir.` |
| Hesaplanan izin süresi sıfır iş günü | `400` | `İzin talebi en az bir iş günü içermelidir.` |
| Yıllık haktan düşen talep kullanılabilir bakiyeyi aşıyor | `400` | `Yetersiz izin bakiyesi.` |
| `/my` için geçersiz `page`, `size` veya `durum` query değeri | `400` | `Geçersiz istek` |
| Aynı personele ait aktif taleple etkili iş günü çakışması | `409` | `Seçilen tarih aralığında mevcut bir izin talebiniz bulunuyor.` |
| Beklenmeyen persistence veya uygulama hatası | `500` | `Beklenmeyen bir hata oluştu.` |

Başarısız işlemler kısmi talep veya audit bırakmaz; ham SQL/constraint/exception ayrıntısı response'a çıkmaz.

### İzin iptal ve karar akışı (uygulandı)

| Method | Endpoint | Rol ve kapsam |
|--------|----------|---------------|
| PATCH | `/api/leave-requests/{talepId}/cancel` | aktif owner; `PERSONEL`, `YONETICI`, `HR`, `ADMIN` |
| GET | `/api/leave-requests/team?page=0&size=10&durum=` | aktif `YONETICI`; yalnız işlem anındaki aktif doğrudan çalışanları |
| GET | `/api/admin/leave-requests?page=0&size=10&durum=` | aktif `ADMIN`; şirket genelindeki bütün izin talepleri |
| PATCH | `/api/leave-requests/{talepId}/approve` | talep sahibinin işlem anındaki aktif doğrudan yöneticisi veya aktif `ADMIN` |
| PATCH | `/api/leave-requests/{talepId}/reject` | talep sahibinin işlem anındaki aktif doğrudan yöneticisi veya aktif `ADMIN` |

- Liste endpoint’lerinde `page` varsayılan `0` ve minimum `0`; `size` varsayılan `10` ve `1..100`; `durum` opsiyonel `TalepDurum` kodudur. Filtre gönderilmezse bütün durumlar döner. Sıralama `talepTarihi DESC`, eşitlikte `izinId DESC` olur.
- Yönetici kuyruğu yalnız halen kendisine doğrudan bağlı ve aktif personellerin taleplerini içerir. Kardeş ekip, dolaylı ast veya başka yöneticiye taşınmış personel görünmez. Admin kuyruğu aktif/pasif personel ayrımı yapmadan şirket geçmişini korur.
- Kuyruk item’ı `{ id, talepSahibi, izinTuru, baslangicTarihi, bitisTarihi, isGunuSayisi, yoneticiyeNot, durum, durumAdi, talepTarihi, kararNotu, islemTarihi, kararVeren }` alanlarını taşır. `talepSahibi` yalnız `{ ad, soyad, departman, profilFoto, aktifMi }`, `kararVeren` varsa yalnız `{ ad, soyad }` içerir.
- İptal request body almaz ve başarılı işlem `204 No Content` döner. Yalnız gerçek owner ve yalnız `ONAY_BEKLIYOR` talep iptal edilebilir. Owner’ın sonradan rol değiştirmesi kendi legacy bekleyen talebini iptal etmesini engellemez; Admin başka bir kullanıcının talebini iptal edemez. Geçmiş tarihli ama hâlâ bekleyen talep iptal edilebilir.
- Onay request body almaz ve başarılı işlem `204 No Content` döner. Karar anında başlangıç tarihi Europe/Istanbul gününe göre yeniden doğrulanır; başlamış veya geçmiş bir talep, bitiş tarihi henüz geçmemiş olsa bile `409 Başlangıç tarihi geçmiş izin talebi onaylanamaz.` ile reddedilir. Bugün başlayan ve gelecekte başlayacak bekleyen talepler onaylanabilir. Yıllık haktan düşen türlerde bakiye de karar anında yeniden doğrulanır; artık yetersizse `409 İzin bakiyesi artık yeterli değil.` döner.
- Red request’i yalnız `{ "kararNotu": "..." }` taşır. Not zorunludur, trim edilir ve trim sonrası uzunluğu `1..500` olmalıdır. Başarılı red `204 No Content` döner.
- Onay/red yetkisi talep oluşturulurken saklanan bir yönetici snapshot’ından değil, işlem anındaki güncel aktif doğrudan yönetici ilişkisinden belirlenir. Admin bütün şirket taleplerinde karar verebilir.
- `ONAYLANDI`, `REDDEDILDI` veya `IPTAL_EDILDI` durumundaki talep yeniden işlenemez; iptal/onay/red denemesi `409 Talep artık işlem beklemiyor.` döner. Yönetici kararı geri alınmaz.
- Talep ve owner satır kilitleri karar yarışlarını sıralar. İptal, onay ve red aynı talepte yarışırsa yalnız kilidi alan ilk işlem tamamlanır; kaybeden güncel durumu görüp kontrollü `409` alır.
- Başarılı iptal, onay ve red işlemleri sırasıyla `IZIN_TALEBI_IPTAL_EDILDI`, `IZIN_TALEBI_ONAYLANDI` ve `IZIN_TALEBI_REDDEDILDI` olayını aynı transaction içinde yazar. Aktör işlemi yapan personel, hedef talep sahibidir; audit detayı yalnız sabit güvenli işlem metnidir ve yönetici/karar notu içermez.

**Red request örneği:**

```json
{
  "kararNotu": "Planlanan teslim dönemiyle çakışıyor."
}
```

| Durum | HTTP | Güvenli mesaj |
|---|---:|---|
| Geçersiz/kanonik olmayan `talepId`, bozuk pagination veya red notu | `400` | `Geçersiz istek` |
| Token yok/geçersiz | `401` | `Kimlik doğrulaması gerekli.` |
| Pasif aktör, JWT–DB rol uyuşmazlığı, owner olmayan iptal veya yetkisiz yönetici | `403` | `Bu işlem için yetkiniz bulunmuyor.` |
| Aktör, talep, talep sahibi veya gerekli durum kaydı bulunamadı | `404` | İlgili güvenli kaynak mesajı |
| Talep artık beklemiyor | `409` | `Talep artık işlem beklemiyor.` |
| Bekleyen talebin başlangıç tarihi karar anında geçmiş | `409` | `Başlangıç tarihi geçmiş izin talebi onaylanamaz.` |
| Onay anında yıllık bakiye artık yeterli değil | `409` | `İzin bakiyesi artık yeterli değil.` |

Mevcut veritabanında foundation migration’ından sonra önce `backend/db/manual-migrations/2026-08-25-add-leave-request-decisions.sql`, ardından `backend/db/manual-migrations/2026-08-25-add-leave-request-cancellation.sql` uygulanmalıdır. İkinci script MySQL audit ENUM listesini iptal olayıyla son hâline getirir; `ddl-auto=update` bu migration’ların yerine geçmez.

HR/Admin izin türü oluşturma/güncelleme/pasifleştirme akışı ile hizmet yılına göre hak tahakkuku ve devreden bakiye modeli uygulanmamıştır. Bunlar mevcut endpoint sözleşmesini değiştirmeden önce ayrıca tasarlanacak gelecekteki ürün işleridir.

---

## Tatil Takvimi

| Method | Endpoint | Role |
|--------|----------|------|
| GET | `/api/holidays?baslangicTarihi=&bitisTarihi=` | aktif authenticated tüm roller |
| POST | `/api/holidays` | aktif HR, admin |
| PATCH | `/api/holidays/{tatilId}` | aktif HR, admin |
| DELETE | `/api/holidays/{tatilId}` | aktif HR, admin |

- Tatil kaydetme request’i `{ ad, tarih, bitisTarihi?, kategori, gunSuresi }` biçimindedir. `tarih` dahil başlangıç, `bitisTarihi` dahil bitiş tarihidir. `bitisTarihi` gönderilmezse başlangıca eşit kabul edilir; mevcut tek günlük request sözleşmesi geçerliliğini korur.
- Response her zaman `{ tatilId, ad, tarih, bitisTarihi, kategori, gunSuresi }` alanlarını taşır. Tek günlük tatilde `tarih` ve `bitisTarihi` aynıdır.
- Tarih aralığı ters olamaz ve iki uç dahil en fazla 366 gün olabilir. Oluşturma ve yeni tarihe taşıma geçmiş başlangıç tarihi kabul etmez; geçmişte başlayan mevcut grup güncellenemez veya silinemez.
- Hiçbir tatil grubunun tarih aralığı başka bir grupla tek gün dahi kesişemez. Çakışma `409 Conflict` ve `Seçilen tarih aralığı mevcut bir tatille çakışıyor.` mesajı döner; kısmi kayıt veya audit oluşmaz. Bir grubun bitişinden sonraki gün başlayan grup çakışma sayılmaz.
- `POST` ve gerçek değişiklik yapan `PATCH`, tatilin kapsadığı günlerde tatil saatleriyle çakışan `AKTIF` oda rezervasyonu varsa `409 Conflict` döner. Normalize edilmiş aynı bilgileri taşıyan no-op `PATCH`, aşağıdaki no-op sözleşmesini korur. Tam gün tatilde aralıktaki bütün aktif rezervasyonlar; yarım gün tatilde mevcut yarım gün bitiş sınırından (`13:00`) sonra biten aktif rezervasyonlar çakışma sayılır. `IPTAL_EDILDI` rezervasyonlar engel değildir. Hata mesajı `Seçilen tarih aralığında tatil saatleriyle çakışan aktif rezervasyonlar bulunuyor. Tatili kaydetmek için önce bu rezervasyonları iptal edin.` biçimindedir; tatil veya audit yan etkisi oluşmaz. İlgili rezervasyonlar iptal edildikten sonra aynı tatil isteği yeniden gönderilebilir.
- `GET /api/holidays`, istenen görüntüleme aralığıyla kesişen grupları döner: `tatil.tarih <= bitisTarihi AND tatil.bitisTarihi >= baslangicTarihi`. Böylece önceki ay başlayıp görüntülenen ayda süren grup da tek kayıt olarak görünür.
- Başarılı oluşturma `201 Created`; gerçek güncelleme, no-op güncelleme ve silme `204 No Content` döner. Normalize edilmiş aynı grubun tekrar güncellenmesi persistence ve audit yan etkisi oluşturmaz.
- Kategori değerleri `RESMI_SABIT`, `RESMI_DEGISKEN`, `SIRKET`; gün süresi değerleri `TAM_GUN`, `YARIM_GUN` biçimindedir. Çok günlük yarım gün grubunda kapsanan her hafta içi gün iş günü hesabına `0,5` katkı yapar.

**Örnek — çok günlük tatil grubu:**

```http
POST /api/holidays
{
  "ad": "Kurban Bayramı",
  "tarih": "2026-08-24",
  "bitisTarihi": "2026-08-28",
  "kategori": "RESMI_DEGISKEN",
  "gunSuresi": "TAM_GUN"
}

201 Created
{
  "tatilId": 42,
  "ad": "Kurban Bayramı",
  "tarih": "2026-08-24",
  "bitisTarihi": "2026-08-28",
  "kategori": "RESMI_DEGISKEN",
  "gunSuresi": "TAM_GUN"
}
```

---

## Doğum Günü Takvimi

| Method | Endpoint | Role |
|--------|----------|------|
| GET | `/api/birthdays?baslangicTarihi=&bitisTarihi=` | aktif authenticated tüm roller |

- `baslangicTarihi` ve `bitisTarihi` ISO `YYYY-MM-DD` biçiminde zorunludur. Aralık ters olamaz ve iki uç dahil en fazla 366 gün olabilir.
- Response, şirket genelindeki yalnız aktif personeller için tarihe, ardından ad-soyad ve departmana göre kararlı sıralanan `{ adSoyad, tarih, departman }` kayıtlarından oluşur.
- `tarih`, kişinin doğum ayı ve günü istenen takvim yılına taşınarak üretilir; gerçek doğum yılı response’a açılmaz. Personel ID, kullanıcı adı, e-posta, telefon ve doğum tarihinin aslı dönmez.
- Yıl sınırını geçen aralıklar desteklenir. 29 Şubat doğum günü yalnız ilgili takvim yılı artık yılsa `29 Şubat` olarak döner; artık olmayan yılda başka güne taşınmaz.
- Eşleşme yoksa `200 OK` ve boş liste döner. Eksik/bozuk tarih, ters aralık veya 366 günü aşan aralık `400`; bulunamayan principal `404`, pasif principal `403`, unauthenticated istek `401` döner.
- Endpoint salt okunurdur; audit veya başka persistence yan etkisi oluşturmaz.

**Örnek:**

```http
GET /api/birthdays?baslangicTarihi=2026-08-01&bitisTarihi=2026-08-31

200 OK
[
  {
    "adSoyad": "Ayşe Yılmaz",
    "tarih": "2026-08-14",
    "departman": "Bilgi Teknolojileri"
  }
]
```

---

## Birleşik Onay Ekranı (planlanan)

| Method | Endpoint | Role |
|--------|----------|------|
| GET | `/api/approvals?type=&status=` | yönetici |

- Bu bölüm uygulanmış izin karar endpoint’lerinin alternatifi değildir. İzin ve ileride eklenecek ekipman taleplerini tek, modüller-arası listede birleştirecek ayrı bir gelecek sözleşmesidir.
- İzin + ekipman taleplerini tek listede birleştirir (iki entity -> ortak `ApprovalItemResponse` DTO)
- İşlenen talepler de listede döner

---

## Duyurular (planlanan)

| Method | Endpoint | Role |
|--------|----------|------|
| GET | `/api/announcements` | tüm roller |
| GET | `/api/announcements/{id}` | tüm roller |
| POST | `/api/announcements` | admin |
| PATCH | `/api/announcements/{id}` | admin |
| DELETE | `/api/announcements/{id}` | admin |

- Detay response'u yorumları ve beğeni bilgisini içerir: `{ baslik, icerik, yazan, olusturmaTarihi, begeniSayisi, benBegendimMi, yorumlar[] }`
- `begeniSayisi` duyuru_begeni tablosundan COUNT ile canlı hesaplanır

## Yorumlar (planlanan)

| Method | Endpoint | Role |
|--------|----------|------|
| POST | `/api/announcements/{id}/comments` | tüm roller |
| PATCH | `/api/comments/{id}` | owner |
| DELETE | `/api/comments/{id}` | owner \| admin |

- Admin yorumu siler ama düzenleyemez   
- Düzenlenen yoruma "edited" etiketi -> ER'a `duzenleme_tarihi` //open question

## Beğeni (planlanan)

| Method | Endpoint | Role |
|--------|----------|------|
| POST | `/api/announcements/{id}/like` | tüm roller |
| DELETE | `/api/announcements/{id}/like` | tüm roller |


- Kimin beğenisi token'dan — URL'de beğeni id yok
- Tekrarlanan POST idempotent olmalı frontend'i basitleştirir

---

## Admin audit kayıtları

| Method | Endpoint | Role |
|--------|----------|------|
| GET | `/api/admin/audit-logs` | yalnız aktif `ADMIN` |

Opsiyonel query parametreleri:

- `baslangicTarihi`, `bitisTarihi`: `yyyy-MM-dd`; Türkiye saat dilimindeki yerel günler uçlar dahil değerlendirilir.
- `eylemTipi`: mevcut `AuditEylem` enum değerlerinden biri.
- `aktorPersonelId`, `hedefPersonelId`: canonical UUID.
- `page`: varsayılan `0`, en az `0`.
- `size`: varsayılan `20`, `1–100`.

Bütün filtreler `AND` ile birleşir. Pozitif/geçerli fakat bulunmayan aktör veya hedef UUID’si `200 OK` ile boş sayfa döner. Sıralama `olusmaZamani DESC`, eşit zamanda `auditId DESC` biçimindedir. Okuma salt okunurdur ve yeni audit kaydı oluşturmaz.

```json
{
  "icerik": [
    {
      "auditId": "6ecff45e-f18c-4b88-ae4d-30429d92f0cf",
      "eylemTipi": "IZIN_TALEBI_ONAYLANDI",
      "olusmaZamani": "2026-08-26T10:15:30Z",
      "aktor": {
        "personelId": "0cacb02d-1961-4fae-bac8-143f15d582f8",
        "adSoyad": "Ayşe Yılmaz",
        "kullaniciAdi": "ayse.yilmaz"
      },
      "hedef": {
        "personelId": "c5932475-c472-4585-b2ca-830723e804ce",
        "adSoyad": "Mehmet Kaya"
      },
      "detay": "İzin talebi onaylandı."
    }
  ],
  "sayfa": 0,
  "sayfaBoyutu": 20,
  "toplamKayit": 1,
  "toplamSayfa": 1
}
```

Eski başarısız giriş kayıtlarında bağlı aktör personel bulunmayabilir. Bu durumda `aktor.personelId` ve `aktor.adSoyad` null kalır, olay anındaki güvenli `aktor.kullaniciAdi` snapshot’ı korunur; hedef yoksa `hedef=null` döner. Response parola, token, e-posta, telefon veya entity iç alanları taşımaz.

- Ters tarih aralığı: `400 Başlangıç tarihi bitiş tarihinden sonra olamaz.`
- Geçersiz aktör UUID’si: `400 Aktör personel ID geçerli UUID olmalıdır.`
- Geçersiz hedef UUID’si: `400 Hedef personel ID geçerli UUID olmalıdır.`
- Geçersiz tarih/enum/pagination dönüşümü: `400 Geçersiz istek`.
- Token yok/geçersiz: `401 Kimlik doğrulaması gerekli.`
- `PERSONEL`, `YONETICI`, `HR`, pasif Admin veya JWT–DB rol uyuşmazlığı: `403 Bu işlem için yetkiniz bulunmuyor.`
- JWT principal veritabanında yoksa: `404 Personel bulunamadı.`

Mevcut ortamlarda endpoint açılmadan önce `backend/db/manual-migrations/2026-08-26-add-audit-log-read-index.sql` uygulanmalıdır. Migration `(olusma_zamani, log_id)` bileşik indeksini idempotent biçimde ekler; `ddl-auto=update` production migration sorumluluğunun yerine geçmez.

---

## Personel

Bu tablo yalnız mevcut controller katmanında uygulanmış personel yönetimi endpointlerini gösterir. Kendi profilini görüntüleme ve güncelleme sözleşmeleri yukarıdaki Profil bölümünde tanımlıdır.

| Method | Endpoint                            | Role |
|--------|-------------------------------------|------|
| GET    | `/api/hr/personel`                  | aktif HR, admin |
| GET    | `/api/hr/personel/atanabilir-yoneticiler` | aktif HR, admin |
| GET    | `/api/hr/personel/{personelId}`     | aktif HR, admin; hedef rol kuralları uygulanır |
| POST   | `/api/hr/personel`                  | HR, admin |
| PATCH  | `/api/hr/personel/{personelId}`     | HR, admin |
| PATCH  | `/api/hr/personel/{personelId}/pasiflestir` | HR, admin; hedef rol kuralları uygulanır |
| PATCH  | `/api/hr/personel/{personelId}/aktiflestir` | HR, admin; hedef rol kuralları uygulanır |
| PUT    | `/api/hr/personel/{personelId}/yetenekler` | aktif admin; hedef rol kuralları uygulanır |
| PATCH  | `/api/admin/personel/{personelId}/parola-sifirla` | aktif admin; hedef rol kuralları uygulanır |
| PATCH  | `/api/profil`                       | authenticated tüm roller |
| PUT    | `/api/profil/yetenekler`            | authenticated tüm roller |
| PATCH  | `/api/profil/parola`                | authenticated tüm roller |
| PATCH  | `/api/admin/personel/{personelId}/rol` | admin |
| GET    | `/api/ekip-dizini`                  | authenticated tüm roller |
| GET    | `/api/ekip-dizini/hiyerarsi`       | aktif HR, admin |
| GET    | `/api/ekip-dizini/yonetici-hiyerarsim` | aktif yönetici |
| GET    | `/api/ekip-dizini/yonetici-zincirim` | aktif personel, yönetici |

- Eski `/api/profile/me`, `/api/personnel` ve `/api/profile/me/photo` yolları canlı backend sözleşmesi değildir. Profil fotoğrafı için kesin yollar `/api/profil/fotograf` ve `/api/profil-fotograflari/{dosyaAnahtari}` biçimindedir.


- PATCH body'sinde rol ve sicil_no kabul edilmez
- Personel oluşturma request’i `{ kullaniciAdi, ad, soyad, dogumTarihi, ePosta, departmanId, isGirisTarihi, yoneticiId? }` biçimindedir. Rol, parola, sicil numarası ve diğer server-controlled alanlar kabul edilmez.
- Yeni personelin rolü sunucu tarafından `PERSONEL`, ilk parolası normalize edilmiş kullanıcı adının BCrypt hash’i ve `parolaDegistirmeliMi` değeri `true` olarak belirlenir.
- `sicil_numarasi` MySQL tarafından `AUTO_INCREMENT` ile üretilir. `departmanId` zorunlu, `yoneticiId` opsiyoneldir; gönderilen yönetici mevcut, aktif, `YONETICI` rolünde ve yeni personelle aynı departmanda olmalıdır.
- Kullanıcı adı ve e-posta normalize edilir; mevcut değerle çakışma `409 Conflict` döner. Başarılı oluşturma `{ personelId, kullaniciAdi }` gövdesiyle `201 Created` döner ve `PERSONEL_EKLENDI` audit kaydı oluşturur.
- Personel güncelleme request’i kısmi PATCH semantiği kullanır. Güncellenebilir alanlar `{ ad, soyad, dogumTarihi, ePosta, departmanId, isGirisTarihi, yoneticiId, telNo, mezunOkul, mezunBolum, mezuniyetYili }` biçimindedir; yalnız gönderilen alanlar değerlendirilir.
- Güncelleme sırasında `ad`, `soyad`, `dogumTarihi`, `ePosta`, `departmanId` ve `isGirisTarihi` alanları gönderilirse `null` olamaz. `yoneticiId`, `telNo`, `mezunOkul`, `mezunBolum` ve `mezuniyetYili` açıkça `null` gönderilerek temizlenebilir. Boş metin temizleme anlamına gelmez.
- `personelId`, `kullaniciAdi`, `sicilNumarasi`, rol, parola, aktiflik, işten çıkış tarihi, profil fotoğrafı ve yıllık izin hakkı bu güncelleme endpoint’iyle değiştirilemez.
- Departman gerçekten değiştiriliyorsa aynı request içinde `yoneticiId` alanı da gönderilmelidir. Değer yeni departmandaki aktif bir `YONETICI` rolünün ID’si olabilir veya mevcut yöneticiyi kaldırmak için `null` gönderilebilir.
- Atanacak yönetici hedef personelin kendisi olamaz; aktif, `YONETICI` rolünde ve güncelleme sonrasındaki departmanla aynı departmanda olmalıdır. Yönetici, departman değişmeden de tek başına güncellenebilir veya `null` ile kaldırılabilir. Atama mevcut yönetici zincirinde hedef personele geri dönüyorsa veya zincir kendi içinde tekrar içeriyorsa `400 Yönetici ataması döngü oluşturamaz.` döner ve ilişki değiştirilmez.
- Güncellemede e-posta normalize edilir; hedef dışındaki bir personelle çakışması `409 Conflict` döner. Boş request ve geçersiz ilişki kuralları `400`, bulunamayan hedef/departman/yönetici `404` döner.
- Başarılı personel güncellemesi `204 No Content` döner ve yalnız değişen alan adlarını içeren `PERSONEL_GUNCELLENDI` audit kaydı oluşturur. Mevcut değerlerin tekrar gönderildiği idempotent istek `204` döner, persistence veya audit yan etkisi oluşturmaz.
- `PATCH /api/hr/personel/{personelId}/pasiflestir` request body almaz; aktör JWT principal’dan, hedef path’teki `personelId` değerinden alınır ve başarılı işlem `204 No Content` döner.
- HR/Admin `PERSONEL` ve `HR` hedefleri pasifleştirebilir; `YONETICI` hedefini yalnız Admin pasifleştirebilir. `ADMIN` hedef ve HR/Admin öz-hedef istekleri `403 Forbidden` döner.
- Başarılı pasifleştirme `calisiyorMu=false` ve `isCikisTarihi=işlem tarihi` yapar, hedefin refresh tokenlarını siler ve `PERSONEL_PASIFE_ALINDI` audit kaydı oluşturur. Hedef yönetici ise aktif/pasif ayrımı olmadan bütün doğrudan çalışanlarda `yonetici=null` yapılır.
- Yetkili aktörün zaten pasif hedefe tekrarladığı istek idempotent `204` döner; mevcut çıkış tarihi, tokenlar, yönetici ilişkileri ve audit değişmez. Bulunamayan aktör veya hedef `404 Not Found` döner.
- Pasifleştirme, doğrudan çalışan ilişkileri, refresh-token silme ve audit aynı transaction’a katılır. Stateless access token anında iptal edilmez ve kalan ömrü boyunca, en fazla 15 dakika çalışabilir.
- `PATCH /api/hr/personel/{personelId}/aktiflestir` request body almaz; aktör JWT principal bilgisinden, hedef path parametresinden belirlenir. Gerçek aktifleştirme ve izinli no-op sonucu `204 No Content` döner.
- HR yalnız `PERSONEL` ve kendisi dışındaki `HR` hedeflerini; Admin ise `PERSONEL`, `HR` ve `YONETICI` hedeflerini aktifleştirebilir. Öz-hedef ve `ADMIN` hedefi yasaktır. Aktör aktif değilse `403`, principal veya hedef bulunamazsa `404`, token yoksa `401` döner.
- Gerçek aktifleştirmede `calisiyorMu=true`, `isCikisTarihi=null` yapılır ve aynı transaction içinde `PERSONEL_AKTIFLESTIRILDI` audit kaydı oluşturulur. Parola, rol, departman, yönetici ve token bilgileri değiştirilmez; yöneticinin eski doğrudan bağlıları otomatik geri atanmaz.
- Zaten aktif ve aktörün işlem yapmaya yetkili olduğu hedef no-op kabul edilir: yetki kontrolleri no-op kontrolünden önce çalışır; kayıt güncellenmez ve audit oluşmaz. Mevcut stateless access-token davranışı değişmez.
- `PUT /api/hr/personel/{personelId}/yetenekler` yalnız aktif `ADMIN` aktörün, aktif `PERSONEL`, `HR` veya `YONETICI` hedefinin bütün yetenek kümesini değiştirmesini sağlar. Aktör yalnız JWT principal’dan, hedef yalnız path parametresinden belirlenir; `HR`, `YONETICI` ve `PERSONEL` aktörler, öz-hedef ve bütün `ADMIN` hedefler `403 Forbidden` alır.
- Request yalnız `{ "yetenekIdleri": [1, 3, 7] }` taşır. Boş liste bütün yetenekleri kaldırır; sıra anlamsızdır. Null liste/eleman, sıfır, negatif veya duplicate ID `400 Bad Request`; bulunmayan herhangi bir ID bütün isteği atomik biçimde `404 Yetenek bulunamadı.` ile reddeder.
- İstenen küme farklıysa yalnız eklenen ve kaldırılan ilişkiler güncellenir; aynı küme farklı sırada yeniden gönderilirse `204 No Content` ile auditsiz ve persistence yan etkisiz no-op olur. Öz-hedef, `ADMIN` hedef ve pasif hedef kontrolleri no-op karşılaştırmasından önce çalışır.
- Gerçek değişiklik `204 No Content` döner ve aynı transaction içinde `PROFIL_GUNCELLENDI` auditi oluşturur. Audit aktörü işlemi yapan Admin, hedefi path personeli ve detayı yalnız `Güncellenen alanlar: yetenekler` olur; yetenek adı veya ID audit detayına yazılmaz.
- `PATCH /api/admin/personel/{personelId}/parola-sifirla` yalnız aktif Admin’in `PERSONEL`, `HR` veya `YONETICI` hedefe geçici parola atamasını sağlar. Request `{ "geciciParola": "..." }` biçimindedir. Öz-hedef ve bütün `ADMIN` hedefler `403`; bulunamayan aktör/hedef `404`; pasif aktör `403` alır. Hedef aktif veya pasif olabilir.
- Geçici parola mevcut güçlü parola politikasına uymalıdır ve yalnız BCrypt hash’i saklanır. Başarılı işlem `parolaDegistirilmeliMi=true` yapar, hedefin bütün refresh tokenlarını siler, parola içermeyen `PAROLA_SIFIRLANDI` audit kaydıyla aynı transaction içinde tamamlanır ve `204 No Content` döner. Stateless access token anında iptal edilemez; en fazla 15 dakikalık kalan ömrü boyunca çalışabilir.
- Normal parola değiştirme request’i `{ mevcutParola, yeniParola }` biçimindedir; personel kimliği JWT principal’dan alınır. Başarılı işlem bütün refresh tokenları siler, `PAROLA_DEGISTIRILDI` audit kaydı oluşturur ve `204 No Content` döner.
- Yeni parola 8–72 karakter olmalı, UTF-8 karşılığı 72 byte’ı aşmamalı; küçük harf, büyük harf, rakam ve özel karakter içermeli, boşluk içermemeli, kullanıcı adı veya mevcut parolayla aynı olmamalıdır.
- Rol değiştirme request body’si `{ "rol": "HR" }` biçimindedir. İşlemi yapan Admin JWT principal’dan, hedef personel path’teki `personelId` değerinden alınır.
- Aynı rol tekrar istenirse işlem idempotent biçimde `204 No Content` döner; token veya audit yan etkisi oluşturmaz.
- Admin kendi rolünü ve mevcut başka bir Admin’in rolünü değiştiremez; bu istekler `403 Forbidden` döner.
- Başarılı rol değişikliğinde hedef personelin bütün refresh token kayıtları silinir ve aktör ile hedefi ilişkilendiren `ROL_DEGISTIRILDI` audit kaydı oluşturulur.
- Stateless access token anında iptal edilmez; eski token kalan ömrü boyunca, en fazla 15 dakika çalışabilir.

### HR/Admin personel yönetimi okumaları

- `GET /api/hr/personel`; `page` (varsayılan `0`), `size` (varsayılan `20`, `1–100`), `arama`, `departmanId`, `rol` ve `aktif` query parametrelerini kabul eder.
- `arama`; ad, soyad, `ad + soyad` ve kullanıcı adında büyük/küçük harf duyarsız çalışır. Baş/son boşluklar temizlenir; boş değer filtre uygulanmamış gibi davranır. Bütün filtreler `AND` ile birleşir.
- Pozitif fakat bulunmayan `departmanId`, geçerli fakat eşleşmeyen filtre ve `rol=ADMIN` isteği `200 OK` ile boş sayfa döner. Sıfır/negatif departman ID, geçersiz enum/tip ve pagination değeri `400 Bad Request` döner.
- Liste `SayfaliResponse<PersonelYonetimListeResponse>` kullanır. Öğe alanları `{ personelId, ad, soyad, kullaniciAdi, rol, departmanId, departmanAdi, profilFoto, aktif }`; sıralama `ad`, `soyad`, `personelId` biçimindedir.
- `GET /api/hr/personel/{personelId}` alanları `{ personelId, kullaniciAdi, ad, soyad, dogumTarihi, ePosta, departmanId, yoneticiId, isGirisTarihi, telNo, mezunOkul, mezunBolum, mezuniyetYili, rol, profilFoto, aktif, yetenekler }` olan yönetim detayını döner. Yönetici yoksa `yoneticiId=null` olur. `yetenekler`, form ön seçimi için alfabetik `[{ id, ad }]` listesidir; ilişki yoksa `[]` döner.
- Liste ve detay hedef matrisi: HR yalnız `PERSONEL` ve başka HR; Admin `PERSONEL`, `HR`, `YONETICI`. Öz-hedef ve bütün `ADMIN` hedefler yasaktır. Liste yasak hedefleri içerik ve count'tan çıkarır; detay mevcut fakat yasak hedefte `403`, bulunamayan hedefte `404 Personel bulunamadı.` döner.
- `GET /api/hr/personel/atanabilir-yoneticiler` zorunlu pozitif `departmanId` ve opsiyonel `haricPersonelId` alır. Yalnız seçilen departmandaki aktif `YONETICI` kayıtlarını `{ personelId, ad, soyad, kullaniciAdi }` biçiminde `ad`, `soyad`, `personelId` sırasıyla döner.
- `haricPersonelId` boşsa filtre uygulanmaz; başka bir ID yalnız eşleşen kaydı çıkarır ve ayrıca varlık kontrolü yapılmaz. Bulunmayan departman `404 Departman bulunamadı.`, eşleşmeyen lookup `200 OK` ve `[]` döner.
- Üç okuma endpoint'i yalnız aktif HR/Admin içindir, salt okunurdur ve audit üretmez. Dört mevcut personel mutation'ında da principal veritabanından yeniden yüklenir; bulunamayan aktör `404`, pasif aktör kalan access-token ömründe `403` alır.

### Ekip dizini

| Method | Endpoint | Role |
|--------|----------|------|
| GET | `/api/ekip-dizini` | kimliği doğrulanmış tüm roller |
| GET | `/api/ekip-dizini/hiyerarsi` | aktif HR, admin |
| GET | `/api/ekip-dizini/yonetici-hiyerarsim` | aktif yönetici |
| GET | `/api/ekip-dizini/yonetici-zincirim` | aktif personel, yönetici |

- `GET /api/ekip-dizini` için opsiyonel query parametreleri `page`, `size`, `departmanId`, `yetenekId` ve `arama` biçimindedir. İki hiyerarşi endpoint’i pagination kullanmaz; opsiyonel `arama`, `departmanId` ve `yetenekId` filtrelerini kabul eder.
- `page` varsayılan `0` ve en az `0`; `size` varsayılan `20` ve `1–100` aralığındadır. Geçersiz aralık veya sayısal tip `400 Bad Request` döner.
- Departman ve yetenek filtreleri birlikte gönderilirse `AND` uygulanır. Pozitif fakat bulunmayan veya hiçbir aktif personele uymayan filtre ID’si hata oluşturmaz; sayfalı ekip dizininde boş sayfa, HR/Admin hiyerarşisinde boş `kokler`, Yönetici hiyerarşisinde ise üst zincir ve aktör korunarak boş ast ağacıyla `200 OK` döner. Sıfır/negatif ID veya sayısal olmayan değer `400 Bad Request` döner.
- `arama`; ad, soyad ve `ad + soyad` üzerinde büyük/küçük harf duyarsız çalışır. Başındaki ve sonundaki boşluklar temizlenir; boş değer filtre uygulanmamış gibi davranır.
- `arama`, `departmanId` ve `yetenekId` birlikte gönderildiğinde `AND` uygulanır. Arama koşulu ana ve count sorgularında aynıdır; pagination ve sabit sıralama korunur.
- Sonuç yalnız aktif personelleri içerir. Görünürlük matrisi: `PERSONEL` yalnız kendi departmanındaki kişileri; `YONETICI` ve `HR`, `ADMIN` dışındaki bütün kişileri; `ADMIN` ise bütün kişileri görür. Personelin yöneticisi atama kuralları gereği aynı departmanda bulunduğundan PERSONEL sonucuna ayrıca bir istisna sorgusuyla eklenmez.
- PERSONEL başka bir departmanı `departmanId` filtresiyle seçerek departman sınırını aşamaz; görünürlük koşulu ile kullanıcı filtreleri `AND` uygulanır. Aynı rol/departman koşulları hem içerik hem count sorgusunda bulunur.
- Sonuç `ad`, `soyad`, ardından kararlı pagination için response’ta gösterilmeyen `personelId` ile artan sıralanır.
- `EkipDiziniResponse` alanları `{ ad, soyad, departman, profilFoto, yetenekler, yoneticinMi, bugunIzinDurumu }` biçimindedir; `personelId` response’a eklenmez.
- `yoneticinMi`, yalnız response’u isteyen authenticated personelin atanmış yöneticisi olan kayıtta `true`, diğer bütün kayıtlarda `false` döner. Aktörün yöneticisi yoksa bütün kayıtlar `false` olur. Değer ad, sıralama veya rol tahminiyle değil, backend’de aktörün gerçek `yonetici` ilişkisiyle hesaplanır.
- `bugunIzinDurumu`; kişi bugün etkili, onaylı tam gün izindeyse `IZINLI`, yarım gün izindeyse `YARIM_GUN_IZINLI`, aksi durumda `null` döner. Hafta sonu veya tam günlük şirket/resmî tatili izin rozeti üretmez; frontend izin durumunu yeniden hesaplamaz.
- Yetenek filtresi personelin sonuçta yer alıp almayacağını belirler; eşleşen personelin response’unda yalnız filtrelenen yetenek değil, bütün yetenekleri alfabetik sırayla döner. Yeteneği olmayan personel `yetenekler=[]` alır.
- Response `{ icerik, sayfa, sayfaBoyutu, toplamKayit, toplamSayfa }` alanlarını taşıyan sade bir sayfalama sözleşmesi kullanır; Spring’in `Page` yapısı doğrudan API response’u yapılmaz.
- JWT principal’daki personel bulunamazsa `404`, pasifse kalan access-token ömründe dahi `403`, token yoksa/geçersizse `401` döner.
- Listeleme read-only’dir ve audit kaydı oluşturmaz.
- `GET /api/ekip-dizini/hiyerarsi`, mevcut `Personel.yonetici` ilişkisini kullanan aktif organizasyon forestini `{ kokler }` biçiminde döner. Düğümler yalnız `{ ad, soyad, departman, rol, profilFoto, yoneticinMi, bugunIzinDurumu, dogrudanBaglilar }` alanlarını içerir; unvan veya sentetik CEO üretilmez. HR ve Admin filtresiz istekte bütün aktif foresti görebilir; birden fazla kök desteklenir ve sıralama kararlıdır.
- HR/Admin hiyerarşisi filtrelendiğinde yalnız doğrudan eşleşen kişiler ile onları aktif köklerine bağlamak için gereken üst yönetici zincirleri döner; eşleşmeyen alt, kardeş ve yan dallar budanır. `arama` ad, soyad ve tam ad üzerinde case-insensitive çalışır; yetenek filtresi gerçek personel-yetenek ilişkisini kullanır ve bütün filtreler `AND` ile birleşir.
- `GET /api/ekip-dizini/yonetici-hiyerarsim`, yalnız aktif ve veritabanında da `YONETICI` olan aktöre `{ aktorDepartman: { id, ad }, kokler }` döner. Forest en fazla iki aktif üst yönetici → aktör → yalnız aktörün doğrudan/dolaylı aktif astları biçimindedir; kardeşler, başka kökler ve başka ekip dalları gönderilmez. Aktörün üstü yoksa aktör kök olur.
- Yönetici hiyerarşisi filtrelendiğinde üst zincir ve aktör her zaman korunur; yalnız eşleşen astlar ile aktörden bu astlara ulaşmak için zorunlu ara yöneticiler kalır. Başka ekipteki filtre eşleşmeleri kapsam dışıdır. PERSONEL, HR ve ADMIN bu endpoint’te `403 Forbidden` alır.
- Her hiyerarşi düğümündeki `yoneticinMi`, yalnız JWT principal’ın gerçek `yonetici.personelId` ilişkisiyle hesaplanır. Gerçek yönetici dönen filtreli forest içindeyse yalnız o düğüm `true`; yönetici yoksa, pasifse veya budanmışsa bütün düğümler `false` olur. Bu alan için ilgisiz ikinci dal eklenmez ve hiçbir ilişki ID’si response’a açılmaz.
- Her hiyerarşi düğümündeki `bugunIzinDurumu`, yalnız güvenlik ve filtre sonucunda response’ta kalan personeller için backend’de toplu hesaplanır. Filtre/kapsam dışı personele ait izin bilgisi response’a sızdırılmaz; değerler sayfalı ekip diziniyle aynı `IZINLI | YARIM_GUN_IZINLI | null` sözleşmesini kullanır.
- `GET /api/ekip-dizini/yonetici-zincirim`, aktif PERSONEL/YONETICI aktör için en yakın yöneticiden başlayarak en fazla iki üstü `{ yoneticiler: [{ seviye, ad, soyad, departman, rol, profilFoto }] }` biçiminde döner. Başka ekip, çalışan veya daha uzak üst bilgisi gönderilmez; üst yönetici yoksa zincir doğal biçimde sonlanır.
- Hiyerarşi okumaları salt okunur ve auditsizdir. HR/Admin tam forest okumasında bütün aktif graph, Yönetici görünümünde yalnız aktörün üst ve erişilebilir ast kapsamı döngü için doğrulanır. İlgili kapsamda eski iki veya daha fazla kişilik yönetici döngüsü görülürse kısmi veri yerine `409 Yönetici hiyerarşisinde tutarsızlık tespit edildi.` döner.

---

## Dashboard

| Method | Endpoint              | Role |
|--------|-----------------------|------|
| GET | `/api/dashboard/ozet` | tüm roller |

- Tüm kartlar tek istekte, tek DTO: `{ bugunDogumGunleri[], aktifEkipUyesiSayisi, ekipDiziniOzeti[], sonDuyurular[], izinBakiyesi, bugunIzinliSayisi, odaDoluluk[], sonTakdirler[], bekleyenTaleplerim[] }`.
- `aktifEkipUyesiSayisi`, kullanıcının ekip dizini görünürlük matrisine göre görebildiği bütün aktif kişilerin toplamıdır. PERSONEL kendi departmanındaki aktif kişi sayısını; YONETICI ve HR, Adminler hariç şirket genelindeki aktif kişi sayısını; ADMIN bütün aktif kişi sayısını görür.
- `ekipDiziniOzeti`, aynı görünür kümenin `ad`, `soyad`, gizli `personelId` sıralamasındaki ilk 5 kaydıdır ve mevcut güvenli `EkipDiziniResponse` alanlarını `{ ad, soyad, departman, profilFoto, yetenekler, yoneticinMi, bugunIzinDurumu }` taşır. Personel ID, kullanıcı adı, e-posta, sicil numarası veya başka hesap bilgisi dönmez.
- Dashboard ekip dizini sayımı ve özeti aynı sayfalı ekip dizini sorgusundan üretilir; okuma salt okunurdur, audit veya başka persistence yan etkisi oluşturmaz.
- `izinBakiyesi`, `{ toplamHak, kullanilan, onayBekleyen, kalan }` taşır. `kullanilan` yalnız yıllık haktan düşen `ONAYLANDI`, `onayBekleyen` yalnız yıllık haktan düşen `ONAY_BEKLIYOR` toplamıdır; `kalan = toplamHak - kullanilan - onayBekleyen` biçiminde hesaplanır.
- `izinBakiyesi` ADMIN ve üst yöneticisi olmayan kök YONETICI için `null`; PERSONEL, HR ve uygun non-root YONETICI için backend hesaplı nesne döner. Frontend bu nesne yoksa kalan izin kartını göstermez.
- `odaDoluluk`, bütün odaları kararlı ad/ID sırasında `{ odaId, odaAd, kapasite, anlikDurum, dolulukYuzdesi, doluAraliklar[] }` biçiminde döndürür. `anlikDurum`, açık çalışma saatinde aktif rezervasyona göre `MUSAIT` veya `DOLU`; çalışma saati dışında, hafta sonu, tam gün tatilde ve yarım gün tatilde 13:00 sonrasında `KAPALI` olur.
- `dolulukYuzdesi`, bugünkü aktif rezerve saatlerin o günün açık saatine oranıdır; normal gün varsayılan 10, yarım gün 5 saat üzerinden `HALF_UP` ile tam sayıya yuvarlanır. Kapalı günde `0` döner. `doluAraliklar` yalnız `{ baslangicSaat, bitisSaat }` taşır; iptal kayıt, başlık, owner ve katılımcı bilgisi dashboard özetine eklenmez.
- `sonDuyurular`, `sonTakdirler` ve `bekleyenTaleplerim` ilgili modüller tamamlanana kadar boş liste; `bugunIzinliSayisi` ise mevcut placeholder sözleşmesinde `0` döner. Bunlar uygulanmış ürün verisi olarak yorumlanmamalıdır.

---

## Lookup'lar

Departman ve yetenek lookup GET’leri uygulanmıştır:

| Method | Endpoint                | Role |
|--------|-------------------------|------|
| GET | `/api/departments`      | tüm roller |
| GET | `/api/skills`           | tüm roller |

- İki endpoint de sayfalamasız `[{ id, ad }]` döner; entity doğrudan response yapılmaz.
- Veritabanındaki bütün tanımlar, aktif personele henüz atanmamış olanlar dahil, ada ve ardından ID’ye göre artan sıralanır.
- JWT principal personeli bulunamazsa `404`, pasifse kalan access-token ömründe dahi `403`, token yoksa/geçersizse `401` döner.
- Lookup GET’leri read-only’dir ve audit kaydı oluşturmaz.

### Departman yönetimi

| Method | Endpoint | Role |
|--------|----------|------|
| POST | `/api/departments` | HR, admin |
| PATCH | `/api/departments/{departmanId}` | HR, admin |
| DELETE | `/api/departments/{departmanId}` | HR, admin |

- Request gövdesi `{ "departmanAdi": "..." }` biçimindedir. Departman adı trim edilir, kullanıcının harf biçimi korunur ve trim sonrasındaki uzunluk 1–50 karakter olmalıdır.
- Başarılı oluşturma `201 Created` ile `{ id, ad }`, gerçek güncelleme ve no-op ise gövdesiz `204 No Content` döner.
- Sıfır veya negatif `departmanId` `400`, bulunamayan pozitif ID `404 Departman bulunamadı.` döner.
- Case-insensitive duplicate oluşturma veya başka kayda duplicate olacak güncelleme `409 Departman zaten mevcut.` döner. Ön kontrole rağmen oluşabilecek database unique-constraint yarışı da aynı kontrollü `409` cevabına çevrilir.
- Rename mevcut departman ID’sini ve personel-departman ilişkilerini korur. Yalnız harf biçimi değişikliği gerçek güncellemedir; tam normalize eşitlik no-op olur ve persistence/audit yan etkisi oluşturmaz.
- Endpoint’ler yalnız aktif HR/Admin tarafından kullanılabilir. Principal bulunamazsa `404`, pasifse `403`, authentication yoksa `401` döner; `PERSONEL` ve `YONETICI` erişimi `403` ile reddedilir.
- Oluşturma `DEPARTMAN_EKLENDI`, gerçek güncelleme `DEPARTMAN_GUNCELLENDI` audit kaydı üretir. Aktör JWT principal personelidir, hedef personel null’dır; departman değişikliği ve audit aynı transaction içinde tamamlanır veya birlikte rollback olur.
- İlişkisiz departman `DELETE` ile gövdesiz `204 No Content` sonucu silinir ve `DEPARTMAN_SILINDI` auditi aynı transaction’a katılır. Aktif veya pasif herhangi bir personele bağlı departman cascade uygulanmadan `409 Departman kullanımda olduğu için silinemez.` ile korunur. Satır kilidi, ilişki ön kontrolü ve database FK/constraint fallback’i yarış durumlarını güvenli `409` sonucuna çevirir.
- Departman pasifleştirme ve birleştirme bu sözleşmenin kapsamında değildir.

### Yetenek yönetimi

| Method | Endpoint | Role |
|--------|----------|------|
| POST | `/api/skills` | HR, admin |
| PATCH | `/api/skills/{yetenekId}` | HR, admin |
| DELETE | `/api/skills/{yetenekId}` | HR, admin |

- Yetenek adı trim edilir, kullanıcının harf biçimi korunur ve normalize edilmiş uzunluk 1–50 karakterdir.
- Case-insensitive duplicate oluşturma veya başka kayda duplicate olacak güncelleme `409 Yetenek zaten mevcut.` döner.
- Başarılı oluşturma `201 Created` ile `{ id, ad }`; gerçek güncelleme ve no-op `204 No Content` döner.
- Rename aynı ID ve personel-yetenek ilişkilerini korur. Yalnız harf biçimi değişikliği gerçek güncellemedir; tam normalize eşitlik no-op olur.
- Endpoint’ler yalnız aktif HR/Admin tarafından kullanılabilir. Principal bulunamazsa `404`, pasifse `403`, authentication yoksa `401` döner.
- Gerçek değişiklikler güvenli audit ile aynı transaction içinde tamamlanır veya rollback olur.
- İlişkisiz yetenek `DELETE` ile gövdesiz `204 No Content` sonucu silinir ve `YETENEK_SILINDI` auditi aynı transaction’a katılır. Aktif veya pasif personele ait herhangi bir personel-yetenek ilişkisi varsa cascade uygulanmadan `409 Yetenek kullanımda olduğu için silinemez.` döner. Satır kilidi, ilişki ön kontrolü ve database constraint fallback’i yarış durumlarını kontrollü `409` sonucuna çevirir.

### Planlanan lookup işlemleri

- `durum_turu` ve `rol` için endpoint yok: hiçbir formda kullanıcı bunları seçmiyor, response'larda metin olarak dönüyorlar.

---

## Open Questions

- [x] Admin şirket geneli ekipman talep listesi ayrı `/api/admin/equipment-requests` endpoint’iyle uygulandı.
- [ ] Skill listesi yönetimi: serbest ekleme mi, admin temizliği mi, onay kuyruğu mu?
- [x] Talep→zimmet izlenebilirliği `zimmetleme_bilgileri.talep_id` FK ve unique constraint’iyle uygulandı.
