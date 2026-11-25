export type ProfilRolu = "ADMIN" | "PERSONEL" | "YONETICI" | "HR";

export type ProfilYetenegi = {
    id: number;
    ad: string;
};

export type ProfilBilgileri = {
    kullaniciAdi: string;
    ad: string;
    soyad: string;
    dogumTarihi: string;
    ePosta: string;
    sicilNo: number;
    departman: string;
    rol: ProfilRolu;
    yonetici?: string;
    yillikIzinHakki: number;
    profilFoto: string;
    kalanIzin: number | null;
    mezunOkul: string | null;
    mezunBolum: string | null;
    telNo: string | null;
    isGirisTarihi: string;
    yetenekler: ProfilYetenegi[];
};

export type ProfilGuncellemeRequest = {
    telNo?: string | null;
    mezunOkul?: string | null;
    mezunBolum?: string | null;
};