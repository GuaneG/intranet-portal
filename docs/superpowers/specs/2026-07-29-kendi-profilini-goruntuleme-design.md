# Kendi Profilini Görüntüleme Tasarımı

**Durum:** Uygulandı ve odaklı entegrasyon testleriyle doğrulandı.

## Amaç

Authenticated kullanıcının kendi profilini tek endpoint üzerinden görüntülemesini sağlamak. Response mevcut `ProfilBilgileriResponse` DTO’sunu kullanır; personel temel bilgileri, yetenekleri ve role uygun kalan izin bilgisi gerçek veriden üretilir.

## Endpoint

```text
GET /api/profil
Yetki: authenticated bütün roller
Request body: yok
Başarılı cevap: 200 OK + ProfilBilgileriResponse
```

Aktör/personel kimliği yalnız doğrulanmış JWT principal’dan alınır. Path, query veya request body üzerinden personel ID kabul edilmez.

## Mimari

```text
ProfilController
    -> ProfilService
        -> PersonelRepository
        -> PersonelYetenekRepository
        -> IzinBilgileriRepository
    -> ProfilBilgileriResponse
```

Profil görüntüleme sorumluluğu, oluşturma/güncelleme/pasifleştirme işlerini taşıyan `PersonelService` içine eklenmez. Yeni `ProfilService` yalnız profil okuma ve DTO eşleme sorumluluğunu taşır.

Service metodu:

```text
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
ProfilBilgileriResponse profilimiGetir(String personelId)
```

`ProfilController`, mevcut `ParolaService` bağımlılığına ek olarak `ProfilService` kullanır. `GET /api/profil` yalnız `authentication.getName()` değerini service’e aktarır.

## Response Sözleşmesi

Yeni response DTO oluşturulmaz; mevcut `ProfilBilgileriResponse` doğrudan kullanılır.

Alan eşlemeleri:

- `kullaniciAdi`: `Personel.kullaniciAdi`
- `ad`: `Personel.adi`
- `soyad`: `Personel.soyadi`
- `dogumTarihi`: `Personel.dogumTarihi`
- `ePosta`: `Personel.ePosta`
- `sicilNo`: `Personel.sicilNumarasi`
- `departman`: departman adı
- `rol`: `RolAdi` enum değerinin metin karşılığı
- `yonetici`: varsa yöneticinin `Ad Soyad` değeri, yoksa `null`
- `yillikIzinHakki`: `Personel.yillikIzinHakki`
- `profilFoto`: `Personel.profilFoto`
- `kalanIzin`: role göre canlı hesap veya `null`
- `mezunOkul`: `Personel.mezunOkul`
- `mezunBolum`: `Personel.mezunBolum`
- `telNo`: `Personel.telNo`
- `isGirisTarihi`: `Personel.isGirisTarihi`
- `yetenekler`: yetenek adına göre alfabetik `List<LookUpResponse>`

`mezuniyetYili` response’a eklenmez. ROADMAP’teki “Profil response’una mezuniyet yılını aktar” maddesi uygulama doğrulandıktan sonra kaldırılır. Entity alanı HR güncelleme akışı için korunur.

Parola, parola hash’i, aktiflik, işten çıkış tarihi ve diğer güvenlik/server-controlled alanlar profile response’a eklenmez.

## Yetenek Okuma

Yeni `PersonelYetenekRepository`, hedef personelin `PersonelYetenek` kayıtlarını `Yetenek` ilişkisiyle birlikte getirir ve yetenek adına göre alfabetik sıralar. Service her kaydı:

```text
LookUpResponse(yetenekId, yetenekAdi)
```

biçimine dönüştürür.

Personelin yeteneği yoksa response `yetenekler: []` döner; `null` dönmez.

## İzin Türü ve Durum Modeli

`IzinTuru` dinamik entity olarak kalır; izin türü adı enum yapılmaz. Entity’ye `boolean yillikHaktanDuserMi` alanı eklenir. Böylece HR/Admin ileride yeni izin türleri oluşturabilir ve hangi türlerin yıllık bakiyeden düşeceği ad karşılaştırması olmadan belirlenir.

Sabit talep durumları için yeni `TalepDurum` enum’u oluşturulur:

```text
ONAY_BEKLIYOR
ONAYLANDI
REDDEDILDI
IPTAL_EDILDI
```

`DurumTuru` entity’sindeki Java alanı `durumKodu` adını alır ve mevcut `durum_adi` kolonunda `@Enumerated(EnumType.STRING)` ile enum kodunu saklar. Enum ayrıca frontend’de kullanılabilecek Türkçe `gorunenAd` değerini taşır. Mevcut lookup satırlarının Türkçe değerleri enum kodlarına bir defa dönüştürülür.

## Kalan İzin Hesabı

Yeni `IzinBilgileriRepository`, veritabanında yalnız şu kayıtların `isGunuSayisi` toplamını hesaplar:

```text
personel = authenticated personel
izinTuru.yillikHaktanDuserMi = true
izinDurum.durumKodu = TalepDurum.ONAYLANDI
```

Mazeret, hastalık, bekleyen, reddedilen veya iptal edilen izinler toplamdan düşülmez. Kayıt yoksa sorgu `0` döner.

