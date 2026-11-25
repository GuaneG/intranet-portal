# Faz 6 — Personel Aktifleştirme Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Yetkili HR/Admin kullanıcısının pasif personeli detay ekranından onayla yeniden aktifleştirmesini sağlamak.

**Architecture:** Mevcut pasifleştirme akışının API, modal, hata ve çift-istek koruma yaklaşımı yeni, odaklı bir aktifleştirme component'inde kullanılır. Detay sayfası yalnız component'i yerleştirir ve başarılı işlemden sonra varsayılan aktif listeye yönlendirir.

**Tech Stack:** React 19, TypeScript, React Router, TailAdmin `Modal`, mevcut `apiFetch` ve `ApiError` altyapısı.

## Global Constraints

- Yalnız frontend dosyaları değişir; backend, Git ve mevcut pasifleştirme akışı değiştirilmez.
- Yeni dependency veya test framework'ü eklenmez.
- `PATCH /api/hr/personel/{personelId}/aktiflestir` body olmadan çağrılır; başarılı response `204 No Content` olduğu için JSON okunmaz.
- Frontend görünürlüğü yalnız UX'tir; backend hedef matrisinin güvenlik kaynağıdır.
- Başarıda `/yonetim/personel` adresine `replace: true` ile dönülür; optimistic update yapılmaz.

---

### Task 1: API istemcisine aktifleştirme çağrısını ekle

**Files:**

- Modify: `frontend/src/lib/api.js` — `personelPasiflestir` fonksiyonunun ardından

**Produces:** `personelAktiflestir(personelId: string): Promise<void>`.

- [ ] `personelPasiflestir` ile aynı hata dönüşümünü kullanarak, yalnız endpoint sonu `aktiflestir` olan body’siz fonksiyonu ekle.

```js
export async function personelAktiflestir(personelId) {
    const encodedPersonelId = encodeURIComponent(personelId);
    let response;

    try {
        response = await apiFetch(
            `/api/hr/personel/${encodedPersonelId}/aktiflestir`,
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
            "Personel aktifleştirilemedi. Lütfen tekrar deneyin.",
        );
    }
}
```

- [ ] Run: `npx eslint src/lib/api.js`

### Task 2: Aktifleştirme onay component'ini oluştur

**Files:**

- Create: `frontend/src/components/personel-yonetimi/PersonelAktiflestirmeOnayi.tsx`

**Consumes:** `PersonelYonetimDetayi`, `Modal`, `useModal`, `ApiError`, `personelAktiflestir`.

**Produces:** Pasif hedefte aksiyon ve onay modalini render eden `PersonelAktiflestirmeOnayi` component'i.

- [ ] Component yalnız `detay.aktif` false ise `Aktifleştir` düğmesini render etsin; aktif kayıtta `null` dönsün.
- [ ] Modal hedefin ad-soyadını içersin; `Vazgeç` istek göndermeden kapatsın.
- [ ] Ref tabanlı kilit ve disabled state kullanarak hızlı çift tıklamayı engelle; istek sürerken backdrop, Escape, kapatma düğmesi ve vazgeçme işlemi modalı kapatamasın.
- [ ] Başarılı `personelAktiflestir(detay.personelId)` çağrısı sonrası `onBasarili()` çalışsın. `ApiError` veya fallback hata modalda gösterilsin ve kullanıcı yeniden deneyebilsin.
- [ ] Run: `npx eslint src/components/personel-yonetimi/PersonelAktiflestirmeOnayi.tsx && npx tsc --noEmit`

### Task 3: Detay ekranına aktifleştirme aksiyonunu bağla

**Files:**

- Modify: `frontend/src/pages/PersonelYonetimDetayi.tsx` — mevcut sağ aksiyon grubu

**Consumes:** `PersonelAktiflestirmeOnayi`, mevcut `detay` ve `navigate`.

- [ ] Mevcut `Düzenle` bağlantısını ve `PersonelPasiflestirmeOnayi` component'ini kaldırmadan aynı aksiyon grubuna aktifleştirme component'ini ekle.

```tsx
<PersonelAktiflestirmeOnayi
    detay={detay}
    onBasarili={() =>
        navigate("/yonetim/personel", {
            replace: true,
        })
    }
/>
```

Karşılıklı aktif/pasif render koşulları nedeniyle aynı anda yalnız bir mutation düğmesi görünür.

- [ ] Run: `npx eslint src/pages/PersonelYonetimDetayi.tsx src/components/personel-yonetimi/PersonelAktiflestirmeOnayi.tsx src/lib/api.js && npx tsc --noEmit`

### Task 4: Doğrulama

**Files:** Verify only the three frontend files above.

- [ ] Pasif ve yetkili bir test kaydında aktifleştirme düğmesini, ad-soyadlı modalı ve `Vazgeç`in istek göndermediğini kontrol et.
- [ ] Kullanıcının açık onayıyla test kaydını aktifleştir; başarıda aktif listeye dönüldüğünü ve kaydın tekrar göründüğünü doğrula.
- [ ] Aktif kayıtta pasifleştirme; pasif kayıtta aktifleştirme dışında mutation düğmesi görünmediğini doğrula.
- [ ] HR/Admin hedef matrisinde backend’in `403` kararının frontend görünürlüğünden bağımsız kaldığını doğrula.
- [ ] Run: `npm run lint` — bilinen `CountryMap.tsx`, `Calendar.tsx` ve `svg.d.ts` baseline hatalarını yeni değişikliklerden ayrı raporla.
- [ ] Run: `npm run build` — production build sonucunu raporla.
