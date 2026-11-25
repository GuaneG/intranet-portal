# Admin Personel Yetenek Yönetimi Tasarımı

## 1. Amaç

Aktif Admin’in, yetkili olduğu başka bir aktif personelin bütün yetenek kümesini güvenli biçimde değiştirebilmesini sağlamak. Mevcut self-service `PUT /api/profil/yetenekler` davranışı ve yetkisi değişmeden korunur.

Yeni endpoint:

```http
PUT /api/hr/personel/{personelId}/yetenekler
Content-Type: application/json

{
  "yetenekIdleri": [1, 3, 7]
}
```

Başarılı gerçek değişiklik ve no-op `204 No Content` döner.

## 2. Katmanlar ve Sorumluluklar

### Controller

`HrPersonelController` yalnız:

- path’teki `personelId` değerini,
- `@Valid YetenekUpdateRequest` gövdesini,
- `Authentication.getName()` ile JWT principal kimliğini,
- service çağrısını,
- `204 No Content` response’unu

yönetir. Controller repository kullanmaz ve iş kuralı uygulamaz.

### Service

`ProfilService` iki ayrı public use-case taşır:

- mevcut `profilYeteneklerimiGuncelle`: authenticated personelin kendi yetenekleri,
- yeni Admin hedefli personel yetenek güncellemesi: aktör ve hedef farklı olabilir.

Mevcut yetenek kümesi doğrulama ve fark uygulama algoritması tek private ortak metoda ayrılır. Böylece iki endpoint:

- duplicate ve bilinmeyen ID kontrolünü,
- set karşılaştırmasını,
- yalnız eklenen/kaldırılan ilişkileri yazmayı,
- no-op kararını

aynı uygulamadan kullanır. Yeni bağımsız abstraction veya repository sorgusu eklenmez.

### Yetki servisi

`PersonelYonetimYetkiService` aktif Admin aktörünü veritabanından doğrulayan ve Admin yetenek hedefini kontrol eden küçük, açık metotlar taşır.

JWT’deki rol tek başına güvenilir kabul edilmez. Service method security’ye ek olarak DB’deki aktör:

- mevcut,
- aktif,
- `ADMIN`

olmalıdır.

## 3. Yetki ve Hedef Matrisi

| Aktör/hedef durumu | Sonuç |
|---|---:|
| Aktif Admin → aktif PERSONEL | izinli |
| Aktif Admin → aktif HR | izinli |
| Aktif Admin → aktif YONETICI | izinli |
| Admin → kendisi | `403` |
| Admin → başka ADMIN | `403` |
| Admin → pasif PERSONEL/HR/YONETICI | `403` |
| HR/YONETICI/PERSONEL aktör | `403` |
| Pasif Admin aktör | `403` |
| Aktör bulunamadı | `404 Personel bulunamadı.` |
| Hedef bulunamadı | `404 Personel bulunamadı.` |
| Token yok/geçersiz | `401` |

Öz-hedef, hedef rolü ve hedef aktifliği kontrolleri istenen kümenin mevcut kümeyle aynı olup olmadığına bakılmadan önce çalışır. Böylece yasak hedefler no-op üzerinden yetki kontrolünü atlayamaz.

Aktör yalnız JWT principal’dan, hedef yalnız path parametresinden belirlenir. Request aktör, hedef, rol veya audit alanı taşımaz.

## 4. Request ve Validation

Mevcut `YetenekUpdateRequest` yeniden kullanılır:

```json
{
  "yetenekIdleri": [1, 3, 7]
}
```

Kurallar:

- `yetenekIdleri` null olamaz.
- Liste elemanı null olamaz.
- ID pozitif olmalıdır; sıfır ve negatif değer `400` döner.
- Tekrarlanan ID `400` döner.
- Sıra anlamsızdır.
- Boş liste hedefin bütün yeteneklerini kaldırır.
- İstenen ID’lerden biri bulunamazsa `404 Yetenek bulunamadı.` döner.

