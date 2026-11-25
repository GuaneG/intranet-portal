# Oda Rezervasyonu ve Dashboard Doluluk Tasarımı

**Tarih:** 2026-08-26  
**Durum:** Kullanıcı tarafından bölüm bölüm onaylandı  
**Kapsam:** Backend oda rezervasyonu MVP'si, dashboard oda doluluk özeti, personel pasifleştirme entegrasyonu ve gerekli migration/testler  
**Kapsam dışı:** Frontend implementasyonu

## 1. Amaç

Şirketteki toplantı odalarının bütün aktif roller tarafından güvenli, saatlik ve onaysız biçimde rezerve edilebilmesini sağlamak:

- temiz kurulumda `Büyük Salon (12)` ve `Küçük Oda (6)` hazır bulunur,
- bütün aktif roller kendi adlarına rezervasyon oluşturabilir,
- aynı oda veya aynı owner için çakışan zaman aralıkları engellenir,
- herkes genel oda doluluğunu ve rezervasyonu oluşturan kişiyi görebilir,
- başlık ve katılımcı sayısı yalnız owner ile Admin'e açılır,
- owner kendi başlamamış rezervasyonunu, Admin ise bütün başlamamış rezervasyonları iptal edebilir,
- iptal edilen rezervasyon fiziksel silinmez ve geçmişte korunur,
- Admin şirket genelindeki, diğer kullanıcılar yalnız kendi rezervasyon geçmişlerini görebilir,
- dashboard bugünkü oda durumu, dolu aralıkları ve doluluk oranını gösterir,
- rezervasyon değişikliği ile audit aynı transaction içinde tamamlanır.

## 2. Kaynak isterle uyum

`Staj_Programi_JForce.pdf` belgesinin “3.4 Toplantı Odası Rezervasyonu (Tüm Roller)” bölümü şunları ister:

- iki toplantı odası: `Büyük Salon` ve `Küçük Oda`,
- seçilen gün için saatlik doluluk grid'i,
- boş saat aralığına tıklayarak doğrudan rezervasyon,
- aynı odada çakışan saatlerin engellenmesi,
- rezervasyon başlığı, başlangıç/bitiş saati ve oluşturan kişi bilgisinin tutulması.

Belge rezervasyonu Yönetici onay kuyruğuna bağlamaz; Yönetici onay ekranını yalnız izin ve ekipman talepleri için tanımlar. Bu tasarımda rezervasyon uygunluk kontrolleri geçildiğinde doğrudan kesinleşir.

Kapasite, iptal/geçmiş, Admin yetkisi, tatil politikası, dashboard ayrıntıları ve güvenlik görünürlüğü kaynak isterin üzerine kullanıcı tarafından onaylanan ürün kararlarıdır.

## 3. Kapsam dışı ve MVP sonrası

Bu teslimde aşağıdakiler uygulanmaz; ROADMAP'te gelecekte değerlendirilecek ayrı işler olarak tutulur:

- rezervasyon tarih/saat/oda/başlık düzenleme,
- tekrarlayan rezervasyon serileri ve “tek kayıt mı, bütün seri mi” işlemleri,
- davetli/katılımcı listesi, davet kabul/ret ve bildirimler,
- e-posta veya uygulama içi hatırlatmalar,
- Admin oda ekleme, ad/kapasite güncelleme ve silme,
- bakım/geçici kapatma takvimi,
- oda ekipmanı/özellik filtresi,
- Admin geçmişinde ad veya kullanıcı adıyla personel seçimi,
- yoğun saat, en çok kullanılan oda ve kişi/departman kullanım raporları,
- dakika veya 30 dakika hassasiyetli rezervasyon,
- frontend ekranı ve takvim entegrasyonu.

Şimdiden generic talep motoru, katılımcı tablosu, seri tablosu veya bildirim altyapısı eklenmez.

## 4. Seçilen veri yaklaşımı

Mevcut tek satırlı saatlik aralık modeli genişletilir:

```text
Rezervasyon = tek tarih + tam sayı başlangıç saati + tam sayı bitiş saati
```

