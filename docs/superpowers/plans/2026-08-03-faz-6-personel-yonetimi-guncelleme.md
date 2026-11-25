# Faz 6 Personel Yönetimi Güncelleme Sayfası Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** HR ve ADMIN kullanıcılarının backend'in izin verdiği personel hedeflerinin güncellenebilir bilgilerini ayrı, doğrudan erişilebilir bir frontend sayfasından kısmi PATCH semantiğiyle güncelleyebilmesini sağlamak.

**Architecture:** `/yonetim/personel/:personelId/duzenle` route sayfası detay ve lookup isteklerini koordine eder; odaklı bir form component'ı kullanıcı girdisini, temel doğrulamayı ve yalnız değişen alanlardan PATCH payload'ı oluşturmayı yönetir. Başarılı `204` sonrasında detay route'una dönülür ve güncel kayıt backend'den yeniden okunur.

**Tech Stack:** React 19, TypeScript/JavaScript, React Router 7, mevcut TailAdmin component ve sınıfları, mevcut `apiFetch`/`ApiError` API altyapısı.

## Global Constraints

- Yalnız `frontend/` altındaki görevle ilgili dosyalara ve bu plan belgesine dokun.
- Backend'i ve `API_SPEC.md` içindeki personel güncelleme sözleşmesini source of truth kabul et.
- Backend dosyalarını veya başka Markdown belgelerini değiştirme.
- Yeni dependency veya test framework'ü ekleme.
- Mevcut `ProtectedRoute`, `RoleRoute`, `PublicRoute`, refresh-token ve `apiFetch` davranışlarını değiştirme.
- Route guard ve buton görünürlüğünü gerçek yetkilendirme olarak sunma; backend yetkisi belirleyicidir.
- Request'e parola, token, kullanıcı adı, rol, aktiflik veya başka server-controlled alan ekleme.
- Pasifleştirme, rol değiştirme ve yeni personel oluşturmayı bu teslimin dışında tut.
- Mevcut kullanıcı değişikliklerini koru ve ilgisiz refactor yapma.
- Git komutu çalıştırma; commit adımları yalnız kullanıcıya aittir.
- Her task sonrasında hedef lint ve ayrı yardımcı incelemesi yapılmadan sonraki task'a geçme.
- Gerçek personel kaydını değiştiren tarayıcı testi için kullanıcıdan işlem anında açık onay al.

---

## Dosya yapısı

**Oluşturulacak dosyalar**

- `frontend/src/components/personel-yonetimi/PersonelYonetimDuzenleFormu.tsx`: Form state'i, temel validation, değişiklik karşılaştırması ve PATCH gönderimi.
- `frontend/src/pages/PersonelYonetimDuzenle.tsx`: Route parametresi, detay/departman/yönetici lookup istekleri, stale-response koruması ve yönlendirme.

**Değiştirilecek dosyalar**

- `frontend/src/types/personelYonetim.ts`: Güncelleme request tipini tanımlar.
- `frontend/src/lib/api.js`: Personel güncelleme API yardımcısını ekler.
- `frontend/src/pages/PersonelYonetimDetayi.tsx`: Detay ekranından düzenleme route'una erişim sağlar.
- `frontend/src/App.tsx`: Yeni HR/ADMIN düzenleme route'unu kaydeder.

**Değiştirilmeyecek mevcut component**

- `frontend/src/components/personel-yonetimi/PersonelYonetimDetayBilgileri.tsx`: Salt-okunur bilgi sunumu aynı kalır.

---

### Task 1: Güncelleme tipi ve API yardımcısı

**Files:**

- Modify: `frontend/src/types/personelYonetim.ts`
- Modify: `frontend/src/lib/api.js`

**Interfaces:**

- Consumes: Mevcut `apiFetch`, `ApiError`, `apiErrorOlustur` ve backend `PATCH /api/hr/personel/{personelId}` sözleşmesi.
- Produces: `PersonelYonetimGuncellemeRequest` tipi ve `personelGuncelle(personelId, payload): Promise<void>` fonksiyonu.