Bütün ID’ler ilişki değişikliğinden önce doğrulanır. Bilinmeyen tek ID bile bütün isteği reddeder; mevcut ilişkiler ve audit değişmez.

## 5. Küme Güncelleme Davranışı

Service mevcut ilişkilerden `mevcutIdler`, request’ten `istenenIdler` kümelerini üretir.

1. Yetki ve hedef kontrolleri tamamlanır.
2. Duplicate ve istenen kaynak varlığı doğrulanır.
3. Mevcut hedef ilişkileri okunur.
4. Kümeler eşitse işlem no-op olarak döner.
5. `mevcut - istenen` ilişkileri silinir.
6. `istenen - mevcut` ilişkileri eklenir.
7. Gerçek değişiklik audit edilir.

Entity response yapılmaz. Endpoint body döndürmez.

## 6. Transaction ve Audit

Admin use-case’i tek `@Transactional` service metodu içinde çalışır.

Gerçek değişiklikte:

- audit eylemi: `PROFIL_GUNCELLENDI`,
- aktör: JWT principal’dan yüklenen Admin,
- hedef: path’ten yüklenen personel,
- kullanıcı adı: Admin’in kullanıcı adı,
- detay: `Güncellenen alanlar: yetenekler`

olur.

Audit detayı yetenek adı/ID, token, parola veya başka hassas değer içermez. `AuditService.logKaydetOrtakTransaction` kullanılır; personel-yetenek ilişkileri ve audit birlikte commit veya rollback olur.

No-op işlem persistence ve audit yan etkisi oluşturmaz.

## 7. Method Security

Yeni public service use-case’i `@PreAuthorize("hasRole('ADMIN')")` ile korunur. Bu katman HR, Yönetici ve Personel rollerini erkenden reddeder. Bunun yanında service, principal personelini DB’den yeniden yükleyip gerçek rolün `ADMIN` ve aktörün aktif olduğunu doğrular.

Mevcut `PUT /api/profil/yetenekler` üzerindeki `isAuthenticated()` ve self-service kuralları değişmez.

## 8. Test Tasarımı

Yeni `AdminPersonelYetenekControllerIntegrationTest` gerçek Spring Security, MySQL 8, JPA ve audit persistence davranışını kapsar.

TDD döngüleri:

1. Aktif Admin’in PERSONEL/HR/YONETICI hedeflerinde ekleme, kaldırma ve boş listeyle temizleme davranışı.
2. HR/YONETICI/PERSONEL için `403`; pasif/missing Admin, missing hedef ve tokensız istek.
3. Öz-hedef, başka Admin ve pasif hedefin aynı kümede bile no-op öncesi `403` alması.
4. Null liste, null eleman, sıfır/negatif, duplicate ve bilinmeyen ID validation’ı; bilinmeyen ID’de atomiklik.
5. Aynı kümenin farklı sırada no-op olması ve audit üretmemesi.
6. Gerçek değişiklikte doğru aktör, hedef, güvenli detay ve tek audit.
7. Dış transaction rollback’inde ilişki ve auditin birlikte geri alınması.
8. Mevcut self-service profil yetenekleri, personel yönetimi, ekip dizini ve security regresyonları.

Her production davranışından önce ilgili test doğru nedenle RED görülür. Docker/Testcontainers entegrasyon koşularını kullanıcı yerel ortamında çalıştırır; agent Docker gerektirmeyen derleme ve unit kontrollerini yapar.

## 9. Dokümantasyon

Kod ve testler tamamlandıktan sonra kullanıcıdan yeniden açık onay alınarak yalnız:

- `API_SPEC.md`,
- `SECURITY.md`,
- `PROGRESS.md`

güncellenir. Ürün belgeleri implementasyon tamamlanmadan uygulanmış davranış gibi değiştirilmez.

Git komutu çalıştırılmaz.