Örnek: `10:00–13:00` tek rezervasyon satırıdır. Her saat için ayrı slot satırı üretilmez ve `LocalDateTime` tabanlı dakika hassasiyetine geçilmez.

Bu yaklaşım:

- mevcut `Rezervasyon` entity'siyle uyumludur,
- PDF'deki saatlik grid sözleşmesini karşılar,
- başlık/owner/iptal bilgisini tekrar etmez,
- MVP kapsamını slot veya seri yönetimiyle büyütmez.

## 5. Katman sorumlulukları

### 5.1 Controller

Controller yalnız:

- path/query/body sözleşmesi,
- Jakarta Validation,
- `Authentication#getName` ile principal aktarımı,
- `200`, `201` ve `204` HTTP response'ları

ile ilgilenir. Repository kullanmaz; ownership, Admin yetkisi, zaman veya çakışma kararı hesaplamaz.

### 5.2 Rezervasyon service'i

Service:

- aktörün DB varlığı/aktifliği ve gerekli yerde DB Admin rolünü,
- zaman, çalışma günü, tatil, kapasite ve çakışma kurallarını,
- ownership/Admin iptal yetkisini,
- transaction ve kilit sırasını,
- güvenli DTO görünürlüğünü,
- rezervasyon/audit atomikliğini

yönetir.

Dashboard mevcut `DashboardService` üzerinden, ortak salt-okunur rezervasyon sorgu/hesap bileşenini kullanır. Dashboard controller'ına iş kuralı eklenmez.

### 5.3 Repository

Repository yalnız use-case'e özel veri erişimini taşır:

- Personel ve Oda satırını `PESSIMISTIC_WRITE` ile yükleme,
- owner kilidinden sonra rezervasyonu `PESSIMISTIC_WRITE` ile yükleme,
- aynı oda/tarih için aktif interval çakışması,
- aynı owner/tarih için odadan bağımsız aktif interval çakışması,
- tarih aralığı/oda takvim sorgusu,
- owner-only sayfalı geçmiş,
- Admin şirket-geneli sayfalı geçmiş,
- bugünkü aktif oda aralıkları,
- pasifleştirilen personelin başlamamış aktif rezervasyonları.

## 6. Rol ve görünürlük matrisi

| İşlem | PERSONEL | YONETICI | HR | ADMIN |
|---|---:|---:|---:|---:|
| Oda listesini okuma | 200 | 200 | 200 | 200 |
| Genel aktif rezervasyon takvimini okuma | 200 | 200 | 200 | 200 |
| Kendi adına rezervasyon oluşturma | 201 | 201 | 201 | 201 |
| Kendi geçmişini okuma | 200 | 200 | 200 | 200 |
| Şirket geneli geçmişi okuma | 403 | 403 | 403 | 200 |
| Kendi başlamamış aktif rezervasyonunu iptal | 204 | 204 | 204 | 204 |
| Başkasının başlamamış aktif rezervasyonunu iptal | 403 | 403 | 403 | 204 |

Bütün işlemler aktif principal ister. JWT'de Admin olup DB'de Admin olmayan aktör genel kullanıcı işlemlerini kendi adına kullanabilir fakat Admin geçmişine veya başkasını iptal etme ayrıcalığına sahip olamaz.

Rezervasyon her zaman JWT principal adına oluşturulur. Request içinde owner/personel ID kabul edilmez; Admin de başkasının adına rezervasyon oluşturamaz.

## 7. Endpoint sözleşmeleri

### 7.1 Oda lookup

```http
GET /api/rooms
```

- Bütün aktif roller erişebilir.
- Oda response'u yalnız `{ id, ad, kapasite }` taşır.
- Sıralama `ad`, eşitlikte `id` biçimindedir.
- Okuma salt okunur ve auditsizdir.

### 7.2 Genel rezervasyon takvimi

```http
GET /api/reservations?baslangicTarihi=2026-09-01&bitisTarihi=2026-09-30
GET /api/reservations?baslangicTarihi=2026-09-01&bitisTarihi=2026-09-30&odaId=1
```

