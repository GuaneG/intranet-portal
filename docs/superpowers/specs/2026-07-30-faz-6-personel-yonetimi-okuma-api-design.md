# Faz 6 Personel Yönetimi Okuma API'leri ve Ekip Dizini Araması Tasarımı

**Tarih:** 30.07.2026  
**Durum:** Kullanıcı tarafından onaylandı

## 1. Amaç

Frontend Faz 6 kapsamında HR ve Admin için hazırlanacak personel yönetimi ekranlarının ham ID/UUID girişi istemeden çalışabilmesi için eksik backend okuma sözleşmelerini tamamlamak.

Bu tasarım şu teslimleri kapsar:

- HR/Admin yetkili, sayfalı ve filtrelenebilir yönetim personel listesi,
- HR/Admin yetkili tek personel yönetim detayı,
- departmana göre atanabilir yönetici lookup'u,
- mevcut personel mutation'larında aktif aktör ve hedef matrisi güvenliği,
- mevcut `GET /api/ekip-dizini` endpoint'ine opsiyonel ad/soyad araması.

## 2. Kapsam dışı

- Frontend component, route, state veya API client implementasyonu,
- mevcut mutation endpoint yollarının değiştirilmesi,
- `GET /api/ekip-dizini` DTO veya pagination sözleşmesinin değiştirilmesi,
- entity'lerin doğrudan response yapılması,
- personel silme veya yeniden aktifleştirme,
- Git işlemleri.

Backend Java ve test kodlarını kullanıcı uygular; agent yalnız kopyalanabilir kod blokları sağlar. Markdown belgeleri yalnız kullanıcının verdiği yazma onayı kapsamında agent tarafından güncellenebilir.

## 3. Mevcut durum ve güvenlik boşluğu

Mevcut mutation endpoint'leri:

| Method | Endpoint | Yetki |
| --- | --- | --- |
| POST | `/api/hr/personel` | HR, Admin |
| PATCH | `/api/hr/personel/{personelId}` | HR, Admin |
| PATCH | `/api/hr/personel/{personelId}/pasiflestir` | HR, Admin + hedef kuralları |
| PATCH | `/api/admin/personel/{personelId}/rol` | Admin |

Mevcut `personelGuncelle` akışı aktörün HR/Admin authority taşımasını kontrol eder ancak öz-hedef ve hedef rol kontrolü yapmaz. Dört mutation aktörü veritabanından yüklese de aktörün hâlâ aktif olduğunu ortak bir kuralla doğrulamaz. Pasife alınmış HR/Admin, kalan access-token ömründe yönetim işlemi yapabilir.

Yeni tasarım bu boşlukları mevcut pasifleştirme ve rol değiştirme matrisiyle uyumlu biçimde kapatır.

## 4. Endpoint sözleşmeleri

### 4.1 Yönetim personel listesi

```http
GET /api/hr/personel?page=0&size=20&arama=ayşe&departmanId=2&rol=PERSONEL&aktif=true
```

| Parametre | Zorunluluk | Davranış |
| --- | --- | --- |
| `page` | opsiyonel | Varsayılan `0`, en az `0` |
| `size` | opsiyonel | Varsayılan `20`, `1–100` |
| `arama` | opsiyonel | Ad, soyad, tam ad ve kullanıcı adında case-insensitive arama |
| `departmanId` | opsiyonel | Pozitif departman ID |
| `rol` | opsiyonel | `RolAdi` enum değeri |
| `aktif` | opsiyonel | `true`, `false`; yoksa iki durum da |

Filtreler birlikte gönderildiğinde `AND` uygulanır. `arama` baş ve son boşlukları temizlendikten sonra boşsa filtre uygulanmaz; iç boşluklar korunur. Case-insensitive karşılaştırma kolon ve parametreyi veritabanında aynı biçime dönüştürerek yapılır. Pozitif fakat bulunmayan `departmanId` boş sayfa döndürür. `rol=ADMIN` geçerli bir enum filtresidir ancak hedef matrisi `ADMIN` kayıtlarını dışladığı için boş sayfa döndürür. Diğer geçerli fakat eşleşmeyen filtreler de `200 OK` ile boş sayfa döner. Sıralama `ad ASC`, `soyad ASC`, `personelId ASC` biçimindedir.

Response mevcut `SayfaliResponse<PersonelYonetimListeResponse>` yapısını kullanır:

```json
{
  "icerik": [
    {
      "personelId": "uuid",
      "ad": "Ayşe",
      "soyad": "Yılmaz",
      "kullaniciAdi": "ayse.yilmaz",
      "rol": "PERSONEL",
      "departmanId": 2,
      "departmanAdi": "Bilgi Teknolojileri",
      "aktif": true
    }
  ],
  "sayfa": 0,
  "sayfaBoyutu": 20,
  "toplamKayit": 1,
  "toplamSayfa": 1
}
```

