# Departman Yönetimi Tasarımı

**Tarih:** 03.08.2026  
**Durum:** Kullanıcı tarafından onaylandı

## 1. Amaç

Mevcut departman lookup sözleşmesini değiştirmeden aktif HR ve Admin kullanıcıların departman oluşturabilmesini ve mevcut departmanın adını aynı kayıt üzerinde güncelleyebilmesini sağlamak.

## 2. Kapsam

- `POST /api/departments` ile departman oluşturma,
- `PATCH /api/departments/{departmanId}` ile departman adını güncelleme,
- trim ve uzunluk doğrulaması,
- case-insensitive duplicate kontrolü,
- database unique constraint yarışı için kontrollü `409`,
- aktif aktör doğrulaması ve rol yetkisi,
- güvenli audit ve transaction/rollback davranışı,
- integration ve gerekli unit testleri,
- API, roadmap, progress ve security belgelerinin doğrulanmış sonuçlarla güncellenmesi.

## 3. Kapsam dışı

- Departman silme veya pasifleştirme,
- departman birleştirme,
- personelleri toplu biçimde başka departmana taşıma,
- mevcut `GET /api/departments` lookup sözleşmesini değiştirme,
- frontend implementasyonu,
- Git işlemleri.

Backend Java ve test kodlarını kullanıcı uygular; agent yalnız kopyalanabilir kod blokları sağlar. Markdown belgeleri kullanıcının yazma onayı kapsamında agent tarafından güncellenebilir.

## 4. Mimari

Mevcut `LookupController` ve `LookupService`, tüm authenticated rollerin kullandığı salt-okunur `GET /api/departments` akışını korur.

Yeni mutation akışı:

```text
DepartmanYonetimController
        ↓
DepartmanYonetimService
        ↓
DepartmanRepository
```

Departman mutation'ları yeni `DepartmanYonetimController` ve `DepartmanYonetimService` sınıflarında tutulur. Böylece genel lookup sorumluluğu ile HR/Admin'e özel transaction ve audit sorumluluğu karışmaz. Departman ve yetenek için genel bir katalog abstraction'ı oluşturulmaz; mevcut iki use-case kendi açık sınıflarını korur.

## 5. Endpoint sözleşmeleri

### 5.1 Departman oluşturma

```http
POST /api/departments
Content-Type: application/json
```

Request:

```json
{
  "departmanAdi": "İnsan Kaynakları"
}
```

Başarılı response:

```http
201 Created
```

```json
{
  "id": 2,
  "ad": "İnsan Kaynakları"
}
```

Response için mevcut `LookUpResponse` kullanılır.

### 5.2 Departman adını güncelleme

```http
PATCH /api/departments/{departmanId}
Content-Type: application/json
```

Request aynı `DepartmanAdiRequest` DTO'sunu kullanır. Başarılı gerçek güncelleme ve no-op istek `204 No Content` döner.

Departman silinip yeniden oluşturulmaz. Aynı entity ve aynı `departmanId` üzerinde ad değiştirilir; bağlı personel ilişkileri korunur.

## 6. Normalizasyon ve duplicate semantiği

- `departmanAdi` baş ve son boşluklardan temizlenir.
- Normalize ad `1–50` karakter olmalıdır.
- Null, boş veya yalnız boşluk içeren değer `400 Geçersiz istek` döner.
- Oluşturmada case-insensitive mevcut ad `409 Departman zaten mevcut.` döner.
- Güncellemede başka departmana case-insensitive çakışma `409 Departman zaten mevcut.` döner.
- Aynı normalize metnin tekrar gönderilmesi no-op'tur: `204`, persistence ve audit yan etkisi yoktur.
- Yalnız harf biçimi değişikliği gerçek güncellemedir. Örneğin `İnsan Kaynakları` → `İNSAN KAYNAKLARI` aynı ID üzerinde güncellenir ve audit oluşturur.
- Veritabanı unique constraint'i korunur. Ön kontrolden sonra oluşabilecek eşzamanlı unique yarışındaki `DataIntegrityViolationException`, SQL ayrıntısı sızdırılmadan aynı kontrollü `409` cevabına çevrilir.

## 7. Yetkilendirme ve aktif aktör

