# Profil Yetenekleri Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tüm authenticated kullanıcıların kendi profilindeki yetenek kümesini mevcut katalogdan seçip kaydedebilmesi.

**Architecture:** `UserProfiles` düzenleme modalının açık/kapalı ve başarı state'ini sahiplenir. Modal `GET /api/skills` ile seçenekleri yükler, `PUT /api/profil/yetenekler` ile yalnız seçili ID kümesini gönderir ve başarıdan sonra profil `GET /api/profil` ile yeniden okunur.

**Tech Stack:** React 19, TypeScript, TailAdmin/Tailwind, mevcut `src/lib/api.js`.

## Global Constraints

- Yeni dependency veya frontend test framework eklenmez; git komutu çalıştırılmaz.
- Mevcut `apiFetch`, sessiz refresh, route guard ve backend yetkilendirmesi değiştirilmez.
- Request yalnız `{ yetenekIdleri: number[] }` taşır; personel kimliği, rol veya başka server-controlled alan taşınmaz.
- Takvim, demo route'lar ve backend kodu değiştirilmez.

---

### Task 1: API fonksiyonu ve düzenleme modalı

**Files:**
- Modify: `frontend/src/lib/api.js`
- Create: `frontend/src/components/UserProfile/ProfilYetenekDuzenlemeModali.tsx`

**Interfaces:**

```js
export async function profilYetenekleriniGuncelle(yetenekIdleri) {}
```

```ts
type ProfilYetenekDuzenlemeModaliProps = {
  isOpen: boolean;
  mevcutYetenekIdleri: number[];
  onKapat: () => void;
  onBasarili: () => Promise<void>;
};
```

- [ ] `profilYetenekleriniGuncelle`, `apiFetch("/api/profil/yetenekler", { method: "PUT" })` ile JSON `{ yetenekIdleri }` gönderir; ağ hatasını kontrollü `ApiError` yapar, `204` dışı response'ları `apiErrorOlustur` ile güvenli hata mesajına çevirir.
- [ ] Modal açıldığında `yetenekleriGetir()` ile katalog seçeneklerini yükler; mevcut ID'leri checkbox seçimi olarak gösterir.
- [ ] Boş katalog, loading ve lookup hatası ayrı gösterilir. Kaydet sırasında ref kilidi, disabled kontroller ve `role="alert"` hata alanı uygulanır.
- [ ] Kaydet seçili tüm ID'leri gönderir; başarıda `onBasarili` çağrılır ve modal kapanır.

### Task 2: Profil ekranı ve kart eylemi

**Files:**
- Modify: `frontend/src/components/UserProfile/UserEducationSkillsCard.tsx`
- Modify: `frontend/src/pages/UserProfiles.tsx`

- [ ] Eğitim/yetenek kartının başlığına `Yetenekleri düzenle` butonu eklenir.
- [ ] `UserProfiles`, modalı açar; başarı callback'inde `profilimiGetir()` ile profile yeniden yükler, backend response'unu state'e yazar ve başarı mesajını gösterir.
- [ ] Yükleme, modal hata ve profil yenileme hataları teknik exception ayrıntısı göstermez.

### Task 3: Doğrulama

- [ ] Browser'da profil yetenek kartı, modal, boş katalog ve seçim görünümü kontrol edilir. Kalıcı test verisi oluşturmamak için gerçek PUT mutation yalnız kullanıcı verisiyle manuel denenir.
- [ ] `npx tsc --noEmit`, `npm run lint`, `npm run build` çalıştırılır; mevcut ilgisiz lint hataları raporlanır.
