# Katalog Yönetimi Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** HR ve Admin kullanıcılarının departman ve yetenek kataloglarını tek bir yönetim ekranından listeleyip ekleyebilmesini ve yeniden adlandırabilmesini sağlamak.

**Architecture:** Route seviyesi `KatalogYonetimi` sayfası iki katalog listesinin yükleme, mutation, hata ve başarı state'ini sahiplenir. Görsel liste ve düzenleme modalı küçük tekrar kullanılabilir component'lara ayrılır. API fonksiyonları mevcut `apiFetch` katmanına eklenir; başarılı yazmalardan sonra ilgili GET lookup yeniden çağrılarak backend response'u tek kaynak kabul edilir.

**Tech Stack:** React 19, TypeScript, React Router 7, TailAdmin/Tailwind, mevcut `src/lib/api.js`.

## Global Constraints

- Yeni dependency veya frontend test framework eklenmez.
- Mevcut TypeScript/JavaScript dosya uzantıları korunur.
- API çağrıları yalnız `frontend/src/lib/api.js` içindeki `apiFetch` ile yapılır; sessiz refresh ve single-flight davranışı değiştirilmez.
- `ProtectedRoute`, `PublicRoute` ve backend yetkilendirme davranışı korunur.
- Katalog ekranının menü/route görünürlüğü `HR` ve `ADMIN` ile sınırlıdır; gerçek yetki backend endpoint'lerinde kalır.
- Ham backend exception ayrıntıları kullanıcıya gösterilmez.
- Takvim, `/calendar` route'u ve demo route'lar bu planın kapsamı dışındadır.
- Git komutu çalıştırılmaz.
- Repository'de otomatik frontend test altyapısı bulunmadığından yeni test yazılmaz; her teslimde `npx tsc --noEmit`, `npm run lint` ve `npm run build` çalıştırılır.

---

## Dosya yapısı

- Modify: `frontend/src/lib/api.js` — katalog mutation fonksiyonları.
- Create: `frontend/src/types/katalogYonetimi.ts` — iki katalog türü ve sayfa/component prop sözleşmeleri.
- Create: `frontend/src/components/katalog-yonetimi/KatalogListesi.tsx` — yüklenmiş lookup kayıtlarının boş/liste görünümü.
- Create: `frontend/src/components/katalog-yonetimi/KatalogKayitDuzenlemeModali.tsx` — tek kayıt adını değiştiren, tekrar gönderimi kilitleyen modal.
- Create: `frontend/src/pages/KatalogYonetimi.tsx` — sekmeler, yükleme, ekleme, hata/başarı ve yenileme koordinasyonu.
- Modify: `frontend/src/App.tsx` — `/yonetim/katalog` route'unu `RoleRoute` ile ekler.
- Modify: `frontend/src/layout/AppSidebar.tsx` — Yönetim altına HR/Admin katalog linkini ekler.

## Task 1: API ve tip sözleşmesi

**Files:**
- Modify: `frontend/src/lib/api.js`
- Create: `frontend/src/types/katalogYonetimi.ts`

**Consumes:** `apiFetch`, `apiErrorOlustur`, `ApiError`, mevcut `LookupSecenegi` tipi.

**Produces:**

```ts
export type KatalogTuru = "departman" | "yetenek";

export type KatalogKayit = {
  id: number;
  ad: string;
};
```

```js
export async function departmanOlustur(departmanAdi) {}
export async function departmanGuncelle(departmanId, departmanAdi) {}
export async function yetenekOlustur(yetenekAdi) {}
export async function yetenekGuncelle(yetenekId, yetenekAdi) {}
```

- [ ] **Step 1: Response ve request alanlarını sözleşmeden doğrula.**

`POST /api/departments` için `{ departmanAdi }`, `PATCH /api/departments/{id}` için aynı body; `POST /api/skills` için `{ yetenekAdi }`, `PATCH /api/skills/{id}` için aynı body kullanılır. Oluşturma `201` ve `{ id, ad }`, güncelleme `204` döner.

