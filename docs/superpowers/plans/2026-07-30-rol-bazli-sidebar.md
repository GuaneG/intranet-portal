# Rol Bazlı Sidebar Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sidebar’ı login response’undaki role göre filtrelemek, hazır olmayan gelecek ürün alanlarını tıklanamaz “Yakında” öğeleri olarak göstermek ve yalnız onaylanan TailAdmin demo gruplarını sidebar’dan kaldırmak.

**Architecture:** Menü tanımları `AppSidebar.tsx` içinde kalır. `AuthContext.kullanici.rol` tek rol kaynağıdır; menü ve alt menü öğeleri opsiyonel `roller` ve `yakinda` metadata’sı taşır. Bu görünürlük yalnız kullanıcı deneyimidir; backend authorization yerine geçmez ve bu fazda kullanılmayan `RoleRoute` eklenmez.

**Tech Stack:** React 19, TypeScript, React Router, mevcut AuthContext, TailAdmin/Tailwind sınıfları, Vite.

## Global Constraints

- Git komutu çalıştırma.
- Backend dosyalarına dokunma.
- `App.tsx`, mevcut route’lar ve demo sayfa dosyalarını değiştirme.
- Yeni dependency, test framework’ü, context, hook, route veya ayrı component dosyası ekleme.
- Değişecek tek kod dosyası `frontend/src/layout/AppSidebar.tsx`.
- Sidebar’dan yalnız `Pages`, `Charts`, `UI Elements` ve `Authentication` gruplarını kaldır.
- `Talepler` ve `Ekip Dizini` sidebar’da kalır.
- Hazır olmayan öğeler `<Link>`, çalışan `<button>`, `to`, `href` veya `onClick` almaz.
- `RoleRoute` ilk gerçek rol-sınırlı sayfaya ertelenir.
- Kullanıcı kodu manuel uygular; her kod görevi ayrı yardımcı-agent incelemesinden sonra tamamlanır.

---

### Task 1: Dört rol için tarayıcı RED kaydı

**Files:**
- Modify: Yok
- Read: `frontend/src/layout/AppSidebar.tsx`

**Interfaces:**
- Consumes: `localStorage.token`, `localStorage.kullanici`
- Produces: Faz 5 öncesindeki yanlış ortak sidebar görünümünün kanıtı

- [ ] **Step 1: Protected layout’u API bağımlılığı olmadan aç**

`/blank` route’unu kullan. Her rol için token ve kullanıcıyı sayfa açılmadan önce yerleştir:

```js
localStorage.setItem("token", "faz5-browser-test");
localStorage.setItem(
  "kullanici",
  JSON.stringify({
    ad: "Test",
    soyad: "Kullanıcı",
    rol: "PERSONEL",
  }),
);
```

`rol` değerini sırayla `PERSONEL`, `YONETICI`, `HR`, `ADMIN` yap. `AuthContext` mount sırasında storage okuduğu için her rol değişiminden sonra sayfayı yeniden yükle veya her rol için ayrı browser context kullan.

- [ ] **Step 2: Mevcut davranışın final matrise karşı RED olduğunu doğrula**

Beklenen mevcut yanlışlar:

- Dört rol aynı menüyü görür.
- `Pages`, `Charts`, `UI Elements`, `Authentication` ve `Others` görünür.
- YONETICI ve ADMIN kendi izin/ekipman talebi bağlantılarını görür.
- Ekipman Talepleri yanlış biçimde `/` hedefine gider.
- Duyurular, rezervasyon, yönetim ve “Yakında” görünmez.
- Ekip Dizini gerçek ekran yerine `/basic-tables` demosuna gider.

- [ ] **Step 3: Oturumları kapat**

Her rol için sonucu kısa PASS/RED tablosuyla raporla ve tarayıcı oturumlarını kapat.

---

### Task 2: Menü metadata’sı, onaylı rol matrisi ve filtreleme

**Files:**
- Modify: `frontend/src/layout/AppSidebar.tsx`
- Read: `frontend/src/context/AuthContext.tsx`

**Interfaces:**
- Consumes: `useAuth(): { kullanici }`, `AuthRole`
- Produces: `NavItem`, `NavSubItem`, `rolIzinliMi`, `menuyuRoleGoreFiltrele`, `gorunenNavItems`

- [ ] **Step 1: React ve auth importlarını ekle**

```ts
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { useAuth, type AuthRole } from "../context/AuthContext";
```

Final ikon kümesi:

```ts
import {
  BoxCubeIcon,
  CalenderIcon,
  ChatIcon,
  ChevronDownIcon,
  GridIcon,
  GroupIcon,
  HorizontaLDots,
  ListIcon,
  UserCircleIcon,
} from "../icons";
```

- [ ] **Step 2: Menü tiplerini metadata ile tanımla**

