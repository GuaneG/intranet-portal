# Frontend–Backend Entegrasyonu Tasarımı

**Durum:** Faz 1–4 uygulandı. Rol bazlı navigasyon Faz 5 tasarımı kullanıcı tarafından onaylandı; uygulaması bekliyor.

## Amaç

Gerçekten uygulanmış backend akışlarını mevcut React/TailAdmin frontend’ine küçük ve doğrulanabilir fazlarla bağlamak. Entegrasyon; mevcut `src/lib/api.js` içindeki sessiz refresh ve single-flight davranışını, `ProtectedRoute`/`PublicRoute` guard’larını ve kullanılabilir TailAdmin parçalarını korur.

Seçilen yaklaşım, tam bir frontend mimarisi kurmak yerine küçük bir ortak auth katmanı eklemektir:

```text
AuthContext
    -> mevcut api.js
    -> localStorage'daki mevcut token/kullanıcı anahtarları
    -> ProtectedRoute / PublicRoute
    -> AppSidebar / UserDropdown

Route sayfası
    -> apiFetch
    -> backend response DTO
    -> TailAdmin ürün component'ları
```

## Kaynak Gerçek ve Kısıtlar

- Canlı controller, request DTO ve response DTO kodu endpoint sözleşmesinin asıl kaynağıdır.
- `API_SPEC.md` planlanan endpoint’leri de içerdiği için kodda bulunmayan yolu frontend kullanmaz.
- Access token mevcut tasarıma uygun olarak `localStorage` içinde kalır.
- Refresh token yalnız `httpOnly` cookie ile taşınır.
- Token, parola veya hassas response ayrıntıları console’a yazılmaz.
- Frontend görünürlüğü backend yetkilendirmesinin yerine geçmez.
- Yeni dependency veya frontend test framework’ü eklenmez.
- TypeScript/JavaScript dosyaları topluca dönüştürülmez.
- İlgisiz TailAdmin demo sayfaları bu fazlarda değiştirilmez.

## Kapsam

Backend sözleşmesi hazır olduğu için uygulanabilir frontend akışları:

1. Login, sessiz refresh ve logout sağlamlaştırması
2. İlk girişte zorunlu parola değiştirme
3. Kendi profilini görüntüleme
4. Giriş yapmış kullanıcının normal parola değiştirmesi
5. Kendi profilindeki iletişim ve eğitim bilgilerinin sınırlı güncellenmesi
6. Login response’undaki role göre sidebar ve route kullanıcı deneyimi

Backend yazma akışları hazır olsa da kullanıcı seçimi için okuma sözleşmeleri eksik olan akışlar:

- HR personel oluşturma
- HR personel güncelleme
- HR personel pasifleştirme
- Admin rol değiştirme

Bu dört ekran için departman, yönetici ve hedef personel seçimini sağlayan canlı liste/detay endpoint’leri yoktur. Kullanıcıya ham departman ID veya personel UUID girdiren geçici ekran yapılmaz. Gerekli backend sorguları tamamlandıktan sonra bu akışlar ayrı bir tasarım ve uygulama planıyla ele alınır.

## Kapsam Dışı

- Backend endpoint veya DTO değişikliği
- `telNo`, `mezunOkul` ve `mezunBolum` dışındaki kendi profil alanlarını güncelleme
- `mezuniyetYili` düzenlemesi; bu alan mevcut `GET /api/profil` response’unda bulunmadığı için frontend kapsamına alınmaz
- Aktif ekip dizini ve filtreleri
- Profil fotoğrafı yükleme
- İzin talebi/onay modülü
- TailAdmin demo alanlarının toplu temizliği
- Access token saklama modelini değiştirme
- JWT decode ederek ikinci bir kullanıcı/rol kaynağı oluşturma
- Genel amaçlı veri-fetching veya state-management kütüphanesi ekleme

## Ortak Auth Katmanı

Yeni minimal `AuthContext`, yalnız oturumla ilgili paylaşılan state ve işlemleri taşır:

- `kullanici`: `{ ad, soyad, rol }` veya `null`
- `isAuthenticated`: mevcut access token ve kullanıcı state’inden türetilir
- `signIn(credentials)`: login çağrısını yapar, başarılı response’u mevcut localStorage anahtarlarına ve React state’ine yazar
- `signOut()`: backend logout isteğini dener; ağ hatası olsa da local state ve localStorage temizliğini `finally` içinde garanti eder
- `clearSession()`: refresh başarısızlığı veya parola değişikliği sonrası yerel oturumu temizler

