# Sınırlı Profil Güncelleme Uygulama Planı

> **Agent çalışma kuralı:** Bu plan görev görev uygulanır. Kullanıcı her kod adımını kendisi uygular; her görevden sonra ayrı bir yardımcı agent salt-okunur inceleme yapar. Git, branch, worktree, commit ve push komutları kullanılmaz.

**Hedef:** Kimliği doğrulanmış kullanıcının `/profile` sayfasında yalnız telefon, mezun olunan okul ve mezun olunan bölüm bilgilerini canlı `PATCH /api/profil` sözleşmesiyle güncelleyebilmesi.

**Mimari:** Mevcut salt-okunur profil kartları korunur. Ayrı bir inline form yalnız üç izinli alanın diff payload’ını üretir; API katmanı PATCH’i yapar, form başarılı `204` sonrasında profili yeniden GET eder ve sayfa güncel response’u kartlara aktarır. PATCH başarılı fakat GET başarısızsa form yeni PATCH’e kapanır ve yalnız GET retry sunar.

**Teknoloji:** React 19, TypeScript/JavaScript, React Router, TailAdmin/Tailwind, mevcut `apiFetch` ve `ApiError` altyapısı.

## Global Kısıtlar

- Yalnız frontend kodu ve bu planın onaylı tasarım belgesi kapsamındadır; backend kodu değişmez.
- Düzenlenebilir alanlar tam olarak `telNo`, `mezunOkul`, `mezunBolum` olmalıdır.
- `mezuniyetYili`, `GET /api/profil` response’unda bulunmadığı için forma ve request tipine eklenmez.
- Ad, soyad, kullanıcı adı, e-posta, doğum tarihi, sicil, departman, rol, yönetici, izin, profil fotoğrafı, yetenek ve işe giriş tarihi salt-okunur kalır.
- `PATCH /api/profil` hedef personel ID’si almaz; kimlik backend’de JWT principal’dan belirlenir.
- Alan gönderilmezse korunur; temizlenen alan `null`, dolu alan trim edilmiş string gönderilir.
- Değişiklik yoksa PATCH çağrısı yapılmaz.
- `204 No Content` JSON olarak parse edilmez.
- PATCH başarılı/GET başarısız durumda retry yalnız GET’i tekrarlar.
- Parola, token veya profil formu değerleri storage ya da log içine yazılmaz.
- Yeni dependency veya frontend test framework’ü eklenmez.
- `App.tsx`, route’lar, parola akışı, sidebar/dropdown, `AuthContext` ve mevcut profil kartları değiştirilmez.
- Otomatik frontend test altyapısı olmadığı için TDD davranış kapısı tarayıcıda RED/GREEN senaryolarıyla uygulanır.

---

### Görev 1: Mevcut davranışı tarayıcıda RED olarak kaydet

**Dosyalar:** Değişiklik yok.

- [ ] `agent-browser` oturumunda yalnız test origin’i için localStorage’a kontrollü mock token/kullanıcı yaz ve `/profile` sayfasını aç.
- [ ] Kod veya dependency eklemeden browser network interception/fetch mock ile `GET /api/profil` için telefon, okul ve bölüm içeren kontrollü response kullan.
- [ ] “Bilgilerimi Düzenle” kontrolünün ve inline formun bulunmadığını doğrula.
- [ ] `PATCH /api/profil` başlatacak bir etkileşim olmadığını doğrula.

**Beklenen RED:** Profil kartları görünür; düzenleme butonu/formu yoktur ve PATCH sayısı `0` kalır.

**Review kapısı:** Yalnız gözlem yapılmalı; dosya veya kalıcı browser storage yapısı değiştirilmemelidir.

---

### Görev 2: `InputField` için geriye uyumlu `maxLength` desteği ekle

**Dosya:** Modify `frontend/src/components/form/input/InputField.tsx`

**Üretilen arayüz:**

```tsx
interface InputProps {
  // mevcut props korunur
  maxLength?: number;
}
```

Native input aktarımı:

```tsx
<input
  // mevcut props korunur
  maxLength={maxLength}
  className={inputClasses}
/>
```

