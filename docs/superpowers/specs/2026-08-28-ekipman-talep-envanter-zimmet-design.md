# Ekipman Talep, Envanter ve Zimmet Tasarımı

**Tarih:** 2026-08-28  
**Durum:** Kullanıcı tarafından ürün kararları ve agent-council incelemesi sonrasında onaylandı  
**Kapsam:** Backend ekipman tipi kataloğu, ekipman talepleri, yönetici/Admin karar akışı, fiziksel envanter, zimmet, iade, personel pasifleştirme/rol değişikliği entegrasyonu, migration ve testler  
**Kapsam dışı:** Frontend implementasyonu

## 1. Amaç

Şirket ekipmanlarının talep, onay, fiziksel cihaz seçimi, zimmet ve iade yaşam döngüsünü güvenli ve izlenebilir biçimde yönetmek:

- aktif `PERSONEL` ve `HR` kendi ekipman taleplerini oluşturur,
- aktif `YONETICI` yalnız doğrudan çalışanlarının taleplerini işler,
- aktif `ADMIN` şirket genelindeki talepleri işler ve envanter/zimmeti yönetir,
- bir talep tek ekipman tipinden tek fiziksel cihazı temsil eder,
- karar veren, talep tipindeki uygun fiziksel cihazı seri numarasıyla seçer,
- onay, cihaz durumu, zimmet ve audit aynı transaction içinde tamamlanır,
- fiziksel envanter veya geçmiş kayıtlar silinmez,
- yarış durumlarında yalnız bir işlem kazanır ve kısmi veri oluşmaz.

## 2. Kaynak isterle uyum

`Staj_Programi_JForce.pdf` belgesinin “3.3 Onay Ekranı”, “3.5 Ekipman (Zimmet) Yönetimi” ve “4.3 Ekipman (Zimmet) Bilgileri” bölümleri şunları ister:

- personelin ihtiyaç duyduğu ekipman için talep oluşturabilmesi,
- yöneticinin bağlı personelin ekipman taleplerini izin talepleriyle aynı pending/approved/rejected akışında işlemesi,
- Admin'in ekipman tipi, marka/model ve stok durumunu yönetmesi,
- onaylanan talebin personel üzerine zimmetli envantere dönüşmesi,
- tiplerin dinamik tanım tablosundan yönetilmesi,
- marka, model, seri numarası, teslim ve iade tarihlerinin izlenmesi.

HR rolünün self-service talebe eklenmesi, Admin'in şirket geneli karar yetkisi, iptal/düzenleme, aktif/pasif tip, `KULLANIM_DISI`, fiziksel cihaz seçimi, concurrency ve audit ayrıntıları kaynak isterin üzerine kullanıcı tarafından onaylanan ürün kararlarıdır.

## 3. Seçilen yaklaşım ve reddedilen alternatifler

### 3.1 Seçilen yaklaşım

Karar veren Yönetici/Admin, yalnız yetkili olduğu talebin tipindeki `DEPODA` cihazların dar ve güvenli listesini görür. Onay request'i seçilen `ekipmanId` değerini taşır. Backend, istemci seçimine güvenmeden cihazı kilit altında tekrar doğrular.

Bu yaklaşımda “Onayla” işlemi ürün anlamında “Onayla ve zimmetle”dir:

1. talep `ONAY_BEKLIYOR` durumundadır,
2. seçilen cihaz aynı tipte ve `DEPODA` durumundadır,
3. zimmet kaydı oluşturulur,
4. cihaz `PERSONELDE` yapılır,
5. talep `ONAYLANDI` yapılır,
6. tek güvenli audit kaydı yazılır.

Adımlar aynı transaction'da tamamlanır veya birlikte rollback olur.

### 3.2 Reddedilen otomatik cihaz seçimi

Backend'in uygun herhangi bir cihazı otomatik seçmesi reddedilmiştir. Fiziksel olarak teslim edilen seri numarasıyla sistemin otomatik seçtiği cihazın farklı olma riski vardır.

### 3.3 Reddedilen iki aşamalı fulfillment

“Yönetici onayladı, Admin daha sonra cihaz atayacak” modeli bu MVP'de kullanılmaz. Ayrı bir `ZIMMET_BEKLIYOR` durumu, ek kuyruk ve ek karar semantiği gerektirir.

### 3.4 Reddedilen generic talep motoru

İzin ve ekipman akışları benzer olsa da stok, fiziksel cihaz ve zimmet davranışı ekipmana özgüdür. Şimdiden bütün talep türlerini kapsayan generic workflow veya ortak persistence katmanı kurulmaz.