Context access token’ı ayrı bir React state kopyasında tutmaz. Token’ın tek istemci kaynağı mevcut `localStorage` anahtarı olarak kalır; `apiFetch` her istek ve retry sırasında buradan okur.

Context ilk açılışta mevcut `token` ve `kullanici` anahtarlarını birlikte okur. Bunlardan yalnız biri varsa tutarsız oturum kalıntısı temizlenir ve kullanıcı sign-in ekranına yönlendirilir.
`api.js` şu sorumlulukları korur:

- base URL
- authenticated `apiFetch`
- 401 sonrası bir kez refresh ve bir kez retry
- aynı sekmedeki paralel 401 cevapları için refresh single-flight
- refresh başarısızlığında session temizliği ve sign-in yönlendirmesi
- server-side logout çağrısı

Public auth çağrıları aynı base URL ve kontrollü hata okuma davranışını paylaşacak biçimde `api.js` üzerinden sunulur. Component’lar kendi sabit `http://localhost:8080` değerini taşımaz.

## Route ve Guard Tasarımı

Mevcut guard anlamları korunur:

- `ProtectedRoute`: oturum yoksa `/signin`
- `PublicRoute`: oturum varsa `/`

Yeni public route:

```text
/ilk-parola
```

Yeni protected route:

```text
/profil/parola
```

Mevcut `/profile` route’u gerçek profil görüntüleme sayfasına dönüştürülür.

HR/Admin ekranları geldiğinde küçük bir rol guard kullanılabilir. Bu guard yalnız kullanıcı deneyimi sağlar; yetkisiz API isteğini engelleyen asıl kontrol backend’deki `@PreAuthorize` kurallarıdır.

## Login ve İlk Parola Akışı

Login formu:

1. Submit sırasında butonu disabled yapar ve ikinci isteği engeller.
2. Ağ ve response hatalarını kontrollü kullanıcı mesajına dönüştürür.
3. `200 OK` cevabında kullanıcı ve token bilgisini ortak auth katmanına aktarır.
4. `428 Precondition Required` cevabında `/ilk-parola` sayfasına yönlendirir.

İlk parola ekranı kullanıcı adı, mevcut parola, yeni parola ve yeni parola tekrarı alır. Login `428` cevabında yalnız kullanıcı adı, ilk parola akış yardımcısı üzerinden `sessionStorage` içinde tutulur ve formun başlangıç değerini oluşturur. Parola, access token veya refresh token bu akış kaydına yazılmaz. Sayfa yenilendiğinde kullanıcı adı korunur; parola başarıyla değiştiğinde veya kullanıcı `/signin` sayfasına döndüğünde ilk parola akış kaydı temizlenir.

Başarılı `PATCH /api/auth/ilk-parola` çağrısı `204` döner. Kullanıcı sign-in ekranına yönlendirilir ve parolasının değiştiğine dair tek kullanımlık mesaj gösterilir.

## Profil Görüntüleme ve Sınırlı Güncelleme

`/profile` route seviyesi sayfa:

- `GET /api/profil` çağrısını `apiFetch` ile yapar,
- response’u yerel page state’inde tutar,
- loading, error, retry ve başarılı görünüm durumlarını koordine eder.

TailAdmin profil kartları salt-okunur ürün component’larına dönüştürülür:

- kimlik ve rol özeti
- iletişim/eğitim bilgileri
- departman ve yönetici
- işe giriş ve sicil bilgileri
- alfabetik yetenekler
- yalnız `PERSONEL` ve `HR` için izin özeti

`yetenekler=[]` boş durum olarak gösterilir. `yonetici` veya opsiyonel profil alanları `null` ise yanlış bilgi üretilmez; “Belirtilmemiş” gibi bağlama uygun metin kullanılır. `ADMIN` ve `YONETICI` için `kalanIzin=null` olduğunda izin kartı gösterilmez.

Salt-okunur kartlar düzenleme sorumluluğu almaz. Profil başarıyla yüklendiğinde `/profile` sayfasındaki ayrı bir inline form yalnız şu alanları düzenler:

- `telNo`
- `mezunOkul`
- `mezunBolum`

Backend `mezuniyetYili` alanını `PATCH /api/profil` request’inde kabul etse de mevcut `GET /api/profil` response’u bu alanı döndürmez. Frontend mevcut değeri güvenle önceden dolduramayacağı için mezuniyet yılı bu fazın kapsamına alınmaz.