- [ ] **Step 2: Katalog türü ve lookup kayıt tipini ekle.**

`KatalogTuru` yalnız `"departman" | "yetenek"` değerlerine izin verir. `KatalogKayit`, mevcut lookup response ile aynı `id` ve `ad` alanlarını taşır; personel ID, rol veya başka yönetim verisi eklenmez.

- [ ] **Step 3: Dört mutation API fonksiyonunu ekle.**

Her fonksiyon şu kalıbı kullanır:

```js
const response = await apiFetch("/api/departments", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ departmanAdi }),
});

if (!response.ok) {
  throw await apiErrorOlustur(
    response,
    "Departman kaydedilemedi. Lütfen tekrar deneyin.",
  );
}

return response.json();
```

`PATCH` fonksiyonları path ID'sini `encodeURIComponent(String(id))` ile encode eder ve başarılı `204` için response body okumaz. Ağ hatası her fonksiyonda kontrollü `ApiError(0, "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.")` üretir.

- [ ] **Step 4: Tip kontrolü çalıştır.**

Run: `npx tsc --noEmit`

Expected: Katalog tipleri ve yeni API importları için TypeScript hatası yok.

## Task 2: Liste ve düzenleme modalı

**Files:**
- Create: `frontend/src/components/katalog-yonetimi/KatalogListesi.tsx`
- Create: `frontend/src/components/katalog-yonetimi/KatalogKayitDuzenlemeModali.tsx`

**Consumes:** `KatalogTuru`, `KatalogKayit`, mevcut `Modal` ve `useModal` pattern'i.

**Produces:**

```ts
type KatalogListesiProps = {
  tur: KatalogTuru;
  kayitlar: KatalogKayit[];
  yukleniyor: boolean;
  onDuzenle: (kayit: KatalogKayit) => void;
};

type KatalogKayitDuzenlemeModaliProps = {
  tur: KatalogTuru;
  kayit: KatalogKayit | null;
  gonderimSuruyor: boolean;
  hata: string | null;
  onKapat: () => void;
  onKaydet: (ad: string) => Promise<void>;
};
```

- [ ] **Step 1: Liste component'ını oluştur.**

`yukleniyor` durumunda TailAdmin uyumlu bir durum metni göster. Liste boşsa tür adına uygun `Henüz departman bulunmuyor.` veya `Henüz yetenek bulunmuyor.` mesajını göster. Kayıt olduğunda her satırda kayıt adı ve `Düzenle` butonu kullan:

```tsx
<li className="flex items-center justify-between gap-4 px-4 py-3">
  <span className="min-w-0 break-words text-sm font-medium text-gray-800 dark:text-white/90">
    {kayit.ad}
  </span>
  <button type="button" onClick={() => onDuzenle(kayit)}>
    Düzenle
  </button>
</li>
```

Liste salt görseldir; API çağrısı veya mutation state'i içermez.

- [ ] **Step 2: Düzenleme modalını oluştur.**

Modal açıldığında input başlangıç değeri `kayit.ad` olur. Input label'ı tür adına göre `Departman adı` veya `Yetenek adı` olur. Input değeri `trim()` sonrası boşsa Kaydet disabled kalır. Kaydet, `onKaydet(trimlenmisAd)` çağrısını yapar.

```tsx
<Modal
  isOpen={kayit !== null}
  onClose={onKapat}
  showCloseButton={!gonderimSuruyor}
  className="max-w-[520px] p-6"
>
```

`gonderimSuruyor` iken Vazgeç, kapatma ve Kaydet disabled kalır. Modal kendi `useRef(false)` kilidiyle, çift tıklamada `onKaydet` fonksiyonunun ikinci kez çağrılmasını engeller. `hata` yalnız kontrollü kullanıcı mesajı olarak `role="alert"` ile gösterilir.

- [ ] **Step 3: Tip kontrolü çalıştır.**

Run: `npx tsc --noEmit`

Expected: Props sözleşmeleri ve modal kapanma davranışı için TypeScript hatası yok.

## Task 3: Katalog Yönetimi sayfası