## 4. Kapsam dışı ve MVP sonrası

Bu teslimde aşağıdakiler uygulanmaz:

- Admin'in talepsiz doğrudan zimmet oluşturması,
- çoklu adet, paket talep ve kısmi karşılama,
- bakım/onarım olay geçmişi,
- kayıp/çalıntı ve geçici cihaz akışları,
- barkod/QR,
- çoklu depo, ofis veya lokasyon,
- toplu envanter içe aktarma,
- teslim alan personelin dijital teyidi,
- stok geldi bildirimi ve talep SLA raporu,
- tipe özgü dinamik RAM, ekran boyutu veya klavye düzeni alanları,
- cursor pagination, full-text arama, outbox veya event sourcing,
- frontend ekranları.

`zimmetleme_bilgileri.talep_id` nullable bırakılır. Bu, legacy kayıtları ve ileride ayrıca onaylanabilecek doğrudan zimmet akışını destekler; bu teslimde public direct-assignment endpoint'i eklenmez.

## 5. Rol ve yetki matrisi

| İşlem | PERSONEL | YONETICI | HR | ADMIN |
|---|---:|---:|---:|---:|
| Aktif ekipman tipi kataloğunu okuma | 200 | 200 | 200 | 200 |
| Kendi talebini oluşturma | 201 | 403 | 201 | 403 |
| Kendi taleplerini okuma | 200 | 403 | 200 | 403 |
| Bekleyen kendi talebini düzenleme/iptal | 204 | 403 | 204 | 403 |
| Doğrudan takım taleplerini okuma | 403 | 200 | 403 | 403 |
| Şirket geneli talepleri okuma | 403 | 403 | 403 | 200 |
| Yetkili talep için uygun cihazları okuma | 403 | 200 | 403 | 200 |
| Yetkili talebi onaylama/reddetme | 403 | 204 | 403 | 204 |
| Ekipman tipi ve envanter yönetimi | 403 | 403 | 403 | 200/201/204 |
| Kendi zimmetlerini okuma | 200 | 200 | 200 | 200 |
| Şirket geneli zimmetleri okuma/iade | 403 | 403 | 403 | 200/204 |

Bütün işlemler aktif principal ister. Genel route yetkisi method security ile, aktiflik/DB rolü/ownership/doğrudan yönetici ve hedef kapsamı service katmanında doğrulanır.

Yönetici yalnız işlem anındaki gerçek ve aktif doğrudan çalışanını işler. Yönetici değişikliği eski yöneticinin erişimini hemen kaldırır; yeni yönetici devralır. Yöneticisi olmayan talepleri yalnız Admin işler.

Admin kendi talebini oluşturamaz. Legacy veya rol değişiminden kalan öz-hedef talebi de onaylayamaz; rol değişikliği entegrasyonu bekleyen talebi zaten otomatik iptal eder.

## 6. Katman sorumlulukları

### 6.1 Controller

Controller yalnız:

- path/query/body sözleşmesi,
- Jakarta Validation,
- `Authentication#getName` ile principal aktarımı,
- `200`, `201` ve `204` response'ları

ile ilgilenir. Repository erişimi, rol/ownership, stok, durum geçişi veya audit kararı içermez.

### 6.2 Service

Service:

- principal'ın DB varlığı, aktifliği ve gerçek DB rolü,
- owner/direct-report/Admin kapsamı,
- tip ve cihaz yaşam döngüsü,
- duplicate bekleyen talep,
- request state transition,
- transaction ve kilit sırası,
- cihaz–talep tipi eşleşmesi,
- zimmet ve iade,
- DTO görünürlüğü,
- ana işlem ile audit atomikliği

konularını yönetir.

Talep, envanter/tip ve zimmet use-case'leri mevcut paket yapısını izleyen ayrı somut service sınıflarında tutulabilir. Sırf interface kullanmış olmak için interface eklenmez.

### 6.3 Repository

Repository yalnız use-case'e özgü sorguları taşır:

- owner/talep/cihaz satırlarını pessimistic lock ile yükleme,
- duplicate bekleyen talep kontrolü,
- owner/team/Admin sayfalı talep sorguları,
- talep tipindeki `DEPODA` cihaz seçkisi,
- tip/cihaz/zimmet filtreleri,
- aynı cihazın açık zimmeti,
- personel pasifleştirme veya rol değişiminde bekleyen talepler.

## 7. Veri modeli

### 7.1 `EkipmanTipi`

Alanlar:

- pozitif integer `ekipmanTipiId`,
- trim edilmiş, case-insensitive unique, en fazla 50 karakter `tipAdi`,
- non-null `aktifMi`, varsayılan `true`.

Kurallar:

- fiziksel silme yoktur,
- pasif tip yeni talepte ve yeni envanter girişinde seçilemez,
- geçmiş talep, cihaz ve zimmetlerde görünmeye devam eder,
- yeniden aktifleştirilebilir,
- bekleyen talebi bulunan tip pasife alınamaz ve `409` döner,
- mevcut envanter veya geçmiş/açık zimmet tip pasifleştirmeyi engellemez.

### 7.2 `EkipmanBilgileri`

Mevcut alanlar korunur:

- UUID `ekipmanId`,
- `ekipmanTipi`,
- `marka`,
- nullable `model`,
- unique `seriNo`,
- `ekipmanDurum`.

Durum kümesi:

```text
DEPODA
PERSONELDE
KULLANIM_DISI
```

Yeni nullable alan:

- `kullanimDisiNedeni`, en fazla 500 karakter.

Kurallar:

- create her zaman server tarafından `DEPODA` başlatılır,
- tip create sonrasında değiştirilemez,
- marka/model/seri numarası Admin tarafından düzeltilebilir,
- normalize aynı metadata PATCH'i auditsiz `204` no-op olabilir,
- `DEPODA -> PERSONELDE` yalnız onay/zimmetle,
- `PERSONELDE -> DEPODA` yalnız iadeyle,
- Admin yalnız `DEPODA <-> KULLANIM_DISI` geçişini elle yapabilir,
- açık zimmet varken kullanım dışına alma yasaktır,
- kullanım dışına alırken trim sonrası `1..500` neden zorunludur,
- tekrar `DEPODA` yapıldığında güncel kullanım dışı nedeni temizlenir,
- fiziksel silme yoktur.

### 7.3 `EkipmanTalep`

Alanlar:

- UUID `talepId`,
- owner `personel`,
- `ekipmanTipi`,
- nullable ve en fazla 255 karakter `aciklama`,
- `talepDurum`,
- non-null `talepTarihi`,
- nullable `islemYapan`,
- nullable `islemTarihi`,
- nullable ve en fazla 500 karakter `kararNotu`.

Bir talep tek cihaz içindir; `adet` alanı eklenmez.

### 7.4 `ZimmetlemeBilgileri`

Alanlar:

- UUID `zimmetId`,
- `ekipman`,
- `personel`,
- nullable unique `talep`,
- non-null `teslimTarihi`,
- nullable `iadeTarihi`.

Kurallar:

- non-null `talep_id` bir talebin en fazla bir zimmet üretmesini sağlar,
- aynı cihazın aynı anda en fazla bir açık zimmeti olabilir,
- `iadeTarihi`, `teslimTarihi` değerinden önce olamaz,
- kapalı zimmet silinmez veya yeniden açılmaz,
- talep onaylı kalır; iade yeni talep durumu değildir.

Talep ve zimmet tablolarında personel FK'sının bulunması 3NF'yi bozmaz. Zimmet personeli zimmet ilişkisinin doğrudan niteliğidir; talep ID unique alternatif izlenebilirlik anahtarıdır. Tip, seri numarası veya personel adı zimmet tablosuna kopyalanmaz.

## 8. Endpoint sözleşmeleri

### 8.1 Aktif ekipman tipi kataloğu

```http
GET /api/equipment-types
```

- Bütün aktif roller erişebilir.
- Yalnız aktif tipler alfabetik, eşitlikte ID sırasında döner.
- Response yalnız `{ id, ad }` taşır.
- Stok, cihaz, marka/model veya seri numarası dönmez.
- Stokta cihaz bulunmaması tipi katalogdan kaldırmaz.
- Okuma auditsizdir.

### 8.2 Admin ekipman tipi yönetimi

```http
GET   /api/admin/equipment-types?page=0&size=20&aktif=true&arama=mon
POST  /api/admin/equipment-types
PATCH /api/admin/equipment-types/{ekipmanTipiId}
```

Create body:

```json
{ "ad": "Monitör" }
```

PATCH body adı ve/veya `aktifMi` değerini taşıyabilir. Tip pasifleştirme öncesinde bekleyen talep kontrol edilir. Aynı normalize ad no-op ise persistence/audit yan etkisi oluşmaz.

### 8.3 Talep oluşturma

