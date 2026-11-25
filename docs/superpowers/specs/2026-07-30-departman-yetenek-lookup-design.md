# Departman ve Yetenek Lookup Tasarımı

**Durum:** Onaylandı, henüz uygulanmadı.

## Amaç

Ekip dizini ve personel formlarının departman/yetenek ID’lerini frontend’de hardcode etmeden veritabanından alabilmesini sağlamak. Bu kapsam yalnız salt okunur lookup endpointlerini içerir; tanım yönetimi ve personele yetenek atama ayrı işlerdir.

## Endpoint Sözleşmesi

```text
GET /api/departments
Yetki: authenticated bütün roller
Request body/query parametresi: yok
Başarılı cevap: 200 OK + List<LookUpResponse>
```

```text
GET /api/skills
Yetki: authenticated bütün roller
Request body/query parametresi: yok
Başarılı cevap: 200 OK + List<LookUpResponse>
```

Her iki endpoint de sayfalamasız JSON listesi döner:

```json
[
  {
    "id": 1,
    "ad": "Bilgi Teknolojileri"
  },
  {
    "id": 2,
    "ad": "İnsan Kaynakları"
  }
]
```

Response yalnız mevcut `LookUpResponse(Integer id, String ad)` alanlarını içerir. Entity doğrudan API response’u yapılmaz.

## Onaylanan Ürün Kararları

- `ADMIN`, `HR`, `YONETICI` ve `PERSONEL` rolleri iki endpoint’e de erişebilir.
- JWT principal’daki personel her istekte veritabanından doğrulanır.
- Principal personeli bulunamazsa `404 Not Found` ve `Personel bulunamadı.` mesajı döner.
- Principal pasifse, stateless access token teknik olarak hâlâ geçerli olsa bile `403 Forbidden` döner.
- Token yoksa veya geçersizse mevcut Spring Security akışı `401 Unauthorized` döner.
- Veritabanındaki bütün departman ve yetenek tanımları listelenir; aktif personele henüz atanmamış kayıtlar gizlenmez.
- Sonuçlar ada göre artan alfabetik sırada, eşit ad durumunda ID ile kararlı biçimde sıralanır.
- Sayfalama uygulanmaz.
- Kayıt yoksa `200 OK` ve `[]` döner.
- Listeleme audit kaydı veya başka bir persistence yan etkisi oluşturmaz.

## Mimari

```text
GET /api/departments ─┐
                      ├─ LookupController
GET /api/skills ──────┘
                             ↓
                       LookupService
                       ├─ aktif principal kontrolü
                       ├─ entity → LookUpResponse
                       └─ read-only transaction
                         ↓                 ↓
             DepartmanRepository    YetenekRepository
```

Ortak `LookupController` ve `LookupService` tercih edilir. İki endpoint aynı güvenlik, response, sıralama ve yan etkisizlik davranışını paylaşır; ayrı controller/service çiftleri bu küçük kapsamda gereksiz tekrar oluşturur.

`LookupService`, principal doğrulamasını kendi private metodu içinde yapar. `EkipDiziniService` veya diğer mevcut servisler bu görev sırasında ortak bir güvenlik bileşenine taşınmaz; ilgisiz refactor yapılmaz.

## Bileşen Sorumlulukları

### LookupController

- `/api/departments` ve `/api/skills` GET isteklerini karşılar.
- Personel kimliğini yalnız `Authentication.getName()` üzerinden alır.
- İş kuralı veya repository erişimi içermez.
- Service sonucunu `200 OK` ile döner.

### LookupService

- `@PreAuthorize("isAuthenticated()")` ile authenticated erişimi belirtir.
- `@Transactional(readOnly = true)` transaction sınırını taşır.
- Principal personelin varlığını ve aktifliğini doğrular.
- İlgili repository listesini çağırır.
- `Departman` ve `Yetenek` entity’lerini `LookUpResponse` listesine dönüştürür.
- Audit servisi çağırmaz.

### DepartmanRepository

Mevcut repository korunur ve departman adı, ardından departman ID ile artan sıralı listeleme metodu eklenir.

### YetenekRepository

`JpaRepository<Yetenek, Integer>` tabanlı yeni repository oluşturulur. Yetenek adı, ardından yetenek ID ile artan sıralı listeleme sağlar.

