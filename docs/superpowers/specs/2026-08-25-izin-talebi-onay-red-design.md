# İzin Talebi Onay/Red ve Karar Kuyrukları Tasarımı

**Tarih:** 2026-08-25  
**Durum:** Kullanıcı tarafından 2026-08-25 tarihinde onaylandı  
**Kapsam:** Backend izin talebi karar akışı; frontend ve owner iptali kapsam dışı

## 1. Amaç

Mevcut izin talebi oluşturma ve kişinin kendi taleplerini listeleme akışını, güvenli Yönetici/Admin karar akışıyla tamamlamak:

- Yönetici yalnız mevcut aktif doğrudan çalışanlarının izin taleplerini listeler.
- Admin aktif/pasif bütün personellerin izin taleplerini listeler.
- İşlem anındaki güncel aktif doğrudan yönetici veya Admin, bekleyen talebi onaylar ya da reddeder.
- Red gerekçesi talep sahibine güvenli biçimde gösterilir.
- Durum değişikliği, işlem yapan bilgisi ve audit aynı transaction içinde tamamlanır.
- Aynı talebe paralel kararların yalnız biri başarılı olabilir.

## 2. Kapsam dışı

- Talep sahibinin bekleyen talebi iptal etmesi
- Bekleyen talebi düzenleme
- İzin türü yönetimi
- Ekipman talebi onay/red implementasyonu
- Rezervasyon akışı
- Genel/generic bir talep workflow motoru
- Frontend ekran ve API entegrasyonu

Owner iptali ayrı bir sonraki backend teslimidir.

## 3. Neden generic talep motoru oluşturulmuyor

İzin ve ekipman talepleri `TalepDurum`, `DurumTuru`, işlem yapan/tarih alanı, audit ve güvenli hata altyapısını paylaşır. Ancak karar yan etkileri aynı değildir:

- İzin onayı güncel yıllık izin bakiyesi, bekleyen rezervasyon ve personel kilidi gerektirir.
- Ekipman onayı ileride stok, fiziksel ekipman seçimi, zimmet oluşturma ve ekipman durumu değişikliği gerektirir.
- Rezervasyon onay kuyruğu değildir; uygun oda doğrudan rezerve edilir ve owner tarafından silinir.

Bu nedenle mevcut ortak temeller yeniden kullanılır fakat spekülatif `Talep<T>` veya generic karar motoru eklenmez. Ekipman akışı gerçekten uygulandığında iki somut implementasyon karşılaştırılıp yalnız kanıtlanmış tekrarlar ayrıştırılabilir.

## 4. Katman ve bileşen sorumlulukları

### 4.1 Controller

Yeni karar controller'ı yalnız şunlarla ilgilenir:

- HTTP path/query/body sözleşmesi
- `Authentication#getName` ile principal kimliğinin service'e aktarılması
- Jakarta Validation sonucu
- `200` liste ve `204` mutation response'ları

Controller repository kullanmaz ve karar yetkisi hesaplamaz.

### 4.2 `IzinTalepKararService`

Yeni service şunlardan sorumludur:

- Yönetici/Admin aktör doğrulaması
- Güncel aktör-hedef yetkisi
- Yönetici ve Admin listelerinin güvenli kapsamı
- Talep ve talep sahibi kilitleri
- Durum geçişi
- Onay anındaki güncel bakiye doğrulaması
- Red gerekçesi normalizasyonu
- İşlem yapan ve işlem zamanı
- Aynı-transaction audit
- Entity→güvenli DTO dönüşüm koordinasyonu

Mevcut `IzinTalepService` create ve owner `/my` sorumluluğunda kalır. `/my` response mapping'i yeni nullable karar alanlarını taşıyacak biçimde genişler.

### 4.3 Repository

Repository yalnız use-case'e özel sorguları taşır:

- talebi `PESSIMISTIC_WRITE` ile yükleme
- Yönetici için mevcut aktif doğrudan çalışan talepleri
- Admin için şirket geneli talepler
- opsiyonel durum filtresi
- güncel yıllık hak hesabı için mevcut owner-scoped toplam sorguları

## 5. Endpoint sözleşmesi

### 5.1 Yönetici karar listesi

```http
GET /api/leave-requests/team?page=0&size=10
GET /api/leave-requests/team?page=0&size=10&durum=ONAY_BEKLIYOR
```

- Yalnız aktif ve DB rolü gerçekten `YONETICI` olan aktör erişebilir.
- Yalnız aktörün işlem anındaki aktif doğrudan çalışanlarının talepleri döner.
- Dolaylı çalışan, başka ekip, pasif çalışan ve aktörün kendi talebi dönmez.
- Çalışan daha sonra başka yöneticiye geçtiyse geçmiş talepleri güncel yöneticinin kapsamına girer; eski yöneticiye dönmez.
- `durum` yoksa bütün durumlar döner; frontend varsayılan olarak “Hepsi” gösterebilir.