**Files:**
- Create: `frontend/src/pages/KatalogYonetimi.tsx`

**Consumes:** `departmanlariGetir`, `yetenekleriGetir`, dört mutation fonksiyonu, `ApiError`, `KatalogListesi`, `KatalogKayitDuzenlemeModali`.

**Produces:** `/yonetim/katalog` route'una verilecek route component'i.

- [ ] **Step 1: Sayfa state'ini oluştur.**

Sayfa şu state'leri taşır:

```ts
const [aktifTur, setAktifTur] = useState<KatalogTuru>("departman");
const [departmanlar, setDepartmanlar] = useState<KatalogKayit[]>([]);
const [yetenekler, setYetenekler] = useState<KatalogKayit[]>([]);
const [yukleniyor, setYukleniyor] = useState(false);
const [listeHatasi, setListeHatasi] = useState<string | null>(null);
const [yeniAd, setYeniAd] = useState("");
const [gonderimSuruyor, setGonderimSuruyor] = useState(false);
const [mutationHatasi, setMutationHatasi] = useState<string | null>(null);
const [basariMesaji, setBasariMesaji] = useState<string | null>(null);
const [duzenlenenKayit, setDuzenlenenKayit] = useState<KatalogKayit | null>(null);
```

`useRef` sayaçları, sekme değişimi veya mutation sonrasında eski isteğin state'i ezmesini önler. Ayrı `useRef(false)` gönderim kilidi ekleme ve düzenleme isteklerini tekilleştirir.

- [ ] **Step 2: Liste yükleme akışını uygula.**

Aktif sekme değiştiğinde yalnız o türün lookup fonksiyonunu çağır. Başarılı response'u `id`, ardından `ad` sıralamasını değiştirmeden state'e yaz. Hata eşlemesi aşağıdaki gibi kontrollü olur:

```ts
function katalogHataMesaji(error: unknown, varsayilanMesaj: string) {
  if (!(error instanceof ApiError) || error.status >= 500) {
    return varsayilanMesaj;
  }

  if (error.status === 0) {
    return "Sunucuya ulaşılamadı. Bağlantınızı kontrol edip tekrar deneyin.";
  }

  return error.message || varsayilanMesaj;
}
```

İlk yükleme, hata ve empty state birbirini dışlar. Hata kartındaki `Tekrar dene` yalnız aktif türü yeniden yükler.

- [ ] **Step 3: Ekleme formunu uygula.**

Form aktif türe göre input label'ını ve `departmanOlustur(yeniAd.trim())` ya da `yetenekOlustur(yeniAd.trim())` çağrısını seçer. Boş trimlenmiş ad gönderilmez. İstek sürerken form inputu ve buton devre dışı olur. Başarılı işlemde input temizlenir, `Departman eklendi.` veya `Yetenek eklendi.` mesajı gösterilir ve yalnız aktif liste yeniden yüklenir.

- [ ] **Step 4: Düzenleme akışını bağla.**

Liste `onDuzenle` callback'i seçili kaydı state'e alır. Modal kaydet callback'i aktif türe göre aşağıdaki endpoint wrapper'ını çağırır:

```ts
const kaydet = aktifTur === "departman"
  ? () => departmanGuncelle(duzenlenenKayit.id, ad)
  : () => yetenekGuncelle(duzenlenenKayit.id, ad);
```

Başarıda modal kapanır, `Departman güncellendi.` veya `Yetenek güncellendi.` mesajı görünür, ilgili liste yeniden yüklenir. Hata durumunda modal açık kalır ve hata alanı gösterilir.

- [ ] **Step 5: Responsive TailAdmin düzenini oluştur.**

Sayfa `PageMeta` ve `PageBreadcrumb` kullanır. Sekmeler gerçek `<button type="button" role="tab">` elemanlarıdır; aktif durum `aria-selected` ile belirtilir. Ekleme formu küçük ekranda dikey, `sm:` ekranlarda yatay hizalanır. Liste satırları uzun adları kırar, düzenleme butonu daralmaz.

- [ ] **Step 6: Tip kontrolü çalıştır.**

