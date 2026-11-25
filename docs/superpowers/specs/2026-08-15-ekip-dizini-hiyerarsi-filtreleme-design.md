# Ekip Dizini Hiyerarşi Filtreleme ve Yönetici Görünümü Tasarımı

**Durum:** Kullanıcı tarafından onaylandı; henüz uygulanmadı.

## 1. Amaç

Mevcut ekip dizini hiyerarşisini üç güvenli görünürlük ihtiyacını karşılayacak biçimde genişletmek:

- HR ve Admin için mevcut tam aktif organizasyon forest'ını bozmadan arama, departman ve yetenek filtreleri eklemek,
- Yöneticiye yalnız kendi üst zincirini, kendisini ve kendi aktif ast ağacını gösteren ayrı bir hiyerarşi görünümü sağlamak,
- hiyerarşi düğümlerinde JWT principal'ın gerçek yöneticisini backend ilişkisine dayanarak güvenli biçimde işaretlemek.

Bu tasarım yeni bir organizasyon modeli, unvan sistemi veya çapraz departman yönetici kuralı kurmaz. Mevcut `Personel.yonetici` ilişkisi, aktiflik kuralları ve güvenli kişi alanları kullanılır.

## 2. Kapsam ve kapsam dışı işler

Kapsam:

- `GET /api/ekip-dizini/hiyerarsi` için opsiyonel filtreler,
- yeni `GET /api/ekip-dizini/yonetici-hiyerarsim` endpoint'i,
- bütün hiyerarşi düğümlerinde `yoneticinMi`,
- ortak filtre doğrulaması ve AND semantiği,
- mevcut Personel departman sınırının regresyon testleri,
- döngü güvenliği, salt okunurluk ve veri minimizasyonu.

Kapsam dışı:

- frontend değişiklikleri,
- `GET /api/ekip-dizini/yonetici-zincirim` sözleşmesinin değiştirilmesi,
- yeni departman lookup endpoint'i,
- personel, rol, yönetici veya yetenek ilişkisi yazma davranışlarının değiştirilmesi,
- unvan, CEO/genel müdür seviyesi veya yeni organizasyon entity'si,
- bu aşamada `API_SPEC.md`, `SECURITY.md`, `PROGRESS.md` ve `ROADMAP.md` güncellemesi.

## 3. Yaklaşım kararı

Repository iki ayrı veri kümesi sağlar:

1. Hiyerarşiyi güvenli biçimde kurmak ve döngüyü doğrulamak için gerekli bütün aktif kişiler.
2. Verilen `arama`, `departmanId` ve `yetenekId` koşullarının tamamına doğrudan uyan personel kimlikleri.

Service, doğrudan eşleşmeleri ve zorunlu bağlantı düğümlerini bellek içinde hesaplar. Bu yaklaşım recursive MySQL CTE'ye bağımlılık oluşturmaz, mevcut küçük/orta ölçekli intranet yapısına uyar ve budama kurallarını tek bir iş katmanında görünür tutar.

N+1 biçiminde her düğüm için ayrı yönetici veya çalışan sorgusu çalıştırılmaz.

## 4. HTTP sözleşmeleri

### 4.1 HR/Admin tam hiyerarşi

```http
GET /api/ekip-dizini/hiyerarsi?arama=ayse&departmanId=10&yetenekId=3
```

Opsiyonel query parametreleri:

- `arama`: ad, soyad ve `ad + soyad` üzerinde büyük/küçük harf duyarsız arama,
- `departmanId`: pozitif departman kimliği,
- `yetenekId`: pozitif yetenek kimliği.

Yalnız aktif `HR` ve `ADMIN` erişebilir.

Filtre yoksa mevcut davranış korunur: bütün aktif organizasyon forest'ı döner. Filtre varsa yalnız doğrudan eşleşen kişiler ile bu kişileri kendi köklerine bağlamak için zorunlu aktif üst yönetici zincirleri kalır. Eşleşmeyen alt dallar, kardeşler ve bağlantı için gerekmeyen yan dallar response'a girmez.

Başarılı response:

```json
{
  "kokler": [
    {
      "ad": "Ayşe",
      "soyad": "Yılmaz",
      "departman": "Bilgi Teknolojileri",
      "rol": "YONETICI",
      "profilFoto": "def_pfp.png",
      "yoneticinMi": false,
      "dogrudanBaglilar": []
    }
  ]
}
```

Tam hiyerarşi response'una `aktorDepartman` metadata'sı eklenmez.

### 4.2 Yöneticinin güvenli hiyerarşisi

```http
GET /api/ekip-dizini/yonetici-hiyerarsim?arama=ayse&departmanId=10&yetenekId=3
```

Yalnız aktif `YONETICI` erişebilir. `PERSONEL`, `HR` ve `ADMIN` için sonuç `403 Forbidden` olur.

Filtresiz response tek bir güvenli görünüm içinde şunları taşır:

- aktör yöneticinin en fazla iki aktif üst yöneticisi,
- aktör yönetici,
- aktörün doğrudan ve dolaylı bütün aktif çalışanları.

Aktörün kardeşleri, üst yöneticilerin başka çalışanları, başka kökler ve başka ekip dalları response'a girmez.

Filtre varsa filtre yalnız aktörün aktif astları için doğrudan eşleşme üretir. Response'ta her zaman en fazla iki üst yönetici ile aktör bulunur; eşleşen astlar ve aktörden bu astlara ulaşmak için zorunlu ara yöneticiler eklenir. Eşleşmeyen ast dalları budanır. Hiçbir ast eşleşmezse üst zincir ile aktör kalır ve aktörün `dogrudanBaglilar` listesi boş olur.

Response yapısı ilişkiyi recursive olarak açıkça korur:

```text
en fazla iki üst yönetici
  → aktör yönetici
    → eşleşen astlar ve zorunlu ara yöneticiler
```

İki üst yönetici varsa en uzaktaki üst `kokler` içinde yer alır; onun `dogrudanBaglilar` zinciri sırasıyla yakın üst yöneticiyi, aktör yöneticiyi ve aktörün güvenli ast ağacını taşır. Aktörün üst yöneticisi yoksa aktör doğrudan `kokler` içinde kök düğüm olur ve aktif/eşleşen astları aktörün `dogrudanBaglilar` alanında yer alır.

Başarılı response:

```json
{
  "aktorDepartman": {
    "id": 10,
    "ad": "Bilgi Teknolojileri"
  },
  "kokler": [
    {
      "ad": "İkinci Seviye",
      "soyad": "Yönetici",
      "departman": "Bilgi Teknolojileri",
      "rol": "YONETICI",
      "profilFoto": "def_pfp.png",
      "yoneticinMi": false,
      "dogrudanBaglilar": [
        {
          "ad": "Doğrudan",
          "soyad": "Yönetici",
          "departman": "Bilgi Teknolojileri",
          "rol": "YONETICI",
          "profilFoto": "def_pfp.png",
          "yoneticinMi": true,
          "dogrudanBaglilar": [
            {
              "ad": "Aktör",
              "soyad": "Yönetici",
              "departman": "Bilgi Teknolojileri",
              "rol": "YONETICI",
              "profilFoto": "def_pfp.png",
              "yoneticinMi": false,
              "dogrudanBaglilar": [
                {
                  "ad": "Ara",
                  "soyad": "Yönetici",
                  "departman": "Bilgi Teknolojileri",
                  "rol": "YONETICI",
                  "profilFoto": "def_pfp.png",
                  "yoneticinMi": false,
                  "dogrudanBaglilar": [
                    {
                      "ad": "Eşleşen",
                      "soyad": "Personel",
                      "departman": "Bilgi Teknolojileri",
                      "rol": "PERSONEL",
                      "profilFoto": "def_pfp.png",
                      "yoneticinMi": false,
                      "dogrudanBaglilar": []
                    }
                  ]
                }
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

`aktorDepartman`, frontend'in ilk açılışta yöneticinin departmanını seçili göstermesi için yeterlidir. Bunun için yeni bir endpoint eklenmez.

### 4.3 Mevcut kişisel zincir

```http
GET /api/ekip-dizini/yonetici-zincirim
```

Bu endpoint'in rol matrisi, en fazla iki üst yönetici davranışı ve response şekli değişmez. Yeni yönetici hiyerarşisi bunun yerine geçmez.

## 5. `yoneticinMi` kesin kuralı

Her recursive hiyerarşi düğümü zorunlu `yoneticinMi: boolean` alanını taşır.

- Karar yalnız JWT principal'ın veritabanındaki `yonetici.personelId` ilişkisi ile düğümün internal `personelId` değeri karşılaştırılarak verilir.
- İsim, soyad, rol, sıralama veya frontend tahmini kullanılmaz.
- Dönen forest içinde principal'ın gerçek yöneticisi bulunuyorsa yalnız o düğüm `true`, diğer bütün düğümler `false` olur.
- Principal'ın yöneticisi yoksa, pasif olduğu için response dışında kalıyorsa veya filtre budaması nedeniyle forest içinde bulunmuyorsa bütün düğümler `false` olur.
- `yoneticinMi` değerini `true` yapabilmek için filtreyle ilgisiz ikinci bir dal ya da yönetici zinciri response'a eklenmez.

Böylece response genelinde değer **en fazla bir** düğümde `true` olabilir.

## 6. Filtre semantiği ve doğrulama

Üç filtre birlikte gönderildiğinde AND uygulanır:

```text
arama eşleşmesi
AND departmanId eşleşmesi
AND yetenekId ilişkisi
```

Kurallar:

- `arama` baştan ve sondan trim edilir.
- Boş veya yalnız boşluk içeren `arama`, filtre yokmuş gibi davranır.
- Arama ad, soyad ve aralarında tek boşluk bulunan tam ad üzerinde case-insensitive çalışır.
- `yetenekId`, gerçek `PersonelYetenek` ilişkisi için `EXISTS` koşuluyla uygulanır.
- Sayısal parametrede metin veya dönüştürülemeyen değer `400 Bad Request` olur.
- `departmanId <= 0` veya `yetenekId <= 0` değeri `400 Bad Request` olur.
- Veritabanında bulunmayan pozitif ID hata değildir; doğrudan eşleşme kümesi boş olur ve `200 OK` dönülür.
- HR/Admin tam hiyerarşisinde boş eşleşme `{ "kokler": [] }` üretir.
- Yönetici hiyerarşisinde boş eşleşme en fazla iki üst yönetici ile aktörü korur; ast dalları boş kalır.

Bu kurallar mevcut `/api/ekip-dizini` için hedeflenen ortak filtre sözleşmesidir. Uygulama aşamasında pozitif olmayan ID'leri halen boş sonuç sayan bir kod yolu varsa ortak doğrulayıcıyla `400` davranışına hizalanır; Personel'in zorunlu departman kapsamı zayıflatılmaz.

## 7. Departman kapsamı

Mevcut `GET /api/ekip-dizini` davranışı korunur:

- `PERSONEL` yalnız kendi departmanındaki aktif kişileri görebilir.
- Personel başka bir pozitif `departmanId` gönderirse kullanıcı filtresi ile zorunlu kendi departman kapsamı AND uygulanır; iki değer farklıysa boş sayfa döner, kapsam aşılmaz.
- `YONETICI` mevcut ekip dizininde kendi departmanı filtresiyle arama yapabilir.
- Yeni yönetici hiyerarşisinde filtre, yalnız aktörün güvenli ast ağı içinde uygulanır; aynı ID başka ekipte eşleşse bile o kişi veya dal dönmez.

Personel departman dropdown'ının gizlenmesi ve Yönetici ekranında “Senin departmanın” metni frontend sorumluluğudur. Backend yalnız `aktorDepartman { id, ad }` metadata'sını yeni Yönetici endpoint'inde sağlar.

## 8. Hiyerarşi oluşturma algoritmaları

### 8.1 Tam forest

1. Principal veritabanından bulunur; aktifliği ve `HR`/`ADMIN` rolü doğrulanır.
2. Bütün aktif kişiler yönetici, departman ve rol bilgileriyle toplu okunur.
3. Tüm aktif graph üzerinde döngü kontrolü yapılır. Döngü varsa hiçbir kısmi veri dönmeden kontrollü `409` üretilir.
4. Filtre yoksa mevcut tam forest kurulur.
5. Filtre varsa repository doğrudan eşleşen internal personel ID'lerini getirir.
6. Her eşleşmeden aktif köke kadar üst yöneticiler retained kümeye eklenir.
7. Yalnız retained kümeden forest kurulur; ilgisiz çocuk ve kardeş dalları eklenmez.
8. Kökler ve çocuklar mevcut kararlı sıralama kuralıyla sıralanır.
9. DTO dönüşümünde `yoneticinMi` hesaplanır; internal ID response'a yazılmaz.

### 8.2 Yönetici görünümü

1. Principal veritabanından bulunur; aktif ve tam `YONETICI` olduğu doğrulanır.
2. Aktörün yukarı zincirinde en fazla iki aktif yönetici alınır; daha yukarısı response'a girmez.
3. Aktörden aşağı doğru yalnız aktif doğrudan/dolaylı çalışanlar kapsama alınır.
4. Üst zincir ve aktörün erişilebilir ast graph'ında döngü kontrolü yapılır. Döngü varsa kontrollü `409` dönülür.
5. Filtre yoksa bütün güvenli ast graph'ı korunur.
6. Filtre varsa doğrudan eşleşme yalnız ast kapsamındaki kişilerden seçilir.
7. Eşleşen her asttan aktöre kadar zorunlu ara yönetici düğümleri korunur; ilgisiz alt dallar budanır.
8. Üst zincir ve aktör filtre sonucundan bağımsız olarak korunur.
9. `aktorDepartman` aktörün gerçek departman ilişkisinden oluşturulur.

Başka bir organizasyon dalındaki legacy döngü Yönetici response'una veri sızdırmaz. Ancak aktörün üst zincirinde veya erişilebilir ast graph'ında döngü varsa güvenli response üretilemeyeceği için `409` gerekir.

## 9. Katman sorumlulukları

### Controller

- Path ve query parametrelerini alır.
- JWT principal adını `Authentication` üzerinden service'e aktarır.
- İş kuralı, graph dolaşımı veya repository erişimi içermez.
- Başarılı sonucu `200 OK` ile döndürür.

### Service

- Veritabanındaki principal, aktiflik ve rol doğrulamasını yapar.
- Filtreleri normalize eder ve pozitif ID doğrulamasını uygular.
- Rol bazlı graph kapsamını belirler.
- Döngüyü tespit eder, retained düğümleri hesaplar, ağacı budar ve kararlı sıralar.
- `yoneticinMi` değerini gerçek ilişki üzerinden hesaplar.
- Entity'leri güvenli DTO'lara dönüştürür.
- `@Transactional(readOnly = true)` çalışır ve audit oluşturmaz.

### Repository

- Aktif graph için gerekli ilişkileri toplu okur.
- Arama/departman/yetenek AND koşullarına doğrudan uyan internal personel ID'lerini getirir.
- Yetenek filtresini join ilişkisinde `EXISTS` ile uygular.
- Yetkilendirme veya recursive response kurma kararı içermez.

### DTO

- Ortak recursive düğüm güvenli görüntüleme alanları, `yoneticinMi` ve `dogrudanBaglilar` taşır.
- Yönetici response'u buna ek olarak yalnız `aktorDepartman { id, ad }` metadata'sını taşır.
- Personel ID, yönetici ID, kullanıcı adı, e-posta, telefon, parola/hash, token, audit ve fiziksel dosya yolu dönmez.

## 10. Yetki matrisi

| Endpoint | PERSONEL | YONETICI | HR | ADMIN |
|---|---:|---:|---:|---:|
| `GET /api/ekip-dizini/hiyerarsi` | 403 | 403 | 200 | 200 |
| `GET /api/ekip-dizini/yonetici-hiyerarsim` | 403 | 200 | 403 | 403 |
| `GET /api/ekip-dizini/yonetici-zincirim` | 200 | 200 | 403 | 403 |

Ortak durumlar:

- token yok/geçersiz: `401`,
- principal personel kaydı yok: kontrollü `404 Personel bulunamadı.`,
- principal pasif: `403`,
- izin verilmeyen rol: `403`,
- hiyerarşi döngüsü: kontrollü `409 Yönetici hiyerarşisinde tutarsızlık tespit edildi.`,
- geçersiz query parametresi: güvenli `400`.

Ham framework, SQL veya entity ayrıntıları response'a yazılmaz.

## 11. Test stratejisi

Uygulama TDD ile küçük RED/GREEN döngülerine ayrılır.

### HR/Admin tam hiyerarşi

- filtresiz mevcut tam aktif forest ve kararlı sıralama korunur,
- arama filtresi doğrudan eşleşen kişi ile yalnız köke kadar zinciri bırakır,
- departman filtresi ilgisiz alt ve yan dalları budar,
- yetenek filtresi gerçek ilişki üzerinden çalışır,
- üç filtre AND ile birleşir,
- eşleşme yoksa boş forest döner,
- pozitif fakat bulunmayan departman/yetenek ID'si boş forest ve `200` üretir,
- metin, sıfır ve negatif ID `400` üretir,
- `yoneticinMi` yalnız gerçek yönetici retained forest içindeyse tek `true` olur,
- gerçek yönetici budanmışsa bütün değerler `false` olur ve ilgisiz dal eklenmez.

### Yönetici hiyerarşisi

- yalnız aktif Yönetici erişir; diğer üç rol `403` alır,
- en fazla iki üst, aktör ve bütün aktif doğrudan/dolaylı astlar doğru nesting ile döner,
- kardeşler, başka üst yöneticilerin çalışanları ve başka ekipler dönmez,
- filtreli görünüm üst zincir ile aktörü korur, yalnız eşleşen astları ve zorunlu ara yöneticileri bırakır,
- eşleşme yoksa üst zincir ile aktör kalır, ast listesi boş olur,
- başka ekipte aynı filtreye uyan kişi sızmaz,
- `aktorDepartman` doğru ID/ad taşır,
- `yoneticinMi` gerçek üst yönetici response'taysa yalnız o düğümde `true` olur,
- pozitif olmayan/metin ID `400`; bulunmayan pozitif ID güvenli `200` davranışı üretir.

### Departman sınırı ve regresyon

- Personel farklı `departmanId` göndererek kendi departmanından çıkamaz,
- Personel filtre değeri kendi departmanından farklıysa boş sayfa alır,
- Yönetici kendi departmanı filtresiyle mevcut ekip dizinini arayabilir,
- mevcut `yonetici-zincirim` testleri değişmeden geçer,
- pasif principal, missing principal ve unauthenticated istek davranışları korunur,
- pasif kişiler hiçbir yeni hiyerarşi görünümüne girmez,
- mevcut iki kişilik ve uzun döngü testleri kontrollü `409` üretmeye devam eder,
- response'larda hassas alan bulunmaz,
- GET işlemleri audit veya persistence yan etkisi oluşturmaz.

## 12. Uyum ve dokümantasyon

- Tam hiyerarşi endpoint'ine opsiyonel parametreler ve node'a zorunlu boolean eklenmesi geriye uyumlu sözleşme genişlemesidir.
- Yönetici hiyerarşisi yeni endpoint'tir.
- Kişisel üst yönetici zinciri değişmez.
- Uygulama tamamlanıp testler doğrulandıktan sonra `API_SPEC.md`, `SECURITY.md`, `PROGRESS.md` ve gerekirse `ROADMAP.md` için ayrı kullanıcı onayı alınır.

## 13. Tamamlanma ölçütleri

- İki hiyerarşi görünümü onaylanan rol ve filtre kurallarını uygular.
- Filtreli forest yalnız eşleşmeleri ve zorunlu bağlantı düğümlerini içerir.
- Yönetici başka ekip veya ilişki göremez.
- Her düğüm `yoneticinMi` taşır ve response genelinde en fazla bir `true` bulunur.
- Parametre doğrulaması ve AND semantiği kesin sözleşmeye uyar.
- Döngü güvenliği, mevcut ekip dizini görünürlüğü ve kişisel zincir davranışı gerilemez.
- Odaklı ve ilgili regresyon testleri sıfır failure/error ile tamamlanır.
