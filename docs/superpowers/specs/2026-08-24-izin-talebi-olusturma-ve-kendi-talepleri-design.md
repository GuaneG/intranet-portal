# İzin Talebi Oluşturma ve Kendi Taleplerini Listeleme Tasarımı

**Tarih:** 2026-08-24  
**Durum:** Kullanıcı onaylı tasarım; implementasyon başlamadı

## 1. Amaç ve kapsam

Bu teslim, hazır durumdaki izin entity'lerini, `BigDecimal` tabanlı iş günü hesabını ve tatil takvimini ilk kullanılabilir izin talebi akışına dönüştürür.

Kapsam:

- güvenli izin türü lookup,
- uygun roller için iş günü önizlemesi,
- kişinin kendi izin talebini oluşturması,
- kişinin kendi taleplerini sayfalı ve durum filtreli listelemesi,
- yıllık izin bakiyesinin bekleyen taleplerle rezerve edilmesi,
- etkili iş günü çakışmalarının engellenmesi,
- başlangıç durumları, izin türleri ve audit olayı için kalıcı MySQL migration,
- transaction, concurrency, audit ve HTTP testleri.

Kapsam dışı:

- talep iptali,
- yönetici/Admin bekleyen talep listeleri,
- onaylama ve reddetme,
- hizmet yılına göre 14/20/26 gün tahakkuk ve devreden hak,
- izin türü yönetim mutation'ları,
- frontend implementasyonu.

## 2. Mevcut altyapı

Mevcut kodda `IzinBilgileri`, `IzinTuru`, `DurumTuru`, `TalepDurum`, izin request/response DTO iskeletleri, yarım gün destekli `isGunuSayisi`, `IsGunuHesaplamaService` ve `GET /api/leave-requests/workday-preview` bulunmaktadır. Eksik parçalar gerçek mutation, kişisel liste, izin türü lookup, kalıcı seed ve yeni audit enum migration'ıdır.

## 3. Endpoint sözleşmeleri

### 3.1 İzin türü lookup

```http
GET /api/leave-types
```

```json
[
  {
    "id": 1,
    "ad": "Yıllık İzin",
    "yillikHaktanDuserMi": true
  }
]
```

Response alfabetik sıralı, read-only ve auditsizdir. Entity doğrudan dönmez.

| PERSONEL | Uygun YONETICI | Kök YONETICI | HR | ADMIN |
|---:|---:|---:|---:|---:|
| 200 | 200 | 403 | 200 | 200 |

Uygun Yönetici, veritabanında atanmış, aktif ve `YONETICI` rolündeki bir yöneticisi bulunan aktif Yönetici aktördür. Admin lookup'ı kullanabilir ancak kendi adına izin talebi oluşturamaz.

### 3.2 İş günü önizlemesi

Mevcut endpoint korunarak uygun Yönetici rolüne genişletilir:

```http
GET /api/leave-requests/workday-preview?baslangicTarihi=2026-09-01&bitisTarihi=2026-09-05
```

```json
{ "isGunuSayisi": 4.0 }
```

| PERSONEL | Uygun YONETICI | Kök YONETICI | HR | ADMIN |
|---:|---:|---:|---:|---:|
| 200 | 200 | 403 | 200 | 403 |

Önizleme yalnız hesaplama yapar; talep veya audit oluşturmaz.

### 3.3 İzin talebi oluşturma

```http
POST /api/leave-requests
Content-Type: application/json

{
  "izinTuruId": 1,
  "baslangicTarihi": "2026-09-01",
  "bitisTarihi": "2026-09-05",
  "yoneticiyeNot": "Aile ziyareti"
}
```

Başarılı response `201 Created` olur:

```json
{
  "id": "8f198ffe-3334-4ec0-9dc6-a28c14ca966f",
  "izinTuru": "Yıllık İzin",
  "baslangicTarihi": "2026-09-01",
  "bitisTarihi": "2026-09-05",
  "isGunuSayisi": 4.0,
  "yoneticiyeNot": "Aile ziyareti",
  "durum": "ONAY_BEKLIYOR",
  "durumAdi": "Onay bekliyor",
  "talepTarihi": "2026-08-24T10:30:00"
}
```

Aktör yalnız JWT principal'dan belirlenir. Request'ten personel, yönetici, iş günü sayısı, durum, audit aktörü veya karar veren kişi kabul edilmez.

| PERSONEL | Uygun YONETICI | Kök YONETICI | HR | ADMIN |
|---:|---:|---:|---:|---:|
| 201 | 201 | 403 | 201 | 403 |

