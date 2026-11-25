# Kendi Yeteneklerini Güncelleme Tasarımı

## Amaç

Aktif ve kimliği doğrulanmış personelin kendi yetenek listesini tek bir istekle
güncelleyebilmesini sağlamak.

Bu işlem yalnız JWT içindeki principal kimliğiyle bulunan personeli etkiler.
İstemciden personel kimliği alınmaz ve başka bir personelin yetenekleri bu
endpoint üzerinden değiştirilemez.

## API sözleşmesi

### Endpoint

```http
PUT /api/profil/yetenekler
```

### Request

```json
{
  "yetenekIdleri": [1, 3, 7]
}
```

`yetenekIdleri`, işlem tamamlandıktan sonra personelde bulunması istenen yetenek
ID'lerinin tamamıdır. İstek mevcut listeye ekleme yapmaz; mevcut listeyi verilen
listeyle değiştirir.

Boş liste geçerlidir:

```json
{
  "yetenekIdleri": []
}
```

Bu istek personelin bütün yeteneklerini kaldırır.

### Başarılı cevap

Gerçek bir değişiklik yapıldığında da istenen küme mevcut kümeyle aynı olduğunda
da:

```http
204 No Content
```

Response body dönülmez.

## Doğrulama ve hata davranışı

| Durum | HTTP sonucu | Davranış |
| --- | --- | --- |
| Authentication yok | `401 Unauthorized` | İşlem yapılmaz |
| Token sahibine ait personel bulunamaz | `404 Not Found` | `Personel bulunamadı.` |
| Personel pasif | `403 Forbidden` | İşlem yapılmaz |
| Request body eksik veya geçersiz | `400 Bad Request` | İşlem yapılmaz |
| `yetenekIdleri` alanı `null` | `400 Bad Request` | İşlem yapılmaz |
| Listede `null` eleman var | `400 Bad Request` | İşlem yapılmaz |
| Listede sıfır veya negatif ID var | `400 Bad Request` | İşlem yapılmaz |
| Aynı ID birden fazla gönderilmiş | `400 Bad Request` | İşlem yapılmaz |
| İstenen ID'lerden en az biri bulunamaz | `404 Not Found` | `Yetenek bulunamadı.`; hiçbir ilişki değiştirilmez |
| Boş liste | `204 No Content` | Mevcut bütün yetenekler kaldırılır |
| Mevcut kümeyle aynı ID'ler farklı sırada gönderilmiş | `204 No Content` | No-op; veritabanı yazımı ve audit oluşturulmaz |

MVP kapsamında personelin sahip olabileceği yetenek sayısı için ayrıca üst sınır
uygulanmaz.

## Yetkilendirme

Endpoint bütün aktif ve kimliği doğrulanmış roller tarafından kullanılabilir.
Servis seviyesinde `isAuthenticated()` kontrolü korunur.

Güncellenecek personel kimliği yalnız `Authentication#getName()` üzerinden
alınır. Request içinde personel ID'si bulunmaz.

Odaklı rol testleri `PERSONEL`, `HR` ve `YONETICI` rollerini kapsar. Ürün
davranışı bütün aktif roller için aynı olmasına rağmen, alınan karar gereği
`ADMIN` için ayrı bir rol senaryosu yazılmaz.

## Mimari

Mevcut profil akışı genişletilir:

- `ProfilController` içine `PUT /api/profil/yetenekler` endpoint'i eklenir.
- `ProfilService` içine yetenek güncelleme metodu eklenir.
- `YetenekUpdateRequest` alan doğrulamalarını taşır.
- `PersonelYetenekRepository`, mevcut ilişkileri okumak ve hesaplanan farkları
  silip eklemek için kullanılır.
- `YetenekRepository#findAllById`, istenen yeteneklerin gerçekten var olduğunu
  doğrulamak için kullanılır.

Yeni controller, yeni service veya bu işleme özel audit enum değeri oluşturulmaz.
Mevcut profil sorumluluğu ve `PROFIL_GUNCELLENDI` audit işlemi kullanılır.

## Güncelleme algoritması

Servis metodu tek transaction içinde aşağıdaki sırayı izler:

1. Request alanları doğrulanır.
2. JWT principal kimliğiyle personel bulunur.
3. Personelin aktif olduğu doğrulanır.
4. Tekrarlanan yetenek ID'si bulunmadığı doğrulanır.
5. İstenen yetenekler `YetenekRepository#findAllById` ile yüklenir.
6. Dönen yetenek sayısı benzersiz istek ID'si sayısından azsa
   `Yetenek bulunamadı.` hatası verilir.
7. Personelin mevcut `PersonelYetenek` ilişkileri yüklenir.
8. Mevcut ID kümesiyle istenen ID kümesi karşılaştırılır.
9. Kümeler aynıysa işlem no-op olarak `204` ile tamamlanır.
10. Mevcutta olup istekte olmayan ilişkiler silinecekler olarak belirlenir.
11. İstekte olup mevcutta olmayan yetenekler eklenecekler olarak belirlenir.
12. Yalnız hesaplanan farklar `deleteAll` ve `saveAll` ile uygulanır.
13. Audit kaydı oluşturulur.
14. Transaction başarıyla tamamlanır ve endpoint `204` döner.

Bu yaklaşım bütün ilişkileri silip yeniden eklemez. Değişmeyen ilişkiler korunur.
İstek listesinin sırası anlam taşımaz; karşılaştırma küme semantiğiyle yapılır.

## Atomiklik

Yetenek doğrulaması bütün yazma işlemlerinden önce tamamlanır. Tek bir bilinmeyen
yetenek ID'si bile varsa hiçbir ilişki eklenmez veya silinmez.

İlişki değişiklikleriyle audit kaydı aynı transaction içindedir. Audit
oluşturulurken ya da transaction tamamlanmadan önce hata oluşursa ilişki
değişiklikleri ve audit birlikte geri alınır.

## Audit

Gerçek bir değişiklikte:

- İşlem türü: `PROFIL_GUNCELLENDI`
- Actor: JWT principal personeli
- Target: JWT principal personeli
- Detay: `Güncellenen alanlar: yetenekler`

Audit detayına eski veya yeni yetenek adları ve ID'leri yazılmaz.

Mevcut yetenek kümesiyle istenen küme aynıysa audit oluşturulmaz.

## Test stratejisi

Akış, ayrı bir `ProfilYetenekControllerIntegrationTest` sınıfında gerçek HTTP,
security, JPA ve MySQL davranışıyla test edilir.

Temel senaryolar:

1. Mevcut yeteneklerin bir kısmını kaldırır, yenilerini ekler ve değişmeyen
   ilişkiyi korur.
2. Boş liste bütün yetenekleri kaldırır.
3. Aynı küme farklı sırada gönderildiğinde `204` döner, ilişki kümesi değişmez ve
   audit oluşmaz.
4. Bilinmeyen yetenek ID'si `404` döndürür; mevcut ilişkiler ve audit değişmez.
5. Tekrarlanan ID `400` döndürür.
6. `null` eleman `400` döndürür.
7. Sıfır ve negatif ID değerleri `400` döndürür.
8. Eksik, `null` veya geçersiz request body `400` döndürür.
9. Aktif `PERSONEL`, `HR` ve `YONETICI` kendi yeteneklerini güncelleyebilir.
10. Pasif principal `403` alır ve değişiklik yapılmaz.
11. Veritabanında karşılığı olmayan principal `404 Personel bulunamadı.` alır.
12. Authentication olmayan istek `401` alır.
13. Gerçek değişiklikte actor, target, işlem türü ve güvenli detay alanıyla audit
    oluşturulur.
14. Transaction sonradan hata aldığında yetenek ilişkileri ve audit birlikte geri
    alınır.

İlk RED aşamasında endpoint henüz bulunmadığı için kimliği doğrulanmış isteklerin
`404` dönmesi beklenir. Token sahibinin veritabanında bulunmadığı test, genel
route `404` cevabıyla yanlışlıkla başarılı olmaması için özellikle
`Personel bulunamadı.` mesajını da doğrular.

## Kapsam dışı

- Başka bir personelin yeteneklerini yönetmek
- Tekil yetenek ekleme ve silme endpoint'leri
- Yetenek tanımı oluşturma, yeniden adlandırma veya silme
- Yetenek seviyeleri
- Yetenek sayısı üst sınırı
- Response body içinde güncel profil veya yetenek listesini döndürme