- [ ] `InputProps` içine opsiyonel `maxLength?: number` ekle.
- [ ] Component destructuring listesine `maxLength` ekle.
- [ ] Native `<input>` elemanına `maxLength={maxLength}` aktar.
- [ ] Mevcut props, sınıflar, hint ve error davranışlarını değiştirme.
- [ ] `npx eslint src\components\form\input\InputField.tsx` çalıştır.

**Beklenen GREEN:** Prop verilmediğinde mevcut input davranışı değişmez; verildiğinde tarayıcı karakter sınırını uygular.

**Review kapısı:** Değişiklik yalnız opsiyonel prop ve native attribute aktarımı olmalıdır.

---

### Görev 3: Profil update request tipi ve API yardımcısını ekle

**Dosyalar:**

- Modify `frontend/src/types/profil.ts`
- Modify `frontend/src/lib/api.js`

**Üretilen TypeScript arayüzü:**

```ts
export type ProfilGuncellemeRequest = {
  telNo?: string | null;
  mezunOkul?: string | null;
  mezunBolum?: string | null;
};
```

**Üretilen JavaScript arayüzü:**

```js
export async function profilimiGuncelle(payload) {
  let response;

  try {
    response = await apiFetch("/api/profil", {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
  } catch {
    throw new ApiError(
      0,
      "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
    );
  }

  if (!response.ok) {
    throw await apiErrorOlustur(
      response,
      "Profil bilgileri güncellenemedi. Lütfen tekrar deneyin.",
    );
  }
}
```

- [ ] Request tipine yalnız üç izinli alanı ekle.
- [ ] API yardımcısında mevcut `apiFetch` ve `apiErrorOlustur` desenini kullan.
- [ ] Doğrudan `fetch`, personel ID, GET/refetch veya storage işlemi ekleme.
- [ ] `204` response body’sini parse etme.
- [ ] `npx eslint src\lib\api.js src\types\profil.ts` çalıştır.

**Beklenen GREEN:** `204` boş body ile resolve olur; non-2xx kontrollü `ApiError` üretir ve body yalnız çağıranın verdiği üç izinli alanı taşır.

**Review kapısı:** `mezuniyetYili` veya server-controlled alan bulunmamalı; profil GET single-flight ve parola helper’ları değişmemelidir.

---

### Görev 4: Inline `ProfilDuzenleForm` component’ini oluştur

**Dosya:** Create `frontend/src/components/UserProfile/ProfilDuzenleForm.tsx`

**Tüketilen arayüz:**

```ts
type ProfilDuzenleFormProps = {
  baslangicDegerleri: Pick<
    ProfilBilgileri,
    "telNo" | "mezunOkul" | "mezunBolum"
  >;
  onIptal: () => void;
  onProfilYenilendi: (profil: ProfilBilgileri) => void;
};
```

Form `profilimiGuncelle`, `profilimiGetir`, `ApiError`, `ProfilBilgileri` ve `ProfilGuncellemeRequest` arayüzlerini kullanır.

**Normalize/diff kuralı:**

```ts
function nullableMetniNormalizeEt(deger: string | null): string | null {
  if (deger === null) {
    return null;
  }

  const temizlenmisDeger = deger.trim();
  return temizlenmisDeger === "" ? null : temizlenmisDeger;
}
```

Hem başlangıç değeri hem yeni input aynı fonksiyonla normalize edilir. Yalnız iki normalize değer eşit değilse alan payload’a eklenir.

| Başlangıç | Yeni input | Payload sonucu |
|---|---|---|
| `"0555"` | `" 0555 "` | `telNo` eklenmez |
| `"0555"` | `"   "` | `telNo: null` |
| `null` | `"   "` | `telNo` eklenmez |
| `null` | `" Okul "` | `mezunOkul: "Okul"` |

