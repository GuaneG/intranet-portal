import type { LookupSecenegi } from "./personelYonetim";
import type { AuthRole } from "../context/AuthContext";

export type BugunIzinDurumu = "IZINLI" | "YARIM_GUN_IZINLI" | null;

/**
 * Ekip dizininde gösterilen personel alanları backend response'u ile bire birdir.
 * Kimlik ve iletişim alanları bu sözleşmede bilinçli olarak bulunmaz.
 */
export type EkipDiziniKisi = {
    ad: string;
    soyad: string;
    departman: string;
    profilFoto: string | null;
    yetenekler: LookupSecenegi[];
    yoneticinMi: boolean;
    bugunIzinDurumu: BugunIzinDurumu;
};

export type EkipDiziniSayfasi = {
    icerik: EkipDiziniKisi[];
    sayfa: number;
    sayfaBoyutu: number;
    toplamKayit: number;
    toplamSayfa: number;
};

export type EkipDiziniFiltreleri = {
    page: number;
    size: number;
    arama?: string;
    departmanId?: number;
    yetenekId?: number;
};

/** HR ve Admin'in görebildiği yönetici–çalışan hiyerarşisindeki tek düğüm. */
export type HiyerarsiDugumu = {
    ad: string;
    soyad: string;
    departman: string;
    rol: AuthRole;
    profilFoto: string | null;
    yoneticinMi: boolean;
    bugunIzinDurumu: BugunIzinDurumu;
    dogrudanBaglilar: HiyerarsiDugumu[];
};

export type HiyerarsiResponse = {
    kokler: HiyerarsiDugumu[];
};

/** Yöneticinin yalnız kendi güvenli hiyerarşi ağacına ait response. */
export type YoneticiHiyerarsisiResponse = HiyerarsiResponse & {
    aktorDepartman: LookupSecenegi;
};

/** Personel ve yönetici için en yakından başlayarak en fazla iki üst yönetici. */
export type YoneticiZinciriElemani = {
    seviye: number;
    ad: string;
    soyad: string;
    departman: string;
    rol: AuthRole;
    profilFoto: string | null;
};

export type YoneticiZinciriResponse = {
    yoneticiler: YoneticiZinciriElemani[];
};