Form mevcut değerlerini profil response’undan alır. Submit sırasında metinler trim edilir; yalnız gerçekten değişen alanlar request gövdesine eklenir. Mevcut opsiyonel değer kullanıcı tarafından temizlenirse ilgili alan `null` gönderilir. Hiçbir alan değişmemişse `PATCH /api/profil` çağrısı yapılmaz.

Başarılı `204 No Content` cevabından sonra frontend `GET /api/profil` çağrısını yeniden yapar ve salt-okunur kartları normalize edilmiş güncel response ile yeniler. PATCH başarılı fakat takip eden GET başarısız olursa kullanıcıya bilgilerin kaydedildiği fakat güncel profilin yüklenemediği açıkça bildirilir. Bu durumda yeniden deneme yalnız GET isteğini tekrarlar; PATCH ikinci kez gönderilmez.

Submit sırasında form alanları ve eylem düğmeleri disabled olur; eşzamanlı ikinci PATCH davranışsal olarak engellenir. PATCH hatasında form açık, kullanıcının girdiği değerler korunmuş ve yeniden denenebilir kalır.

## Normal Parola Değiştirme

Protected parola formu:

- mevcut parola
- yeni parola
- yeni parola tekrarı

alanlarını içerir. İstemci yalnız yeni parola tekrarının eşleşmesini kontrol eder; asıl parola politikası ve mevcut parola doğrulaması backend’dedir.

Başarılı `PATCH /api/profil/parola` çağrısından sonra backend bütün refresh token’ları sildiği için frontend de logout akışını çalıştırır, cookie ve yerel oturumu temizler ve kullanıcıyı yeniden girişe yönlendirir. Submit sırasında buton disabled olur ve aynı PATCH iki kez gönderilemez.

## Rol Bazlı Sidebar ve Route Deneyimi

Sidebar menü ve alt menü tanımları opsiyonel `roller` ve `yakinda` alanları alır. Menü, `AuthContext.kullanici.rol` değerine göre filtrelenir; `localStorage` sidebar içinde doğrudan okunmaz. `roller` tanımı bulunmayan öğe bütün authenticated rollere görünür. Görünür alt öğesi kalmayan grup render edilmez.

`yakinda: true` öğeler çalışan sayfa izlenimi vermez: `<Link>` veya click handler yerine tıklanamayan metin olarak render edilir ve salt renge dayanmayan görünür bir “Yakında” rozeti taşır. Bu davranış yeni component, state veya dependency gerektirmez.

| Menü | PERSONEL | YONETICI | HR | ADMIN |
|---|---|---|---|---|
| Ana Sayfa | Aktif | Aktif | Aktif | Aktif |
| Takvim | Yakında | Yakında | Yakında | Yakında |
| Duyurular | Yakında | Yakında | Yakında | Yakında |
| Ekip Dizini | Yakında | Yakında | Yakında | Yakında |
| Ekipmanlar kataloğu | Yakında | Yakında | Yakında | Envanter ve Zimmet içinde |
| İzin Taleplerim | Yakında | Gizli | Yakında | Gizli |
| Ekipman Taleplerim | Yakında | Gizli | Yakında | Gizli |
| Rezervasyon Taleplerim | Yakında | Gizli | Yakında | Gizli |
| Takım Talepleri | Gizli | Yakında | Gizli | Gizli |
| Tüm Talepler | Gizli | Gizli | Gizli | Yakında |
| Personel Yönetimi | Gizli | Gizli | Yakında | Yakında |
| İzin ve Tatil Yönetimi | Gizli | Gizli | Yakında | Yakında |
| Rol Yönetimi | Gizli | Gizli | Gizli | Yakında |
| Envanter ve Zimmet | Gizli | Gizli | Gizli | Yakında |
| Audit Log | Gizli | Gizli | Gizli | Yakında |

Duyurular bütün rollere tek sidebar maddesi olarak görünür. Oluşturma ve moderasyon eylemleri gelecekte sayfa içinde ayrılır: PERSONEL yalnız görüntüler; YONETICI doğrudan takımına duyuru oluşturur; HR, HR/şirket duyurusu oluşturur ve takım duyurularını denetler; ADMIN şirket duyurusu oluşturur ve moderasyon yapar.

YONETICI kendi izin, ekipman veya rezervasyon talebini oluşturmaz. “Takım Talepleri” gelecekte yalnız doğrudan takımın izin ve ekipman taleplerini işleme ekranına gider; rezervasyon onay akışına karıştırılmaz. Yönetici rezervasyonları ürünleşmiş takvim üzerinden görür.

