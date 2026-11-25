# Tatil Takvimi ve İş Günü Hesaplama Tasarımı

Tarih: 2026-08-22  
Durum: Tasarım onaylandı; implementasyon bekliyor

## 1. Amaç ve kapsam

Bu teslim, izin yönetiminin ihtiyaç duyduğu şirket genelindeki tatil takvimini ve ortak iş günü hesaplama altyapısını kurar. HR ve ADMIN tatil kayıtlarını yönetir; tüm aktif roller takvimi okuyabilir; aktif PERSONEL ve HR ise izin talebi oluşturmadan önce tarih aralığının iş günü karşılığını backend üzerinden önizleyebilir.

Kapsama dahil olanlar:

- Şirket genelinde tek tatil takvimi ve HR/ADMIN CRUD akışı
- Tam gün ve yarım gün tatil modeli
- Hafta sonu ve tatilleri dikkate alan ortak hesaplama servisi
- PERSONEL/HR için salt-okunur iş günü önizleme endpoint'i
- İzin iş günü alanlarının `BigDecimal` yapısına geçirilmesi
- Yetki, transaction, audit, yarış durumu ve regresyon testleri

Kapsam dışı olanlar:

- Hizmet yılına göre 14/20/26 gün izin hakkı tahakkuku ve geçmiş veri doldurma
- Gerçek izin talebi oluşturma/listeleme
- Yönetici onay/red akışı
- Frontend değişiklikleri
- Otomatik resmi tatil veya dış servis entegrasyonu

## 2. Onaylanan ürün kararları

- Tatiller HR/ADMIN tarafından elle yönetilir; otomatik başlangıç verisi yoktur.
- Takvim bütün şirket için tektir; departman veya personel bazlı takvim yoktur.
- Geçmiş kayıtlar değiştirilemez ve silinemez. Bugün ve gelecek yönetilebilir.
- Bir tarihte en fazla bir tatil kaydı bulunur.
- Tarih aralığı başlangıç ve bitiş günleri dahil hesaplanır.
- Pazartesi-cuma normal gün `1.0`, yarım gün tatil `0.5`, tam gün tatil ve hafta sonu `0.0` katkı verir.
- Hafta sonundaki tatil görüntülenir ancak gün zaten `0.0` olduğu için toplamı değiştirmez.
- `YARIM_GUN` yalnız tatil kaydının süresidir; bu teslim kullanıcıya yarım günlük izin seçme davranışı eklemez.
- Önizleme sonucu sıfırsa `200 OK` ve `0.0` döner.
- Gerçek izin talebi daha sonra eklendiğinde aynı hesap sıfırsa güvenli `400 Bad Request` ile reddedilir.
- Frontend iş gününü tekrar hesaplamaz; backend önizleme endpoint'ini kullanır.

## 3. Veri modeli

Yeni `Tatil` entity'si:

- `tatilId`
- `ad`
- `tarih` — unique constraint ile benzersiz
- `kategori` — `RESMI_SABIT | RESMI_DEGISKEN | SIRKET`
- `gunSuresi` — `TAM_GUN | YARIM_GUN`
- `version` — optimistic locking

`kategori` yalnız sınıflandırma ve görüntüleme içindir; hesabı `gunSuresi` belirler. Uygulama duplicate kontrolüne ek olarak veritabanı unique constraint'i eşzamanlı ekleme yarışını korur.

Mevcut `IzinBilgileri.isGunuSayisi`, ilgili response alanı ve onaylı izin toplam sorgusu `Integer` yerine `BigDecimal` kullanır. Persistence alanı `precision = 4, scale = 1` ile bir izin talebinin en fazla 366 günlük tarih aralığını güvenle taşır. Değerler tek ondalık basamakla temsil edilir; mevcut `14` anlamı `14.0` olarak korunur. MySQL sütun dönüşümü Testcontainers entegrasyon testiyle doğrulanır.

## 4. Katman sorumlulukları

### TatilRepository

- Tarih aralığındaki tatilleri tek sorguda getirir.
- Tarih benzersizliği ve kayıt erişimini destekler.

### TatilTakvimiService

- JWT principal'ı veritabanındaki personel kaydıyla doğrular.
- Aktiflik, rol, tarih, ad, enum, geçmiş kayıt ve duplicate kurallarını uygular.
- Create/update/delete işlemlerini transaction içinde yürütür.
- Gerçek mutation ile audit'i aynı transaction içinde kaydeder.
- Aynı değerlerle güncellemeyi `204` no-op yapar; persistence ve audit üretmez.
- Geçmiş kayıt kontrolünü no-op karşılaştırmasından önce yapar; geçmiş kayıt aynı değerlerle de gönderilse `409` döner.