### 5.2 Admin karar listesi

```http
GET /api/admin/leave-requests?page=0&size=10
GET /api/admin/leave-requests?page=0&size=10&durum=REDDEDILDI
```

- Yalnız aktif ve DB rolü gerçekten `ADMIN` olan aktör erişebilir.
- Aktif ve pasif bütün personellerin talepleri döner.
- `durum` yoksa bütün durumlar döner.

### 5.3 Ortak liste parametreleri

- `page`: varsayılan `0`, minimum `0`
- `size`: varsayılan `10`, `1..100`
- `durum`: opsiyonel `TalepDurum`
- sıralama: `talepTarihi DESC`, eşitlikte `izinId DESC`

Response mevcut `SayfaliResponse` sözleşmesidir:

```json
{
  "icerik": [],
  "sayfa": 0,
  "sayfaBoyutu": 10,
  "toplamKayit": 0,
  "toplamSayfa": 0
}
```

Liste item örneği:

```json
{
  "id": "8f198ffe-3334-4ec0-9dc6-a28c14ca966f",
  "talepSahibi": {
    "ad": "Ayşe",
    "soyad": "Yılmaz",
    "departman": "Yazılım",
    "profilFoto": "fotosu.jpg",
    "aktifMi": true
  },
  "izinTuru": "Yıllık İzin",
  "baslangicTarihi": "2026-09-01",
  "bitisTarihi": "2026-09-05",
  "isGunuSayisi": 5.0,
  "yoneticiyeNot": "Aile ziyareti",
  "durum": "ONAY_BEKLIYOR",
  "durumAdi": "Onay bekliyor",
  "talepTarihi": "2026-08-25T10:00:00",
  "kararNotu": null,
  "islemTarihi": null,
  "kararVeren": null
}
```

Talep sahibinin ID'si, kullanıcı adı, e-posta veya telefon bilgisi response'a eklenmez.

### 5.4 Onay

```http
PATCH /api/leave-requests/{talepId}/approve
```

- Request body yoktur.
- Başarı: `204 No Content`.
- Frontend başarı sonrası listeyi yeniden getirir.

### 5.5 Red

```http
PATCH /api/leave-requests/{talepId}/reject
Content-Type: application/json

{
  "kararNotu": "Ekip planlaması nedeniyle uygun değil."
}
```

- `kararNotu` zorunludur.
- Trim edilir; trim sonrası uzunluk `1..500` olmalıdır.
- Başarı: `204 No Content`.
- Frontend başarı sonrası listeyi yeniden getirir.

## 6. Mevcut response'un geriye uyumlu genişletilmesi

Create ve `GET /api/leave-requests/my` tarafından kullanılan `IzinTalepResponse` şu nullable alanlarla genişletilir:

```json
{
  "kararNotu": "Ekip planlaması nedeniyle uygun değil.",
  "islemTarihi": "2026-08-25T14:30:00",
  "kararVeren": {
    "ad": "Ayşe",
    "soyad": "Yılmaz"
  }
}
```

- Bekleyen talepte üç alan da `null`.
- Onaylanan talepte `kararNotu=null`, işlem zamanı ve karar veren bulunur.
- Reddedilen talepte üç alan da bulunur.
- Karar veren response'u yalnız ad ve soyad taşır; personel ID, kullanıcı adı ve iletişim alanları açılmaz.

## 7. Rol ve hedef matrisi

| İşlem | PERSONEL | HR | YONETICI | ADMIN |
|---|---:|---:|---:|---:|
| Takım karar listesini okuma | 403 | 403 | 200 | 403 |
| Admin karar listesini okuma | 403 | 403 | 403 | 200 |
| Mevcut aktif doğrudan çalışanın bekleyen talebini onay/red | 403 | 403 | 204 | 204 |
| Başka ekip/dolaylı çalışanın talebini onay/red | 403 | 403 | 403 | 204 |
| Pasif personelin talebini onay/red | 403 | 403 | 403 | 204 |

Her mutation'da JWT authority ile DB rolü eşleşir ve aktörün aktifliği doğrulanır. Admin'in bütün talepleri işleyebilmesi bakiye ve durum değişmezlerini atlamasına izin vermez.

## 8. Durum makinesi ve karar kuralları

İzin karar akışı yalnız şu geçişleri kabul eder:

```text
ONAY_BEKLIYOR -> ONAYLANDI
ONAY_BEKLIYOR -> REDDEDILDI
```

