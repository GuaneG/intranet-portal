# Faz 6 Personel Yönetimi Listesi Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** HR ve ADMIN için rol korumalı, filtrelenebilir ve sayfalanabilir salt-okunur personel yönetimi listesi oluşturmak.

**Architecture:** `RoleRoute` route seviyesinde UX erişimini sınırlar. Route sayfası filtre, pagination ve API state'ini yönetir; tablo yalnız görünümü render eder. API çağrıları mevcut `apiFetch` ve backend'in `SayfaliResponse` sözleşmesini kullanır.

**Tech Stack:** React 19, TypeScript/JavaScript, React Router, Tailwind/TailAdmin, mevcut `apiFetch`.

## Global Constraints

- Yalnız frontend dosyaları ve bu plan kapsamındaki Markdown belgeleri değişebilir.
- Backend, dependency, test framework'ü ve mevcut `ProtectedRoute` değişmez.
- Git komutlarını yalnız kullanıcı çalıştırır.
- İlk liste isteği `aktif=true` kullanır; boş opsiyonel query parametreleri gönderilmez.
- Frontend görünürlüğü backend yetkilendirmesinin yerine geçmez.
- Her görev ayrı uygulanır, doğrulanır ve kullanıcıya raporlanır.

---

## Dosya Haritası

- Create `frontend/src/components/RoleRoute.tsx`: rol bazlı UX route guard.
- Create `frontend/src/types/personelYonetim.ts`: liste, filtre ve sayfalama tipleri.
- Create `frontend/src/components/personel-yonetimi/PersonelYonetimTablosu.tsx`: salt-okunur tablo.
- Create `frontend/src/pages/PersonelYonetimi.tsx`: filtre, istek, pagination ve durum koordinasyonu.
- Modify `frontend/src/lib/api.js`: personel listesi ve departman lookup fonksiyonları.
- Modify `frontend/src/App.tsx`: `/yonetim/personel` route'u.
- Modify `frontend/src/layout/AppSidebar.tsx`: Personel Yönetimi gerçek linki.

### Task 1: Mevcut Davranışı RED Olarak Kaydet

**Files:** Değişiklik yok.

**Interfaces:**
- Consumes: mevcut `/yonetim/personel` route ve sidebar davranışı.
- Produces: uygulama sonrası karşılaştırılacak browser kabul tablosu.

- [ ] PERSONEL, YONETICI, HR ve ADMIN için ayrı browser context/reload kullan.
- [ ] `/yonetim/personel` doğrudan erişimin şu an fallback/404 olduğunu doğrula.
- [ ] HR/ADMIN sidebar'ında Personel Yönetimi öğesinin `Yakında` olduğunu doğrula.
- [ ] Oturumları kapat ve dosya değişmediğini raporla.

### Task 2: RoleRoute ve Gerçek Route

**Files:**
- Create: `frontend/src/components/RoleRoute.tsx`

**Interfaces:**
- Consumes: `useAuth()`, `AuthRole`, React Router `Navigate`.
- Produces: `<RoleRoute roller={["HR", "ADMIN"]}>`.

- [ ] `RoleRoute` davranışını oluştur:

```tsx
type RoleRouteProps = {
  children: React.ReactNode;
  roller: readonly AuthRole[];
};

// oturum yok: /signin replace
// rol izinli değil: / replace
// izinli: children
```

- [ ] Hedef lint çalıştır:

```powershell
cd frontend
npx eslint src/components/RoleRoute.tsx
```

- [ ] Kaynak incelemesiyle signin, yetkisiz dashboard ve izinli children kollarını doğrula. Gerçek route browser kontrolü Task 4'te yapılır.

### Task 3: Tipler ve API Katmanı

**Files:**
- Create: `frontend/src/types/personelYonetim.ts`
- Modify: `frontend/src/lib/api.js`

**Interfaces:**
- Produces:
  - `PersonelYonetimListeOgesi`
  - `PersonelYonetimSayfasi`
  - `PersonelYonetimFiltreleri`
  - `LookupSecenegi`
  - `personelleriGetir(params)`
  - `departmanlariGetir()`

- [ ] Tipleri backend alanlarıyla bire bir tanımla:

```ts
export type PersonelYonetimListeOgesi = {
  personelId: string;
  ad: string;
  soyad: string;
  kullaniciAdi: string;
  rol: AuthRole;
  departmanId: number;
  departmanAdi: string;
  aktif: boolean;
};

export type PersonelYonetimFiltreleri = {
  page: number;
  size: number;
  arama?: string;
  departmanId?: number;
  rol?: AuthRole;
  aktif?: boolean;
};

export type LookupSecenegi = { id: number; ad: string };

export type PersonelYonetimSayfasi = {
  icerik: PersonelYonetimListeOgesi[];
  sayfa: number;
  sayfaBoyutu: number;
  toplamKayit: number;
  toplamSayfa: number;
};
```

