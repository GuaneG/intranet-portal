# Güvenli Profil Fotoğrafı Tasarımı

**Tarih:** 2026-08-12  
**Durum:** Kullanıcı tarafından onaylanan tasarım  
**Kapsam:** Backend profil fotoğrafı yükleme, değiştirme, silme ve authenticated görüntüleme akışı

## 1. Amaç

Kullanıcının yalnız kendi profil fotoğrafını güvenli biçimde yükleyebilmesini, değiştirebilmesini ve kaldırabilmesini sağlamak; fotoğrafları yalnız aktif ve kimliği doğrulanmış kullanıcılara sunmak.

Özellik şu gereksinimleri karşılamalıdır:

- JPEG ve PNG dışındaki formatları reddetmek.
- Kullanıcının gönderdiği ham dosyayı doğrudan saklamamak.
- Görseli decode edip yeniden üreterek ek içerik ve metadata'yı ayıklamak.
- Dosyayı uygulamanın ve webroot'un dışında saklamak.
- İstemci tarafından belirlenen dosya adı veya yolu kullanmamak.
- Fotoğraf yokken frontend'in ad ve soyad baş harflerini göstermeye devam edebilmesini sağlamak.
- Dosya sistemi ile veritabanı arasındaki kısmi hata durumlarında mevcut geçerli fotoğrafı korumak.

## 2. Mevcut Durum

`Personel.profilFoto`, veritabanında `VARCHAR(100) NOT NULL` olarak tutulur ve varsayılan değeri `def_pfp.png` değeridir. Mevcut profil, ekip dizini ve dashboard response'ları bu alanı metin olarak taşır. Güvenli bir upload veya authenticated dosya sunum endpoint'i bulunmaz. Frontend şu anda fotoğraf yerine ad ve soyad baş harflerini gösterir.

Bu tasarım entity alanının `String` türünü ve mevcut `def_pfp.png` iç değerini korur:

- `def_pfp.png`: özel profil fotoğrafı yoktur.
- `<uuid>.jpg` veya `<uuid>.png`: özel profil fotoğrafının sunucu tarafından üretilmiş dosya anahtarıdır.

Veritabanına mutlak veya göreli fiziksel yol, kullanıcı tarafından gönderilen orijinal ad ya da URL yazılmaz.

## 3. Seçilen Depolama Yaklaşımı

Fotoğraflar uygulama dizininin dışındaki yapılandırılabilir yerel klasörde saklanır.

Development varsayılanı:

```text
C:\intranet-portal-data\profil-fotograflari
```

Yapılandırma:

```properties
app.profil-fotografi.depolama-yolu=${PROFILE_PHOTO_STORAGE_PATH:C:/intranet-portal-data/profil-fotograflari}
spring.servlet.multipart.max-file-size=2MB
spring.servlet.multipart.max-request-size=3MB
```

`max-request-size`, multipart sınırları ve request başlıkları için dosya sınırından daha yüksek tutulur; kabul edilen dosyanın sınırı yine 2 MB'dir.

Uygulama başlangıcında depolama dizini yoksa oluşturulur. Dizin oluşturulamıyor, normalleştirilemiyor veya yazılamıyorsa uygulama kontrollü biçimde başlamaz. Depolama dizini webroot ya da `src/main/resources/static` altında bulunmaz.

Testler gerçek development dizinini kullanmaz. Her test kendi geçici dizinini yapılandırır ve yalnız o dizini temizler.

## 4. Desteklenen Görseller ve Normalizasyon

Ham upload sınırları:

| Kural | Değer |
|---|---:|
| Format | JPEG veya PNG |
| Dosya boyutu | En fazla 2 MB |
| Genişlik | En fazla 2048 px |
| Yükseklik | En fazla 2048 px |
| Toplam piksel | En fazla 4.194.304 |

Kalıcı dosya sınırları:

- Görselin en uzun kenarı en fazla 1024 piksel olur.
- En-boy oranı korunur.
- Daha küçük görsel büyütülmez.
- JPEG girdisi temiz JPEG, PNG girdisi temiz PNG olarak yeniden üretilir.
- SVG, GIF, WebP ve animasyonlu formatlar kabul edilmez.
- Orijinal dosyanın EXIF, yorum ve yardımcı metadata alanları kalıcı çıktıya taşınmaz.

Doğrulama sırası:

1. Multipart alanının varlığı ve boş olmaması.
2. Ham dosya boyutu.
3. Normalize edilmiş orijinal uzantının allowlist içinde olması.
4. Bildirilen `Content-Type` değerinin allowlist içinde olması.
5. JPEG/PNG dosya imzası kontrolü.
6. Uzantı, MIME ve imzanın aynı formatı göstermesi.
7. Görsel genişliği ve yüksekliğinin tam decode öncesinde okunması.
8. Boyut ve toplam piksel sınırı.
9. Görselin gerçek bir resim olarak decode edilmesi.
10. En fazla 1024 piksel uzun kenarla yeniden üretilmesi.

Kullanıcının ham dosyası kalıcı depolama alanına doğrudan kopyalanmaz.

## 5. API Sözleşmeleri

### 5.1 Fotoğraf yükleme veya değiştirme

```http
PUT /api/profil/fotograf
Authorization: Bearer <access-token>
Content-Type: multipart/form-data
```

Multipart alan adı:

```text
dosya
```

Başarılı response:

```http
200 OK
```

```json
{
  "profilFoto": "a43e135c-80fc-4f23-960b-2c297ba6cf97.jpg"
}
```

Request personel ID almaz. Aktör JWT principal üzerinden belirlenir ve yalnız kendi fotoğrafını değiştirebilir.

### 5.2 Fotoğraf silme

```http
DELETE /api/profil/fotograf
Authorization: Bearer <access-token>
```

Başarılı veya no-op response:

```http
204 No Content
```

Gerçek silmede entity alanı `def_pfp.png` değerine döner. Zaten bu değerde olan kayıt için persistence, dosya sistemi veya audit yan etkisi oluşturulmaz.

### 5.3 Fotoğraf görüntüleme

```http
GET /api/profil-fotograflari/{dosyaAnahtari}
Authorization: Bearer <access-token>
```

Başarılı response:

```http
200 OK
Content-Type: image/jpeg veya image/png
X-Content-Type-Options: nosniff
Cache-Control: private, max-age=3600
```

Response body görsel byte'larıdır. Endpoint public static resource değildir ve mevcut JWT filter chain içinden geçer.

Dosya anahtarı yalnız şu biçimlerden birini kabul eder:

```text
<UUID>.jpg
<UUID>.png
```

`def_pfp.png` fiziksel fotoğraf anahtarı sayılmaz. Frontend bu değerde baş harfli avatar gösterir ve fotoğraf endpoint'ine istek göndermez.

## 6. Yetkilendirme

| İşlem | Aktif authenticated kullanıcı | Pasif kullanıcı | Authentication yok |
|---|---:|---:|---:|
| Kendi fotoğrafını yükleme/değiştirme | İzinli | `403` | `401` |
| Kendi fotoğrafını silme | İzinli | `403` | `401` |
| Bilinen fotoğraf anahtarını görüntüleme | İzinli | `403` | `401` |

Yükleme ve silme endpoint'lerinde istemciden aktör veya hedef personel ID alınmaz. JWT principal personeli bulunamazsa kontrollü `404` döner.

Fotoğraf anahtarları yalnız mevcut profil, ekip dizini, dashboard ve izin verilen personel yönetimi response'larında görünür. Fotoğraf GET endpoint'i, aktif authenticated kullanıcının bildiği geçerli anahtarı döndürür; bu sürümde fotoğraf başına ayrıca rol veya ownership matrisi uygulanmaz.

## 7. Yükleme ve Değiştirme Akışı

1. Security filter JWT'yi doğrular.
2. Service principal personelini yükler ve aktiflik kontrolü yapar.
3. Dosya bütün biçimsel ve içerik doğrulamalarından geçirilir.
4. Görsel decode edilir, gerekirse küçültülür ve metadata'sız temiz görsel olarak yeniden üretilir.
5. Backend `UUID.randomUUID()` ile dosya anahtarı üretir; uzantı doğrulanan çıktı formatından belirlenir.
6. Temiz çıktı depolama kökü altındaki geçici bir dosyaya yazılır.
7. Geçici dosya aynı depolama kökü içinde son UUID adına, mevcut hedefin üzerine yazmayan normal taşıma ile yayımlanır. UUID hedefi çakışırsa mevcut dosya korunur ve yeni UUID ile yeniden denenir.
8. `Personel.profilFoto` yeni anahtarla güncellenir.
9. Güvenli audit kaydı aynı veritabanı transaction'ına katılır.
10. Transaction rollback olursa yeni dosya telafi işlemiyle silinir.
11. Transaction commit olursa önceki değer özel bir UUID anahtarıysa eski dosya silinir.
12. Yeni anahtar `200 OK` response'unda döner.

