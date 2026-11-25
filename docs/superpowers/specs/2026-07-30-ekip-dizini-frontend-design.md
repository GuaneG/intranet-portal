# Ekip Dizini Frontend Tasarımı

**Tarih:** 2026-07-30  
**Durum:** Kullanıcı tarafından onaylandı; backend `arama` sözleşmesinin tamamlanması bekleniyor.

## Amaç

Aktif personelleri bütün authenticated rollere gösteren ekip dizinini iki yerde ürünleştirmek:

- dashboard üzerinde ilk beş kişilik kısa önizleme,
- `/ekip-dizini` route’unda filtrelenebilir ve sayfalanabilir tam görünüm.

İki görünüm aynı backend endpoint’ini, aynı response tiplerini ve aynı personel listeleme component’ını kullanır. Ekip dizini personel yönetimi hedef seçicisi değildir; response’ta `personelId`, rol veya aktiflik bilgisi bulunmaz.

## Kapsam

### Dashboard önizlemesi

- `GET /api/ekip-dizini?page=0&size=5` çağrılır.
- En fazla beş aktif personel backend’in kararlı alfabetik sıralamasıyla gösterilir.
- Her satırda avatar, ad soyad, departman ve yetenekler bulunur.
- İlk iki yetenek rozet olarak gösterilir; kalan yetenek sayısı `+N` biçiminde özetlenir.
- Filtre ve pagination kontrolü bulunmaz.
- “Tüm ekibi gör” bağlantısı `/ekip-dizini` route’una gider.
- Loading, error/retry ve empty durumları yalnız kartı etkiler; dashboard’un kalanını engellemez.

### Tam ekip dizini

- Route `/ekip-dizini` olur ve mevcut `ProtectedRoute` altındaki `AppLayout` içinde çalışır.
- Bütün authenticated roller erişebilir; ayrıca rol bazlı route guard gerekmez.
- Kullanıcı ad/soyad metni, departman ve yetenek ile filtreleyebilir.
- Sonuçlar backend pagination bilgisiyle gösterilir.
- Personel satırlarında bütün yetenekler gösterilir.
- Yetenek bulunmadığında “Yetenek belirtilmemiş” metni kullanılır.

## Backend Sözleşmesi

Liste endpoint’i:

```http
GET /api/ekip-dizini?page={page}&size={size}&arama={arama}&departmanId={id}&yetenekId={id}
Authorization: Bearer <access-token>
```

Parametreler:

- `page`: sıfır tabanlı sayfa; dashboard için `0`.
- `size`: dashboard için `5`, tam sayfa için seçilen sabit sayfa boyutu.
- `arama`: opsiyonel ad/soyad araması.
- `departmanId`: opsiyonel tek departman filtresi.
- `yetenekId`: opsiyonel tek yetenek filtresi.
- Arama, departman ve yetenek birlikte gönderildiğinde backend `AND` uygular.

Frontend implementasyonundan önce backend’in güncel `API_SPEC.md` belgesinde `arama` için şu davranışlar kesinleşmiş olmalıdır:

- baştaki ve sondaki boşlukların trim edilmesi,
- boş metnin arama filtresi uygulanmamış gibi değerlendirilmesi,
- ad, soyad ve `ad + soyad` üzerinde kısmi eşleşme,
- büyük/küçük harf ve Türkçe karakter davranışı,
- kabul edilen maksimum arama uzunluğu,
- arama ile departman ve yeteneğin `AND` birleşimi,
- arama koşulunun hem liste hem pagination count sorgusunda uygulanması.

Frontend, backend’in belgelediği maksimum uzunluğu input `maxLength` değeri olarak kullanır; backend sözleşmesinden farklı ek arama normalizasyonu üretmez.

Sayfalı response:

```json
{
  "icerik": [
    {
      "ad": "Ayşe",
      "soyad": "Yılmaz",
      "departman": "Bilgi Teknolojileri",
      "profilFoto": "def_pfp.png",
      "yetenekler": [
        { "id": 1, "ad": "Java" }
      ]
    }
  ],
  "sayfa": 0,
  "sayfaBoyutu": 20,
  "toplamKayit": 1,
  "toplamSayfa": 1
}
```

Filtre seçenekleri:

```http
GET /api/departments
GET /api/skills
Authorization: Bearer <access-token>
```

İki lookup da `[{ "id": number, "ad": string }]` döndürür.

Backend `arama` parametresini uygulayıp kesin sözleşmeyi teslim etmeden frontend implementasyonu başlatılmaz. Departman, yetenek ve pagination sözleşmeleri mevcut backend kodunda hazırdır.

## Component ve Dosya Yapısı

### Yeni dosyalar

#### `frontend/src/types/ekipDizini.ts`

Şu frontend tiplerini taşır:

- lookup seçeneği,
- ekip dizini personeli,
- sayfalı ekip dizini response’u,
- ekip dizini sorgu parametreleri.

#### `frontend/src/components/ekip-dizini/EkipDiziniListesi.tsx`

Yalnız personel satırlarının ortak sunumundan sorumludur:

- veri çağrısı yapmaz,
- filtre veya pagination state’i tutmaz,
- dashboard ve tam sayfa tarafından kullanılır,
- dashboard için gösterilecek yetenek sayısını props üzerinden sınırlar,
- tam sayfada bütün yetenekleri satır içinde sararak gösterir,
- satırlar etkileşim veya kişi bazlı local state taşımaz,
- masaüstünde yatay, mobilde alt alta uyarlanır.

Response bilinçli olarak `personelId` taşımaz. React render anahtarı aynı response içindeki sıra, ad, soyad ve departman birleşiminden oluşturulur. Sıra değeri yalnız aynı isim ve departmana sahip iki satırı birbirinden ayırır; domain kimliği olarak kullanılmaz. Satırlar state taşımadığı için filtre veya sayfa değişiminde yanlış kişiye state aktarımı oluşmaz.

#### `frontend/src/components/dashboard/EkipDiziniOnizleme.tsx`

Dashboard kartından sorumludur:

- `page=0&size=5` ile veri yükler,
- loading, error/retry ve empty durumlarını kart içinde yönetir,
- ortak `EkipDiziniListesi` component’ını iki yetenek sınırıyla kullanır,
- `/ekip-dizini` bağlantısını gösterir.

#### `frontend/src/pages/EkipDizini.tsx`

Tam route sayfasından sorumludur:

- liste ve lookup isteklerini koordine eder,
- taslak filtre alanları ile uygulanmış filtreleri ayrı tutar,
- filtre submit edildiğinde `page=0` yapar,
- pagination state’ini yönetir,
- loading, error/retry ve empty durumlarını koordine eder,
- ortak `EkipDiziniListesi` component’ını yetenek sınırı olmadan kullanır.

### Değişecek dosyalar

#### `frontend/src/lib/api.js`

- Query parametrelerini `URLSearchParams` ile oluşturan ekip dizini GET fonksiyonu eklenir.
- Departman ve yetenek lookup GET fonksiyonları eklenir.
- Mevcut `apiFetch`, refresh ve kontrollü `ApiError` davranışı kullanılır.
- Parametreli liste çağrılarına profil single-flight yaklaşımı kopyalanmaz.

#### `frontend/src/pages/Dashboard/Home.tsx`

- `RecentOrders` import ve kullanımı kaldırılır.
- Aynı dashboard alanında `EkipDiziniOnizleme` gösterilir.
- Diğer dashboard kartları değiştirilmez.

#### `frontend/src/App.tsx`

- Mevcut protected layout grubuna `/ekip-dizini` route’u eklenir.
- Demo route’lar değiştirilmez.

#### `frontend/src/layout/AppSidebar.tsx`

- “Ekip Dizini” öğesindeki `yakinda` kaldırılır.
- `path: "/ekip-dizini"` eklenir.
- Rol matrisi ve diğer menü öğeleri değiştirilmez.

### Değişmeyecek dosyalar

- `frontend/src/components/ecommerce/RecentOrders.tsx`
- `frontend/src/components/form/Select.tsx`
- backend kaynak dosyaları
- Faz 6 HR/Admin personel yönetimi dosyaları

`RecentOrders` ürün component’ına dönüştürülmez; dashboard yalnız bu demo component’ı artık kullanmaz. Mevcut `Select` internal state kullandığı için filtre sıfırlama akışında değiştirilmez; tam sayfa kontrollü native select kullanır.

## Veri Akışı

### Dashboard

```text
Home
  → EkipDiziniOnizleme
      → GET /api/ekip-dizini?page=0&size=5
      → EkipDiziniListesi
          → ilk 2 yetenek +N
```

### Tam sayfa

```text
/ekip-dizini
  → liste + departman lookup + yetenek lookup
  → taslak filtre alanları
  → Filtrele veya Enter
      → uygulanmış filtreleri güncelle
      → page=0
      → GET /api/ekip-dizini
  → sayfa değişimi
      → uygulanmış filtreleri koru
      → yalnız page değerini değiştir
```

Filtreler kullanıcı yazarken veya seçim yaparken otomatik istek göndermez. “Filtrele” düğmesi ve form submit tek liste isteği başlatır. “Temizle” bütün taslak ve uygulanmış filtreleri kaldırır, sayfayı sıfırlar ve filtresiz listeyi yükler.

Tam sayfa her liste isteğine artan bir istek sıra numarası verir. Yalnız en son başlatılan isteğin sonucu veya hatası state’e yazılabilir; geç tamamlanan eski cevaplar yok sayılır. İlk yüklemede placeholder gösterilir. Filtreleme, temizleme, pagination veya retry sırasında mevcut başarılı liste ekranda korunur, liste alanı `aria-busy` olur ve yeni istek tamamlanana kadar Filtrele, Temizle, Önceki ve Sonraki eylemleri geçici olarak pasifleşir.

## Görsel Tasarım