### 4.2 Tek personel yönetim detayı

```http
GET /api/hr/personel/{personelId}
```

`PersonelYonetimDetayResponse`:

```json
{
  "personelId": "uuid",
  "kullaniciAdi": "ayse.yilmaz",
  "ad": "Ayşe",
  "soyad": "Yılmaz",
  "dogumTarihi": "1995-04-12",
  "ePosta": "ayse.yilmaz@example.com",
  "departmanId": 2,
  "yoneticiId": "yonetici-uuid",
  "isGirisTarihi": "2022-01-10",
  "telNo": "05550000000",
  "mezunOkul": "Örnek Üniversitesi",
  "mezunBolum": "Bilgisayar Mühendisliği",
  "mezuniyetYili": 2018,
  "rol": "PERSONEL",
  "aktif": true
}
```

Yönetici yoksa `yoneticiId` açıkça `null` döner. Response parola/hash, ilk parola flag'i, refresh token, sicil numarası, yıllık izin hakkı, profil fotoğrafı veya işten çıkış tarihi içermez.

### 4.3 Atanabilir yönetici lookup

```http
GET /api/hr/personel/atanabilir-yoneticiler?departmanId=2&haricPersonelId=uuid
```

- `departmanId` zorunlu ve pozitiftir.
- Departman bulunamazsa `404 Departman bulunamadı.` döner.
- `haricPersonelId` opsiyoneldir ve güncellenen hedefi sonuçtan çıkarır. Gönderilmezse veya trim sonrasında boşsa hariç tutma uygulanmaz; başka bir ID verilmesi yalnız eşleşen kaydı sonuçtan çıkarır ve bu ID için ayrıca varlık kontrolü yapılmaz.
- Yalnız aktif, `YONETICI` rolündeki ve seçilen departmandaki personeller döner.
- Sayfalama yoktur.
- Sıralama `ad`, `soyad`, `personelId` biçimindedir.
- Eşleşme yoksa `200 OK` ile `[]` döner.

`AtanabilirYoneticiResponse` alanları `{ personelId, ad, soyad, kullaniciAdi }` biçimindedir.

## 5. Yetki ve hedef matrisi

Yeni üç endpoint yalnız aktif HR/Admin aktörlere açıktır.

| Aktör | Listede/detayda izin verilen hedef |
| --- | --- |
| HR | `PERSONEL`, başka bir `HR` |
| Admin | `PERSONEL`, `HR`, `YONETICI` |
| HR öz-hedef | Yasak |
| Admin öz-hedef | Yasak |
| `ADMIN` hedef | Yasak |

Liste sorgusu yasak hedefleri veritabanında filtreler; bu hedefler `icerik`, `toplamKayit` ve `toplamSayfa` değerlerine dahil olmaz. Detay sorgusunda bulunamayan hedef `404`, mevcut fakat yasak hedef `403` döner.

Atanabilir yönetici lookup'u bilinçli istisnadır: HR, personel formunda seçim yapabilmek için aktif yönetici adaylarını görebilir ancak bu yöneticilerin yönetim detayını açamaz veya onları güncelleyemez.

## 6. Mimari

Mevcut `HrPersonelController` korunur. Controller mutation işlemleri için mevcut `PersonelService`, okuma işlemleri için yeni `PersonelYonetimSorguService` kullanır.

Yeni `PersonelYonetimYetkiService`:

- JWT principal personelini veritabanından yükler,
- aktörün aktif olduğunu doğrular,
- aktöre göre izinli hedef rol kümesini üretir,
- öz-hedef ve hedef rol matrisini doğrular.

Bu servis hem `PersonelYonetimSorguService` hem mevcut `PersonelService` tarafından kullanılır. Liste repository sorgusu izinli rol kümesini parametre olarak alarak aynı matrisi veritabanında uygular.

## 7. Repository ve veri akışı

`PersonelRepository` üç ihtiyaca özel sorguyla genişletilir:

1. Yönetim personel sayfası departman ve rolü fetch eder; aktörü sonuçtan çıkarır; izinli hedef rollerini ve arama/departman/rol/aktif filtrelerini uygular; count sorgusunda aynı koşulları tekrarlar.
2. Yönetim detayı personel, departman, rol ve opsiyonel yöneticiyi kontrollü fetch eder.
3. Atanabilir yöneticiler aktiflik, `YONETICI` rolü, departman ve opsiyonel hariç tutma koşullarını uygular.

Entity'ler controller'a veya response serialization'a verilmez; service katmanında DTO'ya dönüştürülür.

## 8. Ekip dizini arama genişletmesi

Mevcut `GET /api/ekip-dizini` endpoint'i opsiyonel `arama` parametresi alır.

Arama ad, soyad ve `ad + " " + soyad` üzerinden case-insensitive çalışır. Baş ve son boşluklar trim edilir. `null`, boş veya yalnız boşluk içeren değer filtre uygulanmamış gibi davranır. İç boşluklar değiştirilmez.