- [ ] **Step 1: RED kaynak kontrolünü çalıştır**

Run:

```powershell
rg -n "PersonelYonetimGuncellemeRequest|personelGuncelle" frontend/src/types/personelYonetim.ts frontend/src/lib/api.js
```

Expected: Eşleşme bulunmamalı; exit code `1` bu başlangıç kontrolünde beklenen sonuçtur.

- [ ] **Step 2: Güncelleme request tipini ekle**

`frontend/src/types/personelYonetim.ts` sonuna backend alan adlarıyla birebir şu tipi ekle:

```ts
export type PersonelYonetimGuncellemeRequest = {
    ad?: string;
    soyad?: string;
    dogumTarihi?: string;
    ePosta?: string;
    departmanId?: number;
    isGirisTarihi?: string;
    yoneticiId?: string | null;
    telNo?: string | null;
    mezunOkul?: string | null;
    mezunBolum?: string | null;
    mezuniyetYili?: number | null;
};
```

Rol, aktiflik, kullanıcı adı ve kimlik alanlarını bu tipe ekleme.

- [ ] **Step 3: API yardımcısını ekle**

`frontend/src/lib/api.js` içinde mevcut personel detay yardımcısının yakınına şu sorumlulukta fonksiyon ekle:

```js
/**
 * @param {string} personelId
 * @param {import("../types/personelYonetim").PersonelYonetimGuncellemeRequest} payload
 * @returns {Promise<void>}
 */
export async function personelGuncelle(personelId, payload) {
    const encodedPersonelId = encodeURIComponent(personelId);
    let response;

    try {
        response = await apiFetch(
            `/api/hr/personel/${encodedPersonelId}`,
            {
                method: "PATCH",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload),
            },
        );
    } catch {
        throw new ApiError(
            0,
            "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
        );
    }

    if (!response.ok) {
        throw await apiErrorOlustur(
            response,
            "Personel bilgileri güncellenemedi. Lütfen tekrar deneyin.",
        );
    }
}
```

`204` response üzerinde `response.json()` çağırma.

- [ ] **Step 4: Hedef lint ve sözleşme kontrolünü çalıştır**

Run:

```powershell
cd frontend
npx eslint src/types/personelYonetim.ts src/lib/api.js
```

Expected: Exit code `0`.

Kaynak kontrolü:

```powershell
rg -n "personelId\?:|kullaniciAdi\?:|rol\?:|aktif\?:|parola|token" src/types/personelYonetim.ts
```

Expected: Yeni `PersonelYonetimGuncellemeRequest` bloğunda server-controlled alan bulunmamalı.

- [ ] **Step 5: Ayrı yardımcı incelemesi yaptır**

İnceleme kapsamı: yalnız iki dosya; path encoding, 204 işleme, izin verilen alanlar ve mevcut API hata kalıbıyla uyum. Yardımcı dosya değiştirmemeli.

---

### Task 2: Personel güncelleme form component'ı

**Files:**

- Create: `frontend/src/components/personel-yonetimi/PersonelYonetimDuzenleFormu.tsx`

**Interfaces:**

- Consumes: `PersonelYonetimDetayi`, `LookupSecenegi`, `AtanabilirYoneticiSecenegi`, `PersonelYonetimGuncellemeRequest`, `personelGuncelle`.
- Produces: Aşağıdaki props sözleşmesine sahip varsayılan React component'ı.

```ts
type PersonelYonetimDuzenleFormuProps = {
    detay: PersonelYonetimDetayi;
    departmanlar: LookupSecenegi[];
    departmanlarYukleniyor: boolean;
    departmanHatasi: string | null;
    onDepartmanlariTekrarDene: () => void;
    yoneticiler: AtanabilirYoneticiSecenegi[];
    yoneticilerYukleniyor: boolean;
    yoneticiHatasi: string | null;
    onDepartmanDegisti: (departmanId: number) => void;
    onYoneticileriTekrarDene: (departmanId: number) => void;
    onIptal: () => void;
    onKaydedildi: () => void;
};
```

