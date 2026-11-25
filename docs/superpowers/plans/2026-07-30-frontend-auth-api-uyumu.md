# Frontend Auth/API Uyumu ve İlk Parola Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mevcut login, refresh ve logout entegrasyonunu minimal `AuthContext` ile tutarlı hâle getirmek ve backend’in `428` cevabını gerçek ilk parola değiştirme ekranına bağlamak.

**Architecture:** `src/lib/api.js` HTTP ve token-storage ayrıntılarını korur; yeni `AuthContext` kullanıcı state’ini React içinde reaktif olarak paylaşır. Mevcut guard, login ve kullanıcı menüsü bu context’i tüketir; yeni public ilk parola sayfası `PATCH /api/auth/ilk-parola` sözleşmesini kullanır.

**Tech Stack:** React 19, React Router 7, TypeScript/JavaScript karışık mevcut yapı, TailAdmin, Vite, mevcut Fetch API

## Global Constraints

- Her mevcut dosya, kart veya component değiştirilmeden önce kullanıcıya hangi parçanın neden değişmesi gerektiği commentary mesajıyla bildirilir.
- Agent git komutu çalıştırmaz; commit adımları kullanıcıya aittir ve bu planda çalıştırılacak adım olarak yer almaz.
- Yeni dependency veya frontend test framework’ü eklenmez.
- Repository’de otomatik frontend test altyapısı olmadığı için TDD uygulanmaz; doğrulama lint, build ve tarayıcı akışlarıyla yapılır.
- Mevcut `frontend/src/lib/api.js` sessiz refresh ve single-flight davranışı korunur.
- `ProtectedRoute` ve `PublicRoute` anlamları korunur.
- Access token mevcut `localStorage` anahtarında, refresh token mevcut `httpOnly` cookie’de kalır.
- Token, parola, cookie veya ham backend hata nesnesi console’a yazılmaz.
- Frontend route/menu görünürlüğü backend yetkilendirmesinin yerine geçmez.
- TypeScript/JavaScript dosyaları topluca dönüştürülmez.
- İlgisiz TailAdmin demo sayfaları, kartları ve asset’leri değiştirilmez.
- Mutation formlarında `isSubmitting` veya eşdeğer kilit ile çift gönderim davranışsal olarak engellenir.
- Her görev sonunda en az `npm run lint` çalıştırılır; faz sonunda ayrıca `npm run build` ve mümkünse gerçek tarayıcı doğrulaması yapılır.
- Markdown belgelerinde bu plan dışında yeni bir değişiklik gerekirse kullanıcıdan ayrıca onay alınır.

---

## File Map

### Yeni dosyalar

- `frontend/src/context/AuthContext.tsx`: React içindeki tek reaktif oturum state’i ve `signIn`, `signOut`, `clearSession` işlemleri.
- `frontend/src/components/auth/IlkParolaForm.tsx`: İlk parola değiştirme form state’i, submit kilidi ve kontrollü hata davranışı.
- `frontend/src/pages/AuthPages/IlkParola.tsx`: İlk parola formunu mevcut `AuthPageLayout` ve sayfa metadata’sıyla birleştiren route sayfası.

### Değişecek mevcut dosyalar

- `frontend/src/lib/api.js`: Component içindeki doğrudan login fetch’ini ortak base URL’ye taşımak, kontrollü `ApiError`, ilk parola çağrısı ve her durumda yerel logout temizliği eklemek için değişir. Refresh single-flight algoritması korunur.
- `frontend/src/main.tsx`: `AuthProvider` uygulamayı kapsasın diye değişir.
- `frontend/src/components/ProtectedRoute.jsx`: Doğrudan `localStorage` okumak yerine reaktif auth state’i kullanmak için değişir.
- `frontend/src/components/PublicRoute.jsx`: Doğrudan `localStorage` okumak yerine reaktif auth state’i kullanmak için değişir.
- `frontend/src/components/auth/SignInForm.tsx`: Ortak `signIn`, submitting, ağ hatası, `428` yönlendirmesi ve tek kullanımlık auth mesajı için değişir.
- `frontend/src/components/header/UserDropdown.tsx`: Kullanıcı bilgisini context’ten almak ve ağ hatasında da tamamlanan logout kullanmak için değişir. “Edit profile” metni mevcut `/profile` davranışını doğru anlatan “Profilim” metnine çevrilir.
- `frontend/src/App.tsx`: Yeni public `/ilk-parola` route’unu eklemek için değişir.

