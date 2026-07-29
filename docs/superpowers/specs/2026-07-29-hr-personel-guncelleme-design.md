# HR Personel Güncelleme Tasarımı

**Durum:** Uygulandı; tam backend paketi 61 testle ve başarılı build ile doğrulandı.

## Amaç

Bu tasarım, Roadmap Aşama 1 kapsamındaki HR personel güncelleme akışını tanımlar.

Hedefler:

- HR veya Admin, hedef personelin yalnız gönderdiği alanlarını güncelleyebilsin.
- JSON'da bulunmayan alan ile açıkça `null` gönderilen alan birbirinden ayrılabilsin.
- Personel kimliği, kullanıcı adı, sicil numarası, rol, parola ve çalışma durumu bu endpoint üzerinden değiştirilemesin.
- Departman ve yönetici ilişkisi gelecekteki takım/ownership kurallarıyla uyumlu kalsın.
- Güncelleme kontrollü hata sözleşmesi ve audit kaydıyla aynı transaction içinde yürüsün.

## Kapsam Dışı

Bu çalışma şunları içermez:

- Personeli pasife alma veya yeniden aktifleştirme
- Rol değiştirme
- Kullanıcı adı, sicil numarası veya parola değiştirme
- Personelin kendi profilini güncellemesi
- Profil fotoğrafı ve yetenek güncelleme
- Personel listeleme veya ekip dizini

Pasif personelin geçmiş bilgilerindeki kayıt hataları HR/Admin tarafından düzeltilebilir. Ancak aktiflik ve işten çıkış tarihi ayrı pasife alma akışının sorumluluğudur.

## Endpoint

```text
PATCH /api/hr/personel/{personelId}
Yetki: HR veya ADMIN
Başarılı cevap: 204 No Content
```

Path'teki `personelId` hedef kaydı seçer; personel kimliği değiştirilemez.

## Request Modeli

`PersonelGuncelleRequest`, `record` yerine normal sınıf olarak oluşturulur. Sınıfın üzerinde şu karar açıklanır:

```java
// PATCH isteğinde JSON'da bulunmayan alan ile açıkça null gönderilen alanı
// ayırt edebilmek için record yerine alan varlığını takip eden sınıf kullanılır.
```

Her alan için:

- alan değeri,
- JSON içinde gönderilip gönderilmediğini belirten `...GonderildiMi` bilgisi

tutulur. İlgili `@JsonSetter` çağrıldığında alanın gönderildiği işaretlenir.

Bu yaklaşım seçilmiştir çünkü:

- normal nullable bir `record`, alanın gönderilmemesi ile `null` gönderilmesini ayıramaz,
- `JsonNode` veya `Map` tip güvenliğini ve Jakarta Validation kullanımını zayıflatır,
- `JsonNullable` yeni bir bağımlılık ekler,
- alan varlığını DTO içinde izlemek yeni bağımlılık olmadan açık PATCH semantiği sağlar.

### Güncellenebilir alanlar

```text
ad
soyad
dogumTarihi
ePosta
departmanId
isGirisTarihi
yoneticiId
telNo
mezunOkul
mezunBolum
mezuniyetYili
```

### Personel kaydında null olamayan alanlar

Şu alanların request'te bulunması zorunlu değildir; ancak gönderilirlerse `null` veya geçersiz değer olamazlar:

```text
ad
soyad
dogumTarihi
ePosta
departmanId
isGirisTarihi
```

### Nullable alanlar

Şu alanlar:

- request'te bulunmazsa mevcut değerini korur,
- açıkça `null` gönderilirse temizlenir,
- değerle gönderilirse doğrulanıp güncellenir:

```text
yoneticiId
telNo
mezunOkul
mezunBolum
mezuniyetYili
```

Boş JSON nesnesi güncellenecek alan içermediği için `400 Bad Request` üretir.

### Kısmi request validation

Gönderilmeyen alanların Java değeri de `null` olacağı için personel kaydında null olamayan alanlara doğrudan `@NotNull` veya `@NotBlank` koyulmaz. Aksi hâlde yalnız `ad` gönderen geçerli bir PATCH isteği, gönderilmeyen diğer alanlar nedeniyle yanlışlıkla reddedilir.

Validation iki parçaya ayrılır:

- `@Size`, `@Email`, `@Past`, `@Positive` ve `@PastOrPresent` gibi null değeri kabul eden biçimsel constraint'ler gönderilen değerlerin şeklinin kontrolünde kullanılır.
- Alan-gönderildi flag'leri kullanılarak boş request, gönderilmiş zorunlu nitelikteki alanın `null` olması ve trim sonrası boş metin gibi PATCH'e özgü kurallar kontrollü biçimde doğrulanır.