- [ ] Üç controlled input state’ini başlangıç profilinden üret; `null` değeri inputta `""` göster.
- [ ] Diff karşılaştırmasında hem başlangıç hem yeni değeri `nullableMetniNormalizeEt` ile normalize et.
- [ ] Her input için benzersiz `id` ve buna bağlı `Label htmlFor` kullan.
- [ ] Telefon alanında `maxLength={30}` ve `autoComplete="tel"` kullan.
- [ ] Okul ve bölüm alanlarında `maxLength={100}` kullan.
- [ ] Payload’ı yalnız normalize edilmiş değişen alanlardan üret.
- [ ] Payload’ı submit kilidini kurmadan önce hesapla. Payload boşsa API çağırma veya kilit kurma; `role="status"` ile “Kaydedilecek bir değişiklik bulunmuyor.” göster ve kullanıcı daha sonra alan değiştirip submit edebilsin.
- [ ] Payload doluysa submit sırasında `useRef` senkron kilit ve `isSubmitting` state’ini birlikte kullan.
- [ ] Submit sırasında input, kaydet ve iptal kontrollerini disabled yap.
- [ ] İptal için native `<button type="button">` kullan; ortak `Button` type prop’u taşımadığı için iptalde kullanma.
- [ ] PATCH hatasında `ApiError.message` veya sabit fallback’i `role="alert"` ile göster; değerleri koru ve kilidi kaldır.
- [ ] PATCH `204` sonrasında `profilimiGetir()` çağır.
- [ ] GET başarılıysa `onProfilYenilendi(yeniProfil)` çağır.
- [ ] PATCH başarılı, GET başarısızsa `yenilemeBekliyor` durumuna geç; input ve PATCH submit’ini kapat, “Bilgiler kaydedildi ancak güncel profil yüklenemedi.” uyarısı göster.
- [ ] `yenilemeBekliyor` durumunda iptal/kapat kontrolünü render etme; GET başarılı olana kadar stale başlangıçla formun yeniden açılmasına izin verme.
- [ ] “Güncel Profili Yükle” kontrolü yalnız `profilimiGetir()` çağırmalı; `profilimiGuncelle()` çağırmamalı.
- [ ] GET retry için ayrı `useRef` senkron kilit ve `isYeniliyor` state’i kullan; retry sırasında kontrol disabled olmalı ve hızlı çift tıklama yalnız bir GET üretmeli.
- [ ] GET retry başarılıysa `onProfilYenilendi`; başarısızsa iki retry kilidini kaldır, aynı uyarıyı ve yeniden deneme imkânını koru.
- [ ] `npx eslint src\components\UserProfile\ProfilDuzenleForm.tsx` çalıştır.

**Beklenen GREEN:** Form yalnız üç alanı yönetir; no-change, null temizleme, diff, double-submit ve kısmi başarı durumları ayrıdır.

**Review kapısı:** PATCH başarılı/GET başarısız durumda ikinci PATCH’in mümkün olmadığı ve retry’ın yalnız GET yaptığı doğrulanmalıdır.

---

### Görev 5: Formu `/profile` sayfasına bağla

**Dosya:** Modify `frontend/src/pages/UserProfiles.tsx`

**Yeni page state’i ve callback:**

```ts
const [duzenleniyor, setDuzenleniyor] = useState(false);
const [basariMesaji, setBasariMesaji] = useState("");

const profilGuncellendi = (guncelProfil: ProfilBilgileri) => {
  setProfil(guncelProfil);
  setDuzenleniyor(false);
  setBasariMesaji("Profil bilgileriniz güncellendi.");
};
```

- [ ] Mevcut “Parolamı Değiştir” bağlantısını ve hedefini koru.
- [ ] Profil başarıyla yüklendiğinde aynı header action grubuna responsive “Bilgilerimi Düzenle” butonu ekle.
- [ ] Buton loading/error durumunda form açmamalı.
- [ ] `duzenleniyor` true olduğunda yeni düzenleme açma kontrolünü gizle veya disabled yap ve mevcut kartların üstünde tek `ProfilDuzenleForm` göster.
- [ ] Form kısmi başarı nedeniyle iptali kapattığında sayfa, stale profil üzerinden ikinci bir form instance’ı açmamalı.
- [ ] Başlangıç değerleri olarak mevcut profilin `telNo`, `mezunOkul`, `mezunBolum` alanlarını ver.
- [ ] `onIptal` formu kapatmalı.
- [ ] `onProfilYenilendi` güncel profili state’e yazmalı, formu kapatmalı ve `role="status"` başarı mesajı göstermeli.
- [ ] İlk profil GET loading/error/retry akışını koru.
- [ ] `UserMetaCard`, `UserInfoCard` ve `UserEducationSkillsCard` prop/render davranışlarını değiştirme.
- [ ] `App.tsx`, route, sidebar, dropdown veya parola dosyalarına dokunma.
- [ ] `npx eslint src\pages\UserProfiles.tsx src\components\UserProfile\ProfilDuzenleForm.tsx` çalıştır.