### Değişmeyecek fakat kullanılacak dosyalar

- `frontend/src/pages/AuthPages/AuthPageLayout.tsx`: Yeni ilk parola sayfasının mevcut responsive auth düzeni.
- `frontend/src/components/form/input/InputField.tsx`: İlk parola alanları.
- `frontend/src/components/form/Label.tsx`: Form label’ları.
- `frontend/src/components/ui/button/Button.tsx`: Mevcut `disabled` prop’u submit kilidi için yeterlidir; bu component değiştirilmez.

---

### Task 1: Ortak API hata ve auth çağrıları

**Files:**
- Modify: `frontend/src/lib/api.js`

**Why this existing file changes:** Login şu anda `SignInForm` içinde ayrı ve hard-coded bir fetch kullanıyor. Base URL, kontrollü backend hata okuma, ilk parola çağrısı ve logout temizliğinin tek yerde olması için mevcut ortak API katmanı genişletilir; refresh algoritması yeniden yazılmaz.

**Interfaces:**
- Produces: `ApiError`, `login(credentials)`, `ilkParolayiDegistir(payload)`, `clearStoredSession()`, güvenilir `logout()`
- Preserves: `apiFetch(path, options)` ve refresh single-flight

- [ ] **Step 1: Mevcut doğrulama tabanını çalıştır**

Run:

```powershell
cd frontend
npm run lint
```

Expected: Mevcut lint sonucu kaydedilir. Önceden var olan hata varsa yeni değişiklikten ayrı raporlanır.

- [ ] **Step 2: Kontrollü API hata tipini ekle**

`BASE_URL` tanımından sonra şu sınıfı ve response helper’ını ekle:

```js
export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

async function apiErrorOlustur(response, fallbackMessage) {
  const body = await response.json().catch(() => ({}));
  return new ApiError(
    response.status,
    body.message || fallbackMessage,
  );
}
```

`ApiError.status`, `SignInForm` tarafından yalnız `428` ayrımı için kullanılır. Ham response veya exception nesnesi kullanıcıya aktarılmaz.

- [ ] **Step 3: Public login çağrısını ekle**

`api.js` içine şu export’u ekle:

```js
export async function login({ kullaniciAdi, parola }) {
  let response;

  try {
    response = await fetch(`${BASE_URL}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({ kullaniciAdi, parola }),
    });
  } catch {
    throw new ApiError(
      0,
      "Sunucuya ulaşılamadı. Lütfen bağlantınızı kontrol edip tekrar deneyin.",
    );
  }

  if (!response.ok) {
    throw await apiErrorOlustur(response, "Giriş başarısız.");
  }

  return response.json();
}
```

Beklenen başarılı veri mevcut backend sözleşmesidir:

```ts
{
  token: string;
  ad: string;
  soyad: string;
  rol: "PERSONEL" | "YONETICI" | "HR" | "ADMIN";
}
```

- [ ] **Step 4: İlk parola çağrısını ekle**

```js
export async function ilkParolayiDegistir({
  kullaniciAdi,
  mevcutParola,
  yeniParola,
}) {
  let response;

  try {
    response = await fetch(`${BASE_URL}/api/auth/ilk-parola`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ kullaniciAdi, mevcutParola, yeniParola }),
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
      "Parola değiştirilemedi. Bilgilerinizi kontrol edip tekrar deneyin.",
    );
  }
}
```

Endpoint `204 No Content` döndürdüğü için başarılı response JSON olarak okunmaz.

- [ ] **Step 5: Yerel oturum temizliğini tek fonksiyona çıkar**

```js
export function clearStoredSession() {
  localStorage.removeItem("token");
  localStorage.removeItem("kullanici");
}
```

Mevcut `apiFetch` refresh-failure bloğundaki iki `removeItem` satırını `clearStoredSession()` çağrısıyla değiştir. `sessionStorage.authMesaji` ve `window.location.href = "/signin"` davranışlarını koru.

- [ ] **Step 6: Logout’u ağ hatasında da yerel temizliği garanti edecek şekilde değiştir**

```js
export async function logout() {
  try {
    await fetch(`${BASE_URL}/api/auth/logout`, {
      method: "POST",
      credentials: "include",
    });
  } finally {
    clearStoredSession();
  }
}
```

Bu davranış backend logout’a ulaşmaya çalışır; ağ hatası local çıkışı engellemez. Hata üst katmana çıkabilir fakat hassas ayrıntı gösterilmez.

- [ ] **Step 7: Lint ile mekanik doğrulama yap**

Run:

```powershell
npm run lint
```

Expected: Task 1’in eklediği koddan kaynaklanan yeni ESLint hatası yok.

---

### Task 2: Minimal AuthContext ve provider

**Files:**
- Create: `frontend/src/context/AuthContext.tsx`
- Modify: `frontend/src/main.tsx`

**Why the existing file changes:** `main.tsx`, context’in sidebar, guard, login ve kullanıcı menüsünün tamamına erişebilmesi için uygulama kökünde provider eklenen tek noktadır.

**Interfaces:**
- Consumes: `login`, `logout`, `clearStoredSession` from `src/lib/api.js`
- Produces: `AuthProvider`, `useAuth()`, `AuthUser`, `SignInCredentials`

- [ ] **Step 1: AuthContext tiplerini ve güvenli başlangıç okumasını oluştur**

`frontend/src/context/AuthContext.tsx`:

```tsx
import {
  createContext,
  ReactNode,
  useCallback,
  useContext,
  useMemo,
  useState,
} from "react";
import {
  clearStoredSession,
  login,
  logout,
} from "../lib/api";

export type AuthRole = "PERSONEL" | "YONETICI" | "HR" | "ADMIN";

export type AuthUser = {
  ad: string;
  soyad: string;
  rol: AuthRole;
};

export type SignInCredentials = {
  kullaniciAdi: string;
  parola: string;
};

