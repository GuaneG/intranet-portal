# API Specification

> Planlama dokümanı — canlı OpenAPI spec'i SpringDoc tarafından `/v3/api-docs` üzerinden otomatik üretilir (Swagger UI: `/swagger-ui.html`).
> Bu dosya endpoint yüzeyini, rol kurallarını ve tasarım notlarını tutar.

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
| 409 | Conflict | Çakışan rezervasyon, duplicate kayıt |
| 428 | Precondition Required | İlk girişte parola değişikliği zorunlu |

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

## Rezervasyon

| Method | Endpoint | Role |
|--------|----------|------|
| GET | `/api/rooms` | tüm roller |
| GET | `/api/reservations?roomId=&date=` | tüm roller |
| POST | `/api/reservations` | tüm roller |
| DELETE | `/api/reservations/{id}` | tüm roller (owner) |

- POST'ta çakışma kontrolü service katmanında: `yeni.baslangic < mevcut.bitis AND yeni.bitis > mevcut.baslangic` → varsa **409**. Unique index `(tarih, oda_id, baslangic_saat)` race-condition emniyeti olarak kalıyor (interval çakışmasını tek başına yakalayamıyor)
- Rezervasyon iptali full delete

**Örnek — çakışan rezervasyon:**

```
POST /api/reservations
{ "odaId": 1, "tarih": "2026-07-10", "baslangicSaat": 10, "bitisSaat": 12, "baslik": "Sprint planlama" }

-> 201 Created
-> 409 Conflict (aynı odada 11:00-12:00 dolu ise)
```

---

## Takdir Panosu

| Method | Endpoint | Role |
|--------|----------|------|
| GET | `/api/kudos?limit=` | tüm roller |
| POST | `/api/kudos` | tüm roller |
| DELETE | `/api/kudos/{id}` | admin (moderasyon) |

- POST body: `{ aliciPersonelId, mesaj }` gönderen token'dan alınır, body'den değil

---

## Ekipman — Envanter (admin stok ekranı)

| Method | Endpoint | Role |
|--------|----------|------|
| GET | `/api/equipment` | admin |
| POST | `/api/equipment` | admin |
| PATCH | `/api/equipment/{id}` | admin |

- Ekipman silinmez, durumu güncellenir (PATCH) kayıt silmek zimmet geçmişinin FK'larını kırar

## Ekipman — Talepler

| Method | Endpoint | Role |
|--------|----------|------|
| POST | `/api/equipment-requests` | personel |
| GET | `/api/equipment-requests/my` | personel |
| PATCH | `/api/equipment-requests/{id}/cancel` | personel (owner) |
| PATCH | `/api/equipment-requests/{id}/approve` | yönetici |
| PATCH | `/api/equipment-requests/{id}/reject` | yönetici |

- Approve yan etkisi: zimmet kaydı otomatik oluşur + ekipman durumu personelde olur ayrı endpoint'i yok
- Cancel yalnızca durum `Onay bekliyor` iken, iptal eden `islem_yapan_id`'ye yazılır 

## Ekipman — Zimmet

| Method | Endpoint | Role |
|--------|----------|------|
| GET | `/api/assignments/my` | personel |
| GET | `/api/assignments` | admin |
| PATCH | `/api/assignments/{id}/return` | admin |

- İade admin tarafından işlenir `iade_tarihi` dolar, ekipman `depoda`ya döner zimmet satırı silinmez, geçmiş kaydı olur

---

## İzin Yönetimi

| Method | Endpoint | Role |
|--------|----------|------|
| POST | `/api/leave-requests` | personel |
| GET | `/api/leave-requests/my` | personel |
| PATCH | `/api/leave-requests/{id}/cancel` | personel (owner) |
| PATCH | `/api/leave-requests/{id}/approve` | yönetici |
| PATCH | `/api/leave-requests/{id}/reject` | yönetici |