`ONAYLANDI`, `REDDEDILDI` veya `IPTAL_EDILDI` taleplerde aynı ya da farklı karar tekrar gönderilirse idempotent no-op yapılmaz:

```http
409 Talep artık işlem beklemiyor.
```

Karar yetkisi talep oluşturma anında snapshot olarak saklanmaz. İşlem anında talep sahibinin güncel `yonetici.personelId` ilişkisi kullanılır. Eski yönetici karar veremez; yeni aktif doğrudan yönetici karar verebilir. Admin bu ilişkiye bağlı değildir.

## 9. Transaction, kilit ve bakiye akışı

Mutation service transaction sırası:

1. JWT principal aktörü DB'den yükle; aktiflik ve JWT–DB rol eşleşmesini doğrula.
2. `talepId` biçimini doğrula.
3. Talep satırını `PESSIMISTIC_WRITE` ile kilitle.
4. Talep sahibi personel satırını `PESSIMISTIC_WRITE` ile kilitle.
5. Güncel aktör-hedef yetkisini doğrula.
6. Talebin hâlâ `ONAY_BEKLIYOR` olduğunu doğrula.
7. Onayda güncel yıllık bakiye değişmezini doğrula.
8. Durum, işlem yapan, işlem zamanı ve karar notunu güncelle.
9. Talebi `saveAndFlush` ile yaz.
10. Audit'i aynı transaction içinde yaz.

İki paralel karar aynı talep satırı üzerinde seri hale gelir. İlk karar commit olduktan sonra ikinci istek yeni durumu görür ve `409` alır.

### 9.1 Onay anında bakiye

Yıllık haktan düşen izin türünde, talep sahibi kilitlendikten sonra:

```text
ONAYLANDI toplamı + ONAY_BEKLIYOR toplamı <= güncel yıllık izin hakkı
```

değişmezi doğrulanır. Mevcut karar verilen bekleyen talep bu toplamın içindedir. Personelin hakkı talep oluşturulduktan sonra azaltıldıysa ve değişmez artık sağlanmıyorsa:

```http
409 İzin bakiyesi artık yeterli değil.
```

Admin bu kontrolü atlayamaz. Yıllık haktan düşmeyen izin türünde bakiye kontrolü yapılmaz.

Red işlemi bakiye tüketmez; bekleyen rezervasyon durum `REDDEDILDI` olduğunda mevcut toplam sorgularından doğal olarak çıkar.

## 10. Veri modeli ve migration

Mevcut `IzinBilgileri` alanları kullanılmaya devam eder:

- `izin_durum_id`
- `islem_yapan_id`
- `islem_tarihi`

Yeni nullable alan:

```sql
karar_notu VARCHAR(500) NULL
```

Yeni idempotent manual migration:

- `izin_bilgileri.karar_notu` kolonunu ekler.
- MySQL `audit_log.eylem_tipi` ENUM kümesine mevcut değerleri koruyarak:
  - `IZIN_TALEBI_ONAYLANDI`
  - `IZIN_TALEBI_REDDEDILDI`
  ekler.
- Yönetici/Admin liste sorgularında gerekli talep durumu ve talep tarihi indekslerini idempotent biçimde ekler.
- Eski talepleri değiştirmez; karar alanları doğal olarak `null` kalır.

Migration testi gerçek eski MySQL ENUM fixture'ı kurar; migration öncesi yeni audit tokenlarının bulunmadığını, migration sonrasında Java enum kümesiyle exact eşitliği, gerçek insert'i, eski verinin korunmasını ve ikinci çalıştırmanın idempotentliğini doğrular. `ddl-auto=update`, mevcut MySQL ENUM genişlemesinin yerine geçmez.

## 11. Audit

Yeni audit olayları:

- `IZIN_TALEBI_ONAYLANDI`
- `IZIN_TALEBI_REDDEDILDI`

Audit sözleşmesi:

| Karar | Aktör | Hedef | Detay |
|---|---|---|---|
| Onay | Yönetici/Admin | Talep sahibi | `İzin talebi onaylandı.` |
| Red | Yönetici/Admin | Talep sahibi | `İzin talebi reddedildi.` |

Red gerekçesi, talep tarihleri, izin türü, bakiye ve kişisel bilgiler audit detayına yazılmaz. Talep mutation'ı ile audit aynı transaction içinde tamamlanır veya birlikte rollback olur. Liste okumaları audit üretmez.

## 12. Hata sözleşmesi