- Başlangıç ve bitiş tarihleri zorunlu ve uçlar dahildir.
- Aralık ters olamaz ve en fazla 366 gün içerebilir.
- `odaId` opsiyonel ve pozitif olmalıdır.
- Pozitif fakat bulunmayan oda filtresi boş `200 OK` listesi döndürür.
- Yalnız `AKTIF` rezervasyonlar döner; iptal edilenler oda doluluğu oluşturmaz.
- Sıralama `tarih`, `baslangicSaat`, `odaId`, `rezervasyonId` biçimindedir.
- Liste salt okunur ve auditsizdir.

Takvim öğesi:

```json
{
  "id": "8f198ffe-3334-4ec0-9dc6-a28c14ca966f",
  "oda": { "id": 1, "ad": "Büyük Salon", "kapasite": 12 },
  "tarih": "2026-09-10",
  "baslangicSaat": 10,
  "bitisSaat": 12,
  "olusturan": { "ad": "Ayşe", "soyad": "Yılmaz" },
  "benMiOlusturdum": false,
  "baslik": null,
  "katilimciSayisi": null
}
```

- Oluşturanın ad/soyadı bütün aktif kullanıcılara görünür.
- Owner veya DB rolü gerçekten Admin olan aktörde `baslik` ve `katilimciSayisi` gerçek değeri taşır.
- Diğer aktörlerde bu iki alan `null` olur; backend hassas başlığı göndermez.
- Kullanıcı adı, e-posta, telefon, personel UUID veya başka hesap alanı dönmez.

### 7.3 Rezervasyon oluşturma

```http
POST /api/reservations
Content-Type: application/json

{
  "odaId": 1,
  "tarih": "2026-09-10",
  "baslangicSaat": 10,
  "bitisSaat": 12,
  "baslik": "Sprint planlama",
  "katilimciSayisi": 8
}
```

- Bütün aktif roller kendi adına oluşturabilir.
- Başlık zorunludur, trim edilir ve trim sonrası `1..50` karakterdir.
- Katılımcı sayısı oluşturan dahil toplam sayıdır; en az `1`, en fazla oda kapasitesidir.
- Başarı `201 Created` ve owner'a açık tam rezervasyon response'u döndürür.
- Uygunluk kontrollerini geçen rezervasyon onay beklemeden `AKTIF` olur.

### 7.4 Kişisel geçmiş

```http
GET /api/reservations/my?page=0&size=20
GET /api/reservations/my?durum=IPTAL_EDILDI&odaId=1&baslangicTarihi=2026-01-01&bitisTarihi=2026-12-31&page=0&size=20
```

- Principal ownership repository sorgusunun zorunlu predicate'idir.
- `page=0`, `size=20` varsayılandır; `size` aralığı `1..100` olur.
- `durum`, `odaId` ve birlikte verilen tarih aralığı opsiyonel filtrelerdir; `AND` uygulanır.
- İki tarih de yok veya ikisi birlikte bulunmalıdır.
- Sıralama `tarih DESC`, `baslangicSaat DESC`, `rezervasyonId DESC` olur.
- Aktif, geçmiş ve iptal edilmiş owner kayıtları döner.
- Response başlık, katılımcı sayısı, `gecmisMi`, durum ve nullable iptal alanlarını taşır.

### 7.5 Admin geçmişi

```http
GET /api/admin/reservations?page=0&size=20
GET /api/admin/reservations?durum=AKTIF&odaId=1&olusturanId=<uuid>&page=0&size=20
```

- Yalnız aktif ve DB rolü gerçekten `ADMIN` olan aktör erişebilir.
- Aktif/pasif bütün owner'ların aktif, geçmiş ve iptal edilmiş kayıtları döner.
- Kişisel geçmiş filtrelerine ek olarak canonical `olusturanId` filtresi bulunur.
- Geçerli fakat bulunmayan owner UUID boş sayfa döndürür; canonical olmayan UUID `400` olur.
- Admin bütün başlık, katılımcı ve iptal ayrıntılarını görür.
- Ad/kullanıcı adı lookup bu MVP'nin kapsamında değildir.

