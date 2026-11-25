# Faz 6 — Personel Oluşturma Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** HR ve Admin'in backend sözleşmesine uygun yeni `PERSONEL` kaydı oluşturabilmesini sağlamak.

**Architecture:** Ayrı route seviyesi sayfa lookup verilerini ve başarı durumunu yönetir. Form component'i yalnız alan doğrulama, departman-yönetici seçimi ve `POST` isteğinden sorumludur; başarılı response'taki kimlikle sayfa başarı kartı gösterir.

**Tech Stack:** React 19, TypeScript, React Router, TailAdmin form component'leri, mevcut `apiFetch`/`ApiError` altyapısı.

## Global Constraints

- Yalnız frontend dosyaları değişir; backend dosyaları ve Git komutları kapsam dışıdır.
- Yeni dependency veya test framework'ü eklenmez.
- İstek yalnız `{ kullaniciAdi, ad, soyad, dogumTarihi, ePosta, departmanId, isGirisTarihi, yoneticiId? }` alanlarını gönderir.
- Başarı response'u `{ personelId, kullaniciAdi }` ve status `201 Created` biçimindedir.
- Rol, parola, sicil numarası, aktiflik ve işten çıkış tarihi formda/request'te bulunmaz.
- Frontend görünürlüğü UX içindir; yetkilendirme ve ilişki doğrulaması backend'dedir.

---

### Task 1: Tipler ve API istemcisi

**Files:**

- Modify: `frontend/src/types/personelYonetim.ts`
- Modify: `frontend/src/lib/api.js`

**Produces:** `PersonelOlusturmaRequest`, `PersonelOlusturmaResponse`, `personelOlustur(payload)`.

- [ ] `personelYonetim.ts` sonuna aşağıdaki tipleri ekle:

```ts
export type PersonelOlusturmaRequest = {
    kullaniciAdi: string;
    ad: string;
    soyad: string;
    dogumTarihi: string;
    ePosta: string;
    departmanId: number;
    isGirisTarihi: string;
    yoneticiId?: string;
};

export type PersonelOlusturmaResponse = {
    personelId: string;
    kullaniciAdi: string;
};
```

- [ ] `api.js` içinde `personelGuncelle` öncesine `personelOlustur(payload)` ekle. `apiFetch("/api/hr/personel", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) })` kullansın; ağ hatasını `ApiError(0, ...)`, non-OK cevabı `apiErrorOlustur(..., "Personel oluşturulamadı. Lütfen tekrar deneyin.")` ile dönüştürsün; başarıda `return response.json()` dönsün.

- [ ] Run: `npx eslint src/lib/api.js src/types/personelYonetim.ts && npx tsc --noEmit`

### Task 2: Oluşturma form component'i

**Files:**

- Create: `frontend/src/components/personel-yonetimi/PersonelYonetimOlusturmaFormu.tsx`

**Consumes:** `LookupSecenegi`, `AtanabilirYoneticiSecenegi`, `PersonelOlusturmaRequest`, `PersonelOlusturmaResponse`, `personelOlustur`, `ApiError`, `Label`, `Input`.

**Produces:** Lookup props alan, başarılı response'u `onOlusturuldu(response)` ile üst sayfaya veren form component'i.

- [ ] Form state'ini yalnız request alanlarıyla tanımla. `yoneticiId` HTML state'inde boş string olabilir; payload oluştururken boşsa alanı hiç ekleme.
- [ ] Alanları şu bölümlerde render et: `Hesap bilgileri` (kullanıcı adı), `Kişisel bilgiler` (ad, soyad, doğum tarihi, e-posta), `İş bilgileri` (departman, yönetici, işe giriş tarihi).
- [ ] İstemci doğrulaması backend kurallarıyla uyumlu olsun: kullanıcı adı 3–50; ad 1–50; soyad 3–50; e-posta boş değil; doğum tarihi geçmişte; departman pozitif; işe giriş tarihi bugün veya öncesi. Bu kontrol UX içindir, backend kararını değiştirmez.
- [ ] Departman seçimi boşsa yönetici select disabled olsun. Departman değiştiğinde `yoneticiId` temizlensin; pozitif yeni ID için `onDepartmanDegisti(departmanId)` çağrılsın. Lookup yüklenirken veya hata varken yönetici select kapansın; yönetici backend'de opsiyonel olduğu için kullanıcı `Yönetici yok` ile formu yine gönderebilsin.
- [ ] `useRef(false)` kilidi, state disabled durumunu beklemeden çift POST isteğini engellesin. Başarısız istekte form değerlerini koru; `ApiError` mesajını göster; başarıda response'u üst sayfaya ilet.
- [ ] `İptal` `onIptal` callback'ini çağırır; istek sürerken bütün form kontrolleri ve retry aksiyonları disabled olur.
- [ ] Run: `npx eslint src/components/personel-yonetimi/PersonelYonetimOlusturmaFormu.tsx && npx tsc --noEmit`