- `is_gunu_sayisi` backend'de hesaplanır client formda bilgilendirme amaçlı kendi hesabını gösterir, POST body'sinde bu alan yok
- Yönetici/Admin izin talebi oluşturamaz -> 403 (admin ve yönetici izin talebi oluşturmasına gerek kalmasın diye düşündüm bu yüzdden frontend'de de route gizli)
- Cancel yalnızca `Onay bekliyor` durumunda

**Örnek — izin talebi:**

```
POST /api/leave-requests
{ "izinTuruId": 1, "baslangicTarihi": "2026-07-22", "bitisTarihi": "2026-07-24", "yoneticiyeNot": "..." }

->  201 Created
{ "id": "...", "isGunuSayisi": 3, "durum": "Onay bekliyor", ... }
```

---

## Onay Ekranı (Approvals)

| Method | Endpoint | Role |
|--------|----------|------|
| GET | `/api/approvals?type=&status=` | yönetici |

- İzin + ekipman taleplerini tek listede birleştirir (iki entity -> ortak `ApprovalItemResponse` DTO)
- İşlenen talepler de listede döner

---

## Duyurular

| Method | Endpoint | Role |
|--------|----------|------|
| GET | `/api/announcements` | tüm roller |
| GET | `/api/announcements/{id}` | tüm roller |
| POST | `/api/announcements` | admin |
| PATCH | `/api/announcements/{id}` | admin |
| DELETE | `/api/announcements/{id}` | admin |

- Detay response'u yorumları ve beğeni bilgisini içerir: `{ baslik, icerik, yazan, olusturmaTarihi, begeniSayisi, benBegendimMi, yorumlar[] }`
- `begeniSayisi` duyuru_begeni tablosundan COUNT ile canlı hesaplanır

## Yorumlar

| Method | Endpoint | Role |
|--------|----------|------|
| POST | `/api/announcements/{id}/comments` | tüm roller |
| PATCH | `/api/comments/{id}` | owner |
| DELETE | `/api/comments/{id}` | owner \| admin |

- Admin yorumu siler ama düzenleyemez   
- Düzenlenen yoruma "edited" etiketi -> ER'a `duzenleme_tarihi` //open question

## Beğeni

| Method | Endpoint | Role |
|--------|----------|------|
| POST | `/api/announcements/{id}/like` | tüm roller |
| DELETE | `/api/announcements/{id}/like` | tüm roller |


- Kimin beğenisi token'dan — URL'de beğeni id yok
- Tekrarlanan POST idempotent olmalı frontend'i basitleştirir

---

## Personel

| Method | Endpoint                            | Role |
|--------|-------------------------------------|------|
| GET    | `/api/profile/me`                   | tüm roller |
| PATCH  | `/api/profile/me`                   | tüm roller |
| PUT    | `/api/profile/me/skills`            | tüm roller |
| GET    | `/api/personnel?department=&skill=` | tüm roller (ekip dizini) |
| POST   | `/api/profile/me/photo`             | tüm roller |
| DELETE | `/api/profile/me/photo`             | tüm roller |
| POST   | `/api/hr/personel`                  | HR, admin |
| PATCH  | `/api/hr/personel/{personelId}`     | HR, admin |
| PATCH  | `/api/profil/parola`                | authenticated tüm roller |
| PATCH  | `/api/admin/personel/{personelId}/rol` | admin |



- PATCH body'sinde rol ve sicil_no kabul edilmez
- Personel oluşturma request’i `{ kullaniciAdi, ad, soyad, dogumTarihi, ePosta, departmanId, isGirisTarihi, yoneticiId? }` biçimindedir. Rol, parola, sicil numarası ve diğer server-controlled alanlar kabul edilmez.
- Yeni personelin rolü sunucu tarafından `PERSONEL`, ilk parolası normalize edilmiş kullanıcı adının BCrypt hash’i ve `parolaDegistirmeliMi` değeri `true` olarak belirlenir.
- `sicil_numarasi` MySQL tarafından `AUTO_INCREMENT` ile üretilir. `departmanId` zorunlu, `yoneticiId` opsiyoneldir; gönderilen yönetici mevcut, aktif, `YONETICI` rolünde ve yeni personelle aynı departmanda olmalıdır.
- Kullanıcı adı ve e-posta normalize edilir; mevcut değerle çakışma `409 Conflict` döner. Başarılı oluşturma `{ personelId, kullaniciAdi }` gövdesiyle `201 Created` döner ve `PERSONEL_EKLENDI` audit kaydı oluşturur.
- Personel güncelleme request’i kısmi PATCH semantiği kullanır. Güncellenebilir alanlar `{ ad, soyad, dogumTarihi, ePosta, departmanId, isGirisTarihi, yoneticiId, telNo, mezunOkul, mezunBolum, mezuniyetYili }` biçimindedir; yalnız gönderilen alanlar değerlendirilir.
- Güncelleme sırasında `ad`, `soyad`, `dogumTarihi`, `ePosta`, `departmanId` ve `isGirisTarihi` alanları gönderilirse `null` olamaz. `yoneticiId`, `telNo`, `mezunOkul`, `mezunBolum` ve `mezuniyetYili` açıkça `null` gönderilerek temizlenebilir. Boş metin temizleme anlamına gelmez.
- `personelId`, `kullaniciAdi`, `sicilNumarasi`, rol, parola, aktiflik, işten çıkış tarihi, profil fotoğrafı ve yıllık izin hakkı bu güncelleme endpoint’iyle değiştirilemez.
- Departman gerçekten değiştiriliyorsa aynı request içinde `yoneticiId` alanı da gönderilmelidir. Değer yeni departmandaki aktif bir `YONETICI` rolünün ID’si olabilir veya mevcut yöneticiyi kaldırmak için `null` gönderilebilir.
- Atanacak yönetici hedef personelin kendisi olamaz; aktif, `YONETICI` rolünde ve güncelleme sonrasındaki departmanla aynı departmanda olmalıdır. Yönetici, departman değişmeden de tek başına güncellenebilir veya `null` ile kaldırılabilir.
- Güncellemede e-posta normalize edilir; hedef dışındaki bir personelle çakışması `409 Conflict` döner. Boş request ve geçersiz ilişki kuralları `400`, bulunamayan hedef/departman/yönetici `404` döner.
- Başarılı personel güncellemesi `204 No Content` döner ve yalnız değişen alan adlarını içeren `PERSONEL_GUNCELLENDI` audit kaydı oluşturur. Mevcut değerlerin tekrar gönderildiği idempotent istek `204` döner, persistence veya audit yan etkisi oluşturmaz.
- Normal parola değiştirme request’i `{ mevcutParola, yeniParola }` biçimindedir; personel kimliği JWT principal’dan alınır. Başarılı işlem bütün refresh tokenları siler, `PAROLA_DEGISTIRILDI` audit kaydı oluşturur ve `204 No Content` döner.
- Yeni parola 8–72 karakter olmalı, UTF-8 karşılığı 72 byte’ı aşmamalı; küçük harf, büyük harf, rakam ve özel karakter içermeli, boşluk içermemeli, kullanıcı adı veya mevcut parolayla aynı olmamalıdır.
- Skill ekleme için PUT kullanıyoruz çünkü, birden fazla skill eklenmeye çalıştığında PATCH çalışmaz.
- Ekip dizini DTO'su: ad-soyad, departman, yetenekler, foto
- Rol değiştirme request body’si `{ "rol": "HR" }` biçimindedir. İşlemi yapan Admin JWT principal’dan, hedef personel path’teki `personelId` değerinden alınır.
- Aynı rol tekrar istenirse işlem idempotent biçimde `204 No Content` döner; token veya audit yan etkisi oluşturmaz.
- Admin kendi rolünü ve mevcut başka bir Admin’in rolünü değiştiremez; bu istekler `403 Forbidden` döner.
- Başarılı rol değişikliğinde hedef personelin bütün refresh token kayıtları silinir ve aktör ile hedefi ilişkilendiren `ROL_DEGISTIRILDI` audit kaydı oluşturulur.
- Stateless access token anında iptal edilmez; eski token kalan ömrü boyunca, en fazla 15 dakika çalışabilir.

---

## Dashboard

| Method | Endpoint              | Role |
|--------|-----------------------|------|
| GET | `/api/dashboard/ozet` | tüm roller |

- Tüm kartlar tek istekte, tek DTO: `{ bugunDogumGunleri[], sonDuyurular[], kalanIzin, bugunIzinliSayisi, odaDoluluk[], sonTakdirler[], bekleyenTaleplerim[] }`
- Role göre içerik: Yönetici/Admin'de `kalanIzin` dönmez (izin talep edemiyorlar)  endpoint aynı, DTO içeriği role göre dolar
- `kalanIzin` canlı hesap: `yillik_izin_hakki - onaylanmış yıllık izinlerin toplamı`
- Oda doluluk özeti rezervasyon sorgusundan türetilir aynı service metodu, ayrı endpoint yok

---

## Lookup'lar (form dropdown'ları)

| Method | Endpoint                | Role |
|--------|-------------------------|------|
| GET | `/api/leave-types`      | tüm roller |
| GET | `/api/equipment-types`  | tüm roller |
| GET | `/api/departments`      | tüm roller |
| GET | `/api/skills`           | tüm roller |
| POST | `/api/skills`           | tüm roller |

- Lookup GET'leri `[{ id, ad }]` döner — frontend hardcode etmez, DB ile ID senkronu bozulmaz ("dinamik tanım tablosu" isteri)
- POST /skills: listede olmayan yetenek eklenebilir; `yetenek_adi` unique duplicate gelirse mevcut kayıt dönülür
- `durum_turu` ve `rol` için endpoint yok: hiçbir formda kullanıcı bunları seçmiyor, response'larda metin olarak dönüyorlar

---

## Open Questions

- [ ] `GET /api/reservations` (admin, filtresiz tüm liste) hangi ekran için? Ayrı admin yönetim sayfası olsunmu?
- [ ] `GET /api/equipment-requests` yine admin yönetim sayfası olsunmu?
- [ ] Skill listesi yönetimi: serbest ekleme mi, admin temizliği mi, onay kuyruğu mu?
- [ ] Logout server-side bir şey yapmalı mı (token blacklist) yoksa client-side yeterli mi?
- [ ] Talep→zimmet izlenebilirliği: `zimmetleme_bilgileri.talep_id` FK'sı eklensin mi?