```http
POST /api/equipment-requests
Content-Type: application/json

{
  "ekipmanTipiId": 1,
  "aciklama": "Evden çalışma için ikinci ekran gerekiyor."
}
```

- Yalnız aktif `PERSONEL` ve `HR` kendi adına oluşturabilir.
- Owner request body'den değil JWT principal'dan belirlenir.
- Tip ID pozitif ve tip aktif olmalıdır.
- Açıklama opsiyonel, trim sonrası `0..255` karakterdir; blank değer `null` normalize edilir.
- Aynı owner+tip için başka `ONAY_BEKLIYOR` talep varsa `409` döner.
- Aynı tipte açık zimmet bulunması create'i engellemez.
- Başarı `201 Created` ve owner talep DTO'su döndürür.

### 8.4 Kendi talepleri

```http
GET /api/equipment-requests/my?page=0&size=20
GET /api/equipment-requests/my?durum=ONAY_BEKLIYOR&ekipmanTipiId=1&page=0&size=20
```

- Yalnız aktif `PERSONEL` ve `HR` erişebilir.
- Ownership repository sorgusunun zorunlu predicate'idir.
- Filtreler birlikte verilirse `AND` uygulanır.
- Sıralama `talepTarihi DESC`, `talepId DESC` biçimindedir.
- Owner response'u tip, açıklama, durum enumu/görünen adı, talep/işlem zamanı, red gerekçesi, güvenli karar veren özeti ve varsa kendi zimmet özetini taşır.

### 8.5 Bekleyen talebi düzenleme

```http
PATCH /api/equipment-requests/{talepId}
Content-Type: application/json

{
  "ekipmanTipiId": 2,
  "aciklama": "Klavye talebi."
}
```

- Yalnız owner ve yalnız `ONAY_BEKLIYOR` durumda mümkündür.
- Yeni tip aktif olmalı ve owner+tip duplicate bekleyen talep oluşturmamalıdır.
- Normalize aynı değerler `204` auditsiz no-op olur.
- Terminal talep `409`, yabancı owner güvenli `404` alır.

### 8.6 Talebi iptal etme

```http
PATCH /api/equipment-requests/{talepId}/cancel
```

- Yalnız owner ve `ONAY_BEKLIYOR` durumda mümkündür.
- Durum `IPTAL_EDILDI`, işlem yapan owner, işlem zamanı server zamanı olur.
- Tekrar iptal veya terminal talep `409` döner.
- Başarı `204 No Content` döner.

### 8.7 Yönetici takım kuyruğu

```http
GET /api/equipment-requests/team?page=0&size=20
GET /api/equipment-requests/team?durum=ONAY_BEKLIYOR&ekipmanTipiId=1&page=0&size=20
```

- Yalnız aktif `YONETICI` erişebilir.
- Yalnız işlem anındaki aktif doğrudan çalışanlar query predicate'ine girer.
- Varsayılan görünüm bütün durumları getirir; filtre opsiyoneldir.
- Dolaylı çalışan, eski çalışan, başka takım veya pasif owner içeriğe/count'a girmez.

### 8.8 Admin şirket geneli talep görünümü

```http
GET /api/admin/equipment-requests?page=0&size=20
GET /api/admin/equipment-requests?durum=ONAY_BEKLIYOR&ekipmanTipiId=1&personelId=<uuid>&page=0&size=20
```

- Yalnız aktif ve DB rolü gerçekten `ADMIN` olan aktör erişebilir.
- Aktif/pasif owner geçmişini bütün durumlarla görebilir.
- Filtreler `AND` semantiği taşır.
- Canonical olmayan UUID `400`; pozitif/geçerli fakat eşleşmeyen filtre boş sayfa döndürür.

Takım/Admin item'ında talep sahibi yalnız görüntüleme için gereken ad, soyad, departman, profil fotoğrafı ve aktiflik alanlarını taşır. Kullanıcı adı, e-posta, telefon, yönetici ID'si veya başka hesap alanları dönmez.

### 8.9 Talep için uygun fiziksel cihazlar

```http
GET /api/equipment-requests/{talepId}/available-equipment?page=0&size=20
```

- Yalnız bu talepte karar vermeye yetkili Yönetici veya Admin erişebilir.
- Yöneticiye genel envanter yetkisi vermez.
- Yalnız talep tipiyle eşleşen `DEPODA` cihazlar döner.
- Response `{ id, marka, model, seriNo }` taşır.
- Kapsam dışı talep güvenli `404` döndürür.
- Liste salt okunur ve auditsizdir.

### 8.10 Onay ve red

