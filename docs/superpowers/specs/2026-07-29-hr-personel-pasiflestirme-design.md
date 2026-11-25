# HR Personel Pasifleştirme Tasarımı

**Durum:** Tasarım ve yazılı spec kullanıcı tarafından onaylandı.

## Amaç

Bu tasarım, Roadmap Aşama 1 kapsamındaki personel pasifleştirme akışını tanımlar.

Hedefler:

- Personel kaydını hard delete yapmadan çalışma dışı bırakmak.
- HR ve Admin rollerinin pasifleştirme yetkilerini hedef role göre güvenli biçimde uygulamak.
- Yönetici pasifleştirilirken ona bağlı personelleri pasif bir yöneticiye bağlı bırakmamak.
- Hedefin refresh tokenlarını iptal etmek.
- Personel değişikliği, çalışan ilişkileri, token silme ve audit kaydını tek transaction içinde yürütmek.

## Kapsam Dışı

Bu çalışma şunları içermez:

- Pasif personeli yeniden aktifleştirme
- Personel silme
- Rol değiştirme
- Ekip dizini endpoint'i
- Profil güncelleme
- Access token denylist veya `tokenVersion`
- Pasifleştirilen yöneticinin çalışanlarını başka bir yöneticiye otomatik atama

Yeniden aktifleştirme; çıkış tarihinin temizlenmesi, rolün yeniden değerlendirilmesi, yönetici ataması ve güvenlik etkileri nedeniyle ayrı bir tasarım konusu olacaktır.

## Endpoint

```text
PATCH /api/hr/personel/{personelId}/pasiflestir
Yetki: HR veya ADMIN
Request body: yok
Başarılı cevap: 204 No Content
```

Path'teki `personelId` hedef personeli seçer. İşlemi yapan aktör doğrulanmış JWT principal'dan alınır; request body veya path üzerinden aktör kabul edilmez.

## Yetki ve İş Kuralları

Hedef role göre kurallar:

| Hedef rol | HR | Admin |
|---|---:|---:|
| `PERSONEL` | İzinli | İzinli |
| `HR` | İzinli | İzinli |
| `YONETICI` | Yasak | İzinli |
| `ADMIN` | Yasak | Yasak |

Ek kurallar:

- HR veya Admin kendisini pasifleştiremez.
- `ADMIN` rolündeki hedef bu endpoint üzerinden hiçbir zaman pasifleştirilemez.
- `YONETICI` hedefini yalnız persisted rolü `ADMIN` olan aktör pasifleştirebilir.
- Genel endpoint erişimi `@PreAuthorize("hasAnyRole('HR', 'ADMIN')")` ile korunur.
- Hedefe özgü rol ve öz-hedef kontrolleri service katmanında uygulanır.
- İstemciden gelen rol veya aktiflik bilgisine güvenilmez.

Yetki ve hedef rol kontrolleri, zaten pasif hedef için idempotent dönüşten önce uygulanır. Böylece HR, pasif olsa bile bir yönetici üzerinde kendisine verilmeyen işlemi başarılı göstermiş olmaz.

## İdempotent Davranış

Hedef zaten pasifse ve aktör hedef üzerinde işlem yapmaya yetkiliyse endpoint `204 No Content` döner.

Bu durumda:

- `isCikisTarihi` değiştirilmez,
- refresh token silme işlemi tekrar çağrılmaz,
- çalışanların yönetici ilişkileri tekrar işlenmez,
- audit kaydı oluşturulmaz.

## Veri Değişiklikleri

Gerçek pasifleştirme işleminde hedef üzerinde:

```text
calisiyorMu = false
isCikisTarihi = LocalDate.now()
```

uygulanır.

Hedef `YONETICI` rolündeyse ona doğrudan bağlı bütün personeller bulunur ve her birinin:

```text
yonetici = null
```

alanı güncellenir. Bu temizlik aktif veya pasif ayrımı yapmadan mevcut doğrudan ilişkilerin tamamına uygulanır; hiçbir personel pasif yöneticiye bağlı bırakılmaz.

Pasifleştirilen hedefin kendi yöneticisi, departmanı, rolü ve geçmiş bilgileri değiştirilmez.

## Repository Yaklaşımı

Mevcut `Personel.yonetici` ilişkisini kullanan şu ihtiyaç odaklı repository metodu eklenir:

```text
List<Personel> findAllByYonetici(Personel yonetici)
```

Bulunan personeller transaction içinde managed entity olarak güncellenir. İlk sürümde bulk JPQL update kullanılmaz; açık entity güncellemesi mevcut JPA yaklaşımıyla daha anlaşılır ve test edilebilirdir.

## Service Veri Akışı

`PersonelService.personelPasiflestir(aktorId, hedefPersonelId)` metodu:

```text
@Transactional
@PreAuthorize("hasAnyRole('HR', 'ADMIN')")
```

ile çalışır.

Akış:

1. Aktör ve hedef personel repository'den bulunur.
2. Aktörün kendisini hedeflemediği doğrulanır.
3. `ADMIN` hedef yasağı uygulanır.
4. Hedef `YONETICI` ise aktörün persisted rolünün `ADMIN` olduğu doğrulanır.
5. Hedef zaten pasifse yan etki oluşturmadan dönülür.
6. Hedef yönetici ise doğrudan bağlı personeller bulunur ve yönetici ilişkileri kaldırılır.
7. Hedefin `calisiyorMu` değeri `false`, `isCikisTarihi` değeri işlem tarihi yapılır.
8. Hedefe ait bütün refresh token kayıtları silinir.
9. Pasifleştirme audit kaydı ana transaction içinde oluşturulur.
10. Transaction commit edilerek bütün değişiklikler birlikte kalıcılaştırılır.

