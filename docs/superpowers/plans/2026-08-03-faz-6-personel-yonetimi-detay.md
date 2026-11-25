# Faz 6 Personel Yönetimi Detay Ekranı Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** HR ve ADMIN için listeden erişilebilen, doğrudan URL ve refresh ile çalışan, güvenli ve salt-okunur personel detay ekranı oluşturmak.

**Architecture:** Ayrı route sayfası URL'deki personel ID ile detay isteğini yapar; başarılı detaydan sonra departman ve gerekiyorsa yönetici lookup verilerini çözer. API/state sorumluluğu route sayfasında, salt görünüm sorumluluğu ayrı detay component'ında kalır. `RoleRoute` yalnız UX korumasıdır; gerçek hedef yetkisini backend uygular.

**Tech Stack:** React 19, TypeScript/JavaScript, React Router, Tailwind/TailAdmin, mevcut `apiFetch`, `Badge` ve tablo component'ları.

## Global Constraints

- Yalnız frontend dosyaları ve bu plan kapsamındaki Markdown belgeleri değişebilir.
- Backend, dependency, test framework'ü, `ProtectedRoute`, sidebar ve ilgisiz frontend dosyaları değişmez.
- Git komutlarını yalnız kullanıcı çalıştırır; agent Git komutu çalıştırmaz.
- Her görev ayrı uygulanır, doğrulanır ve kullanıcıya raporlanır.
- Kullanıcı kodu kendisi yazmak istediği sürece agent yorumlu, kopyalanabilir küçük adımlar verir ve sonucu inceler.
- Personel ID URL'de bulunabilir; sayfa içeriğinde, hata mesajında veya lookup fallback'inde ham UUID render edilmez.
- Parola, token veya backend response'unda bulunmayan hassas alanlar saklanmaz ve gösterilmez.
- Listeye dönüş filtre ve sayfayı korumaz; `/yonetim/personel` başlangıç durumu `aktif=true`, `page=0` olur.
- Frontend rol görünürlüğü backend yetkilendirmesinin yerine geçmez.

---

## Dosya Haritası

- Modify `frontend/src/types/personelYonetim.ts`: detay ve yönetici lookup response tipleri.
- Modify `frontend/src/lib/api.js`: detay ve atanabilir yönetici GET fonksiyonları.
- Create `frontend/src/components/personel-yonetimi/PersonelYonetimDetayBilgileri.tsx`: salt sunum component'ı.
- Create `frontend/src/pages/PersonelYonetimDetayi.tsx`: route, API ve state koordinasyonu.
- Modify `frontend/src/components/personel-yonetimi/PersonelYonetimTablosu.tsx`: İşlem sütunu ve Detayı Gör bağlantısı.
- Modify `frontend/src/App.tsx`: HR/ADMIN detay route'u.

### Task 1: Mevcut Davranışı RED Olarak Kaydet

**Files:** Değişiklik yok.

**Interfaces:**
- Consumes: mevcut personel listesi, tablo ve route davranışı.
- Produces: uygulama sonrası karşılaştırılacak kaynak ve tarayıcı kabul tablosu.

- [ ] `PersonelYonetimTablosu.tsx` içinde `Detayı Gör` bağlantısı veya İşlem sütunu bulunmadığını kaynakta doğrula.
- [ ] `App.tsx` içinde `/yonetim/personel/:personelId` route'u bulunmadığını doğrula.
- [ ] HR oturumunda doğrudan örnek detay URL'sinin fallback/404 davranışını tarayıcıda kaydet.
- [ ] Hiçbir dosyanın değiştirilmediğini raporla.

### Task 2: Detay Tipleri ve API Katmanı

**Files:**
- Modify: `frontend/src/types/personelYonetim.ts`
- Modify: `frontend/src/lib/api.js`

**Interfaces:**
- Produces: `PersonelYonetimDetayi`, `AtanabilirYoneticiSecenegi`, `personelDetayiniGetir(personelId)`, `atanabilirYoneticileriGetir(departmanId, haricPersonelId)`.

- [ ] Aşağıdaki backend response tiplerini ekle:

```ts
export type PersonelYonetimDetayi = {
  personelId: string;
  kullaniciAdi: string;
  ad: string;
  soyad: string;
  dogumTarihi: string;
  ePosta: string;
  departmanId: number;
  yoneticiId: string | null;
  isGirisTarihi: string;
  telNo: string | null;
  mezunOkul: string | null;
  mezunBolum: string | null;
  mezuniyetYili: number | null;
  rol: AuthRole;
  aktif: boolean;
};

export type AtanabilirYoneticiSecenegi = {
  personelId: string;
  ad: string;
  soyad: string;
  kullaniciAdi: string;
};
```

- [ ] `personelDetayiniGetir` fonksiyonunu mevcut API hata kalıbıyla ekle:

```js
/**
 * @param {string} personelId
 * @returns {Promise<import("../types/personelYonetim").PersonelYonetimDetayi>}
 */
export async function personelDetayiniGetir(personelId) {
  const encodedId = encodeURIComponent(personelId);
  // GET /api/hr/personel/{personelId}
  // ağ hatası => ApiError(0, kontrollü mesaj)
  // non-OK => apiErrorOlustur(response, kontrollü fallback)
  // OK => response.json()
}
```

- [ ] Yönetici lookup fonksiyonunda query değerlerini `URLSearchParams` ile oluştur:

```js
/**
 * @param {number} departmanId
 * @param {string} haricPersonelId
 * @returns {Promise<import("../types/personelYonetim").AtanabilirYoneticiSecenegi[]>}
 */
export async function atanabilirYoneticileriGetir(
  departmanId,
  haricPersonelId,
) {
  const query = new URLSearchParams({
    departmanId: String(departmanId),
    haricPersonelId,
  });
  // GET /api/hr/personel/atanabilir-yoneticiler?...
  // mevcut apiFetch ve kontrollü ApiError akışını kullan
}
```

- [ ] Boş veya kontrolsüz `personelId` için API fonksiyonunu component dışında çağırma; route sayfası parametreyi önce doğrular.
- [ ] Hedef lint çalıştır:

```powershell
cd C:\Projects\intranet-portal\frontend
npx eslint src/types/personelYonetim.ts src/lib/api.js
```

- [ ] API path/query değerlerinin güvenli üretildiğini ve yeni fonksiyonların token/parola saklamadığını kaynak incelemesiyle doğrula.

### Task 3: Salt Sunum Detay Component'ı

**Files:**
- Create: `frontend/src/components/personel-yonetimi/PersonelYonetimDetayBilgileri.tsx`

**Interfaces:**
- Consumes: `PersonelYonetimDetayi` ve route sayfasının çözdüğü iki lookup görünümü.
- Produces: API ve route bağımlılığı olmayan detay kartı.

- [ ] Component props sözleşmesini tanımla:

```ts
type LookupAlanGorunumu = {
  metin: string;
  yukleniyor: boolean;
  hata: string | null;
  tekrarDenemeDevreDisi: boolean;
  onTekrarDene: () => void;
};

type PersonelYonetimDetayBilgileriProps = {
  detay: PersonelYonetimDetayi;
  departman: LookupAlanGorunumu;
  yonetici: LookupAlanGorunumu;
};
```

- [ ] Tarih değerlerini saat dilimi kayması üretmeden Türkçe biçimleyen küçük yardımcı kullan:

```ts
function tarihiFormatla(tarih: string) {
  const [yil, ay, gun] = tarih.split("-").map(Number);
  return new Intl.DateTimeFormat("tr-TR").format(
    new Date(yil, ay - 1, gun),
  );
}
```

- [ ] Ad ve soyadın ilk harflerinden fotoğrafsız avatar oluştur; boşlukları trim et ve React metin escaping davranışını koru.
- [ ] Üst alanda ad-soyad, `@kullaniciAdi`, rol rozeti ve aktif/pasif rozeti göster.
- [ ] Bilgileri üç grupta render et:
  - İletişim: e-posta, telefon, doğum tarihi.
  - İş bilgileri: departman lookup alanı, yönetici lookup alanı, işe giriş tarihi.
  - Eğitim: okul, bölüm, mezuniyet yılı.