- [ ] **Step 1: RED dosya kontrolünü çalıştır**

Run:

```powershell
Test-Path frontend/src/components/personel-yonetimi/PersonelYonetimDuzenleFormu.tsx
```

Expected: `False`.

- [ ] **Step 2: Form state ve başlangıç dönüşümünü oluştur**

Form değerlerini HTML input'larıyla uyumlu string state olarak tut:

```ts
type FormDegerleri = {
    ad: string;
    soyad: string;
    dogumTarihi: string;
    ePosta: string;
    departmanId: string;
    yoneticiId: string;
    isGirisTarihi: string;
    telNo: string;
    mezunOkul: string;
    mezunBolum: string;
    mezuniyetYili: string;
};

function formDegerleriniOlustur(
    detay: PersonelYonetimDetayi,
): FormDegerleri {
    return {
        ad: detay.ad,
        soyad: detay.soyad,
        dogumTarihi: detay.dogumTarihi,
        ePosta: detay.ePosta,
        departmanId: String(detay.departmanId),
        yoneticiId: detay.yoneticiId ?? "",
        isGirisTarihi: detay.isGirisTarihi,
        telNo: detay.telNo ?? "",
        mezunOkul: detay.mezunOkul ?? "",
        mezunBolum: detay.mezunBolum ?? "",
        mezuniyetYili:
            detay.mezuniyetYili === null
                ? ""
                : String(detay.mezuniyetYili),
    };
}
```

Başlangıç değerlerini karşılaştırma için `useRef` içinde sabitle. Component farklı `personelId` ile yeniden kullanılacaksa üst sayfa formu `key={detay.personelId}` ile yeniden mount etmelidir.

- [ ] **Step 3: Temel doğrulama ve PATCH payload oluşturmayı ekle**

Zorunlu metinler trim sonrası boş olamaz. Tarihler boş olamaz. Departman pozitif sayıya dönüşmelidir. Mezuniyet yılı boş değilse pozitif ve mevcut yıldan büyük olmamalıdır.

Payload kuralları:

```ts
const nullableMetin = (deger: string) =>
    deger.trim() === "" ? null : deger.trim();

const nullableYil = (deger: string) =>
    deger.trim() === "" ? null : Number(deger);
```

Her alan normalize edilmiş başlangıç değeriyle karşılaştırılır ve yalnız değişmişse `payload` içine eklenir.

Departman-yönetici ilişkisi şu sırayla kurulmalıdır:

```ts
const yeniDepartmanId = Number(degerler.departmanId);
const departmanDegisti =
    yeniDepartmanId !== detay.departmanId;
const yeniYoneticiId =
    degerler.yoneticiId === ""
        ? null
        : degerler.yoneticiId;

if (departmanDegisti) {
    payload.departmanId = yeniDepartmanId;
    payload.yoneticiId = yeniYoneticiId;
} else if (yeniYoneticiId !== detay.yoneticiId) {
    payload.yoneticiId = yeniYoneticiId;
}
```

`Object.keys(payload).length === 0` ise API isteği gönderme ve `Değiştirilen bir alan bulunmuyor.` bilgi mesajı göster.

- [ ] **Step 4: Departman ve yönetici etkileşimini ekle**

Departman `<select>` değiştiğinde:

1. `departmanId` state'ini güncelle.
2. `yoneticiId` değerini `""` yap.
3. Pozitif ID için `onDepartmanDegisti(Number(value))` çağır.

Yönetici `<select>` içinde ilk seçenek:

```tsx
<option value="">Yönetici yok</option>
```

Lookup seçeneklerini `personelId` value ve `Ad Soyad (@kullaniciAdi)` label ile göster. Lookup başarısızken başlangıç yöneticisi değişmemişse select'in kaybolmuş bir değere düşmemesi için şu geçici option'ı göster:

```tsx
{yoneticiHatasi && detay.yoneticiId && (
    <option value={detay.yoneticiId}>
        Mevcut yönetici bilgisi yüklenemedi
    </option>
)}
```