```ts
type RoleSinirliOge = {
  roller?: readonly AuthRole[];
  yakinda?: boolean;
};

type NavSubItem = RoleSinirliOge & {
  name: string;
  path?: string;
};

type NavItem = RoleSinirliOge & {
  name: string;
  icon: React.ReactNode;
  path?: string;
  subItems?: NavSubItem[];
};
```

- [ ] **Step 3: Rol kümelerini tek yerde tanımla**

```ts
const TALEP_OLUSTURABILEN_ROLLER = ["PERSONEL", "HR"] as const;
const EKIPMAN_KATALOGU_ROLLERI = [
  "PERSONEL",
  "YONETICI",
  "HR",
] as const;
const PERSONEL_YONETIM_ROLLERI = ["HR", "ADMIN"] as const;
const YONETICI_ROLU = ["YONETICI"] as const;
const ADMIN_ROLU = ["ADMIN"] as const;
```

- [ ] **Step 4: Onaylı menü matrisini tanımla**

`navItems` şu ürün yapısını taşımalı:

```tsx
const navItems: NavItem[] = [
  {
    icon: <GridIcon />,
    name: "Ana Sayfa",
    path: "/",
  },
  {
    icon: <CalenderIcon />,
    name: "Takvim",
    yakinda: true,
  },
  {
    icon: <ChatIcon />,
    name: "Duyurular",
    yakinda: true,
  },
  {
    icon: <ListIcon />,
    name: "Talepler",
    subItems: [
      {
        name: "İzin Taleplerim",
        roller: TALEP_OLUSTURABILEN_ROLLER,
        yakinda: true,
      },
      {
        name: "Ekipman Taleplerim",
        roller: TALEP_OLUSTURABILEN_ROLLER,
        yakinda: true,
      },
      {
        name: "Rezervasyon Taleplerim",
        roller: TALEP_OLUSTURABILEN_ROLLER,
        yakinda: true,
      },
      {
        name: "Takım Talepleri",
        roller: YONETICI_ROLU,
        yakinda: true,
      },
      {
        name: "Tüm Talepler",
        roller: ADMIN_ROLU,
        yakinda: true,
      },
    ],
  },
  {
    icon: <BoxCubeIcon />,
    name: "Ekipmanlar",
    roller: EKIPMAN_KATALOGU_ROLLERI,
    yakinda: true,
  },
  {
    icon: <GroupIcon />,
    name: "Ekip Dizini",
    yakinda: true,
  },
  {
    icon: <UserCircleIcon />,
    name: "Yönetim",
    subItems: [
      {
        name: "Personel Yönetimi",
        roller: PERSONEL_YONETIM_ROLLERI,
        yakinda: true,
      },
      {
        name: "İzin ve Tatil Yönetimi",
        roller: PERSONEL_YONETIM_ROLLERI,
        yakinda: true,
      },
      {
        name: "Rol Yönetimi",
        roller: ADMIN_ROLU,
        yakinda: true,
      },
      {
        name: "Envanter ve Zimmet",
        roller: ADMIN_ROLU,
        yakinda: true,
      },
      {
        name: "Audit Log",
        roller: ADMIN_ROLU,
        yakinda: true,
      },
    ],
  },
];
```

Bu görevde `othersItems` ve eski demo menü tanımları kaldırılır. `App.tsx` ve demo route’lar korunur.

- [ ] **Step 5: Role göre filtreleme yardımcılarını ekle**

```ts
function rolIzinliMi(
  roller: readonly AuthRole[] | undefined,
  rol: AuthRole,
): boolean {
  return roller === undefined || roller.includes(rol);
}

function menuyuRoleGoreFiltrele(
  items: NavItem[],
  rol: AuthRole,
): NavItem[] {
  return items.flatMap((item) => {
    if (!rolIzinliMi(item.roller, rol)) {
      return [];
    }

    if (!item.subItems) {
      return [item];
    }

    const subItems = item.subItems.filter((subItem) =>
      rolIzinliMi(subItem.roller, rol),
    );

    return subItems.length > 0 ? [{ ...item, subItems }] : [];
  });
}
```

- [ ] **Step 6: Sidebar rolünü yalnız AuthContext’ten al**

```ts
const { kullanici } = useAuth();
const rol = kullanici?.rol;

const gorunenNavItems = useMemo(
  () => (rol ? menuyuRoleGoreFiltrele(navItems, rol) : []),
  [rol],
);
```

Ham `navItems`, aktif-alt-menü effect’i ve render için kullanılmamalı. Her iki yerde de aynı `gorunenNavItems` dizisi kullanılmalı. Sidebar doğrudan `localStorage` okumamalı.

- [ ] **Step 7: Tek menü grubuna göre submenu state’ini sadeleştir**