Görsel işleme veya yeni dosya yazımı başarısız olursa entity ve eski dosya değiştirilmez.

## 8. Dosya Sistemi ve Transaction Tutarlılığı

Dosya sistemi MySQL transaction'ına katılamaz. Tutarlılık aşağıdaki telafi kurallarıyla korunur:

- Yeni kalıcı dosya oluşmadan DB güncellenmez.
- DB transaction'ı rollback olursa yeni dosya silinir.
- Eski dosya DB commit edilmeden silinmez.
- Commit sonrasındaki eski dosya temizliği başarısız olursa yeni DB kaydı geri alınmaz; kullanıcı yeni ve geçerli fotoğrafını görmeye devam eder.
- Commit sonrası temizlik hatası, fiziksel yol veya hassas veri içermeden sunucu loguna yazılır.

Bu yaklaşım, başarısız replacement işleminde kullanıcının mevcut fotoğrafını kaybetmesini engeller. Commit sonrası temizlenemeyen eski dosya erişilemeyen orphan dosya olarak kalabilir; bu operasyonel hata loglanır ve daha sonra kontrollü bakım ile temizlenebilir.

## 9. Silme Akışı

1. JWT ve principal personeli doğrulanır.
2. Personelin aktif olduğu doğrulanır.
3. Mevcut değer `def_pfp.png` ise işlem no-op `204` ile tamamlanır.
4. Entity değeri `def_pfp.png` yapılır.
5. Silme audit kaydı aynı DB transaction'ına yazılır.
6. DB commit edilir.
7. Önceki UUID dosyası, normalize edilmiş hedefin depolama kökü içinde olduğu doğrulandıktan sonra silinir.
8. Response `204 No Content` olur.

Eski dosya DB commit öncesinde silinmez. Böylece rollback durumunda mevcut fotoğraf korunur.

## 10. Dosya Sunum Akışı

1. JWT ve aktif principal kontrol edilir.
2. Path parametresinin UUID ve izin verilen uzantı biçimine uyduğu doğrulanır.
3. Anahtar depolama köküne resolve edilir ve normalize edilir.
4. Sonucun depolama kökü içinde kaldığı doğrulanır.
5. Dosyanın var, düzenli bir dosya ve okunabilir olduğu doğrulanır.
6. Content-Type uzantıdan körlemesine değil, sunucunun izin verdiği anahtar formatından ve gerekirse içerik doğrulamasından belirlenir.
7. Dosya `FileSystemResource` benzeri stream tabanlı response ile döndürülür.
8. `nosniff` ve private cache başlıkları eklenir.

Fotoğraf verisi read-only sunulur ve audit kaydı oluşturmaz.

## 11. Audit

Yeni audit eylemleri:

```text
PROFIL_FOTOGRAFI_GUNCELLENDI
PROFIL_FOTOGRAFI_SILINDI
```

Gerçek yükleme/değiştirme ve gerçek silme audit edilir. Görüntüleme ile no-op silme audit edilmez.

Audit kaydı:

- aktör ve hedef olarak aynı personeli taşır,
- orijinal dosya adını taşımaz,
- UUID dosya anahtarını taşımaz,
- fiziksel yolu taşımaz,
- görsel içeriğini veya metadata'yı taşımaz,
- yalnız güvenli işlem açıklamasını taşır.

## 12. Hata Sözleşmesi

| Durum | HTTP |
|---|---:|
| Token yok veya geçersiz | `401 Unauthorized` |
| Pasif principal | `403 Forbidden` |
| Principal personeli bulunamadı | `404 Not Found` |
| Multipart `dosya` alanı eksik veya boş | `400 Bad Request` |
| Bozuk veya decode edilemeyen görsel | `400 Bad Request` |
| Görsel boyut/piksel sınırı aşılıyor | `400 Bad Request` |
| JPEG/PNG dışındaki format | `415 Unsupported Media Type` |
| Uzantı, MIME ve içerik birbiriyle uyumsuz | `415 Unsupported Media Type` |
| Dosya 2 MB'den büyük | `413 Payload Too Large` |
| Fotoğraf anahtarı geçersiz veya dosya yok | `404 Not Found` |
| Beklenmeyen depolama hatası | Kontrollü `500 Internal Server Error` |