Nullable metin alanlarında boş metin temizleme anlamına gelmez; alanı temizlemek isteyen istemci açıkça `null` göndermelidir.

### Değiştirilemeyen alanlar

Bu alanlar request DTO'da yer almaz ve service tarafından işlenmez:

```text
personelId
kullaniciAdi
sicilNumarasi
rol
parola
calisiyorMu
isCikisTarihi
profilFoto
yillikIzinHakki
```

## Örnek İstekler

Yalnız personel adını değiştirme:

```json
{
  "ad": "Ahmet"
}
```

Telefonu temizleme:

```json
{
  "telNo": null
}
```

Departman ve yöneticiyi birlikte değiştirme:

```json
{
  "departmanId": 5,
  "yoneticiId": "yeni-yonetici-id"
}
```

Departmanı değiştirip yöneticiyi kaldırma:

```json
{
  "departmanId": 5,
  "yoneticiId": null
}
```

## Service Veri Akışı

`PersonelService.personelGuncelle(aktorId, hedefPersonelId, request)` metodu:

```text
@Transactional
@PreAuthorize("hasAnyRole('HR', 'ADMIN')")
```

ile çalışır.

Akış:

1. İşlemi yapan personel ve hedef personel bulunur.
2. Request'te en az bir alan gönderildiği doğrulanır.
3. Yalnız gönderilen alanlar biçimsel ve iş kuralı doğrulamasından geçirilir.
4. Metinsel değerler mevcut oluşturma akışıyla uyumlu biçimde trim edilir.
5. E-posta gönderilmişse trim ve `Locale.ROOT` ile küçük harfe dönüştürülür.
6. Normalize e-postanın hedef dışındaki başka bir personel tarafından kullanılıp kullanılmadığı kontrol edilir.
7. Güncelleme sonrasındaki etkili departman ve yönetici hesaplanır.
8. Departman ve yönetici kuralları doğrulanır.
9. Yalnız gerçekten değişen alanlar entity üzerinde güncellenir.
10. Unique constraint yarışını kontrollü yerde yakalamak için işlem flush edilir.
11. Gerçek değişiklik varsa aynı transaction içinde audit kaydı oluşturulur.
12. `204 No Content` döner.

Gönderilen değerler mevcut değerlerle aynıysa işlem idempotent biçimde `204` döner; persistence veya audit yan etkisi oluşturmaz.

## E-posta Kuralları

- E-posta gönderilmişse boş veya `null` olamaz.
- Geçerli e-posta biçiminde ve en fazla 100 karakter olmalıdır.
- Trim edilip `Locale.ROOT` ile küçük harfe çevrilir.
- Hedef personelin kendi mevcut e-postası conflict oluşturmaz.
- Başka personelin normalize e-postasıyla çakışma `409 Conflict` üretir.
- Veritabanı unique constraint'i yarış durumlarına karşı son güvenlik katmanıdır.
- Flush sırasında oluşan `DataIntegrityViolationException`, ham SQL ayrıntısı sızdırmadan kontrollü `409` cevabına çevrilir.

## Departman ve Yönetici Kuralları

Departman/yönetici doğrulaması yalnız `departmanId` veya `yoneticiId` alanlarından en az biri request'te bulunduğunda çalışır. Böylece yalnız ad, telefon veya başka bağımsız bir alan güncellenirken request'le ilgisiz mevcut ilişki yeniden doğrulanıp işlemi engellemez.

İlişki alanlarından biri gönderilmişse güncelleme sonrasındaki etkili departman:

- `departmanId` gönderilmişse bulunan yeni departman,
- gönderilmemişse hedefin mevcut departmanıdır.

Etkili yönetici:

- `yoneticiId` gönderilmiş ve değer `null` ise yönetici yoktur,
- `yoneticiId` gönderilmiş ve ID verilmişse bulunan yeni yöneticidir,
- alan gönderilmemişse hedefin mevcut yöneticisidir.

Atanacak yönetici:

- hedef personelin kendisi olamaz,
- aktif olmalıdır,
- rolü `YONETICI` olmalıdır,
- etkili departmanla aynı departmanda olmalıdır.

`departmanId` mevcut departmandan farklı bir değere değiştiriliyorsa request mutlaka `yoneticiId` alanını da içermelidir. Bu alan:

- yeni departmandaki geçerli bir yöneticinin ID'si olabilir,
- yöneticiyi kaldırmak için açıkça `null` olabilir.

Departman gerçekten değiştiği hâlde `yoneticiId` hiç gönderilmezse istek `400 Bad Request` ile reddedilir. Mevcut departman ID'sinin tekrar gönderilmesi gerçek değişiklik sayılmaz ve yönetici alanını gönderme zorunluluğu oluşturmaz.