İki geçmiş endpoint'i mevcut `SayfaliResponse` sözleşmesini kullanır.

### 7.6 İptal

```http
PATCH /api/reservations/{rezervasyonId}/cancel
Content-Type: application/json

{
  "iptalNedeni": "Oda bakım çalışması nedeniyle kapatıldı."
}
```

- Owner kendi rezervasyonunda body göndermeyebilir; nedeni opsiyoneldir.
- Admin kendi rezervasyonunda owner kuralına tabidir.
- Admin başkasının rezervasyonunda trim sonrası `1..500` karakter iptal nedeni vermek zorundadır.
- Owner dışındaki PERSONEL/YONETICI/HR `403` alır.
- Yalnız henüz başlamamış `AKTIF` rezervasyon iptal edilebilir.
- Başlamış, geçmiş veya zaten iptal edilmiş rezervasyon `409` döndürür.
- Başarı `204 No Content` döner; kayıt fiziksel silinmez.

## 8. Zaman, çalışma günü ve kapasite kuralları

Sistem saati doğrudan kullanılmaz; `Europe/Istanbul` semantiği taşıyan enjekte edilmiş `Clock` kullanılır.

Yapılandırılabilir varsayılanlar:

```properties
rezervasyon.calisma-baslangic-saati=8
rezervasyon.calisma-bitis-saati=18
rezervasyon.yarim-gun-bitis-saati=13
rezervasyon.maksimum-sure-saat=4
rezervasyon.maksimum-ileri-gun=90
```

Yapılandırma startup'ta doğrulanır:

- başlangıç bitişten küçüktür,
- yarım gün bitişi normal çalışma aralığındadır,
- maksimum süre pozitiftir ve çalışma aralığını aşmaz,
- maksimum ileri gün pozitiftir.

Rezervasyon kuralları:

- saatler tam sayıdır,
- başlangıç dahil, bitiş hariç interval kullanılır,
- en az `1`, varsayılan en fazla `4` saat sürer,
- normal iş gününde varsayılan `08:00–18:00` içinde kalır,
- bugün için başlangıç, mevcut saatten sonraki ilk tam saatten erken olamaz; örneğin `10:20` anında en erken `11:00`, tam `10:00` anında da başlamış slot kabul edildiğinden en erken `11:00` olur,
- en geç bugün + varsayılan 90 gün için oluşturulabilir,
- cumartesi/pazar oluşturulamaz,
- tam gün tatilde oluşturulamaz,
- yarım gün tatil `13:00'te paydos` kabul edilir ve yalnız `08:00–13:00` aralığı kullanılabilir,
- katılımcı sayısı oda kapasitesini aşamaz.

Tatil kararı mevcut gerçek `Tatil` ilişkisi üzerinden verilir; frontend'den tatil/iş günü bilgisi kabul edilmez.

## 9. Çakışma semantiği

İki interval şu koşulda çakışır:

```text
yeni.baslangic < mevcut.bitis
AND
yeni.bitis > mevcut.baslangic
```

`10–11` ile `11–12` çakışmaz.

Create sırasında iki ayrı değişmez korunur:

1. Aynı oda, aynı tarih ve ortak saat aralığında başka `AKTIF` rezervasyon olamaz.
2. Aynı owner, aynı tarih ve ortak saat aralığında başka odada dahi `AKTIF` rezervasyon oluşturamaz.

`IPTAL_EDILDI` kayıtlar iki çakışma sorgusuna da katılmaz.

## 10. Transaction ve kilit sırası

### 10.1 Create

Transaction sırası:

1. JWT principal aktörü Personel tablosundan yükle ve aktifliğini doğrula.
2. Owner olan aktör Personel satırını `PESSIMISTIC_WRITE` ile kilitle.
3. Başlık, tarih, saat, tatil ve süre kurallarını doğrula.
4. Oda satırını `PESSIMISTIC_WRITE` ile kilitle.
5. Katılımcı/kapasite kuralını doğrula.
6. Aynı oda interval çakışmasını sorgula.
7. Aynı owner interval çakışmasını sorgula.
8. Rezervasyonu `saveAndFlush` ile yaz.
9. Audit'i aynı transaction içinde yaz.

Aynı owner'ın farklı odalardaki paralel create işlemleri Personel kilidiyle; farklı owner'ların aynı odadaki paralel create işlemleri Oda kilidiyle seri hale gelir.

### 10.2 Manual iptal

Deadlock riskini azaltmak için hedef owner → rezervasyon kilit sırası korunur:

1. Aktörün DB varlığını/aktifliğini doğrula.
2. Rezervasyonu salt okunur snapshot ile bul ve değişmez owner ID'sini al.
3. Hedef owner Personel satırını `PESSIMISTIC_WRITE` ile kilitle.
4. Rezervasyonu `PESSIMISTIC_WRITE` ile yeniden yükle.
5. Ownership veya gerçek DB Admin yetkisini durum kontrolünden önce doğrula.
6. Durum ve başlamamış olma kuralını doğrula.
7. Gerekli Admin nedenini normalize/doğrula.
8. Durum, iptal eden, zaman ve nedeni yazıp `saveAndFlush` et.
9. Audit'i aynı transaction içinde yaz.

Paralel iki iptalin yalnız biri başarılı olur; ikincisi güncel durumu görüp `409` alır.

### 10.3 Personel pasifleştirme entegrasyonu

Personel pasifleştirme mevcut transaction'ında:

1. hedef Personel satırı kilitlenir,
2. hedefin henüz başlamamış bütün `AKTIF` rezervasyonları kilitli biçimde yüklenir,
3. her biri `IPTAL_EDILDI` yapılır,
4. `iptalEden`, pasifleştirme aktörü; neden `Personel pasife alındı.` olur,
5. her değişen rezervasyon için güvenli iptal auditi yazılır,
6. mevcut personel pasifleştirme audit detayı iptal edilen rezervasyon sayısını güvenli biçimde taşır.

Personel, rezervasyonlar ve auditler aynı transaction'da tamamlanır veya birlikte rollback olur. Create önce commit ederse pasifleştirme yeni rezervasyonu iptal eder; pasifleştirme önce commit ederse create aktiflik kontrolünde `403` alır.

## 11. Veri modeli

### 11.1 `Oda`

Mevcut alanlar korunur:

- `odaId`
- `odaAd` unique, en fazla 50 karakter
- `odaKapasite` pozitif

MVP'de `aktif`, bakım durumu veya ekipman ilişkisi eklenmez.

### 11.2 `Rezervasyon`

Mevcut alanlar:

- UUID `rezervasyonId`
- `olusturan`
- `baslik`
- `tarih`
- `baslangicSaat`
- `bitisSaat`
- `oda`

Yeni alanlar:

- `katilimciSayisi` non-null,
- `durum` non-null: `AKTIF | IPTAL_EDILDI`,
- `olusturmaZamani` non-null,
- nullable `iptalZamani`,
- nullable `iptalEden` Personel ilişkisi,
- nullable `iptalNedeni` en fazla 500 karakter.

Geçmiş fakat iptal edilmemiş kayıt DB'de `AKTIF` kalır; bu değer rezervasyonun iptal edilmediğini ifade eder. Response'taki `gecmisMi` tarih/saat ve Clock ile hesaplanır; günlük durum güncelleyen scheduled job eklenmez.

## 12. Migration ve indeksler

Yeni idempotent manual MySQL migration:

- rezervasyon tablosuna yeni kolonları ekler,
- eski rezervasyonlarda `durum='AKTIF'`, `katilimci_sayisi=1` ve güvenli migration zamanı tabanlı `olusturma_zamani` backfill'i yapar,
- kolonları backfill sonrasında gerekli nullability ile sabitler,
- eski `(tarih, oda_id, baslangic_saat)` unique constraint'ini kaldırır,
- oda/tarih/durum/saat ve owner/tarih/durum sorgu indekslerini ekler,
- Admin tarih sıralaması için gerekli indeksi ekler,
- `Büyük Salon (12)` ve `Küçük Oda (6)` seed'lerini ad unique anahtarı üzerinden idempotent upsert eder,
- mevcut başka oda/verileri silmez,
- audit ENUM kümesine mevcut bütün değerleri koruyarak yeni rezervasyon olaylarını ekler.

Rezervasyon durumu MySQL native `ENUM` yerine genişletilebilir `VARCHAR(30)` olarak saklanır; JPA `EnumType.STRING` kullanır. Uygun bir DB constraint ile yalnız tanımlı iki değer kabul edilir.

Unique başlangıç constraint'i iptal edilmiş aynı slotta yeni rezervasyonu engelleyeceği ve genel interval çakışmasını zaten çözemediği için kaldırılır. Yarış güvenliği Personel/Oda kilidi + transaction içi interval sorgusuyla sağlanır.

Migration gerçek MySQL testinde:

- legacy tablo ve mevcut rezervasyon fixture'ı,
- kolon/backfill/nullability,
- constraint'in kaldırılması,
- indekslerin kolon sırası,
- iki seed ve kapasiteleri,
- mevcut odaların korunması,
- audit Java enum kümesiyle exact uyum ve gerçek insert,
- ikinci çalıştırmanın idempotentliği

doğrulanır. `ddl-auto=update` mevcut MySQL audit ENUM genişlemesinin yerine geçmez.

## 13. Audit

Yeni olaylar:

- `REZERVASYON_OLUSTURULDU`
- `REZERVASYON_IPTAL_EDILDI`

| İşlem | Aktör | Hedef | Detay |
|---|---|---|---|
| Create | Owner | Owner | `Rezervasyon oluşturuldu.` |
| Owner iptali | Owner | Owner | `Rezervasyon iptal edildi.` |
| Admin iptali | Admin | Owner | `Rezervasyon iptal edildi.` |
| Pasifleştirme iptali | HR/Admin | Pasifleştirilen owner | `Rezervasyon iptal edildi.` |

Audit detayına rezervasyon UUID, oda, tarih/saat, başlık, katılımcı sayısı veya iptal nedeni yazılmaz. Okuma endpoint'leri ve dashboard audit üretmez.

## 14. Dashboard oda doluluk sözleşmesi

Mevcut `DashboardOzetResponse.odaDoluluk` placeholder'ı gerçek veriye bağlanır.

```json
{
  "odaId": 1,
  "odaAd": "Büyük Salon",
  "kapasite": 12,
  "anlikDurum": "DOLU",
  "dolulukYuzdesi": 30,
  "doluAraliklar": [
    { "baslangicSaat": 9, "bitisSaat": 11 },
    { "baslangicSaat": 15, "bitisSaat": 16 }
  ]
}
```

`anlikDurum`:

- `MUSAIT`: açık çalışma saatinde ve o anda aktif rezervasyon yok,
- `DOLU`: açık çalışma saatinde o anda aktif rezervasyon var,
- `KAPALI`: çalışma saati dışında, hafta sonu veya tatil; yarım gün tatilde 13:00 sonrası.

Doluluk yüzdesi:

```text
aktif rezerve saat / o günün açık çalışma saati * 100
```

- normal günde varsayılan payda 10 saat,
- yarım gün tatilde varsayılan payda 5 saat,
- kapalı tam gün/hafta sonunda yüzde 0,
- kesir oluşursa en yakın tam sayıya `HALF_UP` yuvarlanır,
- iptal edilmiş rezervasyonlar oran veya aralıklara katılmaz.

Dolu aralıklar saat sırasıyla döner; başlık, owner ve katılımcı bilgisi dashboard özetine eklenmez. Oda öğeleri kararlı ad/ID sırasında döner. Dashboard okuması salt okunur ve auditsizdir.

## 15. Hata sözleşmesi