## Veri Akışı

Her başarılı istek şu sırayla işlenir:

1. JWT filter access token’ı doğrular ve principal kimliğini kurar.
2. Controller `Authentication.getName()` değerini service’e aktarır.
3. Service principal personeli veritabanından bulur.
4. Service personelin aktif olduğunu doğrular.
5. İlgili repository bütün tanımları alfabetik getirir.
6. Service entity listesini `LookUpResponse` listesine dönüştürür.
7. Controller `200 OK` döner.

Normal başarılı istekte iki veri sorgusu bulunur: principal doğrulama sorgusu ve lookup listeleme sorgusu. Entity ilişkisi dolaşılmadığından N+1 sorgu riski yoktur.

## Hata ve Yan Etki Davranışı

| Durum | Sonuç |
|---|---|
| Aktif principal ve geçerli JWT | `200 OK` |
| Lookup tablosunda kayıt yok | `200 OK` + `[]` |
| JWT principal personeli bulunamıyor | `404 Not Found` |
| JWT principal personeli pasif | `403 Forbidden` |
| Token yok veya geçersiz | `401 Unauthorized` |

Framework veya veritabanı exception ayrıntıları response’a eklenmez. GET işlemleri personel, departman, yetenek veya audit verisini değiştirmez.

## TDD ve Test Stratejisi

Yeni `LookupControllerIntegrationTest`, mevcut `AbstractIntegrationTest`, MockMvc, Spring Security ve gerçek MySQL 8 Testcontainers altyapısını kullanır.

Önce production endpointleri eklenmeden testler yazılır. Authenticated isteklerin endpoint bulunmadığı için genel kaynak mesajıyla `404` dönmesi doğru RED olarak doğrulanır. Eksik principal testi `Personel bulunamadı.` mesajını ayrıca kontrol ederek genel route `404` cevabının yanlışlıkla testi geçmesini engeller.

Doğrulanacak senaryolar:

- Departmanlar alfabetik ve kararlı sıralanır.
- Aktif personele atanmamış departman da listelenir.
- Yetenekler alfabetik ve kararlı sıralanır.
- Hiçbir personele atanmamış yetenek de listelenir.
- Yetenek tablosu boşsa `200 OK` ve `[]` döner.
- Response elemanlarında yalnız `id` ve `ad` bulunur.
- Dört rol iki endpoint’e de erişebilir.
- Token olmayan istekler iki endpoint için `401` döner.
- JWT principal personeli bulunamazsa iki endpoint için kontrollü `404` döner.
- Pasif principal iki endpoint için `403` alır.
- Listeleme audit kaydı oluşturmaz.

Geçerli bir `Personel` kaydının departman ilişkisi zorunlu olduğundan, başarılı authenticated HTTP isteği sırasında departman tablosunun tamamen boş olması veritabanı değişmezleriyle oluşturulamaz. Service boş repository listesini doğal olarak `[]` biçiminde map eder; gerçek boş liste integration senaryosu yetenek endpointinde doğrulanır.

## Kapsam Dışı

- `POST`, `PUT`, `PATCH` veya `DELETE` departman yönetimi
- `POST`, `PUT`, `PATCH` veya `DELETE` yetenek yönetimi
- Personele yetenek atama veya personelden yetenek çıkarma
- Lookup sonuçlarında arama veya pagination
- Ekip dizini frontend bağlantısı
- Mevcut ekip dizini, profil veya personel servislerini refactor etme
- Yeni audit olayı ekleme

## Tamamlanma Ölçütleri

- İki GET endpoint’i onaylanan response ve hata sözleşmesini uygular.
- Yalnız aktif ve veritabanında bulunan authenticated principal erişebilir.
- Bütün tanımlar alfabetik ve sayfalamasız döner.
- Mevcut `LookUpResponse` kullanılır; entity API’ye açılmaz.
- Listeleme salt okunur ve audit yan etkisizdir.
- RED sonucu doğru nedenle görülür, ardından odaklı testler GREEN olur.
- Tam backend test paketi sıfır failure/error ile tamamlanır.
- Doğrulamadan sonra `API_SPEC.md`, `ROADMAP.md`, `PROGRESS.md` ve gerekirse `SECURITY.md` uygulanan durumla eşitlenir.