PERSONEL, YONETICI ve HR talep edilebilir ekipman kataloğunu görür. YONETICI kataloğu yalnız görüntüler ve kendi ekipman talebini oluşturmaz. ADMIN ekipmanları ayrı bir katalog maddesi yerine daha kapsamlı “Envanter ve Zimmet” yönetimi içinde görür.

ADMIN kendi talebini oluşturmaz. “Tüm Talepler” gelecekte izin, ekipman ve rezervasyon kayıtlarını bekleyen, onaylanan ve reddedilen durumlarıyla gösterir. Audit Log yalnız ADMIN’e görünür.

Profil bağlantısı mevcut `UserDropdown` içinde “Profilim” olarak kalır; aynı hedef sidebar içinde gereksiz yere tekrarlanmaz. Sidebar’dan yalnız `Pages`, `Charts`, `UI Elements` ve `Authentication` demo grupları kaldırılır. Bu grupların route ve sayfa dosyaları silinmez; doğrudan URL davranışları korunur. `Talepler` ve `Ekip Dizini` sidebar’da kalır.

Takvim dört role de görünür, ancak gerçek API ve ürün verilerine bağlanana kadar tıklanamaz “Yakında” durumundadır. Mevcut `/calendar` demo route’u ve sayfa dosyası doğrudan URL ile erişilebilir kalır.

Mevcut `ProtectedRoute` değiştirilmez. Henüz gerçek rol-sınırlı bir frontend sayfası bulunmadığı için kullanılmayan `RoleRoute` altyapısı bu fazda oluşturulmaz. İlk gerçek rol-sınırlı sayfa geldiğinde guard aynı teslimde oluşturulur, gerçek route’a bağlanır ve dört rolle doğrulanır. Sidebar filtresi ve gelecekteki route guard yalnız kullanıcı deneyimidir; backend yetkilendirmesinin yerine geçmez.

## Hata ve Oturum Davranışı

- Backend’in kontrollü `ErrorResponse.message` alanı, beklenen 400/401/403/404/409/428 cevaplarında kullanılabilir.
- JSON olmayan, ağ kaynaklı veya beklenmeyen hatalarda bağlama özel sabit mesaj gösterilir.
- Ham exception nesnesi, stack trace, SQL ayrıntısı veya response gövdesinin tamamı gösterilmez.
- `apiFetch` 401 gördüğünde mevcut single-flight refresh’i çalıştırır.
- Refresh başarısızsa token ve kullanıcı temizlenir; kullanıcı `/signin` sayfasına yönlendirilir.
- Refresh hata mesajı `sessionStorage` köprüsünden sign-in ekranında bir kez okunur ve sonra silinir.
- Loading sırasında empty mesajı gösterilmez; empty durum hata olarak sunulmaz.
- Mutation formlarında `isSubmitting` ikinci gönderimi davranışsal olarak engeller.
- Profil PATCH’i başarılı fakat takip eden GET başarısızsa durum kısmi başarı olarak gösterilir; yeniden deneme yalnız GET isteğini tekrarlar.

## Fazlar ve Doğrulama

### Faz 1 — Auth/API uyumu

- `AuthContext`
- ortak public auth çağrıları
- login submitting/network/428 davranışı
- `/ilk-parola` ekranı ve ilk parola değiştirme akışı
- logout `finally` temizliği
- refresh hata mesajının sign-in ekranında gösterilmesi
- mevcut guard’ların context ile uyumu

Doğrulama: `npm run lint`, `npm run build` ve login/refresh/logout/428 tarayıcı akışları.

### Faz 2 — Kendi profilini görüntüleme

- `/profile` gerçek API bağlantısı
- TailAdmin profil kartlarının salt-okunur dönüşümü
- loading/error/retry/empty/role durumları

Doğrulama: lint, build ve dört rolle profil response’unun tarayıcı kontrolü.

### Faz 3 — Normal parola değiştirme

- `/profil/parola`
- submit kilidi, kontrollü hata ve başarı sonrası yeniden giriş

Doğrulama: lint, build ve normal parola değiştirme tarayıcı akışı.

### Faz 4 — Sınırlı profil güncelleme

- `/profile` içinde ayrı inline düzenleme formu
- yalnız `telNo`, `mezunOkul` ve `mezunBolum`
- değişen alanlardan kısmi PATCH gövdesi oluşturma
- alan temizlemede `null`, değişiklik yoksa isteksiz tamamlama
- başarılı PATCH sonrasında profil yenileme
- PATCH başarılı/GET başarısız kısmi başarı durumunda yalnız GET retry