**Beklenen GREEN:** Düzenleme aynı `/profile` sayfasında açılır; başarılı PATCH+GET sonrasında kartlar yeni response ile güncellenir.

**Review kapısı:** Edit kontrolü yalnız gerçek profil varken çalışmalı; mevcut kartlar ve parola erişimi korunmalıdır.

---

### Görev 6: Tarayıcı, lint, build ve bağımsız final inceleme

**Hedef dosyalar:**

- `frontend/src/components/form/input/InputField.tsx`
- `frontend/src/types/profil.ts`
- `frontend/src/lib/api.js`
- `frontend/src/components/UserProfile/ProfilDuzenleForm.tsx`
- `frontend/src/pages/UserProfiles.tsx`

**Tarayıcı GREEN senaryoları:**

- [ ] Dolu GET response’u formu doğru başlangıç değerleriyle açar; `null` alanlar boş görünür.
- [ ] Değişiklik yapmadan kaydetmede PATCH sayısı `0` kalır; ardından alan değiştirilip normal submit yapılabilir.
- [ ] GET başlangıç değerinde çevre boşluğu veya boş string olsa bile normalize no-change gereksiz PATCH üretmez.
- [ ] Yalnız okul değişikliğinde body yalnız `{ mezunOkul: "..." }` içerir.
- [ ] Yalnız bölüm değişikliğinde body yalnız `{ mezunBolum: "..." }` içerir.
- [ ] Dolu telefon temizlendiğinde body `{ telNo: null }` içerir.
- [ ] Baştaki/sondaki boşluklar trim edilir.
- [ ] Üç alan body’sinde `mezuniyetYili` veya server-controlled alan bulunmaz.
- [ ] Hızlı çift tıklama/Enter yalnız bir PATCH üretir; işlem sırasında kontroller disabled olur.
- [ ] PATCH `204` sonrasında body parse hatası oluşmaz ve yalnız bir GET yapılır.
- [ ] PATCH 400/403/500/network hatasında form açık, değerler korunmuş ve GET sayısı `0` kalır.
- [ ] PATCH `204` + GET 500 durumunda kısmi başarı uyarısı görünür ve ikinci PATCH oluşmaz.
- [ ] Kısmi başarıda iptal/kapat ve ikinci PATCH yolu kullanılamaz.
- [ ] “Güncel Profili Yükle” hızlı çift tıklamada yalnız bir GET yapar; başarıda kartları günceller, başarısızlıkta yeniden denenebilir kalır.
- [ ] 30/100 karakter sınırları uygulanır.
- [ ] 320–375 px genişlikte yatay taşma yoktur.
- [ ] Klavye ile düzenleme, iptal ve submit çalışır; hata `role="alert"` ile duyurulur.
- [ ] “Parolamı Değiştir” bağlantısı ve `/profil/parola` davranışı korunur.
- [ ] Dört authenticated rol kendi profil formunu açabilir.

**Mekanik doğrulamalar:** Aşağıdaki komutların tamamı `C:\Projects\intranet-portal\frontend` çalışma dizininden çalıştırılır.

```powershell
npx eslint src\components\form\input\InputField.tsx src\types\profil.ts src\lib\api.js src\components\UserProfile\ProfilDuzenleForm.tsx src\pages\UserProfiles.tsx
npm run lint
npm run build
```

- [ ] Hedef lint sonucu ayrı kaydedilir.
- [ ] Tam lint’te kapsam dışı mevcut hatalar Faz 4 dosyalarından ayrılarak raporlanır.
- [ ] Production build exit code ve uyarılar okunur.
- [ ] Ayrı yardımcı agent bütün Faz 4 dosyalarını backend sözleşmesi, React, erişilebilirlik ve kapsam açısından inceler.
- [ ] Canlı backend kullanıcı bilgileri yoksa tarayıcı kontrolleri açıkça mock olarak raporlanır.
- [ ] Frontend test framework’ü bulunmadığı için otomatik testlerin geçtiği iddia edilmez.

## Tamamlanma Raporu

Son raporda değişen dosyalar, diff/null/no-change akışı, PATCH sonrası GET ve kısmi başarı davranışı, korunan mevcut alanlar, bütün doğrulama sonuçları ve `mezuniyetYili` kapsam sınırı açıkça belirtilir.
