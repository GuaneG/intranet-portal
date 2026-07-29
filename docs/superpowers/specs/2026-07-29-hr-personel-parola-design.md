# HR Personel Oluşturma ve Parola Yönetimi Tasarımı

**Durum:** Uygulandı; 29.07.2026 tarihinde 41 testlik backend paketiyle doğrulandı.

## Amaç

Bu tasarım, Roadmap Aşama 1 kapsamındaki HR personel oluşturma akışını ve bunun gerektirdiği ilk giriş ile isteğe bağlı parola değiştirme davranışlarını tanımlar.

Hedefler:

- HR veya Admin yeni personel oluşturabilsin.
- Yeni personelin başlangıç rolü sunucu tarafından `PERSONEL` olarak belirlensin.
- İlk parola normalize edilmiş kullanıcı adı olsun ve yalnız BCrypt hash’i saklansın.
- Personel ilk parolasını değiştirmeden normal access/refresh token alamaz.
- Authenticated personel daha sonra istediği zaman parolasını değiştirebilsin.
- Parola değişikliğinde bütün refresh tokenlar iptal edilsin.
- Personel oluşturma ve parola değişiklikleri aktör/hedef ilişkisiyle audit edilsin.

## Kapsam Dışı

Bu çalışma şunları içermez:

- E-posta veya SMS davet altyapısı
- Özel bir ilk-giriş token türü
- Parola sıfırlama veya “parolamı unuttum” akışı
- Profilde telefon ve mezuniyet bilgilerini güncelleme
- Gelecekte başlayacak personeli otomatik aktifleştiren scheduled işlem
- Anında access-token denylist veya `tokenVersion`

## Temel Ürün Kararları

- HR ve Admin personel oluşturabilir.
- HR rol atayamaz; yeni kayıt her zaman `PERSONEL` rolüyle oluşur.
- `departmanId` zorunludur.
- `yoneticiId` opsiyoneldir; gönderilirse kayıt bulunmalı ve rolü `YONETICI` olmalıdır.
- Telefon ve mezuniyet bilgileri oluşturma request’ine alınmaz; profil/HR güncelleme akışlarına bırakılır.
- `isGirisTarihi` bugün veya geçmiş olmalıdır.
- İlk parola normalize edilmiş kullanıcı adıyla aynıdır.
- Request DTO geçici parola veya rol alanı kabul etmez.
- İlk parola değişikliği tamamlanmadan normal login token üretmez.
- Normal parola değişikliği bütün refresh tokenları siler ve yeniden login gerektirir.
- Stateless access token kalan ömrü boyunca, en fazla 15 dakika geçerli olabilir.

## Kullanıcı Adı Normalizasyonu

HR tarafından gönderilen kullanıcı adı backend’de:

1. baştaki ve sondaki boşluklardan temizlenir,
2. `Locale.ROOT` ile küçük harfe çevrilir,
3. yalnız `a-z`, `0-9`, `.`, `_` ve `-` karakterleri kabul edilir.

Uzunluk 3–50 karakterdir. Normalizasyon sonrasında aynı değere dönüşen kullanıcı adları aynı kabul edilir. Örneğin `Ali.Yilmaz` ve `ali.yilmaz` birlikte oluşturulamaz.

E-posta baştaki/sondaki boşluklardan temizlenip `Locale.ROOT` ile küçük harfe çevrilir; benzersizlik normalize değer üzerinden kontrol edilir.

## Veri Modeli

`Personel` entity’sine şu alan eklenir:

```text
parolaDegistirmeliMi: Boolean
```

Veritabanı karşılığı:

```sql
parola_degistirmeli_mi BOOLEAN NOT NULL DEFAULT FALSE
sicil_numarasi INT NOT NULL AUTO_INCREMENT UNIQUE
```

`DEFAULT FALSE` mevcut kullanıcıların davranışını değiştirmemek içindir. HR/Admin tarafından yeni oluşturulan personelde servis açıkça `true` yazar. İlk başarılı parola değişikliğinde değer `false` olur. Sicil numarası request’ten alınmaz; MySQL tarafından üretilir ve JPA alanı veritabanınca üretilen değer olarak işaretlenir.

## Personel Oluşturma API’si

### Endpoint

```text
POST /api/hr/personel
Yetki: HR veya ADMIN
```

### Request

`PersonelOlusturRequest`:

```text
kullaniciAdi: String       zorunlu, normalize edilir, 3–50
ad: String                 zorunlu, en fazla 50
soyad: String              zorunlu, en fazla 50
dogumTarihi: LocalDate     zorunlu, geçmiş tarih
ePosta: String             zorunlu, geçerli e-posta, en fazla 100

departmanId: Integer       zorunlu, pozitif
isGirisTarihi: LocalDate   zorunlu, bugün veya geçmiş
yoneticiId: String         opsiyonel
```

Request rol, parola, sicil numarası, aktiflik, profil fotoğrafı, izin hakkı, çıkış tarihi, telefon veya mezuniyet alanı kabul etmez.

### Sunucu Tarafından Belirlenen Değerler

```text
rol = PERSONEL
sicilNumarasi = MySQL AUTO_INCREMENT
calisiyorMu = true
parolaHash = BCrypt(normalizeKullaniciAdi)
parolaDegistirmeliMi = true
profilFoto = def_pfp.png
yillikIzinHakki = 14
isCikisTarihi = null
```

### Response

Başarılı işlem:

```text
201 Created
```

`PersonelOlusturResponse`:

```json
{
  "personelId": "uuid",
  "kullaniciAdi": "ali.yilmaz"
}
```

Entity doğrudan response olarak dönmez.

## İlk Parola Değiştirme Akışı

Geçici parola doğru olsa bile `parolaDegistirmeliMi=true` olan kullanıcı için normal login:

- `LOGIN_BASARILI` veya `LOGIN_BASARISIZ` audit’i oluşturmaz,
- access token üretmez,
- refresh token/cookie üretmez,
- `428 Precondition Required` döner,
- kontrollü `"İlk girişte parola değişikliği gerekli."` mesajını verir.

### Endpoint

```text
PATCH /api/auth/ilk-parola
Yetki: permitAll
```

`IlkParolaDegistirRequest`:

```text
kullaniciAdi: String
mevcutParola: String
yeniParola: String
```

Akış:

1. Kullanıcı adı normalize edilir.
2. Kullanıcı bulunur ve aktif olduğu doğrulanır.
3. Mevcut geçici parola BCrypt ile doğrulanır.
4. `parolaDegistirmeliMi=true` olduğu doğrulanır.
5. Yeni parola politikası uygulanır.
6. Yeni parola BCrypt ile hash’lenir.
7. `parolaDegistirmeliMi=false` yapılır.
8. Savunmacı olarak personele ait refresh tokenlar silinir.
9. Audit kaydı yazılır.
10. `204 No Content` döner.

Yanlış kullanıcı adı veya mevcut parola, kullanıcı varlığını ayırt ettirmeyen genel `401` authentication cevabı üretir. Flag zaten kapalıysa istek `409 Conflict` döner. Endpoint IP ve path bazında dakikada 5 istekle sınırlandırılır.

## İsteğe Bağlı Parola Değiştirme Akışı

### Endpoint

```text
PATCH /api/profil/parola
Yetki: authenticated tüm roller
```

`ParolaDegistirRequest`:

```text
mevcutParola: String
yeniParola: String
```

Personel kimliği JWT principal’dan alınır; request’ten personel ID kabul edilmez.

Akış:

1. Authenticated personel principal ID ile bulunur.
2. Mevcut parola BCrypt ile doğrulanır.
3. Yeni parola politikası uygulanır.
4. Yeni parola hash’lenip kaydedilir.
5. Personele ait bütün refresh tokenlar silinir.
6. Audit kaydı yazılır.
7. `204 No Content` döner.
8. Frontend local access tokenı temizleyip login ekranına yönlendirir.

## Parola Politikası

İlk ve isteğe bağlı parola değişikliğinde yeni parola:

- en az 8, en fazla 72 karakter,
- UTF-8 karşılığı en fazla 72 byte,
- en az bir küçük harf,
- en az bir büyük harf,
- en az bir rakam,
- en az bir özel karakter,
- boşluk içermemeli

içermelidir.

Ayrıca:

- normalize edilmiş kullanıcı adıyla aynı olamaz,
- mevcut parolayla aynı olamaz.

Biçimsel kurallar DTO validation ile, kullanıcı adı/mevcut parola karşılaştırmaları service iş kuralı olarak uygulanır.

## Servis ve Controller Sorumlulukları

### HR Personel Controller

- HTTP request/response sözleşmesini yönetir.
- `@Valid` sonucunu alır.
- Authenticated aktör ID’sini security context’ten alır.
- Personel oluşturma service metodunu çağırır.
- İş kuralı veya repository erişimi içermez.

### PersonelService