`PERSONEL` ve `HR` için atanmış yönetici bulunmaması oluşturmayı engellemez; talep ileride Admin tarafından işlenebilir. Yönetici rolündeki aktörün kendi üzerinde aktif ve gerçekten `YONETICI` rolünde bir yönetici ilişkisi bulunmalıdır.

### 3.4 Kendi taleplerini listeleme

```http
GET /api/leave-requests/my?page=0&size=10&durum=ONAY_BEKLIYOR
```

- `page` varsayılan `0`, minimum `0`.
- `size` varsayılan `10`, izin verilen aralık `1..100`.
- `durum` opsiyonel `TalepDurum` kodudur; enum dışı değer `400` döner.
- Sıralama `talepTarihi DESC`, eşitlikte `izinId DESC` olur.
- Sonuç `SayfaliResponse<IzinTalepResponse>` biçimindedir.
- Repository sorgusu principal ID'sini zorunlu ownership koşulu olarak uygular.

Erişim talep oluşturma matrisiyle aynıdır. Liste read-only ve auditsizdir.

## 4. DTO kararları

`IzinTuruLookupResponse` yalnız `id`, `ad`, `yillikHaktanDuserMi` alanlarını taşır.

`IzinTalepCreateRequest`:

- pozitif ve zorunlu `izinTuruId`,
- zorunlu `baslangicTarihi` ve `bitisTarihi`,
- opsiyonel `yoneticiyeNot`.

Not trim edilir; trim sonrası boş değer `null`, maksimum uzunluk 1000 karakter olur.

`IzinTalepResponse` şu alanları taşır:

- `id`,
- `izinTuru`,
- `baslangicTarihi`,
- `bitisTarihi`,
- `isGunuSayisi`,
- `yoneticiyeNot`,
- kararlı enum kodu `durum`,
- kullanıcı metni `durumAdi`,
- `talepTarihi`.

## 5. Yetki ve aktör doğrulaması

Önizleme ve talep işlemleri tek ortak izin-aktörü yetki bileşenini kullanır:

1. JWT principal personeli DB'den yüklenir; yoksa `404`.
2. Aktör pasifse `403`.
3. JWT authority ile DB rolü uyuşmuyorsa `403`.
4. Aktif `PERSONEL` ve `HR` kabul edilir.
5. `YONETICI` için atanmış yöneticinin mevcut, aktif ve gerçekten `YONETICI` rolünde olması gerekir.
6. Kök `YONETICI` ve `ADMIN` talep/önizleme için `403` alır.

`GET /api/leave-types` Admin için bilinçli istisnadır. Frontend görünürlüğü yetkilendirme sayılmaz; kararlar service katmanında uygulanır.

## 6. Tarih ve iş günü kuralları

- Başlangıç ve bitiş zorunludur.
- Bitiş başlangıçtan önce olamaz.
- İki uç dahil en fazla 366 günlük aralık kabul edilir.
- Başlangıç ve bitiş geçmiş tarih olamaz.
- İş günü sayısı `IsGunuHesaplamaService` tarafından yeniden hesaplanır.
- Sonuç `0.0` ise talep `400` ile reddedilir.
- Hafta sonu ve tam/yarım günlük tatil davranışı mevcut ortak hesapla aynıdır.
- Frontend önizleme sonucu POST için güvenilir girdi değildir.

## 7. Bakiye ve rezervasyon

Bu teslimde hak kaynağı mevcut `Personel.yillikIzinHakki` alanıdır.

```text
kullanılan = ONAYLANDI ve yillikHaktanDuserMi=true toplamı
rezerve = ONAY_BEKLIYOR ve yillikHaktanDuserMi=true toplamı
kullanılabilir = yillikIzinHakki - kullanılan - rezerve
```

Yıllık haktan düşen yeni talep kullanılabilir bakiyeyi aşarsa `400 Yetersiz izin bakiyesi.` döner. Yıllık haktan düşmeyen türler bakiye kontrolü ve rezervasyondan muaftır.

Profildeki `kalanIzin` mevcut anlamını korur ve yalnız onaylanmış tüketimi düşer. Bekleyen rezervasyon bu alanı değiştirmez; ayrı bir kullanılabilir/rezerve bakiye sözleşmesi bu teslimde eklenmez.

## 8. Çakışma davranışı

Yalnız aynı personele ait `ONAY_BEKLIYOR` ve `ONAYLANDI` talepler adaydır. `REDDEDILDI` ve `IPTAL_EDILDI` kayıtlar dikkate alınmaz.

Repository önce takvim aralığı kesişen adayları getirir. Her adayla kesişim aralığının etkili iş günü sayısı ortak hesaplama servisiyle bulunur:

- kesişimde `0.5` veya daha fazla iş günü varsa `409 Conflict`,
- kesişim yalnız hafta sonu veya tam günlük tatilden oluşuyorsa çakışma sayılmaz.

Mesaj: `Seçilen tarih aralığında mevcut bir izin talebiniz bulunuyor.`

## 9. Transaction ve concurrency

Talep oluşturma tek transaction'dır:

1. Aktör personel satırı `PESSIMISTIC_WRITE` ile yüklenir.
2. Rol ve aktiflik yeniden doğrulanır.
3. İzin türü ve `ONAY_BEKLIYOR` durum satırı yüklenir.
4. Tarih ve iş günü doğrulanır.
5. Çakışan bekleyen/onaylı kayıtlar kontrol edilir.
6. Onaylanmış tüketim ve bekleyen rezervasyon hesaplanır.
7. Talep kaydedilip flush edilir.
8. Audit aynı transaction'a eklenir.

Personel satırı kilidi aynı kişinin paralel create isteklerini seri hale getirir. Böylece iki istek aynı bakiye veya boş çakışma görünümünü birlikte geçemez. Farklı personeller gereksiz yere birbirini kilitlemez. Talep veya audit hatasında işlem bütünüyle rollback olur.

## 10. Audit

Yeni olay: `IZIN_TALEBI_OLUSTURULDU`.

- Aktör: JWT principal personeli
- Hedef: aynı personel
- Detay: `İzin talebi oluşturuldu.`

Not, izin türü, tarihler, iş günü ve bakiye audite yazılmaz. Lookup, önizleme, kişisel liste ve başarısız oluşturma audit üretmez.

Bu teslimde yalnız oluşturma olayı Java ve MySQL audit ENUM sözleşmesine eklenir. Sonraki teslimlerde ilgili mutation gerçekten implemente edilirken aşağıdaki olaylar ayrı migration ile eklenir; kullanılmayan audit değerleri bugünden spekülatif olarak şemaya veya Java enum'una eklenmez.

| İşlem | Audit olayı | Aktör | Hedef | Güvenli detay |
|---|---|---|---|---|
| Talep oluşturma | `IZIN_TALEBI_OLUSTURULDU` | Talep sahibi | Talep sahibi | `İzin talebi oluşturuldu.` |
| Talep iptali | `IZIN_TALEBI_IPTAL_EDILDI` | Talep sahibi | Talep sahibi | `İzin talebi iptal edildi.` |
| Talep onayı | `IZIN_TALEBI_ONAYLANDI` | Güncel yetkili yönetici veya Admin | Talep sahibi | `İzin talebi onaylandı.` |
| Talep reddi | `IZIN_TALEBI_REDDEDILDI` | Güncel yetkili yönetici veya Admin | Talep sahibi | `İzin talebi reddedildi.` |
| Talep güncelleme | `IZIN_TALEBI_GUNCELLENDI` | Talep sahibi | Talep sahibi | `İzin talebi güncellendi.` |

`IZIN_TALEBI_GUNCELLENDI` yalnız ileride talep düzenleme özelliği ayrıca onaylanıp implemente edilirse kullanılır. Her mutation auditi ana işlemle aynı transaction içinde tamamlanır; validation, yetki reddi, no-op ve read-only işlemler audit oluşturmaz.

## 11. Katman sorumlulukları

- `IzinTalepController`: create ve `/my` HTTP akışı.
- Mevcut `IzinHesaplamaController`: yalnız önizleme.
- `IzinTuruController`: lookup.
- Ortak izin-aktörü yetki bileşeni: önizleme/create/liste rol uygunluğu.
- `IzinTalepService`: validation, hesap, bakiye, çakışma, persistence ve audit.
- `IzinOnizlemeService`: mevcut hesap sorumluluğu ve ortak yetki kullanımı.
- `IzinTuruRepository`: alfabetik lookup ve ID ile bulma.
- `DurumTuruRepository`: enum koduyla durum bulma.
- `IzinBilgileriRepository`: owner-scoped sayfalama, tüketim, rezervasyon ve çakışma adayları.
- `PersonelRepository`: personel ID ile pessimistic-write lookup.

Controller business kuralı uygulamaz. Repository yetki kararı vermez. Entity response sözleşmesi olmaz.

## 12. Hata sözleşmesi

| Durum | Status |
|---|---:|
| Token yok/geçersiz | 401 |
| Yasak rol, pasif aktör, JWT–DB rol uyuşmazlığı | 403 |
| Principal, izin türü veya zorunlu durum kaydı yok | 404 |
| Eksik/geçersiz body, tarih, sayfa veya enum | 400 |
| Sıfır iş günü | 400 |
| Yetersiz izin bakiyesi | 400 |
| Etkili iş günü çakışması | 409 |
| Beklenmeyen persistence hatası | Güvenli 500 |