- Mevcut TailAdmin/JForce renkleri, spacing’i ve tipografisi korunur.
- Yeni font, tasarım dependency’si veya genel tema değişikliği yapılmaz.
- Dashboard kartı mevcut `Recent Orders` alanının ölçüsü ve çerçevesini korur.
- Kart başlığı “Ekip Dizini”, yardımcı metni “Aktif ekipten ilk 5 kişi” olur.
- Tam sayfa filtreleri tek, kompakt bir üst bölümde yer alır.
- Yetenek rozetleri personel satırlarının ayırt edici görsel unsurudur.
- Mobil görünümde personel bilgileri alt alta yerleşir; yatay taşma zorunlu kılınmaz.
- Gereksiz animasyon veya yalnız renkle anlam taşıyan durum kullanılmaz.

## Avatar Davranışı

Backend şu anda `profilFoto` alanında yalnız dosya adı döndürür; fotoğrafı sunan kesin ve güvenli bir URL sözleşmesi yoktur. Frontend tahmini dosya yolu veya harici kaynak URL’si üretmez.

- Bu teslimde `profilFoto` değeri görüntü kaynağı olarak kullanılmaz.
- Bütün personeller için ad ve soyadın ilk harflerinden avatar üretilir.
- Baş harfler Türkçe ad ve soyad metninden güvenli biçimde türetilir.
- Avatar bütün kullanıcılara aynı JForce marka rengiyle gösterilir; dekoratif rastgele renk üretilmez.

Gerçek profil fotoğrafı gösterimi; backend’in izinli kaynak/origin, URL üretimi ve bulunamayan görsel davranışını tanımladığı ayrı fotoğraf sözleşmesi tamamlandığında bu tasarıma eklenir.

## Loading, Error ve Empty Durumları

### Dashboard kartı

- İlk yüklemede kart yüksekliğini koruyan kısa satır placeholder’ları gösterilir.
- Liste hatasında “Ekip dizini yüklenemedi” ve “Tekrar dene” gösterilir.
- Boş sonuçta “Gösterilecek aktif personel bulunmuyor” gösterilir.
- Hata dashboard’un diğer component’larını etkilemez.

### Tam sayfa

- İlk liste yüklenirken empty mesajı gösterilmez.
- Liste yüklenemezse sayfa içinde kontrollü hata ve yeniden deneme sunulur.
- Sonraki liste isteklerinde önceki başarılı içerik korunur ve yükleniyor durumu ayrıca belirtilir.
- Eski bir request yeni isteğin sonucunu veya hatasını ezemez.
- Lookup isteklerinden biri başarısız olup liste başarılıysa personeller gösterilmeye devam eder; filtre bölümünde ayrı uyarı ve lookup retry bulunur.
- Filtreli sonuç boşsa “Seçilen filtrelere uygun personel bulunamadı” gösterilir.
- Filtresiz sonuç boşsa “Gösterilecek aktif personel bulunmuyor” gösterilir.
- Ham exception, response gövdesi veya hassas backend ayrıntısı gösterilmez.

## Pagination

- Backend sıfır tabanlı `sayfa` değeri frontend state’inde aynen korunur.
- Kullanıcıya sayfa numarası bir tabanlı gösterilir: örneğin `1 / 4`.
- İlk sayfada “Önceki”, son sayfada “Sonraki” pasif olur.
- `toplamSayfa=0` durumunda iki pagination eylemi de pasif olur.
- Sayfa değişirken uygulanmış arama, departman ve yetenek filtreleri korunur.
- Dashboard önizlemesinde pagination kontrolü gösterilmez.

## Güvenlik ve Yetki

- Ekip dizini yalnız mevcut protected layout içinde render edilir.
- Endpoint bütün authenticated rollere açık olduğu için `RoleRoute` kullanılmaz.
- Frontend filtreleri veya görünürlüğü backend yetkilendirmesinin yerine geçmez.
- Token, personel ID’si veya hassas veri component state’ine gereksiz yere kopyalanmaz.
- API metinleri normal JSX interpolation ile gösterilir; HTML olarak render edilmez.

## Doğrulama

Yeni test framework’ü eklenmez. Uygulama sonrasında:

- hedef dosya lint kontrolleri,
- `npm run build`,
- mümkünse tam `npm run lint`,
- dört authenticated rolle dashboard önizlemesi,
- dört authenticated rolle tam ekip dizini,
- dashboard’da en fazla beş kişi,
- arama, departman ve yetenek filtreleri,
- üç filtrenin birlikte `AND` davranışı,
- filtre submit ve temizleme,
- pagination sınırları,
- Türkçe ad/soyad için baş harf avatarı,
- eski liste isteğinin yeni sonucu ezmemesi,
- loading, liste hatası, lookup hatası, empty ve retry,
- masaüstü ve mobil yerleşim,
- `/ekip-dizini` sidebar aktif durumu,
- doğrudan route erişimi ve refresh

kontrol edilir.

Tam repository lint’inde görev öncesinden kalan ilgisiz hatalar bulunursa hedef lint sonucundan ayrı raporlanır. Backend `arama` sözleşmesi ve ilgili testleri doğrulanmadan frontend implementasyonu başlatılmaz.