`othersItems` kaldırıldığı için `openSubmenu` yalnız `number | null` index taşımalı; `subMenuHeight` ve `subMenuRefs` anahtarları index ile tutulmalı. Aktif submenu effect’i yalnız `gorunenNavItems` üzerinde, `subItem.path && isActive(subItem.path)` guard’ıyla çalışmalı.

- [ ] **Step 8: Hedef lint çalıştır**

Working directory: `frontend`

Run:

```powershell
npx eslint src/layout/AppSidebar.tsx
```

Expected: exit code `0`.

**Review gate:** Ayrı yardımcı-agent exact rol matrisini, AuthContext kullanımını, boş kalan Yönetim grubunun PERSONEL/YONETICI için gizlenmesini, demo route dosyalarına dokunulmadığını ve hedef lint sonucunu inceler.

---

### Task 3: Tıklanamaz “Yakında” sunumu

**Files:**
- Modify: `frontend/src/layout/AppSidebar.tsx`

**Interfaces:**
- Consumes: `NavItem.yakinda`, `NavSubItem.yakinda`
- Produces: Üst ve alt seviyede non-interactive “Yakında” satırları

- [ ] **Step 1: Üst seviye öğeyi üç kola ayır**

Render sırası:

1. `nav.subItems` varsa mevcut submenu `<button>`.
2. `nav.yakinda` varsa tıklanamaz `<div>`.
3. `nav.path` varsa çalışan `<Link>`.

Tıklanamaz üst öğenin exact yapısı:

```tsx
<div
  aria-disabled="true"
  aria-label={`${nav.name} - Yakında`}
  title={`${nav.name} - Yakında`}
  className="menu-item relative cursor-not-allowed text-gray-400 dark:text-gray-500"
>
  <span className="menu-item-icon-size text-gray-400 dark:text-gray-500">
    {nav.icon}
  </span>

  {!isExpanded && !isHovered && !isMobileOpen && (
    <span
      aria-hidden="true"
      className="absolute right-1 top-1 rounded-full bg-gray-100 px-1 text-[9px] font-semibold text-gray-500 dark:bg-white/10 dark:text-gray-400"
    >
      Y
    </span>
  )}

  {(isExpanded || isHovered || isMobileOpen) && (
    <>
      <span className="menu-item-text">{nav.name}</span>
      <span className="ml-auto rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-500 dark:bg-white/10 dark:text-gray-400">
        Yakında
      </span>
    </>
  )}
</div>
```

Daraltılmış masaüstü sidebar’da küçük `Y` metin rozeti görünür; öğenin `title` ve `aria-label` değerleri tam “Yakında” anlamını taşır. Sidebar hover ile genişlediğinde `Y` kaybolur ve tam “Yakında” rozeti görünür.

- [ ] **Step 2: Alt öğeyi “Yakında” veya Link olarak render et**

```tsx
{subItem.yakinda ? (
  <div
    aria-disabled="true"
    className="menu-dropdown-item cursor-not-allowed text-gray-400 dark:text-gray-500"
  >
    <span>{subItem.name}</span>
    <span className="ml-auto rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-500 dark:bg-white/10 dark:text-gray-400">
      Yakında
    </span>
  </div>
) : (
  subItem.path && (
    <Link
      to={subItem.path}
      className={`menu-dropdown-item ${
        isActive(subItem.path)
          ? "menu-dropdown-item-active"
          : "menu-dropdown-item-inactive"
      }`}
    >
      {subItem.name}
    </Link>
  )
)}
```

`yakinda` kolunda `<Link>`, `to`, `href`, çalışan `<button>` veya `onClick` bulunmamalı.

- [ ] **Step 3: Yalnız ana Menü bölümünü render et**

`Others` başlığı ve render çağrısı kaldırılır. Ana menü:

```tsx
{renderMenuItems(gorunenNavItems)}
```

`Pages`, `Charts`, `UI Elements` ve `Authentication` metinleri `AppSidebar.tsx` içinde kalmamalı. `Talepler`, `Ekip Dizini`, `Duyurular`, `Ekipmanlar` ve role uygun `Yönetim` korunmalı.

- [ ] **Step 4: Hedef lint çalıştır**

Working directory: `frontend`

Run:

```powershell
npx eslint src/layout/AppSidebar.tsx
```

Expected: exit code `0`.

- [ ] **Step 5: Kısa browser GREEN smoke yap**

- PERSONEL: Talepler altında üç “Taleplerim”; Ekipmanlar görünür; Yönetim gizli.
- YONETICI: yalnız Takım Talepleri; Ekipmanlar görünür; Yönetim gizli.
- HR: üç “Taleplerim”; Ekipmanlar; Yönetim altında iki öğe.
- ADMIN: yalnız Tüm Talepler; ayrı Ekipmanlar gizli; Yönetim altında beş öğe.
- Takvim, Duyurular ve Ekip Dizini dört rolde de “Yakında”.
- Her “Yakında” satırı link değildir ve URL’yi değiştirmez.

