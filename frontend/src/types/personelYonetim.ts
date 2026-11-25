import type { AuthRole } from "../context/AuthContext";


/**
 * Yönetim listesindeki tek personel kaydı.
 * Alanlar backend response ile bire birdir.
 */
export type PersonelYonetimListeOgesi = {
    personelId: string;
    ad: string;
    soyad: string;
    kullaniciAdi: string;
    profilFoto: string | null;
    rol: AuthRole;
    departmanId: number;
    departmanAdi: string;
    aktif: boolean;
};

/**
 * Departman gibi seçim listelerinin ortak yapısı.
 */
export type LookupSecenegi = {
    id: number;
    ad: string;
};

/**
 * Backend’den gelen sayfalanmış personel listesi.
 */
export type PersonelYonetimSayfasi = {
    icerik: PersonelYonetimListeOgesi[];
    sayfa: number;
    sayfaBoyutu: number;
    toplamKayit: number;
    toplamSayfa: number;
};

/**
 * Personel listesi endpoint’ine gönderilecek filtreler.
 * page ve size zorunlu, diğerleri opsiyoneldir.
 */
export type PersonelYonetimFiltreleri = {
    page: number;
    size: number;
    arama?: string;
    departmanId?: number;
    rol?: AuthRole;
    aktif?: boolean;
};

/**
 * Tek personelin yönetim detay ekranında kullanılan bilgileri.
 * Alanlar GET /api/hr/personel/{personelId} response'u ile bire birdir.
 */
export type PersonelYonetimDetayi = {
    personelId: string;
    kullaniciAdi: string;
    ad: string;
    soyad: string;
    profilFoto: string | null;
    dogumTarihi: string;
    ePosta: string;
    departmanId: number;

    // Personelin atanmış yöneticisi yoksa backend null döndürür.
    yoneticiId: string | null;

    isGirisTarihi: string;

    // Bu alanlar personel kaydında doldurulmamış olabilir.
    telNo: string | null;
    mezunOkul: string | null;
    mezunBolum: string | null;
    mezuniyetYili: number | null;

    rol: AuthRole;
    aktif: boolean;
    yetenekler: LookupSecenegi[];
};

/**
 * Departmana göre getirilen atanabilir yönetici seçeneği.
 * Frontend bu kayıtları yoneticiId ile eşleştirerek ham UUID yerine ad gösterir.
 */
export type AtanabilirYoneticiSecenegi = {
    personelId: string;
    ad: string;
    soyad: string;
    kullaniciAdi: string;
};

/**
 * HR/Admin personel güncelleme PATCH isteğinde gönderilebilen alanlar.
 * Server-controlled alanlar (rol, aktiflik, kullanıcı adı vb.) burada bulunmaz.
 */
export type PersonelYonetimGuncellemeRequest = {
    ad?: string;
    soyad?: string;
    dogumTarihi?: string;
    ePosta?: string;
    departmanId?: number;
    isGirisTarihi?: string;
    yoneticiId?: string | null;
    telNo?: string | null;
    mezunOkul?: string | null;
    mezunBolum?: string | null;
    mezuniyetYili?: number | null;
};
/**
 * HR/Admin tarafından yeni personel oluştururken gönderilen alanlar.
 * Rol, parola ve diğer server-controlled alanlar request'te bulunmaz.
 */
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

/**
 * POST /api/hr/personel başarılı olduğunda backend'in döndürdüğü bilgiler.
 */
export type PersonelOlusturmaResponse = {
    personelId: string;
    kullaniciAdi: string;
};