type AuthContextValue = {
  kullanici: AuthUser | null;
  isAuthenticated: boolean;
  signIn: (credentials: SignInCredentials) => Promise<AuthUser>;
  signOut: () => Promise<void>;
  clearSession: () => void;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

const roller: AuthRole[] = ["PERSONEL", "YONETICI", "HR", "ADMIN"];

function baslangicKullanicisiniOku(): AuthUser | null {
  const token = localStorage.getItem("token");
  const rawKullanici = localStorage.getItem("kullanici");

  if (!token || !rawKullanici) {
    clearStoredSession();
    return null;
  }

  try {
    const parsed = JSON.parse(rawKullanici);
    const gecerliMi =
      typeof parsed.ad === "string" &&
      typeof parsed.soyad === "string" &&
      roller.includes(parsed.rol);

    if (!gecerliMi) {
      clearStoredSession();
      return null;
    }

    return parsed as AuthUser;
  } catch {
    clearStoredSession();
    return null;
  }
}
```

Token ve kullanıcı anahtarlarından yalnız biri varsa ikisi de temizlenir; yarım oturum state’i oluşturulmaz.

- [ ] **Step 2: Provider işlemlerini ekle**

Aynı dosyada:

```tsx
export function AuthProvider({ children }: { children: ReactNode }) {
  const [kullanici, setKullanici] = useState<AuthUser | null>(
    baslangicKullanicisiniOku,
  );

  const clearSession = useCallback(() => {
    clearStoredSession();
    setKullanici(null);
  }, []);

  const signIn = useCallback(async (credentials: SignInCredentials) => {
    const data = await login(credentials);
    const yeniKullanici: AuthUser = {
      ad: data.ad,
      soyad: data.soyad,
      rol: data.rol,
    };

    localStorage.setItem("token", data.token);
    localStorage.setItem("kullanici", JSON.stringify(yeniKullanici));
    setKullanici(yeniKullanici);
    return yeniKullanici;
  }, []);

  const signOut = useCallback(async () => {
    try {
      await logout();
    } catch {
      // Ağ hatası çıkışı engellemez; kullanıcıya ham hata gösterilmez.
    } finally {
      clearSession();
    }
  }, [clearSession]);

  const value = useMemo(
    () => ({
      kullanici,
      isAuthenticated:
        kullanici !== null && localStorage.getItem("token") !== null,
      signIn,
      signOut,
      clearSession,
    }),
    [clearSession, kullanici, signIn, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error("useAuth yalnız AuthProvider içinde kullanılabilir.");
  }

  return context;
}
```

- [ ] **Step 3: Provider’ı uygulama köküne ekle**

`frontend/src/main.tsx`:

```tsx
import { AuthProvider } from "./context/AuthContext.tsx";
```

Render ağacını şu sırayla güncelle:

```tsx
<StrictMode>
  <ThemeProvider>
    <AuthProvider>
      <AppWrapper>
        <App />
      </AppWrapper>
    </AuthProvider>
  </ThemeProvider>
</StrictMode>
```

`AuthProvider` router hook’u kullanmadığı için `BrowserRouter` dışında bulunabilir.

- [ ] **Step 4: Lint ve build ile context sınırını doğrula**

Run:

```powershell
npm run lint
npm run build
```

Expected: TypeScript `AuthContext` tipleri ve JS importları derlenir; provider eksikliği hatası oluşmaz.

---

### Task 3: Guard ve kullanıcı menüsünü context’e bağla

**Files:**
- Modify: `frontend/src/components/ProtectedRoute.jsx`
- Modify: `frontend/src/components/PublicRoute.jsx`
- Modify: `frontend/src/components/header/UserDropdown.tsx`

**Why these existing components change:** Üç component aynı oturum bilgisini ayrı ayrı `localStorage` üzerinden okuyor. Reaktif auth yaklaşımının ürün davranışına yansıması için guard’lar ve header tek `AuthContext` state’ini kullanmalıdır. `UserDropdown` ayrıca ağ hatasında takılmayan logout’un görünen giriş noktasıdır.

**Interfaces:**
- Consumes: `useAuth()` from `src/context/AuthContext.tsx`
- Preserves: `ProtectedRoute` ve `PublicRoute` yönlendirme yolları

- [ ] **Step 1: ProtectedRoute’u context ile güncelle**

```jsx
import { Navigate } from "react-router";
import { useAuth } from "../context/AuthContext";

function ProtectedRoute({ children }) {
  const { isAuthenticated } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/signin" replace />;
  }

  return children;
}

export default ProtectedRoute;
```

Bu guard yalnız kullanıcı deneyimidir; backend yetkilendirme sorumluluğu değişmez.

- [ ] **Step 2: PublicRoute’u context ile güncelle**

```jsx
import { Navigate } from "react-router";
import { useAuth } from "../context/AuthContext";

function PublicRoute({ children }) {
  const { isAuthenticated } = useAuth();

  if (isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  return children;
}

export default PublicRoute;
```

- [ ] **Step 3: UserDropdown kullanıcı bilgisini context’ten al**

Doğrudan `localStorage` okumasını ve `logout` importunu kaldır. Şunları kullan:

```tsx
import { useAuth } from "../../context/AuthContext";

const [isSigningOut, setIsSigningOut] = useState(false);
const { kullanici, signOut } = useAuth();
const adSoyad = kullanici
  ? `${kullanici.ad} ${kullanici.soyad}`
  : "Kullanıcı";
```

Rol gösterimini:

```tsx
{kullanici?.rol || ""}
```

olarak güncelle.

- [ ] **Step 4: Logout çift gönderimini ve ağ hatası takılmasını engelle**

```tsx
async function handleSignOut() {
  if (isSigningOut) {
    return;
  }

  setIsSigningOut(true);
  try {
    await signOut();
  } finally {
    closeDropdown();
    navigate("/signin", { replace: true });
  }
}
```

Çıkış butonuna:

```tsx
disabled={isSigningOut}
```

ekle ve metni işlem sırasında `"Çıkış yapılıyor..."` olarak göster.

- [ ] **Step 5: Profil bağlantısını gerçek davranışına göre adlandır**

Mevcut `/profile` yolu değişmez. Yalnız İngilizce ve düzenleme varmış izlenimi veren:

```text
Edit profile
```

metnini:

```text
Profilim
```

olarak değiştir. Backend’de profil update olmadığı için “Düzenle” ifadesi kullanılmaz.

- [ ] **Step 6: Lint ve build çalıştır**

Run:

```powershell
npm run lint
npm run build
```

Expected: JS guard’lar TS context export’unu kullanabilir; dropdown null-safe render edilir.

---

### Task 4: Login formunu ortak auth akışına bağla

**Files:**
- Modify: `frontend/src/components/auth/SignInForm.tsx`

**Why this existing component changes:** Mevcut form hard-coded fetch, doğrudan storage yazımı, kontrolsüz ağ hatası ve çift submit içeriyor; ayrıca backend’in `428` cevabını yalnız hata metni olarak gösteriyor. Yeni ortak auth ve ilk parola akışının giriş noktası olduğu için bu component dönüştürülmelidir.

**Interfaces:**
- Consumes: `useAuth().signIn`, `ApiError`, `useNavigate`
- Produces: `/` başarı yönlendirmesi ve `/ilk-parola` için `{ kullaniciAdi }` route state

- [ ] **Step 1: State ve importları güncelle**

`Checkbox` importunu ve işlevsiz `isChecked` state’ini kaldır. Şunları ekle:

```tsx
import { ApiError } from "../../lib/api";
import { useAuth } from "../../context/AuthContext";

const [isSubmitting, setIsSubmitting] = useState(false);
const [bilgi, setBilgi] = useState(() => {
  const mesaj = sessionStorage.getItem("authMesaji") || "";
  sessionStorage.removeItem("authMesaji");
  return mesaj;
});
const { signIn } = useAuth();
```

“Beni hatırla” checkbox’ı davranış üretmediği ve token zaten mevcut tasarımda localStorage’da tutulduğu için kaldırılır. Kullanıcıya uygulanmayan bir seçenek gösterilmez.

- [ ] **Step 2: Submit akışını ortak signIn ile değiştir**

```tsx
async function handleSubmit(e: FormEvent) {
  e.preventDefault();

  if (isSubmitting) {
    return;
  }

  setHata("");
  setBilgi("");
  setIsSubmitting(true);

  try {
    await signIn({ kullaniciAdi, parola });
    navigate("/", { replace: true });
  } catch (error) {
    if (error instanceof ApiError && error.status === 428) {
      navigate("/ilk-parola", {
        replace: true,
        state: { kullaniciAdi },
      });
      return;
    }

    setHata(
      error instanceof ApiError
        ? error.message
        : "Giriş sırasında beklenmeyen bir hata oluştu.",
    );
  } finally {
    setIsSubmitting(false);
  }
}
```

Parola route state veya storage’a yazılmaz.

- [ ] **Step 3: Kullanıcı mesajlarını ve submit kilidini render et**

Hata alanından önce:

```tsx
{bilgi && <p className="text-sm text-success-600">{bilgi}</p>}
```

Hata alanını koru. Submit butonunu:

```tsx
<Button className="w-full" size="sm" disabled={isSubmitting}>
  {isSubmitting ? "Giriş yapılıyor..." : "Giriş yap"}
</Button>
```

olarak güncelle.

Kullanıcı adı ve parola inputlarına `disabled={isSubmitting}` ekle. `handleSubmit` içindeki erken dönüş, Enter ile ikinci submit’i de engeller.

- [ ] **Step 4: Lint ve build çalıştır**

Run:

```powershell
npm run lint
npm run build
```

Expected: Eski doğrudan fetch/storage kodu kalmaz; `ApiError.status` ile 428 ayrımı derlenir.

---

### Task 5: İlk parola formu ve public route

**Files:**
- Create: `frontend/src/components/auth/IlkParolaForm.tsx`
- Create: `frontend/src/pages/AuthPages/IlkParola.tsx`
- Modify: `frontend/src/App.tsx`

**Why the existing file changes:** `App.tsx`, backend’in login sırasında ürettiği `428` cevabının götüreceği gerçek public route’u tanımlamak zorundadır. Mevcut diğer route’lara veya demo sayfalara dokunulmaz.

**Interfaces:**
- Consumes: `ilkParolayiDegistir(payload)`, optional `location.state.kullaniciAdi`
- Produces: `PATCH /api/auth/ilk-parola`, başarı sonrası `/signin`

- [ ] **Step 1: İlk parola form state’ini oluştur**

`frontend/src/components/auth/IlkParolaForm.tsx` dosyasının temel yapısı:

```tsx
import { FormEvent, useState } from "react";
import { useLocation, useNavigate } from "react-router";
import { ApiError, ilkParolayiDegistir } from "../../lib/api";
import Label from "../form/Label";
import Input from "../form/input/InputField";
import Button from "../ui/button/Button";

type IlkParolaLocationState = {
  kullaniciAdi?: string;
};

export default function IlkParolaForm() {
  const location = useLocation();
  const navigate = useNavigate();
  const state = location.state as IlkParolaLocationState | null;

  const [kullaniciAdi, setKullaniciAdi] = useState(
    state?.kullaniciAdi || "",
  );
  const [mevcutParola, setMevcutParola] = useState("");
  const [yeniParola, setYeniParola] = useState("");
  const [yeniParolaTekrar, setYeniParolaTekrar] = useState("");
  const [hata, setHata] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
```

Route state kaybolursa kullanıcı adı inputtan tekrar girilebilir. Hiçbir parola route state’e alınmaz.

- [ ] **Step 2: Submit davranışını ekle**

```tsx
  async function handleSubmit(e: FormEvent) {
    e.preventDefault();

    if (isSubmitting) {
      return;
    }

    setHata("");

    if (yeniParola !== yeniParolaTekrar) {
      setHata("Yeni parola ve parola tekrarı eşleşmiyor.");
      return;
    }

    setIsSubmitting(true);

    try {
      await ilkParolayiDegistir({
        kullaniciAdi,
        mevcutParola,
        yeniParola,
      });
      sessionStorage.setItem(
        "authMesaji",
        "Parolanız değiştirildi. Yeni parolanızla giriş yapabilirsiniz.",
      );
      navigate("/signin", { replace: true });
    } catch (error) {
      setHata(
        error instanceof ApiError
          ? error.message
          : "Parola değiştirilirken beklenmeyen bir hata oluştu.",
      );
    } finally {
      setIsSubmitting(false);
    }
  }
```

Backend’in parola politikası frontend’de kopyalanmaz. Frontend yalnız tekrar alanı eşleşmesini kontrol eder.

- [ ] **Step 3: Formu mevcut TailAdmin alanlarıyla render et**

Form en az şu alanları içerir:

```tsx
<form onSubmit={handleSubmit}>
  <Label>Kullanıcı adı</Label>
  <Input
    value={kullaniciAdi}
    onChange={(e) => setKullaniciAdi(e.target.value)}
    disabled={isSubmitting}
    required
  />

  <Label>Mevcut parola</Label>
  <Input
    type="password"
    value={mevcutParola}
    onChange={(e) => setMevcutParola(e.target.value)}
    disabled={isSubmitting}
    required
  />

  <Label>Yeni parola</Label>
  <Input
    type="password"
    value={yeniParola}
    onChange={(e) => setYeniParola(e.target.value)}
    disabled={isSubmitting}
    required
  />

  <Label>Yeni parola tekrarı</Label>
  <Input
    type="password"
    value={yeniParolaTekrar}
    onChange={(e) => setYeniParolaTekrar(e.target.value)}
    disabled={isSubmitting}
    required
  />

  {hata && <p className="text-sm text-error-500">{hata}</p>}

  <Button className="w-full" size="sm" disabled={isSubmitting}>
    {isSubmitting ? "Parola değiştiriliyor..." : "Parolayı değiştir"}
  </Button>
</form>
```

TailAdmin spacing ve auth sayfasındaki mobil/masaüstü genişlik sınıfları `SignInForm` ile aynı kalıba göre uygulanır.

- [ ] **Step 4: Route sayfasını oluştur**

`frontend/src/pages/AuthPages/IlkParola.tsx`:

```tsx
import PageMeta from "../../components/common/PageMeta";
import IlkParolaForm from "../../components/auth/IlkParolaForm";
import AuthLayout from "./AuthPageLayout";

export default function IlkParola() {
  return (
    <>
      <PageMeta
        title="İlk Parola Değişikliği | JForce Intranet"
        description="İlk giriş parolanızı güvenli biçimde değiştirin."
      />
      <AuthLayout>
        <IlkParolaForm />
      </AuthLayout>
    </>
  );
}
```

- [ ] **Step 5: Public route’u App.tsx içine ekle**

Import:

```tsx
import IlkParola from "./pages/AuthPages/IlkParola.tsx";
```

`/signin` route’unun yanında:

```tsx
<Route
  path="/ilk-parola"
  element={
    <PublicRoute>
      <IlkParola />
    </PublicRoute>
  }
/>
```

428 login cevabında token üretilmediği için `PublicRoute` ilk parola ekranını gösterir.

- [ ] **Step 6: Lint ve production build çalıştır**

Run:

```powershell
npm run lint
npm run build
```

Expected: Yeni route ve form TypeScript hatası olmadan production bundle’a girer.

---

### Task 6: Faz 1 tarayıcı doğrulaması

**Files:**
- No source changes expected

**Interfaces:**
- Consumes: Task 1–5 çıktıları ve çalışan backend/frontend
- Produces: Kullanıcı gözüyle doğrulanmış Faz 1 kontrol listesi

- [ ] **Step 1: Backend ve frontend’in erişilebilir olduğunu doğrula**

Beklenen geliştirme adresleri:

```text
Backend:  http://localhost:8080
Frontend: http://localhost:5173
```

Servisler çalışmıyorsa kullanıcıya durum bildirilir; izinsiz dependency veya ortam değişikliği yapılmaz.

- [ ] **Step 2: Başarılı login akışını kontrol et**

Kontrol listesi:

```text
- Geçerli normal kullanıcıyla POST /api/auth/login -> 200
- Buton istek sırasında disabled
- Aynı login isteği ikinci kez gönderilmiyor
- Başarı sonrası URL /
- Header ad, soyad ve rolü yenileme gerektirmeden gösteriyor
- /signin doğrudan açıldığında PublicRoute / adresine yönlendiriyor
```

- [ ] **Step 3: Login hata durumlarını kontrol et**

```text
- Yanlış credential -> kontrollü backend mesajı
- Backend kapalı/ağ hatası -> sabit bağlantı mesajı
- Ham exception, token veya response nesnesi görünmüyor
- Hata sonrasında form tekrar gönderilebiliyor
```

- [ ] **Step 4: İlk parola akışını kontrol et**

```text
- parolaDegistirmeliMi=true kullanıcı login -> 428
- URL /ilk-parola
- Kullanıcı adı önceden dolu, parola taşınmamış
- Eşleşmeyen yeni parola tekrarında API çağrısı yok
- Başarılı PATCH /api/auth/ilk-parola -> 204
- Başarı sonrası /signin
- Sign-in ekranı başarı mesajını bir kez gösteriyor
- Yeni parolayla normal login -> 200 ve /
```

- [ ] **Step 5: Sessiz refresh single-flight davranışını kontrol et**

Access token süresi dolmuş veya kontrollü biçimde geçersiz token senaryosunda:

```text
- Paralel authenticated istekler 401 aldığında tek refresh isteği oluşuyor
- Refresh başarılıysa her orijinal istek yalnız bir kez retry ediliyor
- Yeni access token localStorage'a yazılıyor
```

Bu kontrol mevcut algoritmanın context değişikliğinde bozulmadığını doğrular.

- [ ] **Step 6: Refresh başarısızlığı ve logout’u kontrol et**

```text
- Geçersiz/olmayan refresh cookie -> local token ve kullanıcı temizleniyor
- /signin yönlendirmesi oluyor
- Backend kontrollü oturum mesajı sign-in ekranında bir kez gösteriliyor
- Logout başarılıysa backend cookie temizliği ve local temizlik oluyor
- Logout sırasında ağ hatası simüle edilirse local token/kullanıcı yine temizleniyor
- Çıkış butonuna hızlı çift tıklama ikinci logout isteği üretmiyor
```

- [ ] **Step 7: Mobil ve masaüstü auth düzenini kontrol et**

```text
- 375px genişlikte sign-in ve ilk parola formu taşmıyor
- Masaüstünde AuthPageLayout iki kolon yapısı korunuyor
- Disabled buton ve input durumları görsel ve davranışsal
- Hata/başarı mesajları form alanlarını okunamaz hâle getirmiyor
```

- [ ] **Step 8: Son lint ve build kanıtını üret**

Run:

```powershell
npm run lint
npm run build
```

Expected: Her iki komut exit code `0`. Gerçek sonuç ve çalıştırılamayan manuel senaryolar kullanıcıya ayrı ayrı raporlanır.

---

## Phase 1 Completion Criteria

- Login component’ında hard-coded backend URL veya doğrudan localStorage yazımı kalmaz.
- `AuthContext` kullanıcı bilgisini guard, header ve login arasında reaktif paylaşır.
- Token’ın istemci kaynağı `localStorage`; refresh cookie’nin kaynağı backend olarak kalır.
- `apiFetch` sessiz refresh ve single-flight davranışı korunur.
- Login, logout ve ilk parola mutation’ları çift gönderime karşı kilitlidir.
- Login `428` cevabı `/ilk-parola` ekranına gider.
- İlk parola başarılı olduğunda kullanıcı yeni parolayla login olmak üzere `/signin` ekranına döner.
- Logout backend/ağ hatasından bağımsız olarak local oturumu temizler.
- Refresh başarısızlığı sign-in mesajını bir kez gösterir.
- `ProtectedRoute` ve `PublicRoute` mevcut yönlendirme anlamlarını context üzerinden sürdürür.
- `UserDropdown` kullanıcı ve rol bilgisini yenileme gerektirmeden gösterir.
- Yeni dependency veya test framework’ü eklenmez.
- `npm run lint` ve `npm run build` sonuçları doğrulanır.
- Çalıştırılabilen ve çalıştırılamayan tarayıcı senaryoları açıkça raporlanır.
