import type { EkipDiziniKisi } from "./ekipDizini";
import type { OdaDoluluk } from "./rezervasyon";

export type DogumGunuOgesi = {
    adSoyad: string;
    departman: string;
    profilFoto: string | null;
};

export type IzinBakiyesi = {
    toplamHak: number;
    kullanilan: number;
    onayBekleyen: number;
    kalan: number;
};

/**
 * GET /api/dashboard/ozet response'undan dashboard'da kullanılan alanlar.
 */
export type DashboardOzeti = {
    bugunDogumGunleri: DogumGunuOgesi[];
    aktifEkipUyesiSayisi: number;
    ekipDiziniOzeti: EkipDiziniKisi[];
    izinBakiyesi: IzinBakiyesi | null;
    odaDoluluk: OdaDoluluk[];
};