**Review gate:** Ayrı yardımcı-agent semantik HTML, expanded “Yakında” rozeti, collapsed `Y` göstergesi ve tam erişilebilir adı, rol matrisi ve route değişmemesini inceler.

---

### Task 4: Dört rol için masaüstü/mobil doğrulama ve son rapor

**Files:**
- Modify: Yok
- Verify: `frontend/src/layout/AppSidebar.tsx`
- Verify unchanged: `frontend/src/App.tsx`

**Interfaces:**
- Consumes: Onaylı rol matrisi
- Produces: Rol başına desktop/mobile doğrulama tablosu

- [ ] **Step 1: Masaüstü görünürlüğünü dört rolle kontrol et**

Viewport: `1440x900`. Her rol için `/blank` protected layout’unu aç.

| Öğe | PERSONEL | YONETICI | HR | ADMIN |
|---|---|---|---|---|
| Ana Sayfa | Aktif | Aktif | Aktif | Aktif |
| Takvim | Yakında | Yakında | Yakında | Yakında |
| Duyurular | Yakında | Yakında | Yakında | Yakında |
| Ekip Dizini | Yakında | Yakında | Yakında | Yakında |
| Ekipmanlar | Yakında | Yakında | Yakında | Gizli |
| İzin Taleplerim | Yakında | Gizli | Yakında | Gizli |
| Ekipman Taleplerim | Yakında | Gizli | Yakında | Gizli |
| Rezervasyon Taleplerim | Yakında | Gizli | Yakında | Gizli |
| Takım Talepleri | Gizli | Yakında | Gizli | Gizli |
| Tüm Talepler | Gizli | Gizli | Gizli | Yakında |
| Personel Yönetimi | Gizli | Gizli | Yakında | Yakında |
| İzin ve Tatil Yönetimi | Gizli | Gizli | Yakında | Yakında |
| Rol Yönetimi | Gizli | Gizli | Gizli | Yakında |
| Envanter ve Zimmet | Gizli | Gizli | Gizli | Yakında |
| Audit Log | Gizli | Gizli | Gizli | Yakında |

`Pages`, `Charts`, `UI Elements`, `Authentication` ve `Others` hiçbir rolde görünmemeli.

- [ ] **Step 2: Mobil görünürlüğü dört rolle kontrol et**

Viewport: `390x844`. Header hamburgeriyle drawer’ı aç:

- Aynı rol matrisi korunur.
- 290 px drawer içinde metin ve badge taşmaz.
- Backdrop ile kapanma davranışı korunur.
- Collapsed/expanded masaüstü davranışı bozulmaz.

- [ ] **Step 3: Yakında ve doğrudan demo URL davranışını kontrol et**

- Görünür her “Yakında” satırı `<a>` değildir.
- Tıklama URL’yi değiştirmez.
- `/calendar`, `/basic-tables`, `/blank`, chart ve UI demo route’ları doğrudan URL ile mevcut ProtectedRoute altında açılmaya devam eder.
- `App.tsx` değiştirilmemiştir.

- [ ] **Step 4: Mekanik doğrulamaları çalıştır**

Working directory: `frontend`

Target lint:

```powershell
npx eslint src/layout/AppSidebar.tsx
```

Expected: exit code `0`.

Production build:

```powershell
npm run build
```

Expected: exit code `0`; mevcut dependency/CSS/chunk uyarıları ayrı raporlanır.

Full lint:

```powershell
npm run lint
```

Başarısızsa yeni sidebar hataları ile önceden var olan `CountryMap.tsx`, `Calendar.tsx`, `svg.d.ts` hatalarını ve Fast Refresh uyarılarını ayır. Sonucu “lint temiz” diye sunma.

- [ ] **Step 5: Son kapsam ve güvenlik incelemesi**

- Değişen tek kod dosyası `frontend/src/layout/AppSidebar.tsx`.
- Backend, `App.tsx`, AuthContext, guard’lar, kart componentleri ve demo sayfalar değişmemiş.
- Yeni dependency/test framework yok.
- `RoleRoute` yok.
- Sidebar görünürlüğü backend authorization olarak sunulmuyor.
- Otomatik frontend test framework’ü bulunmadığı için browser doğrulaması kullanıldığı raporlanıyor.

- [ ] **Step 6: Git kullanmadan sonucu raporla**

Final rapor:

- Değişen dosya ve nedeni
- Dört rol görünürlük matrisi
- Desktop/mobile sonucu
- Yakında non-interactive sonucu
- Demo route korunma sonucu
- Target lint, build ve full lint sonuçları
- Kalan riskler