Run: `npx tsc --noEmit`

Expected: API fonksiyonları, state ve component prop'ları uyumludur.

## Task 4: Route ve menü

**Files:**
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/layout/AppSidebar.tsx`

**Consumes:** `KatalogYonetimi`, `RoleRoute`, mevcut `PERSONEL_YONETIM_ROLLERI` sabiti.

**Produces:** HR/Admin için `/yonetim/katalog` erişimi ve Yönetim altında `Katalog Yönetimi` linki.

- [ ] **Step 1: Korumalı route'u ekle.**

```tsx
<Route
  path="/yonetim/katalog"
  element={
    <RoleRoute roller={["HR", "ADMIN"]}>
      <KatalogYonetimi />
    </RoleRoute>
  }
/>
```

Route mevcut `ProtectedRoute > AppLayout` ağacının içinde kalır. Authentication veya existing guard component'ları değiştirilmez.

- [ ] **Step 2: Sidebar linkini ekle.**

Yönetim `subItems` dizisine aşağıdaki kaydı, Personel Yönetimi kaydının hemen sonrasına ekle:

```ts
{
  name: "Katalog Yönetimi",
  path: "/yonetim/katalog",
  roller: PERSONEL_YONETIM_ROLLERI,
}
```

Bu görünürlük yalnız UX amaçlıdır; endpoint yetkisi backend'de kalır.

- [ ] **Step 3: Manuel route kontrolü yap.**

HR/Admin oturumuyla Yönetim menüsünde linki ve `/yonetim/katalog` ekranını aç. Personel/Yönetici oturumunda link görünmemeli ve route `RoleRoute` davranışını korumalıdır.

## Task 5: Doğrulama

**Files:**
- No file changes.

- [ ] **Step 1: Katalog mutasyonlarını browser üzerinden kontrol et.**

HR veya Admin oturumuyla:

1. Departman ve Yetenek sekmelerinin listelerini aç.
2. Her türden benzersiz isimli bir kayıt ekle; satırın backend response'u ile listelendiğini kontrol et.
3. Her kaydı yeniden adlandır; modalın başarılı mesajından sonra güncel adı GET lookup response'undan gösterdiğini kontrol et.
4. Boş adı göndermeyi dene; istemci isteği göndermeden Kaydet'i disabled tutmalı.
5. Aynı Kaydet/Ekle butonuna hızlıca iki kez bas; yalnız tek mutation isteği oluşmalı.
6. Sunucu erişilemezken veya kontrollü hata döndüğünde ham exception yerine kullanıcı mesajı ve tekrar deneme davranışını kontrol et.

- [ ] **Step 2: Otomatik doğrulamaları çalıştır.**

Run: `npx tsc --noEmit`

Run: `npm run lint`

Run: `npm run build`

Expected: Tip kontrolü ve build başarılı. Lintte mevcut ilgisiz hatalar varsa dosya/ad/satırları raporlanır; bu faz kapsamında düzeltilmez.

- [ ] **Step 3: Faz sınırını teyit et.**

Takvim, `/calendar`, `Blank`, demo form/tablo/grafik route'ları, profil yetenek düzenleme ve backend kodu değiştirilmemiş olmalıdır.

## Plan self-review

- Spec coverage: Faz 1'in route, rol görünürlüğü, API çağrıları, ekleme, yeniden adlandırma, loading, empty, error, başarı, çift gönderim ve mobil davranış gereksinimlerinin her biri Task 1–5 içinde yer alır.
- Scope: Profil Yeteneklerim ve demo temizliği sonraki bağımsız teslimlerdir; bu plan bunları uygulamaz.
- Type consistency: `KatalogTuru` değerleri, request alanları (`departmanAdi`, `yetenekAdi`) ve mutation fonksiyon adları bütün görevlerde aynıdır.
- Test constraint: Repository'de otomatik frontend test altyapısı bulunmadığı, kullanıcı yeni framework/dependency istemediği için test dosyası eklenmemiştir; manual browser senaryoları ve mevcut komutlar tanımlanmıştır.
