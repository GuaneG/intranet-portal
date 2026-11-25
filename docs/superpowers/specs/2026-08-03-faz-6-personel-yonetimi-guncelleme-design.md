# Faz 6 Personel Yönetimi Güncelleme Sayfası Tasarımı

## Amaç ve kapsam

Faz 6'nın üçüncü küçük tesliminde HR ve ADMIN kullanıcılarının, backend'in hedef rol kurallarına göre görüntüleyebildikleri bir personelin izin verilen bilgilerini ayrı bir sayfadan güncelleyebilmesini sağlamak.

Bu teslim personel pasifleştirme, yeni personel oluşturma veya rol değiştirme işlemi içermez. Bu işlemler farklı endpoint, yetki ve kullanıcı deneyimi gerektirdiği için sonraki ayrı teslimlerde ele alınır.

Backend ve `API_SPEC.md` güncelleme sözleşmesinin source of truth'udur. Frontend bu kuralları değiştirmez veya genişletmez.

## Seçilen yaklaşım

Güncelleme formu `/yonetim/personel/:personelId/duzenle` adresinde ayrı bir route sayfası olarak hazırlanır.

Bu yaklaşım:

- on bir düzenlenebilir alanı anlaşılır bölümlere ayırır,
- doğrudan URL ve sayfa yenilemesini destekler,
- salt-okunur detay ekranının sorumluluğunu korur,
- detay yükleme, lookup ve form gönderim durumlarını birbirinden ayırır,
- modal veya detay içinde düzenleme modunun oluşturacağı sıkışıklığı ve state karmaşasını önler.

Route mevcut korumalı uygulama katmanında yer alır ve `RoleRoute` ile `HR` ve `ADMIN` rollerine açılır. Bu guard yalnız frontend kullanıcı deneyimidir; gerçek yetkilendirme backend tarafından yapılır.

## Ekranlar ve component sorumlulukları

### Personel detay sayfası

Mevcut personel detay sayfasına `Düzenle` bağlantısı eklenir. Bağlantı hedef personelin düzenleme route'una gider.

Bu değişikliğin tek nedeni salt-okunur detay ekranından güncelleme akışına erişim sağlamaktır. Detay ekranının mevcut veri sunumu ve lookup davranışı değiştirilmez.

### Route sayfası

Yeni `PersonelYonetimDuzenle.tsx` sayfası:

- route içindeki `personelId` değerini doğrular,
- personel detayını backend'den yükler,
- departman lookup listesini yükler,
- seçili departmana göre atanabilir yönetici listesini koordine eder,
- loading, error ve retry durumlarını yönetir,
- başarılı kayıt veya iptal sonrasında detay sayfasına yönlendirir.

Geçersiz veya boş route parametresiyle API isteği gönderilmez.

### Form component'ı

Yeni `PersonelYonetimDuzenleFormu.tsx` component'ı form alanlarını, kullanıcı girdisini, temel istemci doğrulamasını, değişiklik karşılaştırmasını ve gönderim durumunu yönetir.

Form şu bölümlere ayrılır:

- Kişisel bilgiler: ad, soyad, doğum tarihi, e-posta
- İş bilgileri: departman, yönetici, işe giriş tarihi
- İletişim ve eğitim: telefon, mezun olunan okul, mezun olunan bölüm, mezuniyet yılı

Hedef personelin kullanıcı adı, rolü ve aktiflik durumu yalnız bağlam bilgisi olarak gösterilir. Bu değerler güncelleme request'ine eklenmez.

## Backend sözleşmesi ve PATCH oluşturma

Güncelleme `PATCH /api/hr/personel/{personelId}` endpoint'ine gönderilir.

Request yalnız gerçekten değişen alanları içerir. Güncellenebilir alanlar:

- `ad`
- `soyad`
- `dogumTarihi`
- `ePosta`
- `departmanId`
- `isGirisTarihi`
- `yoneticiId`
- `telNo`
- `mezunOkul`
- `mezunBolum`
- `mezuniyetYili`

`personelId`, `kullaniciAdi`, rol, aktiflik, parola ve diğer server-controlled alanlar request'e eklenmez.

`ad`, `soyad`, `dogumTarihi`, `ePosta`, `departmanId` ve `isGirisTarihi` boş veya `null` gönderilemez. Frontend kullanıcıya temel doğrulama mesajı gösterir; nihai doğrulama backend'de kalır.

`yoneticiId`, `telNo`, `mezunOkul`, `mezunBolum` ve `mezuniyetYili` kullanıcı tarafından temizlenirse açıkça `null` gönderilir. Boş metin temizleme anlamında backend'e gönderilmez.

Tarih değerleri backend sözleşmesine uygun `YYYY-MM-DD` biçiminde tutulur ve gönderilir.

## Departman ve yönetici ilişkisi

Departman seçimi değiştiğinde mevcut yönetici seçimi temizlenir ve yeni departmanın atanabilir yöneticileri yüklenir.

Yönetici alanında `Yönetici yok` seçeneği bulunur ve bu seçim request'te `yoneticiId: null` olarak temsil edilir.