Ham SQL, constraint veya exception ayrıntısı dönmez. Hatalı işlem kısmi izin/audit kaydı bırakmaz.

## 13. MySQL migration

Yeni idempotent script:

```text
backend/db/manual-migrations/2026-08-24-add-leave-request-foundation.sql
```

Script:

1. `durum_turu.durum_adi` ENUM listesini bütün `TalepDurum` kodlarıyla eşitler.
2. `ONAY_BEKLIYOR`, `ONAYLANDI`, `REDDEDILDI`, `IPTAL_EDILDI` kayıtlarını yalnız eksikse ekler.
3. `Yıllık İzin / true`, `Hastalık İzni / false`, `Mazeret İzni / false` kayıtlarını yalnız eksikse ekler.
4. `audit_log.eylem_tipi` ENUM listesine mevcut değerleri koruyarak `IZIN_TALEBI_OLUSTURULDU` ekler.
5. Kişisel tarih/durum sorgusu ve talep zamanı sıralaması için gereken indeksleri yalnız eksikse ekler.
6. Tekrar çalıştırıldığında ID değiştirmez, kayıt silmez ve duplicate üretmez.

Uygulama sabit auto-increment ID'lerine güvenmez. Hibernate `ddl-auto=update`, mevcut MySQL ENUM listesini güvenilir biçimde genişletmez; production endpoint'i açılmadan önce migration uygulanır.

## 14. Test stratejisi

TDD küçük RED/GREEN davranış döngüleriyle uygulanır.

### Migration

- Eski enum tanımlı MySQL şemasına uygulama
- Dört durum ve üç izin türü seed'i
- Audit enum genişlemesi
- İndekslerin varlığı
- İkinci çalıştırmada veri ve ID korunması

### Lookup ve önizleme

- Güvenli/alfabetik lookup
- Aktif PERSONEL, HR, uygun YONETICI ve lookup için ADMIN
- Kök Yönetici, pasif/missing principal ve JWT–DB rol uyuşmazlığı
- Önizlemede uygun Yönetici genişlemesi ve Admin yasağı

### Talep oluşturma

- PERSONEL, HR ve uygun Yönetici `201`
- Kök Yönetici ve Admin `403`
- Pasif/missing aktör ve rol uyuşmazlığı
- Tarih, izin türü ve not validation'ları
- Sıfır iş günü
- Backend hesabı ve `ONAY_BEKLIYOR`
- Güvenli response

### Bakiye, çakışma ve concurrency

- Onaylanmış tüketim ve bekleyen rezervasyon
- Tam bakiye ve aşım
- Haktan düşmeyen tür
- Bekleyen/onaylı etkili iş günü çakışması
- Reddedilen/iptal edilen kayıt
- Yalnız hafta sonu/tam tatil kesişimi
- Gerçek MySQL'de paralel aynı-personel create
- Farklı personellerin bağımsızlığı

### Audit, liste ve regresyon

- Tek güvenli audit, doğru aktör/hedef
- Transaction rollback
- Owner-only `SayfaliResponse`, filtre ve kararlı sıralama
- Profil bakiyesi, önizleme, tatil, authentication/security regresyonları
- Tam backend suite ve JaCoCo

## 15. Dokümantasyon

Implementasyon tamamlandığında:

- `API_SPEC.md`: planlanan endpoint'ler uygulanmış exact sözleşmeye çevrilir.
- `SECURITY.md`: aktör matrisi, ownership, kilit, rezervasyon ve audit eklenir.
- `ROADMAP.md`: lookup/create/my tamamlanır; eski “Yönetici tamamen oluşturamaz” satırı düzeltilir.
- `PROGRESS.md`: migration, dosyalar ve test kanıtları eklenir.
- Kök ve frontend README'lerindeki tamamlanmış doğum günü entegrasyonunu hâlâ bekliyor gösteren ifadeler düzeltilir.

## 16. Sonraki teslim

1. Owner için bekleyen talebi iptal etme
2. Yönetici için güncel doğrudan çalışanların bekleyen taleplerini listeleme
3. Admin için şirket geneli talepleri listeleme
4. Güncel aktif doğrudan yönetici veya Admin tarafından onay/red
5. Karar anında bakiye ve durum concurrency kontrolü
6. Hizmet yılı ve devreden hak modeli

## 17. Çözülmemiş kararlar

Onaylanan kapsam açısından çözülmemiş ürün, güvenlik veya endpoint kararı bulunmamaktadır.