- [ ] `personelleriGetir` içinde `URLSearchParams` kullan; yalnız tanımlı/değerli parametreleri ekle. `aktif=false` değerini yanlışlıkla atlama.
- [ ] İki GET fonksiyonunda ağ hatasını `ApiError(0, ...)`, non-OK cevabı `apiErrorOlustur` ile dönüştür.
- [ ] Hedef lint çalıştır:

```powershell
cd frontend
npx eslint src/lib/api.js src/types/personelYonetim.ts
```

### Task 4: Liste Sayfası ve Tablo

**Files:**
- Create: `frontend/src/components/personel-yonetimi/PersonelYonetimTablosu.tsx`
- Create: `frontend/src/pages/PersonelYonetimi.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/layout/AppSidebar.tsx`

**Interfaces:**
- Consumes: Task 3 tipleri ve API fonksiyonları.
- Produces: çalışan `/yonetim/personel` sayfası.

- [ ] Tablo props'unu salt-okunur tut:

```ts
type Props = {
  personeller: PersonelYonetimListeOgesi[];
};
```

- [ ] Tablo sütunlarını ad/kullanıcı adı, departman, rol ve aktiflik olarak render et. Rol ve aktiflik için mevcut `Badge` kullan; action sütunu ekleme.
- [ ] Sayfa state'lerini oluştur: taslak filtreler, uygulanmış filtreler, sayfa, response, loading/refreshing, liste hatası, departmanlar ve lookup hatası.
- [ ] Başlangıç filtresini kesin olarak kullan:

```ts
const BASLANGIC_FILTRELERI = {
  arama: "",
  departmanId: "",
  rol: "",
  aktiflik: "true",
} as const;
```

- [ ] `const rol = kullanici?.rol` ile nullable rolü güvenle daralt; ADMIN ve HR seçeneklerini ayrı üret, diğer değerlerde boş seçenek kullan.
- [ ] Submit/Enter'da taslak filtreleri API tipine dönüştür, `page=0` yap ve listeyi getir. Temizle başlangıç filtrelerine döner.
- [ ] Her request'e artan numara ver; yalnız son request state yazabilir. Yeni request başarısızsa eski response'u kaldır, aynı sorgu için retry göster.
- [ ] Liste alanına `aria-busy` ekle; request sürerken Filtrele, Temizle, Önceki, Sonraki ve retry eylemlerini disable et.
- [ ] Lookup hatasında listeyi koru; departman filtresini disable et ve lookup retry sun.
- [ ] İlk loading, refreshing, filtresiz empty, filtreli empty ve error durumlarını ayrı render et.
- [ ] Önceki/Sonraki butonlarında backend sıfır tabanlı sayfayı koru, kullanıcıya bir tabanlı göster.
- [ ] `App.tsx` içinde `/yonetim/personel` route'unu `RoleRoute roller={["HR", "ADMIN"]}` ile gerçek sayfaya bağla.
- [ ] `AppSidebar.tsx` içindeki yalnız `Personel Yönetimi` alt öğesinde `yakinda` kaldır ve `path: "/yonetim/personel"` ekle.
- [ ] Hedef lint çalıştır:

```powershell
cd frontend
npx eslint src/pages/PersonelYonetimi.tsx src/components/personel-yonetimi/PersonelYonetimTablosu.tsx src/App.tsx src/layout/AppSidebar.tsx
```

- [ ] Browser'da HR/ADMIN erişimi ile PERSONEL/YONETICI yönlendirmesini doğrula.

### Task 5: Son Doğrulama

**Files:** Değişiklik yok; yalnız doğrulama.

- [ ] Dört rolle route/sidebar matrisi kontrolü yap.
- [ ] Aktif başlangıç, Aktif/Pasif/Tümü, ad/soyad/kullanıcı adı, departman ve rol filtrelerini kontrol et.
- [ ] Birleşik filtre, temizleme, pagination, empty, liste error/retry ve lookup error/retry senaryolarını kontrol et.
- [ ] Geciken eski request'in yeni filtre sonucunu ezemediğini kontrollü response sırasıyla doğrula.
- [ ] Masaüstü ve mobil yerleşimi kontrol et.
- [ ] Hedef lint çalıştır:

```powershell
cd frontend
npx eslint src/components/RoleRoute.tsx src/types/personelYonetim.ts src/components/personel-yonetimi/PersonelYonetimTablosu.tsx src/pages/PersonelYonetimi.tsx src/App.tsx src/layout/AppSidebar.tsx src/lib/api.js
```

- [ ] Production build çalıştır:

```powershell
cd frontend
npm run build
```

- [ ] Tam lint çalıştır ve görev öncesi ilgisiz hataları ayrı raporla:

```powershell
cd frontend
npm run lint
```

- [ ] Değişen dosyaları, veri akışını, UX-only yetki sınırını ve doğrulama sonuçlarını raporla.
