# Faz 6 Personel Yönetimi Detay Ekranı Tasarımı

## 1. Amaç

Faz 6'nın ikinci küçük tesliminde HR ve ADMIN kullanıcılarının personel yönetimi listesinden güvenli, salt-okunur bir personel detay sayfasına ulaşmasını sağlamak.

Bu teslim oluşturma, güncelleme, pasifleştirme veya rol değiştirme işlemi içermez. Detay ekranı bu işlemlerin sonraki ayrı teslimleri için temel oluşturur.

## 2. Kapsam ve güvenlik sınırı

- Yeni route `/yonetim/personel/:personelId` olur.
- Route, `RoleRoute roller={["HR", "ADMIN"]}` ile frontend UX seviyesinde korunur.
- Gerçek hedef yetkisi backend tarafından `GET /api/hr/personel/{personelId}` üzerinde doğrulanır.
- URL'deki `personelId` güvenilir kabul edilmez ve yalnız backend isteğine aktarılır.
- Personel ID route'un çalışması için URL'de bulunur; ancak ham UUID sayfa içeriğinde veya kullanıcı mesajlarında render edilmez. Parola, token veya backend'in detay response'unda bulunmayan hassas alanlar kullanıcıya gösterilmez.
- Backend ve mevcut `ProtectedRoute` değiştirilmez.

## 3. Kullanıcı akışı

1. HR veya ADMIN `/yonetim/personel` listesini açar.
2. İlgili personel satırındaki `Detayı Gör` bağlantısını seçer.
3. Uygulama `/yonetim/personel/{personelId}` route'una gider.
4. Detay sayfası personel kaydını backend'den getirir.
5. Detay başarılı olunca departman lookup'u yüklenir. `yoneticiId` null değilse atanabilir yönetici lookup'u bununla paralel çalışır; null ise gereksiz yönetici isteği gönderilmez.
6. Kullanıcı `Personel listesine dön` bağlantısıyla varsayılan aktif filtreli listeye döner.

Liste filtresi ve sayfası dönüşte korunmaz; liste başlangıç durumu olan `aktif=true`, `page=0` ile yeniden açılır.

## 4. Mimari ve component sorumlulukları

### 4.1 Route sayfası

`frontend/src/pages/PersonelYonetimDetayi.tsx`:

- route parametresinden `personelId` değerini alır,
- detay ve lookup isteklerini koordine eder,
- loading, error ve retry state'lerini yönetir,
- lookup verisini kullanıcıya gösterilecek departman ve yönetici adına dönüştürür,
- hazır görünüm modelini sunum component'ına geçirir.

### 4.2 Sunum component'ı

`frontend/src/components/personel-yonetimi/PersonelYonetimDetayBilgileri.tsx`:

- yalnız props ile aldığı hazır veriyi gösterir,
- API çağrısı, route parametresi veya authentication bilgisi içermez,
- rol ve aktiflik durumunu mevcut `Badge` component'ıyla sunar.

### 4.3 Liste tablosu

`PersonelYonetimTablosu.tsx` mevcut dört sütuna ek olarak `İşlem` sütunu alır. Her satırda `/yonetim/personel/{personelId}` adresine giden klavye erişilebilir `Detayı Gör` bağlantısı bulunur. Bütün satır tıklanabilir yapılmaz.

### 4.4 Tip ve API katmanı

`personelYonetim.ts` içine backend response'larıyla bire bir uyumlu şu tipler eklenir:

- `PersonelYonetimDetayi`
- `AtanabilirYoneticiSecenegi`

`api.js` içine mevcut `apiFetch`, `ApiError` ve `apiErrorOlustur` akışını kullanan şu fonksiyonlar eklenir:

- `personelDetayiniGetir(personelId)`
- `atanabilirYoneticileriGetir(departmanId, haricPersonelId)`

Path ve query değerleri `encodeURIComponent` veya `URLSearchParams` ile güvenli biçimde oluşturulur.

## 5. Veri sözleşmesi ve lookup çözümleme

Detay ekranı `GET /api/hr/personel/{personelId}` response'undaki şu alanları kullanır:

- `personelId`
- `kullaniciAdi`
- `ad`
- `soyad`
- `dogumTarihi`
- `ePosta`
- `departmanId`
- `yoneticiId`
- `isGirisTarihi`
- `telNo`
- `mezunOkul`
- `mezunBolum`
- `mezuniyetYili`
- `rol`
- `aktif`

Departman adı mevcut `GET /api/departments` lookup'unda `departmanId` ile eşleştirilir.