Arama koşulu hem ana sorguda hem count sorgusunda bulunur ve mevcut `departmanId`/`yetenekId` filtreleriyle `AND` bağlanır. Mevcut pagination, kararlı sıralama, `EkipDiziniResponse` ve `SayfaliResponse` değişmez.

## 9. Mevcut mutation güvenliği

Aktif aktör doğrulaması personel oluşturma, personel güncelleme, personeli pasifleştirme ve Admin rol değiştirme işlemlerine uygulanır.

Personel güncellemede onaylanan hedef matrisi uygulanır:

- HR → `PERSONEL` ve başka HR,
- Admin → `PERSONEL`, `HR`, `YONETICI`,
- öz-hedef yasak,
- `ADMIN` hedef yasak.

Pasifleştirme ve rol değiştirme işlemlerinin mevcut hedef, no-op, audit, refresh-token ve rollback davranışları korunur.

## 10. Hata davranışı

| Durum | HTTP |
| --- | --- |
| Token yok/geçersiz | `401` |
| Aktör rolü HR/Admin değil | `403` |
| Aktör pasif | `403` |
| Aktör bulunamadı | `404 Personel bulunamadı.` |
| Öz-hedef veya yasak hedef rol | `403` |
| Detay hedefi bulunamadı | `404 Personel bulunamadı.` |
| Lookup departmanı bulunamadı | `404 Departman bulunamadı.` |
| Geçersiz query parametresi | `400` |
| Geçerli fakat eşleşmeyen filtre | `200` ve boş sonuç |

Okuma endpoint'leri `@Transactional(readOnly = true)` olur ve audit üretmez.

## 11. Test tasarımı

### Ekip dizini integration testleri

- ad, soyad ve tam ad araması,
- case-insensitive ve trim davranışı,
- boş aramanın filtresiz davranması,
- arama + departman + yetenek `AND`,
- count metadata, pagination ve kararlı sıralama.

### Personel yönetimi integration testleri

- HR/Admin erişimi ve diğer rollerin `403` alması,
- hedef matrisine göre liste görünürlüğü,
- aktif/pasif, rol, departman ve arama filtreleri,
- filtre birleşimi, pagination ve sıralama,
- güvenli response alanları,
- detay response'u ve null yönetici,
- yasak/bulunamayan hedef,
- yönetici lookup rol/departman/aktiflik/hariç tutma/sıralama/boş sonuç,
- eksik veya pasif principal,
- token'sız erişim,
- okumalarda audit oluşmaması.

### Mutation güvenlik testleri

- pasif HR/Admin'in dört mutation'ı kullanamaması,
- HR → başka HR güncellemesi,
- Admin → yönetici güncellemesi,
- HR → yönetici yasağı,
- HR/Admin öz-hedef yasağı,
- Admin hedef yasağı,
- mevcut audit/no-op/rollback davranışlarının korunması.

Gerçek sorgu, pagination, count, security filter-chain ve transaction davranışları mevcut MySQL 8 Testcontainers altyapısıyla doğrulanır.

## 12. Beklenen dosya kapsamı

Yeni production dosyaları:

- `model/dto/response/PersonelYonetimListeResponse.java`
- `model/dto/response/PersonelYonetimDetayResponse.java`
- `model/dto/response/AtanabilirYoneticiResponse.java`
- `service/PersonelYonetimSorguService.java`
- `service/PersonelYonetimYetkiService.java`

Değişecek production dosyaları:

- `controller/HrPersonelController.java`
- `controller/EkipDiziniController.java`
- `service/EkipDiziniService.java`
- `service/PersonelService.java`
- `repository/PersonelRepository.java`

Yeni test dosyaları:

- `PersonelYonetimControllerIntegrationTest.java`
- `PersonelYonetimYetkiServiceTest.java`

Değişecek test dosyaları:

- `EkipDiziniControllerIntegrationTest.java`
- `HrPersonelControllerIntegrationTest.java`
- `PersonelServiceTest.java`

Doğrulanmış sonuçlardan sonra `API_SPEC.md`, `ROADMAP.md`, `PROGRESS.md` ve `SECURITY.md` güncellenir.

## 13. Kabul ölçütleri

- Frontend ham personel/departman/yönetici ID'si yazdırmadan gerekli verileri okuyabilir.
- Liste ve detay aynı backend hedef matrisini uygular.
- Mutation işlemleri pasif aktör ve yasak hedef tarafından kullanılamaz.
- Yönetici lookup yalnız geçerli adayları döndürür.
- Ekip dizini araması mevcut filtrelerle `AND` çalışır ve count sorgusuyla tutarlıdır.
- Entity ve hassas alan response'a sızmaz.
- Okuma işlemleri audit üretmez.
- Odaklı testler ve tam backend suite'i `0 failure`, `0 error` ile tamamlanır.
