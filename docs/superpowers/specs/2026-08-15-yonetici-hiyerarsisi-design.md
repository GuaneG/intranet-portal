# Yönetici Hiyerarşisi Tasarımı

## 1. Amaç ve sınır

Mevcut `Personel.yonetici` self-reference ilişkisini kullanarak güvenli, unvansız bir yönetici hiyerarşisi sunmak. Mevcut modelde CEO, genel müdür veya organizasyon seviyesi alanı bulunmadığından bu sürüm unvan üretmez ve departmanlar arası yeni bir organizasyon modeli kurmaz.

Mevcut `GET /api/ekip-dizini` pagination, filtre, dashboard reuse ve görünürlük sözleşmesi değiştirilmez.

## 2. HTTP sözleşmeleri

### Tam hiyerarşi

```http
GET /api/ekip-dizini/hiyerarsi
```

Yalnız aktif HR ve Admin erişir. Bütün aktif organizasyon forest’ını `kokler` listesi olarak döndürür. Tek kök veya sentetik CEO üretilmez.

### Kişisel üst yönetici zinciri

```http
GET /api/ekip-dizini/yonetici-zincirim
```

Yalnız aktif PERSONEL ve YONETICI erişir. En fazla doğrudan yönetici ile onun doğrudan yöneticisini, yakından uzağa sıralı biçimde döndürür. Aktörün kendisi, çalışanları ve başka ekipler response’a girmez.

Yönetici yoksa boş liste; tek üst varsa bir kayıt döner. Üçüncü ve daha yukarı üstler sızdırılmaz.

## 3. DTO sözleşmesi

Güvenli kişi alanları:

```json
{
  "ad": "Ayşe",
  "soyad": "Yılmaz",
  "departman": "Bilgi Teknolojileri",
  "rol": "YONETICI",
  "profilFoto": "def_pfp.png"
}
```

Tam ağaç düğümü bunlara yalnız recursive `dogrudanBaglilar` ekler. Response personel ID, kullanıcı adı, e-posta, sicil numarası, telefon, parola/hash, token, audit veya fiziksel fotoğraf yolu taşımaz.

## 4. Görünürlük ve aktiflik

- Tam ağaç endpoint’i: HR/Admin; aktif bütün kişiler
- Zincir endpoint’i: PERSONEL/YONETICI; yalnız en fazla iki aktif üst yönetici
- Pasif principal: `403`
- Missing principal: `404 Personel bulunamadı.`
- Kimlik doğrulanmamış istek: `401`
- Yanlış endpoint’e erişen authenticated rol: `403`
- Pasif kişiler hiçbir hiyerarşi response’una girmez
- Aktif kişinin yöneticisi pasif/missing görünümdeyse aktif kişi tam ağaçta kök kabul edilir; kişisel zincir doğal olarak sonlanır

HR’ın tam ağaçta Admin düğümünü görmesi, yalnız bu endpoint için kullanıcının istediği bilinçli görünürlük genişlemesidir; mevcut ekip dizini görünürlüğünü değiştirmez.

## 5. Okuma mimarisi

Yeni `HiyerarsiService`, ince controller ve amaca özel response DTO’ları kullanılır. Repository bütün aktif kişileri departman, rol ve yönetici bilgileriyle toplu okur. Service internal personel ID map’iyle forest’ı kurar; ID response’a taşınmaz.

Kökler ve doğrudan bağlılar `ad`, `soyad`, ardından yalnız backend içinde kullanılan `personelId` ile kararlı sıralanır. Okuma read-only ve auditsizdir.

## 6. Döngü güvenliği

Yeni yönetici atamasında aday yöneticiden yukarı doğru yürünür:

- hedef personele tekrar ulaşılırsa `400` ile reddedilir;
- yürüyüşte aynı kayıt ikinci kez görülürse mevcut graph tutarsızlığı kabul edilerek ilişki değiştirilmez.

Tam ağaç ve kişisel zincir okumalarında DFS/visited kontrolü uygulanır. İki kişilik veya daha uzun legacy döngü görülürse kısmi response dönülmez; `409 Yönetici hiyerarşisinde tutarsızlık tespit edildi.` üretilir.

Bu sürüm mevcut aynı-departman ve aktif `YONETICI` atama kuralını korur. Unvan, pozisyon veya çapraz-departman yönetici modeli eklenmez.

## 7. Test tasarımı

Tam ağaç:

- HR/Admin `200`, doğru nesting ve bütün aktif kişiler
- Çoklu kök ve kararlı sıralama
- Pasif kişilerin dışlanması; pasif yönetici altındaki aktif kişinin kök olması
- PERSONEL/YONETICI `403`, missing/pasif principal ve `401`
- Güvenli DTO alanları, read-only ve auditsiz davranış
- İki kişilik ve uzun legacy döngüde kontrollü `409`, kısmi response yok

Kişisel zincir:

- PERSONEL/YONETICI için sıfır, bir ve iki üst
- Üçüncü üstün, çalışanların ve başka ekiplerin sızmaması
- HR/Admin `403`, missing/pasif principal ve `401`
- Güvenli DTO, read-only ve auditsiz davranış
- Legacy döngüde kontrollü `409`

Yazma regresyonu:

- Mevcut self-manager `400`
- A→B varken B→A ve A→B→C varken C→A girişiminin `400` olması
- Başarısız döngü denemesinde yönetici ilişkisi ve auditin değişmemesi
- Normal personel oluşturma/güncelleme ve ekip dizini regresyonlarının korunması