Hata response'u stack trace, decoder mesajı, geçici dosya adı veya fiziksel depolama yolu içermez.

## 13. Frontend Sözleşmesi

Mevcut `profilFoto` alanı dosya anahtarı olmaya devam eder:

- `def_pfp.png`: baş harfli avatar göster.
- `<uuid>.jpg` veya `<uuid>.png`: authenticated API istemcisiyle fotoğraf endpoint'inden `blob` al.

Bearer token doğrudan standart `<img src>` isteğine eklenemediği için frontend API istemcisi görseli `blob` olarak alır, `URL.createObjectURL` ile geçici URL oluşturur ve component unmount olduğunda `URL.revokeObjectURL` çağırır.

Bu tasarım frontend implementasyonunu kapsamaz; yalnız backend sözleşmesini tanımlar.

## 14. Test Stratejisi

Uygulama TDD ile aşağıdaki sırada geliştirilir:

1. Geçerli JPEG upload için RED integration testi.
2. En küçük controller/service/depolama akışı.
3. Geçerli PNG ve temiz yeniden üretme testleri.
4. 1024 piksel çıktı sınırı ve en-boy oranı testi.
5. Boş dosya, 2 MB sınırı, genişlik, yükseklik ve toplam piksel testleri.
6. Sahte uzantı, sahte MIME, hatalı imza, bozuk görsel ve ek payload testleri.
7. UUID adlandırma ve path traversal testleri.
8. `401`, `403`, `404` principal senaryoları.
9. Authenticated GET, content type, `nosniff`, private cache ve bulunamayan dosya testleri.
10. Replacement sırasında yeni anahtar, eski dosya temizliği ve audit testi.
11. DELETE gerçek işlem, no-op ve audit testi.
12. DB rollback durumunda yeni dosyanın temizlenmesi ve eski fotoğrafın korunması testi.
13. Profil, ekip dizini, dashboard ve personel yönetimi response regresyon testleri.
14. İlgili test grubu ve ardından tam backend test paketi/Jacoco doğrulaması.

Test görselleri küçük ve test kodu içinde kontrollü olarak oluşturulur. Gerçek kullanıcının görseli veya repository dışındaki kalıcı dosya kullanılmaz.

## 15. Kapsam Dışı

- Frontend fotoğraf seçme, crop ve preview component'i.
- S3, MinIO veya CDN entegrasyonu.
- Antivirüs servisi entegrasyonu.
- Yüz tanıma, içerik moderasyonu veya uygunsuz içerik analizi.
- Fotoğraf geçmişi ve eski sürümleri kullanıcıya sunma.
- Admin'in başka kullanıcının fotoğrafını değiştirmesi.
- Birden fazla boyutta thumbnail üretme.
- GIF, SVG veya WebP desteği.

## 16. Operasyonel Notlar

- MySQL yedeği profil fotoğraflarını içermez; veritabanı ile depolama klasörü birlikte yedeklenmelidir.
- Backend başka sunucuya taşınırsa depolama klasörü de taşınmalı ve `PROFILE_PHOTO_STORAGE_PATH` yeni mutlak yola ayarlanmalıdır.
- Birden fazla backend instance'ına geçildiğinde yerel klasör ortak storage ile değiştirilmelidir. DB'de UUID anahtarı tutulduğu için bu geçiş entity sözleşmesini değiştirmeden yapılabilir.
- Depolama kökünün işletim sistemi izinleri en az yetki prensibine göre backend servis hesabıyla sınırlandırılmalıdır.

## 17. Kabul Kriterleri

- Aktif authenticated kullanıcı kendi JPEG/PNG fotoğrafını yükleyebilir, değiştirebilir ve silebilir.
- Başkasının hedef ID'sini göndererek fotoğraf değiştirmek mümkün değildir.
- Pasif kullanıcı süresi dolmamış token ile fotoğraf okuyamaz veya değiştiremez.
- Ham dosya doğrudan saklanmaz; temiz yeniden üretilmiş çıktı saklanır.
- Fotoğraf yalnız authenticated endpoint üzerinden alınabilir.
- `def_pfp.png` durumunda frontend baş harfli avatara dönebilir.
- Gerçek mutation audit edilir; read ve no-op audit edilmez.
- Dosya sistemi/DB hata senaryolarında mevcut geçerli fotoğraf kaybedilmez.
- Güvenlik negatif testleri ve mevcut backend regresyon testleri geçer.