JPA `SUM(Integer)` sonucu `Long` olduğu için repository `Long` döndürür. Service gerekli dönüşümü yaparak şu formülü uygular:

```text
kalanIzin = yillikIzinHakki - onaylanmisYillikIzinIsGunuToplami
```

Role göre davranış:

| Rol | `kalanIzin` |
|---|---|
| `PERSONEL` | canlı hesap |
| `HR` | canlı hesap |
| `YONETICI` | `null` |
| `ADMIN` | `null` |

Negatif sonucu gizleyen `Math.max(0, ...)` uygulanmaz. Mevcut izin verisi bulunmamaktadır; ileride izin talebi oluşturma service’i, talep edilen iş günü kalan kotayı aşıyorsa isteği kontrollü `400 Bad Request` ile reddedecektir. Bu kota kontrolünün implementasyonu bu profil görevinin kapsamında değildir.

Frontend, Personel/HR için kullanılan izin değerini `yillikIzinHakki - kalanIzin` ile hesaplayabilir. Yönetici/Admin için kalan izin kartı gizlenir veya role uygun başka bir özetle değiştirilir.

## Hata ve Yan Etki Davranışı

- Authentication yoksa mevcut Spring Security akışı `401 Unauthorized` döner.
- JWT principal’daki personel ID veritabanında yoksa `KaynakBulunamadiException` üzerinden `404 Not Found` döner.
- Yönetici yokluğu hata değildir; `yonetici: null` döner.
- Yetenek veya uygun izin kaydı yokluğu hata değildir.
- Profil görüntüleme yalnız read işlemidir; personel, izin veya yetenek verisini değiştirmez.
- Profil görüntüleme audit kaydı üretmez.
- Mevcut stateless access token davranışı değişmez; pasifleştirme sonrası token kalan ömrü boyunca en fazla 15 dakika çalışabilir.

## Test Stratejisi

Kullanıcının açık tercihiyle bu özellikte TDD uygulanmaz. Production kodu bağımlılık sırasıyla yazıldıktan sonra testler toplu eklenir ve çalıştırılır.

Doğrulanacak senaryolar:

- Personel temel alanları, departman, rol ve yönetici ad-soyadı doğru eşlenir.
- Yönetici yoksa response alanı `null` olur.
- Yetenekler ID/ad biçiminde ve alfabetik döner.
- Yetenek yoksa boş liste döner.
- Personel ve HR için kalan izin doğru hesaplanır.
- Yalnız `Onaylandı` durumundaki `Yıllık izin` kayıtları düşülür.
- Diğer izin türleri ve diğer durumlar kalan izni etkilemez.
- Yönetici ve Admin için `kalanIzin` null döner.
- Authentication olmayan istek `401` döner.
- Principal personeli bulunamazsa `404` döner.
- GET isteği audit veya persistence yan etkisi oluşturmaz.

Gerçek sorgu, ilişki ve collation davranışı MySQL 8 Testcontainers integration testiyle doğrulanır. Testler mevcut backend test grubuyla birlikte çalıştırılır.

## Kapsam Dışı

- Profil güncelleme
- Yetenek ekleme/çıkarma
- Profil fotoğrafı yükleme/silme
- İzin talebi oluşturma veya onaylama
- İzin kota aşımı kontrolünün implementasyonu
- Ekip dizini
- Pasif personeli yeniden aktifleştirme

## Bağımlılık Sırası

1. `TalepDurum` enum’u ve `DurumTuru` enum eşlemesi
2. `IzinTuru.yillikHaktanDuserMi` alanı ve mevcut lookup veri güncellemesi
3. `PersonelYetenekRepository`
4. `IzinBilgileriRepository`
5. `ProfilService`
6. `ProfilController` GET endpoint’i
7. Toplu unit ve HTTP/MySQL integration testleri
8. Doğrulama sonrası API, ROADMAP, PROGRESS ve gerekirse SECURITY belge güncellemeleri

## Tamamlanma Ölçütleri

- Authenticated bütün roller kendi profilini okuyabilir.
- Kimlik yalnız JWT principal’dan alınır.
- Mevcut `ProfilBilgileriResponse` gerçek verilerle doldurulur.
- Yetenekler alfabetik ve boş liste semantiğiyle döner.
- Kalan izin yalnız onaylanmış yıllık izinden ve role göre hesaplanır.
- Endpoint read-only ve audit yan etkisizdir.
- İlgili test grubu ve tam backend testleri başarılıdır.
- Uygulanan davranış proje belgeleriyle tutarlıdır.

## Uygulama Sonucu

- Tasarımdaki üretim akışı `GET /api/profil` olarak uygulandı.
- Enum adı uygulamada `TalepDurum` olarak seçildi ve belge buna uyarlandı.
- Lookup tabloları boş olduğundan mevcut veri migration’ı gerekmedi.
- `ProfilControllerIntegrationTest` 8 test ile profil alanlarını, izin filtrelerini, rol ayrımını, alfabetik yetenekleri, `401` ve `404` davranışlarını gerçek MySQL üzerinde doğruladı.
- Bu turda tam backend test paketi yeniden çalıştırılmadı.
