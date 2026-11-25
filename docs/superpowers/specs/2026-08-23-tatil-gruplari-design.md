# Tatil Grupları Tasarımı

Tarih: 2026-08-23  
Durum: Tasarım onaylandı; implementasyon bekliyor

## 1. Amaç

Mevcut şirket tatil takvimini, tek günlük kayıtları bozmadan dahil tarih aralıklarını temsil eden tatil gruplarıyla genişletmek.

Örneğin 11–15 Ağustos Kurban Bayramı tek `tatilId` altında tutulur, takvimde tek aralık olarak gösterilir ve oluşturma, güncelleme, silme ile audit işlemleri grubun tamamına uygulanır.

Kapsama dahil olanlar:

- Tek günlük ve çok günlük tatil grupları
- Aralık kesişimine göre listeleme
- Atomik çakışma kontrolü
- Eşzamanlı mutation güvenliği
- Çok günlük tam/yarım tatillerin iş günü hesabına etkisi
- Mevcut tek günlük kayıtların veri migration'ı
- Mevcut rol, geçmiş kayıt ve audit politikalarının korunması

Kapsam dışı olanlar:

- Tatil grubu içinde gün bazında farklı süre veya kategori
- Departman/personel bazlı farklı tatil takvimleri
- Otomatik resmî tatil kaynağı
- Frontend değişiklikleri
- İzin talep ve onay akışı
- Mevcut audit ENUM listesini kalıcılaştıracak ayrı migration

## 2. Onaylanan ürün kararları

- Şirket için tek tatil takvimi bulunmaya devam eder.
- Bir tatil grubu başlangıç ve bitiş tarihlerini dahil eder.
- Tek günlük tatilde başlangıç ve bitiş aynı gündür.
- Mevcut `tarih` alanı geriye uyumluluk için başlangıç tarihi olarak korunur.
- Yeni `bitisTarihi` gönderilmezse başlangıç tarihiyle aynı kabul edilir.
- Bir tatil grubu en fazla 366 gün olabilir.
- Başlangıcı bugünden önce olan mevcut grup, kısmen geleceğe uzansa bile geçmiş kayıt kabul edilir ve güncellenemez/silinemez.
- Bugün başlayan grup yönetilebilir.
- Bir grubun bütün günleri aynı ad, kategori ve gün süresini taşır.
- İki tatil grubu tek bir günde dahi kesişemez.
- Yarım günlük çok günlük tatilde kapsanan her hafta içi `0.5` katkı verir.
- Hafta sonundaki günler takvim aralığında görünür fakat iş günü toplamına katkı vermez.
- Gerçek mutation başına bir audit kaydı üretilir.

## 3. Seçilen veri modeli

Her tatil grubu `tatil` tablosunda tek satırdır:

- `tatil_id`
- `ad`
- `tarih` — dahil başlangıç tarihi
- `bitis_tarihi` — dahil bitiş tarihi
- `kategori`
- `gun_suresi`
- `version`

Tek satırlı model şu nedenlerle seçildi:

- Takvim response'u doğal olarak tek event üretir.
- `tatilId` doğrudan bütün grubu hedefler.
- Güncelleme, silme, optimistic locking ve audit tek kayıt üzerinden yürür.
- Gün başına satır veya parent-child yaşam döngüsü gerektirmez.

`bitis_tarihi >= tarih` veritabanı constraint'i bulunur. Aralık sorgusunu destekleyen bir index eklenir. Mevcut başlangıç tarihi unique constraint'i korunabilir; ancak genel aralık çakışmasının tek güvencesi değildir.

## 4. Migration

Yeni manuel migration aşağıdaki sırayı izler:

1. `tatil.bitis_tarihi` kolonunu geçici olarak nullable ekler.
2. Mevcut her satırda `bitis_tarihi = tarih` yapar.
3. Kolonu `NOT NULL` yapar.
4. `bitis_tarihi >= tarih` check constraint'ini ekler.
5. Aralık sorgusunu destekleyen index'i ekler.
6. Şirket takvimi mutation'larını seri hale getirecek tek satırlı teknik kilit tablosunu/satırını oluşturur.

Migration:

- Mevcut `tatil_id`, ad, tarih, kategori, gün süresi ve version değerlerini korur.
- Mevcut tek günlük kayıtların anlamını değiştirmez.
- Aynı ortamda ikinci kez çalıştırıldığında veri kaybetmez ve mevcut version değerlerini sıfırlamaz.
- MySQL 8 üzerinde gerçek migration integration testiyle doğrulanır.

Temiz şema ile eski şemadan yükseltilen şema aynı nihai kolon ve constraint yapısına sahip olmalıdır.

## 5. API sözleşmesi

Endpoint yolları değişmez:

```http
GET    /api/holidays
POST   /api/holidays
PATCH  /api/holidays/{tatilId}
DELETE /api/holidays/{tatilId}
```

### 5.1 Request

```json
{
  "ad": "Kurban Bayramı",
  "tarih": "2026-08-11",
  "bitisTarihi": "2026-08-15",
  "kategori": "RESMI_DEGISKEN",
  "gunSuresi": "TAM_GUN"
}
```