### Task 3: Route seviyesi oluşturma sayfası ve başarı kartı

**Files:**

- Create: `frontend/src/pages/PersonelYonetimOlustur.tsx`

**Consumes:** `departmanlariGetir`, `atanabilirYoneticileriGetir`, `PersonelYonetimOlusturmaFormu`, `PersonelOlusturmaResponse`.

**Produces:** `/yonetim/personel/yeni` route'unda lookup, loading/error/retry ve başarı durumunu yöneten ekran.

- [ ] Sayfa açılırken departman lookup'unu yükle. Her lookup türü için mevcut detay/düzenleme sayfalarındaki gibi kendi istek sayacıyla eski response'un yeni state'i ezmesini engelle.
- [ ] Departman seçildiğinde `atanabilirYoneticileriGetir(departmanId, "")` çağrısı yap. Yeni departman seçimi, eski response'ları geçersiz kılar.
- [ ] Form başarılı olduğunda form yerine başarı kartı render et:

```tsx
<h2>Personel oluşturuldu</h2>
<p>
    @{sonuc.kullaniciAdi} ilk girişte kullanıcı adıyla giriş yapmalı
    ve parolasını değiştirmelidir.
</p>
<Link to={`/yonetim/personel/${encodeURIComponent(sonuc.personelId)}`}>
    Personel detayını aç
</Link>
<Link to="/yonetim/personel">Listeye dön</Link>
```

Parola değeri gösterme veya saklama.

- [ ] İptal ve sayfanın üstündeki geri bağlantı `/yonetim/personel` adresine döner. İlk lookup hatasında form yerine hata/retry + listeye dönüş gösterilir.
- [ ] Run: `npx eslint src/pages/PersonelYonetimOlustur.tsx && npx tsc --noEmit`

### Task 4: Liste giriş noktası ve route

**Files:**

- Modify: `frontend/src/pages/PersonelYonetimi.tsx`
- Modify: `frontend/src/App.tsx`

- [ ] Liste sayfasının mevcut üst başlık/aksiyon alanına `Link` ile `/yonetim/personel/yeni` hedefine giden `Yeni Personel` düğmesi ekle. Filtre, tablo ve pagination state'ini değiştirme.
- [ ] `App.tsx` içinde `PersonelYonetimOlustur` import et; ProtectedRoute/AppLayout altında aşağıdaki route'u ekle:

```tsx
<Route
    path="/yonetim/personel/yeni"
    element={
        <RoleRoute roller={["HR", "ADMIN"]}>
            <PersonelYonetimOlustur />
        </RoleRoute>
    }
/>
```

React Router sıralama puanlaması statik `yeni` segmentini `:personelId` segmentinden öncelikli seçer.

- [ ] Run: `npx eslint src/App.tsx src/pages/PersonelYonetimi.tsx src/pages/PersonelYonetimOlustur.tsx src/components/personel-yonetimi/PersonelYonetimOlusturmaFormu.tsx src/lib/api.js && npx tsc --noEmit`

### Task 5: Manuel kabul ve proje doğrulaması

- [ ] HR/Admin ile listeyi aç; `Yeni Personel` düğmesinden formun açıldığını, doğrudan URL/yenilemede route'un çalıştığını doğrula.
- [ ] Departman seçmeden yönetici seçilemediğini; departman değişince yöneticinin temizlendiğini; lookup yüklenme/hata durumunun kullanıcıyı yanıltmadığını kontrol et.
- [ ] Kullanıcının açık onayıyla benzersiz kullanıcı adı/e-posta içeren test personeli oluştur. Başarı kartında yalnız kullanıcı adı ve ilk giriş/parola değiştirme bilgilendirmesi bulunduğunu, parola görünmediğini; detay ve liste bağlantılarının çalıştığını doğrula.
- [ ] Aynı kullanıcı adı/e-posta ile `409` hatası; eksik/geçersiz alanlarda form hatası; ağ/backend hatasında formun korunması davranışlarını kontrol et.
- [ ] Run: `npm run lint` — mevcut `CountryMap.tsx`, `Calendar.tsx`, `svg.d.ts` baseline hatalarını ayrı raporla.
- [ ] Run: `npm run build` — production build sonucunu raporla.
