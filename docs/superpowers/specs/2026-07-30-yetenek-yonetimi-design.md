# Yetenek Yönetimi Tasarımı

## Amaç

İnsan kaynakları ve yöneticilerin kullandığı yetenek kataloğunun yalnız yetkili
roller tarafından kontrollü biçimde genişletilebilmesini ve mevcut yetenek
adlarının personel ilişkileri bozulmadan düzeltilebilmesini sağlamak.

Bu özellik yetenek oluşturma ve yeniden adlandırma işlemleriyle sınırlıdır.
Yetenek silme, pasifleştirme veya yeniden aktifleştirme ayrı bir tasarımın
konusudur.

## Yetkilendirme

Yönetim endpoint'leri yalnız aktif `HR` ve `ADMIN` rollerine açıktır.

- Method security kuralı `hasAnyRole('HR', 'ADMIN')` olur.
- Aktör kimliği yalnız JWT principal'dan alınır.
- Request içinde aktör veya personel kimliği bulunmaz.
- JWT principal personeli veritabanında bulunamazsa `404 Personel bulunamadı.`
  döner.
- Principal pasifse kalan stateless access-token ömründe dahi `403 Forbidden`
  döner.
- `PERSONEL` ve `YONETICI` rolleri `403`, authentication olmayan istek `401`
  alır.

## API sözleşmesi

### Yetenek oluşturma

```http
POST /api/skills
Content-Type: application/json
```

```json
{
  "yetenekAdi": "  Node.js  "
}
```

Başarılı cevap:

```http
201 Created
```

```json
{
  "id": 12,
  "ad": "Node.js"
}
```

Response için mevcut `LookUpResponse(id, ad)` sözleşmesi kullanılır. Ayrı bir
response DTO oluşturulmaz.

### Yetenek güncelleme

```http
PATCH /api/skills/{yetenekId}
Content-Type: application/json
```

```json
{
  "yetenekAdi": "Node.js Backend"
}
```

Gerçek değişiklikte ve no-op istekte:

```http
204 No Content
```

Response body dönülmez.

## Request DTO

Mevcut fakat kullanılmayan `YetenekCreateRequest`, iki endpoint'in ortak
request sözleşmesini daha doğru anlatması için `YetenekAdiRequest` olarak
yeniden adlandırılır.

DTO yalnız istemcinin belirleyebildiği `yetenekAdi` alanını taşır. Alan `null`,
boş veya yalnız boşluk olamaz. Normalize edilmiş ad 1–50 karakter aralığında
olmalıdır.

## Ad normalizasyonu ve duplicate davranışı

Yetenek adı kaydedilmeden önce `trim()` ile temizlenir:

```text
"  Node.js  " -> "Node.js"
```

Kullanıcının yazdığı harf biçimi korunur. Ad zorla küçük harfe, büyük harfe
veya baş harfleri büyük biçime dönüştürülmez; `Node.js`, `C#` ve `AWS` gibi
teknoloji adları bozulmaz.

Duplicate kontrolü büyük/küçük harf duyarsızdır:

```text
Java
JAVA
 java
```

Bu değerler duplicate kontrolünde aynı kabul edilir. Oluşturmada aynı
normalize edilmiş ada sahip kayıt varsa:

```http
409 Conflict
```

```text
Yetenek zaten mevcut.
```

Mevcut kayıt başarılı oluşturma sonucu gibi geri döndürülmez.

Güncellemede:

- Başka bir yetenek aynı adı büyük/küçük harf duyarsız biçimde kullanıyorsa
  `409 Conflict` döner.
- Mevcut adla normalize edilmiş yeni ad tam olarak aynıysa işlem no-op olur;
  `204` döner, persistence ve audit yan etkisi oluşmaz.
- Yalnız harf biçimi değişiyorsa (`java -> Java`) gerçek güncelleme yapılır ve
  audit oluşturulur.

## Mimari

Mevcut lookup okuma akışı değiştirilmez:

```text
LookupController / LookupService
└── GET /api/skills
```

Yönetim yazmaları ayrı sorumlulukta tutulur:

```text
YetenekYonetimController
├── POST  /api/skills
└── PATCH /api/skills/{yetenekId}

YetenekYonetimService
├── HR/Admin method-security
├── aktif aktör doğrulaması
├── normalizasyon ve duplicate kuralları
├── persistence ve transaction
└── audit
```

Aynı URL'nin farklı HTTP metotlarının ayrı controller sınıflarında bulunması
Spring MVC için geçerlidir.

Yeni yönetim service'i mevcut `YetenekRepository`, `PersonelRepository` ve
`AuditService` bileşenlerini kullanır. Çalışan lookup GET endpoint'i ve
`LookupService` yazma sorumluluğu almaz.

## Repository davranışı

`YetenekRepository` aşağıdaki case-insensitive kontrollerle genişletilir:

- Oluşturma için başka bir kayıt aynı adı kullanıyor mu?
- Güncelleme için hedef ID dışındaki bir kayıt aynı adı kullanıyor mu?

Servis ön kontrolüne rağmen iki eşzamanlı istek aynı adı oluşturmaya veya
atamaya çalışabilir. Veritabanındaki mevcut unique constraint son savunma
olarak korunur.

`saveAndFlush` ile unique constraint hatası transaction tamamlanmadan görünür
hale getirilir. `DataIntegrityViolationException`, SQL veya constraint ayrıntısı
istemciye sızdırılmadan kontrollü `409 Yetenek zaten mevcut.` hatasına çevrilir.
Hatalı transaction içindeki yetenek ve audit değişiklikleri rollback olur.

## Oluşturma veri akışı

1. Method security isteğin `HR` veya `ADMIN` rolünden geldiğini doğrular.
2. JWT principal personeli yüklenir ve aktif olduğu doğrulanır.
3. Request adı trim edilir ve normalize edilmiş uzunluğu doğrulanır.
4. Case-insensitive duplicate ön kontrolü yapılır.
5. Yeni `Yetenek` oluşturulur ve `saveAndFlush` ile kaydedilir.
6. `YETENEK_EKLENDI` audit kaydı aynı transaction içinde oluşturulur.
7. Transaction commit olur ve `201 Created` ile `LookUpResponse` döner.

## Güncelleme veri akışı

1. Method security isteğin `HR` veya `ADMIN` rolünden geldiğini doğrular.
2. JWT principal personeli yüklenir ve aktif olduğu doğrulanır.
3. Path ID'nin pozitif olduğu doğrulanır.
4. Hedef yetenek yüklenir; yoksa `404 Yetenek bulunamadı.` döner.
5. Request adı trim edilir ve normalize edilmiş uzunluğu doğrulanır.
6. Normalize edilmiş ad mevcut adla tam olarak aynıysa no-op olarak `204`
   dönülür.
7. Hedef ID dışındaki kayıtlar için case-insensitive duplicate kontrolü
   yapılır.
8. Aynı `Yetenek` entity'sinin adı değiştirilir ve `saveAndFlush` çağrılır.
9. `YETENEK_GUNCELLENDI` audit kaydı aynı transaction içinde oluşturulur.
10. Transaction commit olur ve `204 No Content` döner.

Entity ID'si değişmediği için mevcut `PersonelYetenek` ilişkileri korunur.
Yeteneğe sahip personeller yeni adı otomatik olarak görür.

## Hata sözleşmesi

| Durum | HTTP sonucu | Mesaj/davranış |
| --- | --- | --- |
| Authentication yok | `401 Unauthorized` | İşlem yapılmaz |
| Rol `PERSONEL` veya `YONETICI` | `403 Forbidden` | İşlem yapılmaz |
| JWT principal personeli yok | `404 Not Found` | `Personel bulunamadı.` |
| Principal pasif | `403 Forbidden` | İşlem yapılmaz |
| Body veya `yetenekAdi` eksik/null/boş | `400 Bad Request` | İşlem yapılmaz |
| Normalize edilmiş ad 50 karakterden uzun | `400 Bad Request` | İşlem yapılmaz |
| Path ID metin, sıfır veya negatif | `400 Bad Request` | İşlem yapılmaz |
| Güncellenecek yetenek yok | `404 Not Found` | `Yetenek bulunamadı.` |
| Duplicate yetenek adı | `409 Conflict` | `Yetenek zaten mevcut.` |
| Oluşturma başarılı | `201 Created` | `{ id, ad }` |
| Güncelleme başarılı | `204 No Content` | Response body yok |
| Güncelleme no-op | `204 No Content` | Yazma ve audit yok |

