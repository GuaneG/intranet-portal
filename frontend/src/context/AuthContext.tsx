import {
    createContext,
    type ReactNode,
    useCallback,
    useContext,
    useMemo,
    useState,
} from "react";
import { clearStoredSession, login, logout } from "../lib/api";

// Uygulamanın desteklediği sabit kullanıcı rolleri.
// Backend login response'undaki rol bu değerlerden biri olmalıdır.
export type AuthRole = "PERSONEL" | "YONETICI" | "HR" | "ADMIN";

// React tarafında oturum açmış kullanıcı için saklanan bilgiler.
// Parola veya token bu nesnenin içinde tutulmaz.
export type AuthUser = {
    ad: string;
    soyad: string;
    rol: AuthRole;
};

// Giriş formunun AuthContext'e gönderdiği bilgiler.
export type SignInCredentials = {
    kullaniciAdi: string;
    parola: string;
};

// AuthContext'i kullanan component'lara sunulacak değer ve fonksiyonlar.
type AuthContextValue = {
    kullanici: AuthUser | null;
    isAuthenticated: boolean;
    signIn: (credentials: SignInCredentials) => Promise<AuthUser>;
    signOut: () => Promise<void>;
    clearSession: () => void;
};

// Başlangıçta undefined kullanıyoruz.
// Böylece useAuth yanlışlıkla AuthProvider dışında kullanılırsa bunu anlayabiliriz.
const AuthContext = createContext<AuthContextValue | undefined>(undefined);

// localStorage içinden okunan rolün gerçekten desteklenen bir rol
// olup olmadığını kontrol etmek için kullanılır.
const roller: AuthRole[] = ["PERSONEL", "YONETICI", "HR", "ADMIN"];

function baslangicKullanicisiniOku(): AuthUser | null {
    // Uygulama yeniden açıldığında mevcut oturum bilgilerini okuyoruz.
    const token = localStorage.getItem("token");
    const rawKullanici = localStorage.getItem("kullanici");

    // Token veya kullanıcı bilgisinden biri eksikse oturum tutarsızdır.
    // Kalan oturum verilerini temizleyip kullanıcıyı giriş yapmamış sayıyoruz.
    if (!token || !rawKullanici) {
        clearStoredSession();
        return null;
    }

    try {
        // localStorage metin sakladığı için kullanıcı bilgisini JSON'a çeviriyoruz.
        const parsed = JSON.parse(rawKullanici);

        // Saklanan verinin beklediğimiz kullanıcı yapısına uyup uymadığını kontrol ediyoruz.
        const gecerliMi =
            typeof parsed.ad === "string" &&
            typeof parsed.soyad === "string" &&
            roller.includes(parsed.rol);

        // Veri bozuk veya rol geçersizse oturumu temizliyoruz.
        if (!gecerliMi) {
            clearStoredSession();
            return null;
        }

        return parsed as AuthUser;
    } catch {
        // JSON okunamazsa localStorage kaydı bozulmuş demektir.
        clearStoredSession();
        return null;
    }
}

export function AuthProvider({ children }: { children: ReactNode }) {
    // Sayfa ilk açıldığında kullanıcı state'i localStorage üzerinden hazırlanır.
    // Fonksiyonu doğrudan vermek, okuma işleminin yalnız ilk render'da çalışmasını sağlar.
    const [kullanici, setKullanici] = useState<AuthUser | null>(
        baslangicKullanicisiniOku,
    );

    // Yerel oturum bilgilerini hem localStorage'dan hem React state'inden temizler.
    const clearSession = useCallback(() => {
        clearStoredSession();
        setKullanici(null);
    }, []);

    // Backend login endpoint'ini çağırır.
    const signIn = useCallback(async (credentials: SignInCredentials) => {
        const data = await login(credentials);

        // Backend response'undan yalnız frontend oturumu için gerekli
        // güvenli kullanıcı bilgilerini alıyoruz.
        const yeniKullanici: AuthUser = {
            ad: data.ad,
            soyad: data.soyad,
            rol: data.rol,
        };

        // Access token mevcut API altyapısının kullandığı anahtara yazılır.
        // Parola hiçbir yerde saklanmaz.
        localStorage.setItem("token", data.token);
        localStorage.setItem("kullanici", JSON.stringify(yeniKullanici));

        // State güncellenince AuthContext kullanan component'lar yeniden render olur.
        setKullanici(yeniKullanici);

        return yeniKullanici;
    }, []);

    // Önce backend logout isteğini dener.
    const signOut = useCallback(async () => {
        try {
            await logout();
        } catch {
            // Ağ hatası çıkışı engellemez.
            // Kullanıcıya ham backend veya bağlantı hatası gösterilmez.
        } finally {
            // Backend isteği başarılı veya başarısız olsa da
            // yerel oturumun temizlenmesi garanti edilir.
            clearSession();
        }
    }, [clearSession]);

    // Context'e verilecek nesneyi yalnız bağımlılıklar değiştiğinde yeniden oluşturur.
    const value = useMemo(
        () => ({
            kullanici,

            // Kullanıcının authenticated sayılması için hem geçerli kullanıcı
            // state'i hem de localStorage access token'ı bulunmalıdır.
            isAuthenticated:
                kullanici !== null && localStorage.getItem("token") !== null,

            signIn,
            signOut,
            clearSession,
        }),
        [clearSession, kullanici, signIn, signOut],
    );

    // AuthProvider altındaki bütün component'lar bu oturum bilgilerine erişebilir.
    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// Component'ların AuthContext'e güvenli ve kısa şekilde erişmesini sağlayan hook.
export function useAuth() {
    const context = useContext(AuthContext);

    // Hook, AuthProvider dışında kullanılırsa geliştirme sırasında açık hata verir.
    if (!context) {
        throw new Error("useAuth yalnız AuthProvider içinde kullanılabilir.");
    }

    return context;
}