Departman değişmiş ve `yoneticiHatasi` bulunuyorsa kaydetmeyi devre dışı bırak. Departman değişmemiş, yönetici alanı değiştirilmemiş ve lookup hatalıysa diğer alanların gönderimine izin ver.

- [ ] **Step 5: Bütün alanları ve erişilebilir durumları render et**

Alan matrisi:

| Label | Input | HTML özellikleri |
|---|---|---|
| Ad | text | `required`, `maxLength={50}` |
| Soyad | text | `required`, `minLength` native desteklenmiyorsa submit doğrulaması, `maxLength={50}` |
| Doğum tarihi | date | `required`, `max` bugün |
| E-posta | email | `required`, `maxLength={100}` |
| Departman | controlled native select | lookup loading/error sırasında disabled |
| Yönetici | controlled native select | lookup loading sırasında disabled |
| İşe giriş tarihi | date | `required`, `max` bugün |
| Telefon | tel | `maxLength={30}` |
| Mezun olunan okul | text | `maxLength={100}` |
| Mezun olunan bölüm | text | `maxLength={100}` |
| Mezuniyet yılı | number | `min="1"`, `max` mevcut yıl |

Mevcut `Input`, `Label`, `Badge` ve button sınıflarını kullan. Controlled ve disabled davranışı gerektiği için mevcut stateful `Select.tsx` yerine personel listesi sayfasındaki controlled native `<select>` kalıbını kullan.

Kullanıcı adı, rol ve aktiflik üst bilgi alanında read-only gösterilir. Rol ve aktiflik request'e dahil edilmez.

Formda:

- submit sırasında `aria-busy`,
- loading/bilgi için `role="status"` ve `aria-live="polite"`,
- hata için `role="alert"`,
- `Kaydet` ve `İptal` butonları,
- submit sırasında iki butonda disabled,
- lookup retry butonlarında açıklayıcı accessible name

bulunmalıdır.

- [ ] **Step 6: Gönderim akışını tamamla**

Çift isteği hem state hem ref ile engelle:

```ts
if (gonderimSuruyorRef.current) return;
gonderimSuruyorRef.current = true;
setGonderimSuruyor(true);
```

Validation veya request hatasında ref ve state'i geri aç. `ApiError` mesajını güvenli biçimde göster; bilinmeyen hata için `Personel bilgileri güncellenirken beklenmeyen bir hata oluştu.` kullan.

Başarılı `await personelGuncelle(detay.personelId, payload)` sonrasında `onKaydedildi()` çağır. Form yerel detay nesnesi üretmemeli.

- [ ] **Step 7: Hedef lint çalıştır**

Run:

```powershell
cd frontend
npx eslint src/components/personel-yonetimi/PersonelYonetimDuzenleFormu.tsx
```

Expected: Exit code `0`.

- [ ] **Step 8: Ayrı yardımcı incelemesi yaptır**

İnceleme kapsamı: payload field allowlist, null/boş metin ayrımı, departman-yönetici atomik request'i, çift submit, form değerlerinin hata sonrası korunması, label/alert/status erişilebilirliği. Yardımcı dosya değiştirmemeli.

---

### Task 3: Düzenleme route sayfası ve lookup koordinasyonu

**Files:**

- Create: `frontend/src/pages/PersonelYonetimDuzenle.tsx`

**Interfaces:**

- Consumes: `personelDetayiniGetir`, `departmanlariGetir`, `atanabilirYoneticileriGetir`, `PersonelYonetimDuzenleFormu`.
- Produces: `App.tsx` tarafından route elementi olarak kullanılacak varsayılan `PersonelYonetimDuzenle` component'ı.

- [ ] **Step 1: RED dosya kontrolünü çalıştır**

Run:

```powershell
Test-Path frontend/src/pages/PersonelYonetimDuzenle.tsx
```

Expected: `False`.

- [ ] **Step 2: Route parametresi ve temel sayfa state'ini oluştur**

`useParams<{ personelId: string }>()` ve `useNavigate()` kullan. Aşağıdaki durumları birbirinden ayrı tut:

```ts
const [detay, setDetay] = useState<PersonelYonetimDetayi | null>(null);
const [detayYukleniyor, setDetayYukleniyor] = useState(true);
const [detayHatasi, setDetayHatasi] = useState<string | null>(null);

const [departmanlar, setDepartmanlar] = useState<LookupSecenegi[]>([]);
const [departmanlarYukleniyor, setDepartmanlarYukleniyor] = useState(false);
const [departmanHatasi, setDepartmanHatasi] = useState<string | null>(null);

const [yoneticiler, setYoneticiler] =
    useState<AtanabilirYoneticiSecenegi[]>([]);
const [yoneticilerYukleniyor, setYoneticilerYukleniyor] = useState(false);
const [yoneticiHatasi, setYoneticiHatasi] = useState<string | null>(null);
```

Boş/whitespace `personelId` için request gönderme; `Geçersiz personel bağlantısı.` göster.

- [ ] **Step 3: Eski-response korumalı yükleme fonksiyonlarını ekle**

Detay, departman ve yönetici için ayrı request counter ref kullan:

```ts
const sonDetayIstegi = useRef(0);
const sonDepartmanIstegi = useRef(0);
const sonYoneticiIstegi = useRef(0);
```

Her yükleme fonksiyonu kendi sayacını artırmalı ve yalnız hâlâ son istekse state yazmalıdır. Yeni personel detayı yüklenirken eski departman/yönetici response'larını geçersiz kıl.

Detay başarıyla geldiğinde:

```ts
void departmanlariYukle();
void yoneticileriYukle(
    sonuc.departmanId,
    sonuc.personelId,
);
```

Yönetici `null` olsa bile düzenleme select'i için mevcut departmanın adayları gerekir; bu nedenle düzenleme sayfasında yönetici lookup her başarılı detay için çağrılır.

- [ ] **Step 4: Form callback'lerini ve yönlendirmeyi bağla**

```ts
const detayYolu = `/yonetim/personel/${encodeURIComponent(detay.personelId)}`;

const kaydedildi = () => {
    navigate(detayYolu, { replace: true });
};

const iptal = () => {
    navigate(detayYolu);
};
```

Departman değişim callback'i seçilen departman ve hedef personel ID'siyle `yoneticileriYukle` çağırmalıdır.

Formu şu anahtar ve props ile render et:

```tsx
<PersonelYonetimDuzenleFormu
    key={detay.personelId}
    detay={detay}
    departmanlar={departmanlar}
    departmanlarYukleniyor={departmanlarYukleniyor}
    departmanHatasi={departmanHatasi}
    onDepartmanlariTekrarDene={() => void departmanlariYukle()}
    yoneticiler={yoneticiler}
    yoneticilerYukleniyor={yoneticilerYukleniyor}
    yoneticiHatasi={yoneticiHatasi}
    onDepartmanDegisti={(departmanId) =>
        void yoneticileriYukle(departmanId, detay.personelId)
    }
    onYoneticileriTekrarDene={(departmanId) =>
        void yoneticileriYukle(departmanId, detay.personelId)
    }
    onIptal={iptal}
    onKaydedildi={kaydedildi}
/>
```

- [ ] **Step 5: Page metadata ve ekran durumlarını tamamla**

- `PageMeta`: detay geldiyse `Ad Soyad | Personel Düzenle`, aksi halde `Personel Düzenle | Intranet Portal`.
- `PageBreadcrumb`: `Personel Düzenle`.
- Detaya dönüş linki: encode edilmiş personel ID'siyle oluşturulmalı.
- İlk loading sırasında form/empty mesajı gösterilmemeli.
- Detay hatasında `role="alert"`, `Tekrar dene` ve personel listesine dönüş gösterilmeli.
- Detay başarılı olduğunda form render edilmeli; departman ve yönetici lookup hataları form props'ları üzerinden kendi alanlarında gösterilmeli.

- [ ] **Step 6: Hedef lint çalıştır**

Run:

```powershell
cd frontend
npx eslint src/pages/PersonelYonetimDuzenle.tsx src/components/personel-yonetimi/PersonelYonetimDuzenleFormu.tsx
```