```http
PATCH /api/equipment-requests/{talepId}/approve
Content-Type: application/json

{ "ekipmanId": "f4c606cb-2e96-48d5-9cc3-c6c9e45c66ae" }
```

```http
PATCH /api/equipment-requests/{talepId}/reject
Content-Type: application/json

{ "kararNotu": "Mevcut cihaz yenileme kriterini karşılamıyor." }
```

- Yönetici yalnız aktif doğrudan çalışanını; Admin şirket genelini işler.
- Aktör ve owner aynı kişi olamaz.
- Talep mutlaka `ONAY_BEKLIYOR` olmalıdır.
- Red gerekçesi trim sonrası `1..500` karakterdir ve owner'a görünür.
- Onayda cihaz UUID canonical olmalı; cihaz aynı tipte ve `DEPODA` olmalıdır.
- Cihaz artık uygun değilse `409`; talep beklemede kalır.
- Başarı `204 No Content` döner.

### 8.11 Admin fiziksel envanter yönetimi

```http
GET   /api/admin/equipment?page=0&size=20&ekipmanTipiId=1&durum=DEPODA&arama=dell
POST  /api/admin/equipment
PATCH /api/admin/equipment/{ekipmanId}
PATCH /api/admin/equipment/{ekipmanId}/status
```

Create body tip, marka, nullable model ve seri numarası taşır; durum body'den alınmaz ve server `DEPODA` koyar.

Metadata PATCH marka/model/seri numarasını günceller; tip veya durum kabul etmez. Status PATCH yalnız `DEPODA` ve `KULLANIM_DISI` arasındaki izinli Admin geçişini taşır. Kullanım dışına alma için `neden` zorunludur.

Admin liste response'u tip, marka, model, seri, durum, nullable kullanım dışı nedeni ve açık zimmet bulunup bulunmadığını taşır. Fiziksel silme endpoint'i yoktur.

### 8.12 Zimmet görüntüleme ve iade

```http
GET   /api/assignments/my?page=0&size=20
GET   /api/admin/assignments?page=0&size=20&aktif=true&personelId=<uuid>&ekipmanTipiId=1
PATCH /api/admin/assignments/{zimmetId}/return
```

- Bütün aktif roller yalnız kendi açık/geçmiş zimmetlerini okuyabilir.
- Admin şirket genelini aktif/pasif owner dahil okuyabilir.
- Admin filtreleri `AND` uygulanır.
- İade yalnız açık zimmette mümkündür.
- İade zamanı server zamanıdır; cihaz aynı transaction'da `DEPODA` yapılır.
- İkinci iade `409` döner.
- Pasif owner'daki açık zimmet Admin listesinde açık ve owner pasif bilgisiyle görünür.

## 9. Sayfalama, sıralama ve doğrulama

Büyük listeler mevcut `SayfaliResponse` biçimini kullanır:

```json
{
  "icerik": [],
  "sayfa": 0,
  "sayfaBoyutu": 20,
  "toplamKayit": 0,
  "toplamSayfa": 0
}
```

- Varsayılan `page=0`, `size=20`; `size` aralığı `1..100` olur.
- Talep sırası `talepTarihi DESC, talepId DESC`.
- Zimmet sırası `teslimTarihi DESC, zimmetId DESC`.
- Envanter sırası tip adı, marka, model, seri numarası ve ID ile kararlı olur.
- Geçersiz enum, non-canonical UUID, sıfır/negatif lookup ID veya pagination `400` döner.
- Pozitif/geçerli fakat eşleşmeyen liste filtresi boş `200` döndürür.
- Tekil mutation hedefi bulunamazsa kontrollü `404` döner.

## 10. Durum geçişleri

### 10.1 Talep

```text
ONAY_BEKLIYOR -> ONAYLANDI
ONAY_BEKLIYOR -> REDDEDILDI
ONAY_BEKLIYOR -> IPTAL_EDILDI
```

Terminal durumlar geri alınmaz. Onay/red/iptal retry'ı no-op değil `409` olur.

### 10.2 Fiziksel cihaz

```text
DEPODA -> PERSONELDE            yalnız onay + zimmet
PERSONELDE -> DEPODA            yalnız iade
DEPODA -> KULLANIM_DISI         yalnız Admin + zorunlu neden
KULLANIM_DISI -> DEPODA         yalnız Admin
```

Başka geçişler `409` döner.

## 11. Transaction, kilit ve yarış güvenliği

Ortak mutation kilit sırası:

```text
hedef owner Personel -> talep -> seçilen ekipman -> zimmet
```

