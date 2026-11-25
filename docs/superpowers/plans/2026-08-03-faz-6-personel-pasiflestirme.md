# Faz 6 — Personel Pasifleştirme Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** HR ve Admin'in yetkili aktif personeli detay ekranından onayla pasifleştirebilmesini sağlamak.

**Architecture:** Mevcut ortak API katmanına body almayan bir pasifleştirme fonksiyonu eklenir. Yeni, odaklı bir onay component'i mevcut `Modal` yapısını kullanarak istek, yüklenme ve hata durumunu yönetir; detay sayfası yalnız başarı sonrası aktif personel listesine yönlendirmekten sorumlu olur.

**Tech Stack:** React 19, TypeScript, React Router, Vite, mevcut TailAdmin `Modal` component'i ve `src/lib/api.js` API katmanı.

## Global Constraints

- Yalnız frontend dosyaları değişir; backend endpoint ve yetki kuralları değişmez.
- Yeni dependency veya frontend test framework'ü eklenmez.
- Git komutlarını yalnız kullanıcı çalıştırır.
- Gerçek yetkilendirme backend'dedir; frontend görünürlüğü yalnız UX'tir.
- `PATCH /api/hr/personel/{personelId}/pasiflestir` body olmadan çağrılır ve başarıda `204 No Content` beklenir.
- Başarılı işlem sonrası `/yonetim/personel` adresine yönlendirilir; yeniden aktifleştirme bu kapsam dışındadır.

---

## Dosya Yapısı

- `frontend/src/lib/api.js`: Pasifleştirme endpoint'inin ortak istemci fonksiyonu.
- `frontend/src/components/personel-yonetimi/PersonelPasiflestirmeOnayi.tsx`: Aktif hedef için düğme, onay modali, gönderim ve hata state'i.
- `frontend/src/pages/PersonelYonetimDetayi.tsx`: Onay component'ini detay verisiyle bağlar ve başarıda listeye yönlendirir.

### Task 1: API katmanına pasifleştirme isteğini ekle

**Files:**

- Modify: `frontend/src/lib/api.js` — `personelGuncelle` fonksiyonunun ardından

**Consumes:** `apiFetch(path, options)`, `ApiError`, `apiErrorOlustur(response, fallbackMessage)`.

**Produces:** `personelPasiflestir(personelId: string): Promise<void>`.

- [ ] **Step 1: Endpoint sözleşmesini doğrula**

`HrPersonelController#personelPasiflestir` metodundan endpoint'in `PATCH /api/hr/personel/{personelId}/pasiflestir`, request body'siz ve başarı cevabının `204 No Content` olduğunu kontrol et.

- [ ] **Step 2: Fonksiyonu ekle**

```js
export async function personelPasiflestir(personelId) {
    const encodedPersonelId = encodeURIComponent(personelId);

    let response;

    try {
        response = await apiFetch(
            `/api/hr/personel/${encodedPersonelId}/pasiflestir`,
            { method: "PATCH" },
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
            "Personel pasifleştirilemedi. Lütfen tekrar deneyin.",
        );
    }
}
```

Fonksiyon response gövdesi okumaz; `204` cevabında JSON bulunmaz.

- [ ] **Step 3: Hedefli kontrolü çalıştır**

Run: `npx eslint src/lib/api.js`

Expected: Bu dosyada ESLint hatası yok.

### Task 2: Pasifleştirme onay component'ini oluştur

**Files:**

- Create: `frontend/src/components/personel-yonetimi/PersonelPasiflestirmeOnayi.tsx`

**Consumes:** `PersonelYonetimDetayi`, `personelPasiflestir`, `ApiError`, `Modal` from `../ui/modal`.

**Produces:** `PersonelPasiflestirmeOnayi` component'i.

- [ ] **Step 1: Component arayüzünü tanımla**

```tsx
type PersonelPasiflestirmeOnayiProps = {
    detay: PersonelYonetimDetayi;
    onBasarili: () => void;
};
```

Component, `detay.aktif` false ise `null` döndürür. Böylece zaten pasif kayıt için yeni istek açılmaz.

- [ ] **Step 2: Onay modalini oluştur**

`useModal` ile açılma state'ini; component içi state ile `pasiflestirmeSuruyor` ve `hata` değerlerini tut. Düğme metni `Pasifleştir` olsun. Modal, hedefin adını ve şu etkileri açıkça söylesin: personel pasif olur, mevcut oturumları sonlandırılır ve bu ekranda yeniden aktifleştirme yoktur.

```tsx
<Modal
    isOpen={isOpen}
    onClose={() => {
        if (!pasiflestirmeSuruyor) closeModal();
    }}
    showCloseButton={!pasiflestirmeSuruyor}
    className="max-w-[520px] p-6"
>
    {/* başlık, açıklama, hata ve aksiyonlar */}
</Modal>
```