Expected: Exit code `0`.

- [ ] **Step 7: Ayrı yardımcı incelemesi yaptır**

İnceleme kapsamı: direct URL/refresh, boş parametre, ayrı request counter'ları, eski response engeli, yönetici lookup'ın null mevcut yönetici halinde de yüklenmesi, navigation encoding ve loading/error ayrımı. Yardımcı dosya değiştirmemeli.

---

### Task 4: Detay erişimi ve App route entegrasyonu

**Files:**

- Modify: `frontend/src/pages/PersonelYonetimDetayi.tsx`
- Modify: `frontend/src/App.tsx`

**Interfaces:**

- Consumes: Task 3'ün varsayılan `PersonelYonetimDuzenle` export'u.
- Produces: Detaydan düzenlemeye erişim ve HR/ADMIN ile sınırlı `/yonetim/personel/:personelId/duzenle` route'u.

- [ ] **Step 1: RED route ve bağlantı kontrolünü çalıştır**

Run:

```powershell
rg -n "PersonelYonetimDuzenle|personelId/duzenle|Düzenle" frontend/src/App.tsx frontend/src/pages/PersonelYonetimDetayi.tsx
```

Expected: Yeni route/import/bağlantı bulunmamalı.

- [ ] **Step 2: Detay sayfasına düzenleme bağlantısı ekle**

Mevcut `PersonelYonetimDetayi.tsx` dosyasındaki listeye dönüş alanını iki eylem gösterecek hale getir. `Düzenle` yalnız detay başarıyla yüklendiğinde render edilmeli:

```tsx
<div className="mb-5 flex flex-wrap items-center justify-between gap-3">
    <Link
        to="/yonetim/personel"
        className="text-sm font-medium text-brand-500 hover:text-brand-600 dark:text-brand-400 dark:hover:text-brand-300"
    >
        ← Personel listesine dön
    </Link>

    {detay && (
        <Link
            to={`/yonetim/personel/${encodeURIComponent(
                detay.personelId,
            )}/duzenle`}
            className="rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-brand-600"
        >
            Düzenle
        </Link>
    )}
</div>
```

Bu mevcut sayfa değişikliğinin tek nedeni güncelleme akışına erişimdir. `PersonelYonetimDetayBilgileri.tsx` sunum component'ını değiştirme.

- [ ] **Step 3: App route'unu ekle**

Import:

```tsx
import PersonelYonetimDuzenle from "./pages/PersonelYonetimDuzenle";
```

Mevcut personel detay route'unun yakınına:

```tsx
<Route
    path="/yonetim/personel/:personelId/duzenle"
    element={
        <RoleRoute roller={["HR", "ADMIN"]}>
            <PersonelYonetimDuzenle />
        </RoleRoute>
    }
/>
```

Route, mevcut `ProtectedRoute` + `AppLayout` grubunun içinde kalmalı. Diğer route'ları değiştirme.

- [ ] **Step 4: Hedef lint ve build öncesi tip kontrolünü çalıştır**

Run:

```powershell
cd frontend
npx eslint src/App.tsx src/pages/PersonelYonetimDetayi.tsx src/pages/PersonelYonetimDuzenle.tsx src/components/personel-yonetimi/PersonelYonetimDuzenleFormu.tsx src/types/personelYonetim.ts src/lib/api.js
```

Expected: Exit code `0`.

- [ ] **Step 5: Ayrı yardımcı incelemesi yaptır**

İnceleme kapsamı: route sırası/eşleşmesi, `RoleRoute` kapsamı, path encoding, loading sırasında düzenleme bağlantısının görünmemesi, ilgisiz route/component değişikliği olmaması. Yardımcı dosya değiştirmemeli.

---

### Task 5: Son doğrulama ve kabul testi

**Files:**

- Verify only: Task 1-4 kapsamındaki frontend dosyaları.

**Interfaces:**

- Consumes: Tamamlanmış güncelleme akışı.
- Produces: Derleme, lint ve kullanıcı onaylı tarayıcı kabul kanıtı.