### IsGunuHesaplamaService

- Tarih aralığını doğrular.
- Aralıktaki tatilleri tek repository sorgusuyla alır.
- Günlerin katkısını hesaplayıp tek ondalık basamaklı `BigDecimal` döndürür.
- HTTP, kullanıcı rolü ve audit sorumluluğu taşımaz.
- Önizleme ve gelecekteki gerçek izin oluşturma aynı servisi kullanır.

### Controller'lar

- `TatilController` listeleme ve yönetim HTTP sözleşmesini taşır.
- `IzinHesaplamaController` iş günü önizleme sözleşmesini taşır.
- Controller yalnız HTTP parametreleri, authentication aktarımı, status ve DTO ile ilgilenir.
- Business validation ve yetki service katmanında kalır.
- Entity doğrudan response yapılmaz.

## 5. Endpoint sözleşmeleri

### Tatil listeleme

```http
GET /api/holidays?baslangicTarihi=2026-01-01&bitisTarihi=2026-12-31
```

Tüm aktif roller erişebilir. İki ISO tarih zorunludur, bitiş başlangıçtan önce olamaz ve aralık en fazla 366 gündür. Response tarih sırasına göre kararlı döner. İşlem read-only ve auditsizdir.

```json
[
  {
    "tatilId": 12,
    "ad": "Cumhuriyet Bayramı",
    "tarih": "2026-10-29",
    "kategori": "RESMI_SABIT",
    "gunSuresi": "TAM_GUN"
  }
]
```

### Tatil oluşturma

```http
POST /api/holidays
Content-Type: application/json

{
  "ad": "Cumhuriyet Bayramı",
  "tarih": "2026-10-29",
  "kategori": "RESMI_SABIT",
  "gunSuresi": "TAM_GUN"
}
```

Yalnız aktif HR/ADMIN; başarı `201 Created` ve güvenli DTO.

### Tatil güncelleme

```http
PATCH /api/holidays/{tatilId}
```

Request oluşturmadaki dört güncel alanı taşır. Yalnız aktif HR/ADMIN; başarı `204 No Content`.

### Tatil silme

```http
DELETE /api/holidays/{tatilId}
```

Yalnız aktif HR/ADMIN; başarı `204 No Content`.

### İş günü önizleme

```http
GET /api/leave-requests/workday-preview?baslangicTarihi=2026-08-24&bitisTarihi=2026-08-30
```

Yalnız aktif PERSONEL/HR; hedef personel ID'si bulunmaz. İşlem read-only ve auditsizdir.

```json
{ "isGunuSayisi": 4.5 }
```

Bu endpoint ayrı tutulur; çünkü `GET /api/leave-requests` ileride izin taleplerini listeleyecektir. Önizleme, listeleme sözleşmesini belirsizleştirmeden ortak backend hesaplama servisine erişir.

## 6. Yetki matrisi

| İşlem | PERSONEL | YONETICI | HR | ADMIN |
|---|---:|---:|---:|---:|
| Tatil listeleme | 200 | 200 | 200 | 200 |
| Tatil oluşturma | 403 | 403 | 201 | 201 |
| Tatil güncelleme | 403 | 403 | 204 | 204 |
| Tatil silme | 403 | 403 | 204 | 204 |
| İş günü önizleme | 200 | 403 | 200 | 403 |

Token yoksa `401`; principal personel bulunamazsa kontrollü `404`; aktör pasifse `403` döner. Rol kararı frontend'e bırakılmaz ve service katmanında veritabanı aktörü üzerinden doğrulanır.

## 7. Doğrulama ve hata sözleşmesi

- Eksik, bozuk, ters veya 366 günü aşan tarih aralığı: `400`
- Boş tatil adı veya geçersiz enum: `400`
- Sıfır/negatif veya sayısal olmayan tatil ID'si: `400`
- Bulunamayan pozitif tatil ID'si: `404`
- Geçmiş tarih için create: `400`
- Geçmiş kayıt update/delete: `409`
- Aynı tarih veya unique constraint yarışı: `409`
- Optimistic locking çakışması: kontrollü `409`

Ham exception, stack trace veya SQL ayrıntısı istemciye verilmez.

## 8. Hesaplama algoritması