`Vazgeç` çağrısı hata mesajını temizleyip modalı kapatır. İşlem sürerken `Vazgeç` ve `Pasifleştir` devre dışı kalır.

- [ ] **Step 3: Tek gönderimli isteği bağla**

```tsx
async function pasiflestirmeyiOnayla() {
    setPasiflestirmeSuruyor(true);
    setHata(null);

    try {
        await personelPasiflestir(detay.personelId);
        onBasarili();
    } catch (error) {
        setHata(
            error instanceof ApiError
                ? error.message
                : "Personel pasifleştirilirken beklenmeyen bir hata oluştu.",
        );
    } finally {
        setPasiflestirmeSuruyor(false);
    }
}
```

`onBasarili` yalnız başarılı `204` sonrasında çağrılır. `403`, `404` veya ağ hatasında modal açık kalır; kullanıcı hata mesajını görür ve tekrar deneyebilir.

- [ ] **Step 4: Hedefli kontrolü çalıştır**

Run: `npx eslint src/components/personel-yonetimi/PersonelPasiflestirmeOnayi.tsx && npx tsc --noEmit`

Expected: Yeni component için lint ve TypeScript hatası yok.

### Task 3: Detay ekranına aksiyonu bağla

**Files:**

- Modify: `frontend/src/pages/PersonelYonetimDetayi.tsx` — üst aksiyon satırı ve importlar

**Consumes:** `PersonelPasiflestirmeOnayi`, `useNavigate`, detay state'i.

**Produces:** Detay ekranından başlatılan, başarı sonrası listeye dönen pasifleştirme akışı.

- [ ] **Step 1: Sayfa yönlendirmesini hazırla**

`react-router` importuna `useNavigate` ekle ve component içinde `const navigate = useNavigate();` tanımla.

- [ ] **Step 2: Mevcut aksiyon grubunu genişlet**

Mevcut `Düzenle` bağlantısını kaldırma. `detay` mevcutsa aynı aksiyon grubunda aşağıdaki component'i ekle:

```tsx
<PersonelPasiflestirmeOnayi
    detay={detay}
    onBasarili={() =>
        navigate("/yonetim/personel", { replace: true })
    }
/>
```

Detay sayfası API isteği veya onay state'i tutmaz; bunlar yeni component'in sorumluluğudur.

- [ ] **Step 3: Hedefli kontrolü çalıştır**

Run: `npx eslint src/pages/PersonelYonetimDetayi.tsx src/components/personel-yonetimi/PersonelPasiflestirmeOnayi.tsx src/lib/api.js && npx tsc --noEmit`

Expected: Değişen TypeScript ve API dosyalarında hata yok.

### Task 4: Manuel kabul ve proje doğrulaması

**Files:**

- Verify only: `frontend/src/lib/api.js`
- Verify only: `frontend/src/components/personel-yonetimi/PersonelPasiflestirmeOnayi.tsx`
- Verify only: `frontend/src/pages/PersonelYonetimDetayi.tsx`

- [ ] **Step 1: Onay açma ve vazgeçme akışını kontrol et**

Yetkili HR/Admin ile aktif bir hedefin detayını aç. `Pasifleştir` düğmesinin göründüğünü, modalın kişinin adını ve etkileri gösterdiğini doğrula. `Vazgeç`e bas; PATCH isteği olmadan detayda kalındığını doğrula.

- [ ] **Step 2: Başarılı işlemi onaylı test verisiyle kontrol et**

Kullanıcının açık onayıyla test kaydını pasifleştir. `Pasifleştir` düğmesinin istek süresince devre dışı kaldığını ve başarılı `204` sonrası `/yonetim/personel` aktif listesine dönüldüğünü doğrula. Test kaydının listede görünmediğini kontrol et.

- [ ] **Step 3: Yetki/hata davranışını kontrol et**

Backend'in öz-hedef, `ADMIN` hedef veya HR'ın `YONETICI` hedefi için gönderdiği `403` cevabının modalda anlaşılır mesaj olarak kaldığını doğrula. Frontend'in bunu güvenlik kontrolü olarak kabul etmediğini teyit et.

- [ ] **Step 4: Proje kontrollerini çalıştır**

Run: `npm run lint`

Expected: Yeni dosyalarda hata yok; mevcut `CountryMap.tsx`, `Calendar.tsx` ve `svg.d.ts` lint hataları ayrı baseline olarak raporlanır.

Run: `npm run build`

Expected: Production build başarılı; mevcut `jvectormap`, CSS `:is()` ve büyük chunk uyarıları ayrı baseline olarak raporlanır.

- [ ] **Step 5: Kullanıcı git işlemlerini isterse uygular**

Agent git komutu çalıştırmaz. Kullanıcı isterse yalnız bu teslimin dosyalarını gözden geçirip kendi commit'ini oluşturur.