| Koşul | Status | Güvenli mesaj |
|---|---:|---|
| Token yok/geçersiz | 401 | mevcut authentication mesajı |
| Pasif aktör, JWT–DB rol uyuşmazlığı, yasak rol/hedef | 403 | `Bu işlem için yetkiniz bulunmuyor.` |
| Aktör bulunamadı | 404 | `Personel bulunamadı.` |
| Geçerli biçimdeki talep ID'si bulunamadı | 404 | `İzin talebi bulunamadı.` |
| Geçersiz talep UUID biçimi | 400 | `Geçersiz istek` |
| Eksik/boş/500 karakterden uzun red gerekçesi | 400 | `Geçersiz istek` |
| Talep artık beklemiyor | 409 | `Talep artık işlem beklemiyor.` |
| Güncel bakiye yetersiz | 409 | `İzin bakiyesi artık yeterli değil.` |
| Beklenmeyen persistence/uygulama hatası | 500 | `Beklenmeyen bir hata oluştu.` |

Ham exception, SQL veya constraint ayrıntısı response'a çıkmaz. Yetkisiz veya başarısız mutation talep, karar alanı ya da audit yan etkisi bırakmaz.

## 13. Test stratejisi

### 13.1 Migration

- Legacy audit ENUM'da yeni tokenlar yoktur.
- `karar_notu` nullable ve doğru uzunluktadır.
- Audit ENUM migration sonrası Java enumuyla exact eşleşir.
- Eski talep/audit verisi korunur.
- Migration ikinci kez çalışır ve veri kaybetmez.

### 13.2 Liste ve görünürlük

- Yönetici yalnız aktif doğrudan çalışanlarını görür.
- Başka ekip, dolaylı çalışan, pasif çalışan ve kendi talebi sızmaz.
- Yönetici değiştiğinde görünürlük güncel ilişkiye geçer.
- Admin aktif/pasif şirket geneli talepleri görür.
- Filtresiz istek bütün durumları getirir.
- Her `TalepDurum` filtresi `AND` semantiğiyle çalışır.
- Sayfalama, toplam metadata ve kararlı sıralama doğrudur.
- Liste read-only ve auditsizdir.

### 13.3 Karar mutation'ları

- Yönetici kendi aktif doğrudan çalışanını onaylar/reddeder.
- Admin aktif/pasif bütün hedefleri onaylar/reddeder.
- PERSONEL, HR, başka/eski/dolaylı Yönetici `403` alır.
- Pasif/missing aktör ve unauthenticated istek doğru status alır.
- Missing/malformed talep ID doğru status alır.
- Red gerekçesi normalize edilir, zorunlu ve en fazla 500 karakterdir.
- İşlem yapan ve sabit Clock tabanlı işlem zamanı yazılır.
- Daha önce işlem görmüş talepte ikinci karar `409` olur.
- Onayda düşürülen güncel hak `409` üretir; nonannual tür etkilenmez.
- Paralel iki kararın yalnız biri başarılı olur.
- Gerçek karar exact güvenli audit üretir.
- Dış transaction hatasında karar ve audit birlikte rollback olur.
- Beklenmeyen persistence hatası güvenli generic `500` döner.

### 13.4 Regresyon

- Create response yeni nullable alanlarla çalışır.
- `/my` karar notu, işlem zamanı ve güvenli karar veren adını gösterir.
- Bekleyen rezervasyon, profil approved-only bakiyesi ve etkili overlap davranışı korunur.
- İzin lookup/preview/create/my rol matrisleri değişmez.
- İlgili security ve tam backend suite/JaCoCo çalıştırılır.

## 14. Frontend entegrasyon notları

- Yönetici/Admin ekranı ilk açılışta `durum` parametresi göndermeden “Hepsi”ni gösterir.
- Filtre seçildiğinde enum kodu query'ye eklenir.
- Onay body göndermez; red yalnız `{ kararNotu }` gönderir.
- Başarılı `204` sonrasında liste yeniden istenir.
- Karar görmüş kayıtta mutation butonları gösterilmez.
- Frontend rol/ilişki tahmini backend yetkisinin yerine geçmez; `403/409` güvenli mesajları ele alınır.

## 15. Başarı ölçütleri

- Onay/red yalnız tanımlı güncel Yönetici/Admin yetkisiyle yapılır.
- Admin bütün şirket kayıtlarını, Yönetici yalnız aktif doğrudan çalışanlarını görür.
- “Hepsi” görünümü durum filtresi olmadan çalışır.
- Karar sonucu owner `/my` response'unda güvenli biçimde görünür.
- Aynı talebe paralel karar çift yazım oluşturmaz.
- Onay anında güncel bakiye değişmezi korunur.
- Talep kararı ile audit atomiktir.
- Migration mevcut MySQL ortamlarında tekrarlanabilir ve audit ENUM uyumludur.
- Mevcut izin create/list/bakiye/overlap davranışları geriye uyumludur.