İki mutation endpoint'i yalnız `HR` ve `ADMIN` authority'lerine açıktır:

```java
@PreAuthorize("hasAnyRole('HR', 'ADMIN')")
```

Service, JWT principal ID'sini veritabanından yeniden yükler:

- principal bulunamazsa `404 Personel bulunamadı.`,
- principal pasifse `403`,
- `PERSONEL` ve `YONETICI` method security tarafından `403`,
- token yoksa `401`.

Request aktör ID'si veya audit alanı taşımaz.

## 8. Hata davranışı

| Durum | HTTP ve mesaj |
| --- | --- |
| Token yok/geçersiz | `401` |
| Aktör rolü HR/Admin değil | `403` |
| Aktör pasif | `403` |
| Aktör bulunamadı | `404 Personel bulunamadı.` |
| Null/boş/geçersiz ad | `400 Geçersiz istek` |
| Pozitif olmayan departman ID | `400` |
| Departman bulunamadı | `404 Departman bulunamadı.` |
| Duplicate ad veya unique yarışı | `409 Departman zaten mevcut.` |

Ham exception, SQL veya constraint adı response'a yazılmaz.

## 9. Audit ve transaction

`AuditEylem` enum'una şunlar eklenir:

- `DEPARTMAN_EKLENDI`
- `DEPARTMAN_GUNCELLENDI`

Oluşturma audit detayı yalnız normalize departman adını içerir. Güncelleme detayı eski ve yeni departman adını içerir. Audit hedef personeli `null` olur; aktör JWT principal personelidir.

Departman değişikliği ve audit aynı `@Transactional` işlem içinde `logKaydetOrtakTransaction` ile tamamlanır veya birlikte rollback olur. No-op güncelleme audit üretmez.

## 10. Repository genişletmesi

`DepartmanRepository` şu türetilmiş sorgularla genişletilir:

```java
boolean existsByDepartmanAdiIgnoreCase(String departmanAdi);

boolean existsByDepartmanAdiIgnoreCaseAndDepartmanIdNot(
        String departmanAdi,
        Integer departmanId
);
```

Mevcut alfabetik lookup sorgusu değiştirilmez.

## 11. Test tasarımı

Gerçek MySQL 8 Testcontainers integration testleri şunları doğrular:

- HR ve Admin oluşturma/güncelleme başarısı,
- trim edilmiş response ve kayıt,
- aynı ID ile rename ve personel–departman ilişkisinin korunması,
- exact no-op ve case-only gerçek güncelleme,
- null, boş, blank ve 50 karakter sınırı,
- pozitif olmayan veya bulunmayan ID,
- case-insensitive duplicate oluşturma/güncelleme,
- token'sız, yasak rol, pasif ve bulunamayan principal,
- güvenli audit içeriği,
- outer transaction rollback davranışı,
- unique constraint yarışı fallback'inin kontrollü `409` dönüşümü.

Odaklı testlerden sonra mevcut lookup testi ve tam backend suite'i çalıştırılır.

## 12. Beklenen dosya kapsamı

Yeni production dosyaları:

- `model/dto/request/DepartmanAdiRequest.java`
- `controller/DepartmanYonetimController.java`
- `service/DepartmanYonetimService.java`

Değişecek production dosyaları:

- `repository/DepartmanRepository.java`
- `model/enums/AuditEylem.java`

Yeni test dosyaları:

- `DepartmanYonetimControllerIntegrationTest.java`
- `DepartmanYonetimServiceTest.java`

Doğrulama sonrasında güncellenecek belgeler:

- `API_SPEC.md`
- `ROADMAP.md`
- `PROGRESS.md`
- `SECURITY.md`

## 13. Kabul ölçütleri

- Aktif HR/Admin departman oluşturabilir ve aynı ID üzerinde yeniden adlandırabilir.
- Lookup endpoint'i mevcut sözleşmesini korur ve güncel adları alfabetik döndürür.
- Duplicate, validation, principal ve rol hataları kontrollü status'lara dönüşür.
- No-op yan etkisizdir; gerçek değişiklik güvenli audit ile aynı transaction'dadır.
- Bağlı personel ilişkileri rename sonrasında korunur.
- Odaklı ve tam backend testleri `0 failure`, `0 error` ile tamamlanır.