## Refresh Token ve Access Token Etkisi

Başarılı pasifleştirmede hedefin bütün refresh token kayıtları `RefreshTokenService.revokeAllForPersonel` üzerinden silinir.

Mevcut authentication kuralları ayrıca:

- pasif personelin yeniden login olmasını,
- pasif personelin refresh ile yeni token almasını

engeller.

MVP stateless JWT kullandığı için önceden verilmiş access token denylist ile anında iptal edilmez. Hedefin mevcut access token'ı kalan ömrü boyunca, en fazla 15 dakika çalışabilir. Anında access-token iptali bu görevin kapsamı değildir.

## Audit ve Transaction

Yeni audit olayı:

```text
PERSONEL_PASIFE_ALINDI
```

Audit kaydında:

- `personel`: işlemi yapan HR/Admin,
- `hedefPersonel`: pasifleştirilen personel,
- detay: `Personel pasife alındı. Yöneticisi kaldırılan personel sayısı: N`

saklanır.

Çalışan kimlikleri, kişisel bilgiler, parola veya token değerleri audit detayına yazılmaz. Her çalışan için ayrı `PERSONEL_GUNCELLENDI` kaydı üretilmez.

Pasifleştirme, çalışanların yönetici ilişkisinin kaldırılması, refresh-token silme ve `PERSONEL_PASIFE_ALINDI` audit kaydı aynı `REQUIRED` transaction'a katılır. Bu adımlardan biri başarısız olursa bütün değişiklikler rollback olur.

## Controller Sorumluluğu

`HrPersonelController`:

- path'ten hedef `personelId` değerini,
- `Authentication` üzerinden aktör ID'sini

alır ve service metodunu çağırır. Request body veya yeni request DTO kullanılmaz.

Controller iş kuralı, rol karşılaştırması, entity güncellemesi veya repository erişimi içermez. Başarılı service çağrısından sonra `204 No Content` döner.

## Hata Sözleşmesi

- `204 No Content`
  - personel başarıyla pasifleştirildi,
  - yetkili aktör zaten pasif hedef için tekrar istek gönderdi.
- `403 Forbidden`
  - authenticated rol HR veya Admin değil,
  - aktör kendisini hedefledi,
  - HR bir `YONETICI` hedefledi,
  - hedef `ADMIN` rolünde.
- `404 Not Found`
  - aktör veya hedef personel bulunamadı.

Request body olmadığı için bu endpoint'e özgü bir validation DTO'su ve `400` senaryosu bulunmaz.

## Test Tasarımı

### Service unit testleri

- HR normal personeli pasifleştirir; çıkış tarihi dolar, tokenlar silinir ve audit oluşur.
- Admin HR personelini pasifleştirir.
- Admin yöneticiyi pasifleştirir; bağlı personellerin yöneticisi kaldırılır ve audit detayı doğru sayıyı içerir.
- HR yöneticiyi pasifleştiremez.
- `ADMIN` hedef pasifleştirilemez.
- HR ve Admin kendisini pasifleştiremez.
- Zaten pasif hedef hiçbir yan etki oluşturmaz.
- Olmayan hedef kontrollü `404` exception'ı üretir.

### HTTP ve MySQL integration testleri

- HR normal personeli pasifleştirir ve `204` alır.
- Admin yöneticiyi pasifleştirir; hedef, doğrudan çalışanlar, refresh token silme ve audit gerçek MySQL üzerinde kalıcılaşır.
- HR yönetici hedefinde `403` alır ve veri değişmez.
- Admin hedefinde `403` alınır.
- Öz-hedef isteği `403` döner.
- `PERSONEL` rolü endpoint'e erişemez.
- Zaten pasif hedef `204` döner; mevcut çıkış tarihi korunur ve audit/token yan etkisi oluşmaz.
- Olmayan hedef `404` döner.
- Dış transaction rollback olduğunda hedef aktifliği, çıkış tarihi, çalışan ilişkileri, refresh tokenlar ve audit birlikte geri alınır.

## Bağımlılık Sırası

1. `PERSONEL_PASIFE_ALINDI` audit enum değeri
2. Doğrudan çalışanları bulan repository metodu
3. `PersonelService.personelPasiflestir`
4. `HrPersonelController` endpoint'i
5. Service unit testleri
6. HTTP/MySQL integration testleri
7. API, Security, Roadmap ve Progress belge güncellemeleri

## Tamamlanma Ölçütleri

- Onaylanan rol matrisi backend'de uygulanır.
- Admin hedef ve öz-hedef pasifleştirme engellenir.
- Yönetici pasifleştirmesinde bütün doğrudan yönetici ilişkileri temizlenir.
- Hedefin refresh tokenları silinir.
- Audit aktör/hedef ilişkisi ve çalışan sayısı doğru kalıcılaşır.
- Zaten pasif hedef idempotent ve yan etkisizdir.
- Ana değişiklikler ve audit aynı transaction'da rollback olur.
- İlgili unit ve gerçek MySQL HTTP/integration testleri başarılıdır.
- Ana proje belgeleri yalnız uygulama doğrulandıktan sonra güncellenir.