Talep ID ile başlanan işlemlerde önce salt-okunur snapshot'tan değişmez owner ID alınır; owner kilitlendikten sonra talep `FOR UPDATE` yeniden yüklenip owner/type doğrulanır.

### 11.1 Create ve update

- Owner Personel satırı `PESSIMISTIC_WRITE` kilitlenir.
- Aktiflik/rol doğrulanır.
- Tip yüklenir ve aktifliği doğrulanır.
- Aynı owner+tip+pending sorgulanır.
- Talep yazılır ve audit aynı transaction'da kaydedilir.

MySQL partial unique desteklemediği ve durum başka lookup tablosunda olduğu için duplicate pending kuralı owner kilidiyle korunur. Bütün create/update yolları aynı owner kilidini almak zorundadır.

### 11.2 Onay

- Aktör DB rolü ve aktifliği doğrulanır.
- Owner kilitlenir.
- Talep yeniden kilitli yüklenir.
- Yetki durumdan önce doğrulanır.
- Pending ve aktif owner doğrulanır.
- Seçilen cihaz kilitlenir.
- Cihaz tipi/durumu ve açık zimmet yokluğu doğrulanır.
- Zimmet insert edilir.
- Cihaz `PERSONELDE`, talep `ONAYLANDI` yapılır.
- Tek güvenli talep-onay auditi yazılır.

İki talep aynı cihazı seçerse yalnız ilk transaction başarılı olur; ikincisi güncel cihaz durumunu görüp `409` alır. Aynı talebe onay/red/iptal yarışında da yalnız biri kazanır.

### 11.3 İade ve kullanım dışına alma

- Owner/cihaz/zimmet kilit sırası korunur.
- İade açık zimmeti kapatır ve cihazı `DEPODA` yapar.
- Kullanım dışına alma cihazı kilitler ve açık zimmet olmadığını doğrular.
- İade–kullanım dışı veya çift iade yarışında yalnız biri geçerli state transition yapabilir.

### 11.4 Rollback

Talep, cihaz, zimmet ve audit aynı transaction'dadır. Audit insert, zimmet constraint veya persistence hatasında hiçbir kısmi durum kalmaz.

## 12. Personel pasifleştirme ve rol değişikliği entegrasyonu

### 12.1 Pasifleştirme

Personel pasifleştirme transaction'ında:

1. hedef Personel kilitlenir,
2. bütün `ONAY_BEKLIYOR` ekipman talepleri kilitlenir,
3. her biri `IPTAL_EDILDI` yapılır,
4. `islemYapan`, pasifleştiren HR/Admin olur,
5. her değişen talep için güvenli iptal auditi yazılır,
6. açık zimmetler değiştirilmez,
7. personel ve bütün audit/değişiklikler birlikte commit veya rollback olur.

Pasif owner'daki açık zimmet, fiziksel iade gerçekleşene kadar `PERSONELDE` kalır ve Admin listesinde istisna olarak gösterilir.

### 12.2 Rol değişikliği

`PERSONEL` veya `HR`, self-service ekipman talebi açamayacak `YONETICI` veya `ADMIN` rolüne geçirildiğinde bekleyen ekipman talepleri rol değişikliği transaction'ında iptal edilir. İşlem yapan rol değişikliğini yapan Admin'dir. Geçmiş talepler ve açık zimmetler değişmez.

Bu kural legacy bekleyen talebin öz-onaylanmasını engeller.

## 13. Migration ve indeksler

Yeni idempotent manual MySQL migration:

- `ekipman_tipi.aktif_mi` kolonunu ekler ve mevcut tipleri `true` backfill eder,
- `ekipman_talep.karar_notu` kolonunu ekler,
- mevcut null `talep_tarihi` değerlerini güvenli biçimde backfill edip non-null yapar,
- ekipman açıklama/karar alanlarının DB uzunluklarını sabitler,
- `KULLANIM_DISI` durumunu destekler,
- `ekipman_bilgileri.kullanim_disi_nedeni` kolonunu ekler,
- `zimmetleme_bilgileri.talep_id` nullable unique FK ekler,
- aynı cihaz için yalnız bir açık zimmeti koruyan generated nullable kolon + unique index ekler,
- iade tarihinin teslim tarihinden önce olmamasını CHECK ile korur,
- owner/tip/durum/tarih, tip/durum/cihaz ve zimmet listeleme indekslerini ekler,
- audit ENUM'a yeni ekipman olaylarını mevcut bütün değerleri koruyarak ekler.