- [ ] **Step 1: Hedef lint'i yeniden çalıştır**

Run:

```powershell
cd frontend
npx eslint src/App.tsx src/pages/PersonelYonetimDetayi.tsx src/pages/PersonelYonetimDuzenle.tsx src/components/personel-yonetimi/PersonelYonetimDuzenleFormu.tsx src/types/personelYonetim.ts src/lib/api.js
```

Expected: Exit code `0`.

- [ ] **Step 2: Tam lint sonucunu kaydet**

Run:

```powershell
npm run lint
```

Expected current baseline: `CountryMap.tsx`, `Calendar.tsx` ve `svg.d.ts` içindeki görev dışı üç error ile context Fast Refresh warning'leri görülebilir. Yeni veya düzenlenen dosyada hata olmamalı. Baseline değişmişse gerçek çıktıyı raporla; başarı varsayma.

- [ ] **Step 3: Production build çalıştır**

Run:

```powershell
npm run build
```

Expected: Exit code `0`. Mevcut jvectormap `eval`, CSS `:is()` ve büyük chunk uyarıları hata sayılmaz; yeni bir uyarı varsa ayrıca incele.

- [ ] **Step 4: Salt-okunur tarayıcı kontrollerini yap**

Gerçek kayıt değiştirmeden:

1. HR ile izinli personel detayını aç.
2. `Düzenle` bağlantısının doğru ID ile route'a gittiğini doğrula.
3. Form başlangıç değerlerini detay response'uyla karşılaştır.
4. Sayfayı yenile; URL ve form değerlerinin korunduğunu doğrula.
5. Departman seçimini değiştirip yönetici seçiminin temizlendiğini ve yeni lookup'ın yüklendiğini gözle; kaydetmeden iptal et.
6. İptal sonrasında detay route'una dönüldüğünü doğrula.
7. Yalnız PERSONEL veya YONETICI rolüyle düzenleme route'unun `/` adresine yönlendirildiğini doğrula.

- [ ] **Step 5: Mutasyon kabul testi için kullanıcıdan açık onay al**

Onay verilirse yalnız belirlenmiş demo personel kaydında:

1. Tek bir geri alınabilir alanı değiştir ve kaydet.
2. Network request payload'ında yalnız o alanın bulunduğunu doğrula.
3. `204` sonrasında detay sayfasının güncel değeri gösterdiğini doğrula.
4. Aynı demo kaydında nullable bir alanı temizle; request'te ilgili alanın `null` olduğunu doğrula.
5. Departman değişikliği test edilecekse request'te `departmanId` ve `yoneticiId` alanlarının birlikte bulunduğunu doğrula.
6. Test verisini geri yüklemek ayrı bir PATCH ve audit oluşturacağı için geri yükleme öncesinde kullanıcıyı bilgilendir.

E-posta çakışması testi yalnız güvenli ve önceden bilinen demo e-posta değeriyle yapılmalı; gerçek kullanıcı e-postası kullanılmamalı.

- [ ] **Step 6: Son kapsam kontrolünü yap**

Doğrula:

- Backend dosyası değişmedi.
- Pasifleştirme, rol değiştirme veya personel oluşturma eylemi eklenmedi.
- Session/local storage'a yeni veri yazılmadı.
- Request payload'ında parola, token, rol veya aktiflik bulunmuyor.
- `PersonelYonetimDetayBilgileri.tsx` değiştirilmedi.
- Yeni dependency eklenmedi.
- Git komutu çalıştırılmadı.

- [ ] **Step 7: Teslim raporunu hazırla**

Raporda şunları ayrı belirt:

- oluşturulan ve değiştirilen dosyalar,
- detay → düzenleme → PATCH → detay veri akışı,
- backend'in gerçek yetkilendirme sorumluluğu,
- loading/error/nullable/departman-yönetici davranışları,
- hedef lint, tam lint ve build sonuçları,
- çalıştırılmayan mutasyon testleri ve nedeni,
- kalan risk veya sıradaki ayrı pasifleştirme teslimi.