Aynı yönetici doğrulama kuralları mevcut personel oluşturma akışına da uygulanır: gönderilen yönetici aktif, `YONETICI` rolünde ve seçilen departmanla aynı departmanda olmalıdır.

## Audit ve Transaction

Yeni audit olayı:

```text
PERSONEL_GUNCELLENDI
```

Audit kaydında:

- `personel`: işlemi yapan HR/Admin,
- `hedefPersonel`: güncellenen personel,
- detay: yalnız değişen alan adları

tutulur.

Eski/yeni e-posta, telefon, doğum tarihi veya mezuniyet değerleri audit detayına yazılmaz. Parola veya token bilgisi hiçbir zaman audit edilmez.

Personel değişikliği ve audit kaydı aynı `REQUIRED` transaction'a katılır. Ana işlem rollback olursa entity değişiklikleri ve audit birlikte geri alınır.

Boş request veya yalnız mevcut değerleri tekrar eden istek audit üretmez.

## Hata Sözleşmesi

- `400 Bad Request`
  - boş request,
  - personel kaydında null olamayan alanın `null` gönderilmesi,
  - gönderilen alanın biçimsel olarak geçersiz olması,
  - departman değişirken `yoneticiId` alanının gönderilmemesi,
  - hedefin kendisinin yönetici seçilmesi,
  - pasif, farklı departmandaki veya `YONETICI` rolü olmayan yönetici seçilmesi
- `403 Forbidden`
  - authenticated rolün HR veya Admin olmaması
- `404 Not Found`
  - işlemi yapan personel, hedef personel, departman veya yönetici bulunamaması
- `409 Conflict`
  - normalize e-postanın başka personel tarafından kullanılması
- `204 No Content`
  - güncellemenin başarılı olması,
  - gönderilen değerlerin zaten mevcut değerlerle aynı olması

Kontrollü response'larda ham framework, SQL veya persistence exception mesajı bulunmaz.

## Controller Sorumluluğu

`HrPersonelController`:

- path'ten hedef `personelId` değerini,
- request body'den `PersonelGuncelleRequest` nesnesini,
- `Authentication` üzerinden aktör ID'sini

alır ve service metodunu çağırır.

Controller iş kuralı, entity güncellemesi veya repository erişimi içermez. Başarılı service çağrısından sonra `204 No Content` döner.

## Test Tasarımı

### Unit testler

- Yalnız `ad` gönderildiğinde sadece ad değişir.
- Gönderilmeyen alanlar korunur.
- Boş request `400` üretir.
- Personel kaydında null olamayan gönderilmiş alanın `null` değeri reddedilir.
- `telNo: null` telefon bilgisini temizler.
- `yoneticiId: null` yöneticiyi kaldırır.
- Departman değişirken yönetici alanı gönderilmezse istek reddedilir.
- Departman ve aynı departmandaki aktif yönetici birlikte değiştirilir.
- Farklı departmandaki yönetici reddedilir.
- Pasif yönetici reddedilir.
- `YONETICI` rolü olmayan kişi reddedilir.
- Hedef personel kendisini yönetici yapamaz.
- Hedefin mevcut e-postası conflict oluşturmaz.
- Başka personelin normalize e-postası conflict üretir.
- Gerçek değişiklik aktör/hedef ve değişen alan adlarıyla audit üretir.
- Aynı değerlerin tekrar gönderilmesi audit üretmez.

### HTTP ve MySQL integration testleri

- HR tek alanı günceller ve gönderilmeyen DB alanları korunur.
- HR nullable alanı açıkça `null` göndererek temizler.
- Admin endpoint'e erişebilir.
- PERSONEL rolü `403` alır.
- Departman değişikliği yönetici alanı olmadan `400` alır.
- Aynı departmandaki aktif yöneticiyle departman değişikliği kalıcı olur.
- Normalize e-posta çakışması `409` döner.
- Başarılı güncelleme `PERSONEL_GUNCELLENDI` audit kaydını aktör/hedef ilişkisiyle kalıcılaştırır.
- Aynı değerleri gönderen istek `204` döner ve audit oluşturmaz.
- Personel oluşturma akışında farklı departmandaki veya pasif yönetici reddedilir.

## Bağımlılık Sırası

1. `PERSONEL_GUNCELLENDI` audit enum değeri
2. Alan varlığını izleyen `PersonelGuncelleRequest`
3. E-posta uniqueness sorgusu
4. Ortak yönetici doğrulama helper'ı
5. `PersonelService.personelGuncelle`
6. Mevcut oluşturma akışının ortak yönetici kuralını kullanması
7. `HrPersonelController` PATCH endpoint'i
8. Unit testler
9. HTTP/MySQL integration testleri
10. API, Security, Roadmap ve Progress belge güncellemeleri