Departman gerçekten değiştiyse backend sözleşmesi gereği aynı PATCH request'inde `yoneticiId` de bulunur. Kullanıcı yeni departmandan bir yönetici seçebilir veya yöneticisiz devam edebilir.

Yönetici lookup yüklenirken seçim alanı devre dışıdır. Departman değişmişken lookup yüklenemezse kayıt engellenir ve yeniden deneme sunulur. Departman değişmemişse yönetici lookup hatası, yönetici alanı değiştirilmediği sürece diğer alanların güncellenmesini engellemez.

Atanabilir yönetici seçenekleri yalnız backend response'undan oluşturulur. Frontend yönetici uygunluğunu tahmin etmez.

## Durum ve veri akışı

Sayfa ilk açıldığında personel detayı ve departman listesi yüklenir. Başlangıç form değerleri personel detayından oluşturulur. Yönetici seçenekleri hedef personelin mevcut departmanına göre alınır.

Form başlangıç değerleriyle güncel değerleri karşılaştırır. Değişiklik yoksa boş PATCH gönderilmez ve kullanıcıya değişiklik bulunmadığı bildirilir.

Gönderim sırasında kaydetme ve iptal eylemleri devre dışı bırakılarak çift istek engellenir. Başarısız request sonrasında form değerleri korunur.

Başarılı `204 No Content` response sonrasında `/yonetim/personel/:personelId` detay adresine `replace` ile dönülür. Detay sayfası güncel veriyi backend'den yeniden yükler; frontend PATCH sonucunu tahmin ederek yerel detay nesnesi üretmez.

İptal eylemi API isteği göndermeden detay sayfasına döner. Bu teslimde tarayıcı veya route değişiminde kaydedilmemiş değişiklik uyarısı eklenmez.

Bir personel ID'si için başlayan eski detay veya lookup response'u başka bir personel ID'sine ait yeni ekran state'ini ezemez. Gerekli istek türleri birbirinden bağımsız eski-response koruması kullanır.

## Loading, empty ve error davranışı

- Personel detayı yüklenirken form yerine loading durumu gösterilir.
- Detay yüklenemezse form oluşturulmaz; güvenli hata, `Tekrar dene` ve detay/listeden geri dönüş seçenekleri sunulur.
- Departman lookup yüklenemezse departman alanı kullanılamaz ve ayrı retry sunulur.
- Yönetici lookup hatası yalnız ilişkili alanı etkiler; departman değişikliği sırasında güvenli kayıt kuralı uygulanır.
- Backend'in güvenli `400`, `403`, `404` ve `409` mesajları mevcut `ApiError` yapısı üzerinden kullanıcıya gösterilir.
- `401` ve token yenileme davranışı mevcut ortak `apiFetch` altyapısında kalır.
- Teknik exception, response nesnesi veya hassas veri kullanıcıya gösterilmez.
- Hata mesajları erişilebilir `role="alert"`, loading ve başarı bildirimleri uygun canlı bölge davranışıyla sunulur.

## Güvenlik ve yetki sınırları

Frontend route ve buton görünürlüğü gerçek yetkilendirme değildir.

Backend:

- aktörü JWT principal'dan alır,
- aktif HR ve ADMIN rollerini doğrular,
- hedef personelin rol ve öz-hedef kurallarını uygular,
- departman ve yönetici ilişkisini doğrular,
- e-posta çakışmasını kontrol eder,
- yalnız değişen alanları audit kaydına dahil eder.

Frontend backend'in reddettiği bir hedefi, alanı veya ilişkiyi zorla geçerli kabul etmez. Parola veya token form state'ine ya da request payload'ına eklenmez.

## Kapsam dışı

Bu teslimde şunlar yapılmaz:

- personel pasifleştirme veya yeniden aktifleştirme,
- rol değiştirme,
- yeni personel oluşturma,
- kullanıcı adı değiştirme,
- profil fotoğrafı yönetimi,
- kaydedilmemiş değişiklik için global route blocker altyapısı,
- backend veya başka modül refactor'u.

## Kabul ve doğrulama ölçütleri

- HR ve ADMIN yalnız backend'in izin verdiği hedefler için düzenleme sayfasını açabilir.
- Düzenleme route'u doğrudan URL ve refresh ile çalışır.
- Form backend detay response'uyla doğru başlangıç değerlerini gösterir.
- Request yalnız değişen ve izin verilen alanları içerir.
- Nullable alanlar açıkça temizlendiğinde `null` gönderilir.
- Departman değişikliğinde `yoneticiId` aynı request'e eklenir.
- Yönetici seçenekleri seçili departmana göre backend'den alınır.
- Başarısız request form değerlerini silmez.
- `409` e-posta çakışması anlaşılır biçimde gösterilir.
- Başarılı `204` sonrasında güncel detay sayfası açılır.
- İptal herhangi bir update isteği oluşturmaz.
- Personel pasifleştirme ve rol değiştirme eylemleri bu teslimde görünmez.
- İlgili dosyaların hedef lint kontrolü ve `npm run build` başarılı olur.
- Tarayıcı kabul testinde yükleme, düzenleme, nullable alan temizleme, departman-yönetici ilişkisi, hata ve yönlendirme akışları doğrulanır.