Migration additive -> backfill -> doğrulama -> constraint/index sırasını izler. Legacy duplicate açık zimmet, duplicate talep bağlantısı veya geçersiz tarih varsa veri kaybetmeden fail-fast olur; sessizce kayıt silmez/düzeltmez.

MySQL DDL implicit commit davranışı nedeniyle production uygulaması bakım penceresi ve yedek gerektirir. `ddl-auto=update`, manual migration'ın yerine geçmez.

## 14. Audit

Yeni olaylar:

- `EKIPMAN_TALEBI_OLUSTURULDU`
- `EKIPMAN_TALEBI_ONAYLANDI`
- `EKIPMAN_TALEBI_REDDEDILDI`
- `EKIPMAN_TALEBI_IPTAL_EDILDI`
- `EKIPMAN_TIPI_EKLENDI`
- `EKIPMAN_TIPI_GUNCELLENDI`
- `EKIPMAN_EKLENDI`
- `EKIPMAN_GUNCELLENDI`
- `ZIMMET_IADE_EDILDI`

Onay işlemi talep+zimmet için tek `EKIPMAN_TALEBI_ONAYLANDI` kaydı üretir. Ayrı ikinci bir zimmet audit'i oluşturulmaz. Pending talep metadata güncellemesi önceki proje kararıyla uyumlu olarak audit üretmez. Normalize no-op tip/envanter PATCH'i audit üretmez.

Audit aktörü işlemi yapan principal, hedefi talep/zimmet owner'ıdır. Katalog/envanter işlemlerinde hedef Admin'in kendisi olabilir. Detaylar sabit güvenli cümlelerdir.

Audit detayına UUID, seri numarası, marka/model, talep açıklaması, red gerekçesi veya kullanım dışı nedeni yazılmaz.

## 15. Hata sözleşmesi

| Koşul | Status | Güvenli davranış |
|---|---:|---|
| Token yok/geçersiz | 401 | Mevcut authentication cevabı |
| Pasif/yetersiz rol veya ownership | 403/404 | Route rolü 403; kapsam dışı tekil kaynak güvenli 404 |
| Principal bulunamadı | 404 | `Personel bulunamadı.` |
| Tip/talep/cihaz/zimmet bulunamadı | 404 | Kaynağa özgü kontrollü mesaj |
| Malformed UUID/enum/body/query veya non-positive ID | 400 | `Geçersiz istek` |
| Duplicate bekleyen talep | 409 | Aynı tipte bekleyen talep bulunduğunu belirten güvenli mesaj |
| Bekleyen talepli tipi pasifleştirme | 409 | Tipin bekleyen talepleri olduğunu belirten güvenli mesaj |
| Terminal talebi yeniden işleme | 409 | `Talep artık işlem beklemiyor.` |
| Yanlış tipte/uygun olmayan cihaz | 409 | Cihazın artık zimmete uygun olmadığını belirten güvenli mesaj |
| Geçersiz cihaz durum geçişi | 409 | Durum geçişinin uygun olmadığını belirten güvenli mesaj |
| Açık zimmette kullanım dışına alma | 409 | Cihazın açık zimmeti olduğunu belirten güvenli mesaj |
| İade edilmiş zimmeti yeniden iade | 409 | Zimmetin artık açık olmadığını belirten güvenli mesaj |
| Beklenmeyen hata | 500 | `Beklenmeyen bir hata oluştu.` |

Ham exception, SQL, constraint veya stack trace response'a çıkmaz. Başarısız mutation persistence veya audit yan etkisi bırakmaz.

## 16. Test stratejisi

### 16.1 Migration

- Legacy ekipman/talep/zimmet ve audit fixture'ı üzerinde gerçek MySQL migration testi.
- Aktif flag backfill, `KULLANIM_DISI`, karar notu, unique nullable `talep_id` ve açık-zimmet constraint'i.
- Audit Java enum kümesiyle MySQL ENUM exact uyumu ve gerçek insert.
- İkinci çalıştırmanın veri koruması/idempotentliği.
- Legacy tutarsızlıkta fail-fast ve veri silinmemesi.

### 16.2 Rol ve güvenlik

- PERSONEL/HR olumlu self-service; YONETICI/Admin create yasağı.
- YONETICI yalnız aktif doğrudan takım; dolaylı/eski/başka takım sızıntısı yok.
- Admin şirket geneli karar ve envanter/zimmet.
- Pasif, missing principal, unauthenticated ve JWT–DB rol uyumsuzluğu.
- Yetkisiz veya olmayan tekil kaynakta güvenli davranış.