## Audit

`AuditEylem` enum'una iki yeni olay eklenir:

```text
YETENEK_EKLENDI
YETENEK_GUNCELLENDI
```

Oluşturma:

```text
Actor: işlemi yapan HR/Admin
Target personel: null
Detay: Yetenek oluşturuldu: Java
```

Güncelleme:

```text
Actor: işlemi yapan HR/Admin
Target personel: null
Detay: Yetenek güncellendi: java -> Java
```

Yetenek adları kişisel veya hassas veri olmadığı ve katalog değişikliğini
izlemek için gerekli olduğu için eski/yeni ad audit detayında tutulabilir.
No-op güncellemede audit oluşturulmaz.

Yetenek yazımıyla audit aynı transaction'a katılır; dış hata durumunda birlikte
rollback olur.

## Test stratejisi

Yeni `YetenekYonetimControllerIntegrationTest`, mevcut
`AbstractIntegrationTest`, MockMvc ve gerçek MySQL 8 Testcontainers altyapısını
kullanır.

Integration senaryoları:

1. Aktif HR ve Admin yetenek oluşturabilir.
2. Başarılı oluşturma adı trim ederek saklar ve `201` ile doğru `{ id, ad }`
   döner.
3. Aktif HR ve Admin yeteneği güncelleyebilir.
4. Rename aynı yetenek ID'sini ve mevcut `PersonelYetenek` ilişkilerini korur.
5. `GET /api/skills` güncel adı mevcut alfabetik sözleşmeyle gösterir.
6. `PERSONEL` ve `YONETICI` yönetim endpoint'lerinde `403` alır.
7. Authentication olmayan istek `401` alır.
8. Pasif HR/Admin `403`, bulunamayan principal `404 Personel bulunamadı.` alır.
9. Eksik, `null`, boş, yalnız boşluk, fazla uzun veya okunamayan request `400`
   alır.
10. Exact, farklı harf biçimli ve çevresinde boşluk bulunan duplicate create
    istekleri `409` alır.
11. Başka kaydın adına duplicate olacak güncelleme `409` alır; hedef adı ve
    audit değişmez.
12. Metin, sıfır ve negatif path ID `400`; bulunamayan pozitif ID `404` alır.
13. Yalnız harf biçimi değişikliği gerçek güncelleme ve audit oluşturur.
14. Tam no-op `204` döner; persistence ve audit yan etkisi oluşturmaz.
15. Oluşturma ve güncelleme audit kayıtları doğru eylem, aktör, null hedef ve
    detayla saklanır.
16. Dış transaction hatasında yetenek oluşturma/güncelleme ve audit birlikte
    rollback olur.

Repository duplicate ön kontrolünden sonra oluşabilecek eşzamanlı unique
constraint hatasını deterministik biçimde doğrulamak için küçük bir
`YetenekYonetimServiceTest` eklenir. Mockito ile ön kontrolün boş döndüğü,
`saveAndFlush` çağrısının `DataIntegrityViolationException` fırlattığı durumda
service'in kontrollü `KaynakCakismasiException` ürettiği ve audit yazmadığı
doğrulanır.

Önce integration testleri endpoint bulunmadığı için beklenen RED sonucunu
verir. Ardından en küçük production koduyla GREEN yapılır; odaklı testlerden
sonra bütün backend suite çalıştırılır.

## Kapsam dışı

- Yetenek silme
- Yetenek pasifleştirme veya yeniden aktifleştirme
- Pasif yeteneğin lookup ve mevcut profillerde nasıl gösterileceği
- Personel-yetenek ilişkilerini toplu kaldırma
- Yetenek seviyesi veya uzmanlık derecesi
- Departman yönetimi
- Mevcut lookup GET akışını başka controller/service'e taşıma
- Entity veya tablo şeması değişikliği