Yönetici adı, detay başarılı olduktan ve `yoneticiId` değerinin null olmadığı görüldükten sonra şu endpoint ile çözülür:

```http
GET /api/hr/personel/atanabilir-yoneticiler?departmanId={departmanId}&haricPersonelId={personelId}
```

- `yoneticiId` null ise `Atanmamış` gösterilir; atanabilir yönetici endpoint'i çağrılmaz.
- Lookup'ta `yoneticiId` eşleşirse yönetici ad-soyadı ve kullanıcı adı gösterilir.
- `yoneticiId` mevcut fakat lookup eşleşmesi yoksa ham ID yerine `Yönetici bilgisi görüntülenemiyor` gösterilir.
- Bu durum, lookup'un yalnız aktif yöneticileri döndürmesi nedeniyle mevcut yöneticinin sonradan pasifleşmesi halinde oluşabilir.

## 6. Ekran düzeni

Üst özet alanında:

- profil fotoğrafı yerine ad-soyad baş harflerinden avatar,
- ad-soyad,
- kullanıcı adı,
- rol rozeti,
- aktif/pasif rozeti

gösterilir.

Alt bilgi alanları üç gruba ayrılır:

1. İletişim: e-posta, telefon, doğum tarihi.
2. İş bilgileri: departman, yönetici, işe giriş tarihi.
3. Eğitim: okul, bölüm, mezuniyet yılı.

Null veya boş opsiyonel alanlar `Belirtilmemiş` olarak gösterilir. Tarihler kullanıcıya Türkçe yerel tarih biçiminde sunulur. Bu teslimde düzenleme, pasifleştirme veya rol değiştirme butonu bulunmaz.

## 7. Loading, empty ve error davranışları

### Detay isteği

- İlk yüklemede detay kartı yerine açık bir loading durumu gösterilir.
- Ağ, `403`, `404` veya diğer kontrollü API hatalarında detay kartı gösterilmez.
- Kullanıcıya kontrollü hata mesajı, `Tekrar dene` ve `Personel listesine dön` eylemleri sunulur.
- Beklenmeyen teknik exception mesajı doğrudan kullanıcıya aktarılmaz.

### Lookup istekleri

- Lookup yüklenirken detayın temel alanları görünür kalır.
- Departman veya yönetici lookup hatası bütün detay ekranını kapatmaz.
- Hata yalnız ilgili alanda kontrollü mesaj ve lookup'a özel `Tekrar dene` eylemi üretir.
- Aynı anda tekrar eden istekler engellenir.
- Geç dönen eski istek yeni route veya retry sonucunu ezemez.

## 8. Route ve rol davranışı

- HR ve ADMIN detay route'unu açabilir.
- PERSONEL ve YONETICI frontend'de `/` adresine `replace` ile yönlendirilir.
- Oturumu olmayan kullanıcı önce dış `ProtectedRoute` tarafından `/signin` adresine yönlendirilir.
- HR/Admin'in yasak hedefe ait ID denemesi frontend görünürlüğüne bırakılmaz; backend'in `403/404` cevabı kontrollü hata olarak gösterilir.

## 9. Dosya etkisi

- Create `frontend/src/pages/PersonelYonetimDetayi.tsx`
- Create `frontend/src/components/personel-yonetimi/PersonelYonetimDetayBilgileri.tsx`
- Modify `frontend/src/types/personelYonetim.ts`
- Modify `frontend/src/lib/api.js`
- Modify `frontend/src/components/personel-yonetimi/PersonelYonetimTablosu.tsx`
- Modify `frontend/src/App.tsx`

Sidebar, backend, Markdown dışı ilgisiz frontend dosyaları, dependency ve test altyapısı değişmez.

## 10. Doğrulama

- HR ve ADMIN listeden detay sayfasına ulaşır.
- Detay route'u doğrudan URL ve refresh ile çalışır.
- PERSONEL ve YONETICI doğrudan detay URL'sinden `/` adresine yönlenir.
- Departman ve aktif yönetici adı doğru eşleşir.
- Yönetici yok, yönetici eşleşmiyor ve boş opsiyonel alan durumları doğru gösterilir.
- Detay hata/retry ile lookup hata/retry davranışları ayrı doğrulanır.
- Listeye dönüş başlangıç filtrelerini açar.
- Masaüstü ve mobil düzen kontrol edilir.
- Hedef lint ve production build çalıştırılır.
- Tam lint'teki görev öncesi ilgisiz hatalar ayrı raporlanır.
- Otomatik frontend test altyapısının bulunmaması kalan risk olarak belirtilir.