Doğrulama: hedef lint, build; değişen tek alan, alan temizleme, değişiklik yok, kontrollü hata, çift submit ve kısmi başarı tarayıcı senaryoları.

### Faz 5 — Rol bazlı navigasyon

- sidebar menü ve alt menülerine `roller` ve `yakinda` metadata’sı
- `AuthContext.kullanici.rol` ile görünürlük filtreleme
- role uygun olmayan öğeleri ve boş kalan grupları gizleme
- hazır olmayan gelecek öğelerini tıklanamaz “Yakında” durumunda gösterme
- PERSONEL/HR kendi izin, ekipman ve rezervasyon talepleri; YONETICI takım talepleri; ADMIN tüm talepler ayrımı
- HR/ADMIN ve yalnız ADMIN yönetim menülerinin onaylı matrise göre hazırlanması
- dört role ortak Duyurular ve Ekip Dizini gelecek görünürlüğü
- yalnız `Pages`, `Charts`, `UI Elements` ve `Authentication` sidebar gruplarını kaldırma
- demo route ve sayfa dosyalarını koruma
- `RoleRoute` oluşturmayı ilk gerçek rol-sınırlı sayfaya erteleme

Doğrulama: dört rol için masaüstü/mobil sidebar; aktif, gizli ve “Yakında” öğeleri; boş grup davranışı; kaldırılan demo grupları; korunan doğrudan demo URL’leri; hedef lint ve production build kontrolleri.

### Faz 6 — HR/Admin ekranları

Bu faz; departman, yönetici ve hedef personel liste/detay sözleşmeleri backend’de tamamlanana kadar blokludur. Önkoşullar sağlandığında oluşturma, güncelleme, pasifleştirme ve rol değiştirme ayrı küçük teslimlere bölünür.

## Test Stratejisi

Repository’de frontend otomatik test altyapısı bulunmadığından yeni framework eklenmez. Her uygulama fazında:

- `npm run lint`
- `npm run build`
- çalışan backend ve frontend ile tarayıcı doğrulaması
- mobil ve masaüstü TailAdmin düzen kontrolü
- olumlu akış, kontrollü hata, ağ hatası ve oturum süresi dolma kontrolü

uygulanır.

Otomatik frontend testlerinin bulunmaması kalan risk olarak raporlanır. Backend testlerinin geçmişte geçmiş olması frontend entegrasyonunun çalıştığını kanıtlamaz; tarayıcı doğrulaması ayrıca yapılır.

Sınırlı profil güncelleme fazında tarayıcı doğrulaması; tek alan değişikliği, mevcut değeri `null` ile temizleme, değişiklik yokken PATCH göndermeme, başarılı PATCH + başarılı GET ve başarılı PATCH + başarısız GET + yalnız GET retry senaryolarını kapsar.

## Tamamlanma Ölçütleri

- Auth bilgisi tek minimal context üzerinden reaktif olarak paylaşılır.
- Mevcut refresh single-flight ve route guard davranışı korunur.
- Login `428` cevabı gerçek ilk parola akışına gider.
- Logout ağ hatasında bile yerel oturumu temizler.
- Profil görüntüleme `GET /api/profil` response’una; sınırlı güncelleme `PATCH /api/profil` sözleşmesine dayanır.
- Profil formu yalnız `telNo`, `mezunOkul` ve `mezunBolum` alanlarını gönderir; `mezuniyetYili` GET response’una eklenene kadar düzenlenebilir gösterilmez.
- Yalnız değişen alanlar PATCH gövdesine alınır; alan temizleme `null` ile yapılır ve değişiklik yoksa PATCH gönderilmez.
- Başarılı profil PATCH’inden sonra kartlar GET ile yenilenir; takip eden GET başarısızsa yeniden deneme PATCH’i tekrarlamaz.
- Normal parola değişikliğinden sonra kullanıcı kontrollü biçimde yeniden girişe yönlendirilir.
- Sidebar login response’undaki role göre şekillenir fakat backend yetkilendirmesinin yerine geçmez.
- Her mutation çift gönderime karşı kilitlenir.
- Loading, empty ve kullanıcıya gösterilebilir error durumları ayrıdır.
- Uygulanmamış backend endpoint’leri frontend’de varmış gibi sunulmaz.
- Her faz için lint, build ve mümkün olan tarayıcı akışları doğrulanır.