- Aktör, departman, yönetici ve varsayılan `PERSONEL` rolünü doğrular.
- Kullanıcı adı/e-posta benzersizliğini kontrol eder; sicil numarasını veritabanına ürettirir.
- Entity’yi server-controlled alanlarla oluşturur.
- Personeli ve aynı transaction’daki audit kaydını koordine eder.

### ParolaService

- İlk ve normal parola değiştirme akışlarının ortak parola doğrulama, politika, hash, token iptali ve audit davranışını yönetir.
- Interface eklenmez; tek somut Spring service yeterlidir.

### AuthService

- Doğru parola ve aktiflik kontrolünden sonra `parolaDegistirmeliMi` değerini kontrol eder.
- Flag açıksa login tokenları ve başarılı login audit’i üretmeden 428 akışını başlatır.

## Repository İhtiyaçları

Yalnız mevcut use-case’in gerektirdiği sorgular eklenir:

- normalize edilmiş kullanıcı adı var mı,
- normalize edilmiş e-posta var mı.

Service seviyesindeki ön kontroller kullanıcıya kontrollü `409` üretir. Yarış durumlarına karşı mevcut unique constraint’ler son güvenlik katmanı olarak kalır; `DataIntegrityViolationException` ham SQL mesajı sızdırmadan kontrollü conflict cevabına çevrilir.

## Hata Sözleşmesi

- Validation hatası: `400 Bad Request`
- Zayıf/yasak yeni parola: `400 Bad Request`
- Yanlış mevcut parola: ilk parola akışında genel `401 Unauthorized`
- Normal authenticated değişiklikte yanlış mevcut parola: kontrollü `400 Bad Request`
- Login’de zorunlu ilk parola: `428 Precondition Required`
- Kullanıcı adı/e-posta çakışması: `409 Conflict`
- Departman/yönetici/rol bulunamadı: `404 Not Found`
- Yönetici rolü `YONETICI` değil: `400 Bad Request`
- Endpoint rolü yetersiz: `403 Forbidden`

Ham framework, SQL veya BCrypt ayrıntısı response’a yazılmaz.

## Audit ve Transaction

Yeni audit olayları:

```text
PERSONEL_EKLENDI
PAROLA_DEGISTIRILDI
```

`PERSONEL_EKLENDI`:

- aktör: HR veya Admin,
- hedef: yeni personel,
- parola veya parola hash’i içermez.

`PAROLA_DEGISTIRILDI`:

- aktör ve hedef: parolası değişen personel,
- ilk/normal değişiklik bağlamı yazılabilir,
- eski, geçici veya yeni parola içermez.

Personel oluşturma ile audit aynı transaction’a katılır. Parola güncelleme, refresh-token silme ve audit aynı transaction’a katılır. Ana işlem rollback olursa bütün yan etkiler geri alınır.

## Test Tasarımı

### Unit Testler

- HR/Admin başarılı personel oluşturur.
- Rol server tarafından `PERSONEL` atanır.
- Düz parola saklanmaz; BCrypt hash oluşur.
- Yeni personelde `parolaDegistirmeliMi=true` olur.
- Aynı normalize kullanıcı adı/e-posta kontrollü conflict üretir.
- Olmayan departman/yönetici reddedilir.
- `YONETICI` olmayan kişi yönetici atanamaz.
- Personel oluşturma doğru aktör/hedefle audit üretir.
- İlk parola doğruysa hash güncellenir ve flag kapanır.
- Yanlış mevcut parola reddedilir.
- Zayıf, kullanıcı adıyla aynı veya mevcut parolayla aynı yeni parola reddedilir.
- Normal değişiklik refresh tokenları siler ve audit üretir.

### HTTP ve MySQL Integration Testleri

- HR personel oluşturur: `201`; rol, hash, flag ve DB-generated sicil numarası doğrulanır.

- PERSONEL oluşturma endpoint’inde `403` alır.
- Geçici parolayla login `428` döner; access token ve refresh cookie oluşmaz.
- İlk parola değişiminden sonra normal login `200` döner.
- Normal parola değişikliği refresh tokenları siler.
- Personel oluşturma ve parola değişiminde audit persistence doğrulanır.


## Bağımlılık Sırası

1. Veritabanı kolonu ve entity alanı
2. Request/response DTO’ları
3. Repository benzersizlik sorguları
4. Audit enum değerleri
5. `ParolaService`
6. `PersonelService` personel oluşturma akışı
7. Controller ve SecurityConfig endpoint kuralları
8. AuthService 428 ilk giriş davranışı
9. Unit testler
10. HTTP/MySQL integration testleri
11. API, Security, Roadmap ve Progress belgeleri