| Koşul | Status | Güvenli mesaj |
|---|---:|---|
| Token yok/geçersiz | 401 | Mevcut authentication mesajı |
| Pasif aktör veya yetersiz ownership/Admin yetkisi | 403 | `Bu işlem için yetkiniz bulunmuyor.` |
| Principal bulunamadı | 404 | `Personel bulunamadı.` |
| Oda bulunamadı | 404 | `Oda bulunamadı.` |
| Rezervasyon bulunamadı | 404 | `Rezervasyon bulunamadı.` |
| Malformed UUID/query/body | 400 | `Geçersiz istek` |
| Geçmiş/çok ileri tarih veya bugün başlamış saat | 400 | `Rezervasyon tarihi veya saati uygun değil.` |
| Çalışma saati/süre sınırı | 400 | `Rezervasyon çalışma saatleri ve süre sınırı içinde olmalıdır.` |
| Hafta sonu | 400 | `Hafta sonu rezervasyon yapılamaz.` |
| Tam gün tatil | 400 | `Tatil gününde rezervasyon yapılamaz.` |
| Yarım gün 13:00 sonrasına taşma | 400 | `Yarım gün tatilde rezervasyon 13:00'e kadar yapılabilir.` |
| Kapasite aşımı | 400 | `Seçilen oda katılımcı sayısı için yetersiz.` |
| Aynı oda interval çakışması | 409 | `Seçilen oda bu saat aralığında dolu.` |
| Owner'ın farklı oda interval çakışması | 409 | `Bu saat aralığında başka bir rezervasyonunuz bulunuyor.` |
| Başlamış/geçmiş/iptal edilmiş kaydı iptal | 409 | `Rezervasyon artık iptal edilemez.` |
| Beklenmeyen hata | 500 | `Beklenmeyen bir hata oluştu.` |

Ham exception, SQL veya constraint ayrıntısı response'a çıkmaz. Yetkisiz/başarısız mutation rezervasyon veya audit yan etkisi bırakmaz.

## 16. Test stratejisi

### 16.1 Rol ve security

- Dört aktif rol oda/takvim/my/create kullanabilir.
- Yalnız gerçek DB Admin şirket geneli geçmişi ve başkasının iptalini kullanabilir.
- JWT Admin/DB non-Admin ayrıcalık kazanamaz.
- Pasif, missing principal ve unauthenticated istekler doğru status döner.

### 16.2 Create ve validation

- Başlık trim/uzunluk ve katılımcı minimumu.
- Büyük Salon 12, Küçük Oda 6 kapasite sınırları.
- Geçmiş, bugün başlamış saat, 90 gün üstü, çalışma saati ve 4 saat sınırı.
- Hafta sonu, tam gün tatil ve yarım gün `08:00–13:00` davranışı.
- Bitiş başlangıçtan büyük ve en az bir saat.
- Olmayan oda `404` ve atomiklik.

### 16.3 Çakışma ve concurrency

- Aynı oda aktif interval çakışması `409`.
- Aynı owner'ın farklı odası `409`.
- `10–11` ve `11–12` sınırı başarılı.
- İptal edilmiş kayıt yeni rezervasyonu engellemez.
- Gerçek MySQL'de aynı oda/farklı owner paralel create işlemlerinden yalnız biri başarılı olur.
- Gerçek MySQL'de aynı owner/farklı oda paralel create işlemlerinden yalnız biri başarılı olur.
- Kilitler farklı owner ve farklı oda rezervasyonlarını gereksiz yere global olarak seri hale getirmez.

### 16.4 Okuma ve gizlilik

- Genel takvim yalnız aktif kayıtları döndürür.
- Oluşturan ad/soyadı görünür; hesap/iletişim alanları sızmaz.
- Owner/Admin başlık ve katılımcıyı görür; diğer roller `null` alır.
- Owner `/my` sorgusunda yabancı owner içerik/count'a girmez.
- Admin aktif/pasif bütün owner geçmişini ve filtreleri görür.
- Sayfalama, tarih/oda/durum AND semantiği ve kararlı sıra doğrulanır.
- Okumalar DB/audit yan etkisi üretmez.