- `tarih` zorunludur ve grubun başlangıcıdır.
- `bitisTarihi` opsiyoneldir; yoksa `tarih` ile aynı normalize edilir.
- Eski dört alanlı tek günlük request aynen kabul edilir.

### 5.2 Response

```json
{
  "tatilId": 17,
  "ad": "Kurban Bayramı",
  "tarih": "2026-08-11",
  "bitisTarihi": "2026-08-15",
  "kategori": "RESMI_DEGISKEN",
  "gunSuresi": "TAM_GUN"
}
```

Response'ta `bitisTarihi` her zaman doludur. Entity, `version` veya teknik kilit bilgisi dönmez.

### 5.3 Listeleme kesişimi

`GET /api/holidays` mevcut zorunlu `baslangicTarihi` ve `bitisTarihi` parametrelerini korur. Grup şu koşulla listeye girer:

```text
tatil.tarih <= istenenBitis
AND tatil.bitisTarihi >= istenenBaslangic
```

Böylece önceki ay başlayıp görüntülenen ayda devam eden grup da tek DTO olarak döner. Sonuç başlangıç tarihi ve ardından `tatilId` ile kararlı sıralanır.

## 6. Validation ve hata davranışı

- Eksik `tarih`: `400`
- Bitişin başlangıçtan önce olması: `400`
- Dahil aralığın 366 günü aşması: `400`
- Yeni başlangıcın geçmişte olması: `400`
- Pozitif olmayan `tatilId`: `400`
- Bulunmayan pozitif `tatilId`: `404`
- Başlangıcı geçmiş mevcut grubu güncelleme/silme: `409`
- Başka grupla herhangi bir gün kesişmesi: `409`
- Optimistic locking çakışması: kontrollü `409`

Çakışma mesajı:

```text
Seçilen tarih aralığı mevcut bir tatille çakışıyor.
```

Güncellemede hedef grubun kendisi çakışma sorgusundan hariç tutulur. Yetki, hedef ve geçmiş kontrolü no-op karşılaştırmasından önce yapılır. Normalize edilmiş aynı bilgiler için yetkili istek `204 No Content` döner; persistence ve audit yan etkisi üretmez.

Ham SQL, constraint veya exception ayrıntısı istemciye verilmez.

## 7. Çakışma ve eşzamanlılık

Aralık çakışması şu koşulla aranır:

```text
mevcut.tarih <= yeniBitis
AND mevcut.bitisTarihi >= yeniBaslangic
```

Tek başına `exists` sorgusu iki paralel isteğin aynı anda kontrolü geçmesini engellemez. Bu nedenle bütün tatil create/update/delete transaction'ları önce şirket takviminin tek teknik kilit satırını `PESSIMISTIC_WRITE` ile kilitler.

Akış:

1. Aktör ve yetki doğrulanır.
2. Takvim kilit satırı alınır.
3. Request ve geçmiş kuralları doğrulanır.
4. Çakışma sorgusu çalışır.
5. Tatil mutation'ı flush edilir.
6. Audit aynı transaction'a yazılır.
7. Transaction commit veya tamamen rollback olur.

Bu yaklaşım farklı uygulama instance'larından gelen mutation'ları da seri hale getirir. Tatil yönetim trafiği düşük olduğundan global takvim mutation kilidinin maliyeti kabul edilebilir. `Tatil.version`, aynı grup üzerindeki stale entity güncellemelerine karşı ek koruma sağlamaya devam eder.

## 8. Yetki ve audit

| İşlem | PERSONEL | YONETICI | HR | ADMIN |
|---|---:|---:|---:|---:|
| Tatil listeleme | 200 | 200 | 200 | 200 |
| Tatil oluşturma | 403 | 403 | 201 | 201 |
| Tatil güncelleme | 403 | 403 | 204 | 204 |
| Tatil silme | 403 | 403 | 204 | 204 |

Token yoksa `401`; principal personel bulunamazsa kontrollü `404`; aktör pasifse `403` döner. JWT rolüne ek olarak aktörün güncel DB rolü doğrulanır.

Gerçek mutation auditleri:

- `TATIL_EKLENDI` — `Tatil oluşturuldu.`
- `TATIL_GUNCELLENDI` — `Tatil güncellendi.`
- `TATIL_SILINDI` — `Tatil silindi.`

Bir grup işlemi bir audit kaydı üretir. Audit aktörü HR/ADMIN, hedef personel `null` olur. Audit ayrıntısında tarih aralığı veya başka gereksiz veri bulunmaz. Listeleme read-only ve auditsizdir.

Mevcut yerel veritabanındaki audit ENUM listesi kullanıcı tarafından güncellenmiştir. Bunun repository'de kalıcı migration'a dönüştürülmesi bu tasarımın migration dosyasına karıştırılmaz ve ayrı açık altyapı işi olarak kalır.

## 9. İş günü hesabı

İzin önizleme sözleşmesi değişmez:

```http
GET /api/leave-requests/workday-preview?baslangicTarihi=2026-08-10&bitisTarihi=2026-08-16
```

Hesaplama servisi önizleme aralığıyla kesişen tatil gruplarını tek repository sorgusuyla alır. Başlangıçtan bitişe kadar her gün için:

- Cumartesi/Pazar: `0.0`
- Kapsayan `TAM_GUN` grubu: `0.0`
- Kapsayan `YARIM_GUN` grubu: `0.5`
- Tatil dışı hafta içi: `1.0`

Gruplar kesişemediği için bir tarihi en fazla bir tatil grubu kapsar. Grup önizleme aralığına kısmen giriyorsa yalnız ortak günler hesaba katılır. Frontend hesabı tekrar üretmez.

## 10. Katman sorumlulukları

### Controller

- Mevcut endpoint yollarını ve HTTP status'larını korur.
- Authentication, path/query parametreleri ve DTO aktarımıyla sınırlıdır.
- Çakışma, tarih veya rol iş kuralı içermez.

### Service

- Request tarihlerini normalize eder.
- Aktiflik, DB rolü, geçmiş, aralık ve çakışma kurallarını uygular.
- Transaction, teknik takvim kilidi, mutation ve audit akışını yönetir.
- Entity–DTO dönüşümünü koordine eder.

### Repository

- Kesişen grupları listeleyen sorguyu taşır.
- Güncellemede hedef ID'yi hariç tutan çakışma sorgusunu sağlar.
- Teknik takvim kilit satırını pessimistic lock ile getirir.
- HTTP veya kullanıcı mesajı üretmez.

### DTO

- Request'e opsiyonel `bitisTarihi` eklenir.
- Response'a zorunlu `bitisTarihi` eklenir.
- Entity response yapılmaz.

## 11. Test stratejisi

### 11.1 Migration

- Eski tek-gün şeması ve kayıtları hazırlanır.
- Migration çalıştırılır.
- `bitis_tarihi`, constraint, index ve takvim kilit satırı doğrulanır.
- Eski ID/içerik/version korunur ve bitiş başlangıca eşitlenir.
- Migration ikinci kez çalıştırılır; veri ve version korunur.
- Migration sonrası eski kayıt yeni JPA modeliyle okunur.

### 11.2 Geriye uyumluluk

- `bitisTarihi` içermeyen eski request tek günlük kayıt oluşturur.
- Response aynı başlangıç ve bitişi döner.
- Mevcut tek günlük create/update/delete/list davranışları korunur.

### 11.3 Çok günlük CRUD ve listeleme

- HR ve Admin çok günlük grup oluşturabilir.
- Liste tek DTO döndürür.
- Sorgu grubun ortasındaki günü veya yalnız devam eden kısmını kapsasa da grup döner.
- Güncelleme grubun tüm alanlarını değiştirir.
- Silme grubun tamamını kaldırır.
- Mutation ve audit dış hata halinde birlikte rollback olur.

### 11.4 Çakışma ve yarış

- Aynı başlangıç, aynı bitiş, iç içe, kısmi sol/sağ ve tek günlük kesişimler `409` olur.
- Bitişten sonraki gün başlayan kayıt kabul edilir.
- Güncelleme kendi ID'sini hariç tutar.
- Başarısız create/update persistence veya audit bırakmaz.
- Gerçek MySQL üzerinde iki eşzamanlı çakışan create isteğinden yalnız biri başarılı olur; diğeri kontrollü `409` alır.

### 11.5 Geçmiş, validation ve güvenlik

- Ters, eksik, geçmiş ve 366 günü aşan aralıklar doğrulanır.
- Başlangıcı geçmiş grup aynı bilgilerle de gönderilse `409` olur.
- ID, missing target, pasif/missing principal ve unauthenticated davranışları korunur.
- PERSONEL/YONETICI mutation yapamaz; bütün aktif roller listeleyebilir.

### 11.6 İş günü hesabı

- Çok günlük tam tatilin bütün hafta içleri düşülür.
- Çok günlük yarım tatilin her hafta içi `0.5` etkisi olur.
- Hafta sonları, kısmi kesişim, tek gün ve sıfır sonuç test edilir.
- Tatiller tek repository sorgusuyla alınır.

### 11.7 Regresyon

- Tatil controller/service, izin önizleme, profil bakiyesi, security ve migration paketleri çalıştırılır.
- Ardından tam backend suite ve JaCoCo doğrulanır.

## 12. Dokümantasyon ve uygulama sınırı

Implementasyon sırasında endpoint sözleşmesi değişeceği için `API_SPEC.md`, güvenlik ve transaction davranışı için `SECURITY.md`, tamamlanma kaydı için `PROGRESS.md` ve ilgili backlog için `ROADMAP.md` güncellenmelidir. Ürün belgeleri, kod gerçekten tamamlanmadan uygulanmış gibi işaretlenmez.

Bu belge tasarım kaydıdır; üretim kodu veya veritabanı değişikliği içermez. Git komutu çalıştırılmaz.