1. Tarih aralığı doğrulanır.
2. Aralıktaki tatiller tek sorguda alınır ve tarihe göre map'e çevrilir.
3. Başlangıçtan bitişe kadar günler dahil dolaşılır.
4. Hafta sonu `0.0` katkı verir.
5. Hafta içindeki tam gün tatil `0.0` katkı verir.
6. Hafta içindeki yarım gün tatil `0.5` katkı verir.
7. Diğer hafta içi günler `1.0` katkı verir.
8. Toplam tek ondalık basamaklı `BigDecimal` döner.

Gün veya tatil başına ayrı sorgu çalıştırılmaz.

## 9. Transaction, eşzamanlılık ve audit

- Listeleme/önizleme `readOnly` transaction kullanır ve audit üretmez.
- Tatil mutation ve audit aynı transaction içinde tamamlanır; hata halinde birlikte rollback olur.
- Unique constraint aynı tarih yarışını, `@Version` eşzamanlı update/delete çakışmasını korur.
- Geçmiş/today kontrolü enjekte edilen `Clock` ile test edilir.

Audit olayları ve sabit detayları:

- `TATIL_EKLENDI` — `Tatil oluşturuldu.`
- `TATIL_GUNCELLENDI` — `Tatil güncellendi.`
- `TATIL_SILINDI` — `Tatil silindi.`

Aktör HR/ADMIN, hedef personel `null` olur. Audit ayrıntısında hassas veri bulunmaz.

## 10. Test stratejisi

Uygulama küçük TDD RED/GREEN döngüleriyle ilerler.

1. Saf hesaplama testleri: hafta içi, hafta sonu, tam/yarım gün, hafta sonu tatili, çok hafta/yıl sınırı, geçersiz aralık, sıfır sonuç ve tek repository sorgusu.
2. Tatil entegrasyon testleri: rol matrisi, pasif/missing/unauthenticated aktör, geçmiş kuralları, duplicate/race `409`, ID/body doğrulaması ve no-op.
3. Audit/transaction testleri: doğru aktör, `null` hedef, güvenli detay, birlikte rollback, başarısız/no-op işlemlerde auditsizlik ve optimistic conflict.
4. Önizleme testleri: doğru `BigDecimal`, PERSONEL/HR erişimi, YONETICI/ADMIN `403`, sıfır için `200/0.0`, validation ve yan etkisizlik.
5. Regresyon testleri: mevcut tamsayı değerlerin `x.0` korunması, toplam/kalan izin hesapları, profil, security, dashboard ve mevcut izin davranışları.

## 11. Sonraki teslimler

1. Hizmet yılına göre 14/20/26 gün izin hakkı tahakkuku ve devreden bakiye
2. Gerçek izin talebi oluşturma/listeleme
3. Yönetici onay/red akışı
4. Frontend tatil takvimi ve önizleme entegrasyonu

Frontend entegrasyonunda `GET /api/leave-requests/workday-preview` kullanılacak; istemci hesabı tekrar üretmeyecektir.

### 11.1 Talep ve onay teslimi için karara bağlanan kurallar

- Aktif `PERSONEL` ve `HR` izin talebi oluşturabilir.
- Aktif `YONETICI`, veritabanında atanmış aktif bir yöneticisi varsa izin talebi oluşturabilir.
- `YONETICI` rolünde olup `yonetici == null` olan bütün kök yöneticiler izin talebi oluşturamaz.
- Bekleyen talep için ayrı bir atanmış onaylayıcı snapshot alanı tutulmaz.
- Normal onay yetkisi, karar anında talep sahibinin veritabanındaki güncel aktif yöneticisine aittir. Yönetici değişirse bekleyen talep yeni yöneticinin kapsamına geçer.
- Talep sahibinin yöneticisi kaldırılırsa talep beklemeye devam eder ve yönetici tarafından sonuçlandırılamaz.
- Aktif `ADMIN`, bütün bekleyen izin taleplerini görebilir ve yönetici ilişkisinden bağımsız olarak hem onaylama hem reddetme işlemlerini yapabilir.
- Admin'in bu yönetim yetkisi, kendi adına izin talebi oluşturma yetkisi vermez.
- Kararı gerçekten veren kişi mevcut `IzinBilgileri.islemYapanId` alanına yazılır.
- Onay/red sırasında talebin hâlâ beklemede olduğu ve aktörün güncel yetkisi aynı transaction içinde yeniden doğrulanır.

## 12. Dokümantasyon sınırı

- Bu tasarım dışında `API_SPEC.md`, `SECURITY.md`, `PROGRESS.md` ve `ROADMAP.md` ayrıca kullanıcı onayı alınmadan değiştirilmez.
- Implementasyon tamamlandığında ilgili ürün belgeleri kullanıcı onayıyla güncellenir.
- Git komutu çalıştırılmaz.