### 16.5 İptal ve pasifleştirme

- Owner body olmadan kendi başlamamış rezervasyonunu iptal eder.
- Admin kendi rezervasyonunda owner kuralını kullanır.
- Admin başkasında zorunlu normalize neden ile iptal eder.
- Non-owner non-Admin durum kontrolünden önce `403` alır.
- Başlamış/geçmiş/iptal edilmiş kayıt `409` alır.
- Paralel iki iptalin yalnız biri başarılı olur.
- İptal alanları ve güvenli audit exact doğrulanır.
- Dış transaction hatasında iptal ve audit birlikte rollback olur.
- Personel pasifleştirmede bütün gelecek aktif rezervasyonlar iptal edilir; geçmiş/iptal kayıt değişmez.
- Pasifleştirme, rezervasyonlar ve auditler dış transaction hatasında birlikte rollback olur.

### 16.6 Dashboard

- Normal gün `MUSAIT/DOLU`, aralık ve yüzde hesabı.
- Çalışma saati dışı `KAPALI`.
- Hafta sonu/tam gün tatil `KAPALI` ve yüzde 0.
- Yarım gün 13:00 öncesi/sonrası durum ve 5 saatlik payda.
- İptal kaydı dolulukta sayılmaz.
- Güvenli dashboard alanları ve auditsiz okuma.

### 16.7 Migration ve regresyon

- Legacy rezervasyon/audit fixture'ı üzerinden gerçek MySQL migration testi.
- Audit Java enum kümesiyle DB ENUM exact eşitliği.
- Migration ikinci çalıştırmada veri kaybetmez.
- Mevcut dashboard, personel pasifleştirme/aktifleştirme, tatil yönetimi ve security testleri korunur.
- İlgili paket, ardından tam backend suite ve JaCoCo çalıştırılır.

Zaman testlerinde sabit enjekte edilmiş `Clock` kullanılır; testler çalıştırıldığı saate veya güne bağlı olmaz.

## 17. Frontend entegrasyon notları

Frontend bu teslimin kapsamı dışındadır. Backend tamamlandığında frontend agent'a şu sözleşme aktarılır:

- saat grid'i `GET /api/reservations` tarih aralığı verisinden oluşturulur,
- boş saat seçimi POST formunu açar,
- oda lookup kapasiteyi gösterir,
- başka owner'ın kaydında başlık yerine `Dolu`, yanında oluşturan ad/soyadı gösterilir,
- owner kendi iptal eylemini, Admin bütün başlamamış aktif kayıtlarda iptal eylemini görür,
- Admin başka owner'ı iptal ederken neden modalı zorunludur,
- dashboard `odaDoluluk` alanını yeniden hesaplamaz; backend durum/aralık/yüzde değerini kullanır,
- frontend görünürlüğü backend ownership/Admin kontrolünün yerine geçmez.

## 18. Başarı ölçütleri

- Bütün aktif roller onaysız ve yalnız kendi adlarına rezervasyon oluşturabilir.
- Saat/tatil/kapasite/90 gün politikaları tek backend kaynağından uygulanır.
- Oda ve owner çakışmaları paralel isteklerde dahi engellenir.
- Genel takvim doluluğu gösterirken başlığı owner/Admin dışına sızdırmaz.
- İptal fiziksel silme yapmaz; owner/Admin geçmişi korunur.
- Admin bütün geçmişi görür ve başlamamış bütün rezervasyonları nedenli iptal edebilir.
- Personel pasifleştirme gelecekteki rezervasyonları atomik olarak iptal eder.
- Dashboard iki seed oda için doğru anlık durum, aralık ve oranı döndürür.
- Create/iptal ile audit atomiktir ve audit detayı hassas bilgi taşımaz.
- Migration mevcut veriyi korur, tekrar çalışır ve audit ENUM uyumsuzluğu üretmez.
- Mevcut backend davranışları regresyon testleriyle korunur.