- [ ] Null veya trim sonrası boş opsiyonel alanları `Belirtilmemiş` göster.
- [ ] Lookup alanında yüklenirken `Yükleniyor…`, hata varsa kontrollü mesaj ve `Tekrar dene`, başarıda `metin` göster.
- [ ] `Tekrar dene` butonunu istek sırasında davranışsal olarak disable et.
- [ ] Düzenleme, pasifleştirme veya rol değiştirme eylemi ekleme.
- [ ] Hedef lint çalıştır:

```powershell
cd C:\Projects\intranet-portal\frontend
npx eslint src/components/personel-yonetimi/PersonelYonetimDetayBilgileri.tsx
```

### Task 4: Detay Sayfası, Route ve Liste Bağlantısı

**Files:**
- Create: `frontend/src/pages/PersonelYonetimDetayi.tsx`
- Modify: `frontend/src/components/personel-yonetimi/PersonelYonetimTablosu.tsx`
- Modify: `frontend/src/App.tsx`

**Interfaces:**
- Consumes: Task 2 API/tipleri ve Task 3 sunum component'ı.
- Produces: çalışan `/yonetim/personel/:personelId` akışı.

- [ ] Route sayfasında şu temel state'leri oluştur:

```ts
const [detay, setDetay] = useState<PersonelYonetimDetayi | null>(null);
const [detayYukleniyor, setDetayYukleniyor] = useState(true);
const [detayHatasi, setDetayHatasi] = useState<string | null>(null);

const [departmanlar, setDepartmanlar] = useState<LookupSecenegi[]>([]);
const [departmanYukleniyor, setDepartmanYukleniyor] = useState(false);
const [departmanHatasi, setDepartmanHatasi] = useState<string | null>(null);

const [yoneticiler, setYoneticiler] = useState<AtanabilirYoneticiSecenegi[]>([]);
const [yoneticiYukleniyor, setYoneticiYukleniyor] = useState(false);
const [yoneticiHatasi, setYoneticiHatasi] = useState<string | null>(null);

const sonDetayIstegi = useRef(0);
const sonDepartmanIstegi = useRef(0);
const sonYoneticiIstegi = useRef(0);
```

- [ ] `useParams<{ personelId: string }>()` ile parametreyi al; eksik veya trim sonrası boşsa istek göndermeden `Geçersiz personel bağlantısı.` hatası göster.
- [ ] Detay isteğinde yalnız kontrollü `ApiError.message` değerini kullanıcıya aktar; diğer exception'larda bağlama özgü fallback kullan.
- [ ] Her detay isteğine artan request numarası ver; route değişirse eski response yeni personel state'ini ezmesin. Yeni detay isteği başlarken departman ve yönetici request sayaçlarını da artırarak önceki lookup cevaplarını geçersiz kıl.
- [ ] Detay başarılı olduğunda departman lookup'unu çağır. `yoneticiId !== null` ise yönetici lookup'unu departman lookup'uyla paralel başlat; null ise yönetici isteği gönderme.
- [ ] Her departman ve yönetici lookup isteğinde kendi request sayacını artır; success, error ve finally state yazımlarından önce ilgili numaranın hâlâ güncel olduğunu doğrula.
- [ ] Lookup retry fonksiyonlarını detay mutation'ından bağımsız tut; yönetici retry yalnız geçerli `departmanId`, `personelId` ve null olmayan `yoneticiId` ile çalışsın. Yeni retry önceki aynı-tür lookup cevabını geçersiz kılsın.
- [ ] Görünüm metinlerini şu kurallarla çöz:

```ts
const departmanMetni =
  departmanlar.find((item) => item.id === detay.departmanId)?.ad ??
  "Departman bilgisi görüntülenemiyor";

const yonetici = yoneticiler.find(
  (item) => item.personelId === detay.yoneticiId,
);

const yoneticiMetni =
  detay.yoneticiId === null
    ? "Atanmamış"
    : yonetici
      ? `${yonetici.ad} ${yonetici.soyad} (@${yonetici.kullaniciAdi})`
      : "Yönetici bilgisi görüntülenemiyor";
```
- [ ] İlk loading, detay error/retry ve başarılı detay görünümünü birbirinden ayır. Lookup hatası başarılı detay kartını gizlemesin.
- [ ] Sayfaya `PageMeta`, `PageBreadcrumb` ve `/yonetim/personel` adresine giden `Personel listesine dön` bağlantısı ekle.
- [ ] Tabloya `İşlem` başlığı ve her satıra güvenli route bağlantısı ekle:

```tsx
<Link
  to={`/yonetim/personel/${encodeURIComponent(personel.personelId)}`}
  className="text-sm font-medium text-brand-500 hover:text-brand-600"
>
  Detayı Gör
</Link>
```

- [ ] `App.tsx` içindeki korumalı layout grubuna route'u ekle:

```tsx
<Route
  path="/yonetim/personel/:personelId"
  element={
    <RoleRoute roller={["HR", "ADMIN"]}>
      <PersonelYonetimDetayi />
    </RoleRoute>
  }
/>
```

- [ ] Sidebar ve diğer route'ları değiştirme.
- [ ] Hedef lint çalıştır:

```powershell
cd C:\Projects\intranet-portal\frontend
npx eslint src/pages/PersonelYonetimDetayi.tsx src/components/personel-yonetimi/PersonelYonetimDetayBilgileri.tsx src/components/personel-yonetimi/PersonelYonetimTablosu.tsx src/App.tsx src/types/personelYonetim.ts src/lib/api.js
```

### Task 5: Son Doğrulama

**Files:** Değişiklik yok; yalnız doğrulama.

- [ ] HR listesinde `Detayı Gör` bağlantısının görünmesini ve izinli PERSONEL/başka HR hedefinin açılmasını doğrula.
- [ ] ADMIN listesinde PERSONEL, HR ve YONETICI hedef detaylarının açılmasını doğrula.
- [ ] Detay URL'sini yenileyerek veri ve lookup'ların tekrar yüklendiğini doğrula.
- [ ] PERSONEL ve YONETICI ile doğrudan detay URL'sinin `/` adresine yönlendirildiğini doğrula.
- [ ] Oturumsuz doğrudan detay URL'sinin `/signin` adresine yönlendirildiğini doğrula.
- [ ] Yönetici atanmış, atanmamış ve lookup'ta bulunamayan yönetici görünümlerini doğrula.
- [ ] Boş telefon/eğitim alanlarının `Belirtilmemiş` gösterimini doğrula.
- [ ] Detay request hata/retry ile departman/yönetici lookup hata/retry davranışlarını ayrı kontrol et.
- [ ] Lookup hatasında temel detay bilgilerinin görünür kaldığını doğrula.
- [ ] `Personel listesine dön` bağlantısının varsayılan `aktif=true`, `page=0` listeyi açtığını doğrula.
- [ ] Masaüstü ve 390 px mobil görünümde sayfa taşması, butonlar ve okunabilirliği kontrol et.
- [ ] Hedef lint çalıştır:

```powershell
cd C:\Projects\intranet-portal\frontend
npx eslint src/components/RoleRoute.tsx src/types/personelYonetim.ts src/components/personel-yonetimi/PersonelYonetimTablosu.tsx src/components/personel-yonetimi/PersonelYonetimDetayBilgileri.tsx src/pages/PersonelYonetimi.tsx src/pages/PersonelYonetimDetayi.tsx src/App.tsx src/lib/api.js
```

- [ ] Production build çalıştır:

```powershell
cd C:\Projects\intranet-portal\frontend
npm run build
```

- [ ] Tam lint çalıştır; görev öncesinden bilinen `CountryMap.tsx`, `Calendar.tsx`, `svg.d.ts` ve Fast Refresh bulgularını yeni değişikliklerden ayrı raporla:

```powershell
cd C:\Projects\intranet-portal\frontend
npm run lint
```

- [ ] Değişen dosyaları, detay/lookup veri akışını, frontend UX-only rol sınırını, çalıştırılan kontrolleri ve kalan riskleri raporla.