### 16.3 Katalog ve envanter

- Aktif alfabetik safe katalog, pasif tipin gizlenmesi, stok bilgisinin sızmaması.
- Tip create/rename/no-op/duplicate/reactivate.
- Bekleyen talepli tipin pasifleştirilememesi.
- Envanter create'in zorunlu `DEPODA` başlaması.
- Seri numarası uniqueness ve normalize sınırları.
- Metadata update/no-op ve type immutability.
- Bütün geçerli/geçersiz status transition'ları.
- Kullanım dışı nedeninin yalnız Admin'e görünmesi.

### 16.4 Talep

- Create validation, unknown/inactive tip ve açıklama trim/sınırları.
- Aynı owner+tip duplicate bekleyen; terminal talep sonrasında yeni create.
- Bekleyen update/cancel ve terminal state `409`.
- Owner sorgusunda yabancı kayıtların içerik/count'a girmemesi.
- Team/Admin filtre, sayfalama ve kararlı sıra.
- Okumaların DB/audit yan etkisi üretmemesi.

### 16.5 Karar, zimmet ve iade

- Uygun cihaz listesinde yalnız aynı tip `DEPODA` ve minimal DTO.
- Doğru Yönetici/Admin onayı.
- Yanlış tip, `PERSONELDE`, `KULLANIM_DISI`, missing cihaz ve owner pasifliği.
- Red gerekçesi trim/sınır ve owner görünürlüğü.
- Onayda talep+cihaz+zimmet+audit atomikliği.
- İadede zimmet geçmişi ve cihaz `DEPODA` durumu.
- Pasif personelde açık zimmet görünürlüğü.

### 16.6 Concurrency ve rollback

- Aynı owner+tip paralel iki create işleminden yalnız biri başarılı.
- Aynı talebe approve/approve, approve/reject ve approve/cancel yarışları.
- İki talebin aynı cihazı seçmesi.
- Çift iade ve iade–kullanım dışı yarışları.
- Personel pasifleştirme–approve ve rol değişikliği–approve iki commit sırası.
- Audit, zimmet veya persistence hata enjeksiyonunda tam rollback.
- Kilit sırasının bağımsız owner/cihaz işlemlerini global olarak seri hale getirmemesi.

### 16.7 Regresyon ve kalite

- Mevcut izin karar, personel pasifleştirme/rol değişikliği, security ve audit testleri.
- İlgili unit/integration testleri, ardından tam backend suite ve JaCoCo.
- Büyük listelerde N+1 oluşmaması ve gerekli indekslerin gerçek sorgularla doğrulanması.

## 17. Ölçeklenebilirlik değerlendirmesi

Bu tasarım küçük ve orta ölçekli şirketler için yeterlidir:

- ekipman tipleri dinamik tablodur; yeni tip kod değişmeden eklenir,
- fiziksel cihazlar seri numarasıyla ayrı satırlardır,
- bounded pagination ve sorgu indeksleri kontrolsüz liste büyümesini engeller,
- pessimistic lock yalnız ilgili owner/talep/cihazı seri hale getirir,
- DB constraint'leri service hatasına karşı temel değişmezleri korur,
- nullable talep FK'sı ileride direct assignment'a izin verir.

Ölçüm olmadan cursor pagination, cache, read replica, event bus, dinamik attribute/EAV veya JSON özellik altyapısı eklenmez. Tipe özgü özellik ihtiyacı ortaya çıkarsa ayrı tasarım yapılır.

## 18. Başarı ölçütleri

- PERSONEL/HR yalnız kendi aktif tip taleplerini yönetebilir.
- YONETICI yalnız aktif doğrudan takımını, Admin şirket genelini işler.
- Karar veren yalnız talep kapsamındaki uygun fiziksel cihazları görür.
- Onay, cihaz, zimmet ve audit atomik tamamlanır.
- Paralel işlemler duplicate talep, çift karar, çift zimmet veya çift iade üretemez.
- Tip ve cihazlar silinmez; geçmiş izlenebilir kalır.
- Personel pasifliği ve uygunsuz rol değişikliği bekleyen talepleri atomik iptal eder fakat fiziksel cihazı otomatik iade etmez.
- Migration mevcut veriyi korur, audit ENUM'u eksik bırakmaz ve gerçek MySQL'de doğrulanır.
- Güvenli DTO'lar gereksiz personel, envanter veya karar ayrıntısı sızdırmaz.
- Gelecek genişlemeler için gereksiz generic altyapı eklenmeden açık genişleme noktaları korunur.
