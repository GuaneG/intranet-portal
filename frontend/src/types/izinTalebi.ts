export type IzinTalepDurumu =
    | "ONAY_BEKLIYOR"
    | "ONAYLANDI"
    | "REDDEDILDI"
    | "IPTAL_EDILDI";

export type IzinTuru = {
    id: number;
    ad: string;
    yillikHaktanDuserMi: boolean;
};

export type IzinTalebi = {
    id: string;
    izinTuru: string;
    baslangicTarihi: string;
    bitisTarihi: string;
    isGunuSayisi: number;
    yoneticiyeNot: string | null;
    durum: IzinTalepDurumu;
    durumAdi: string;
    talepTarihi: string;
    kararNotu: string | null;
    islemTarihi: string | null;
    kararVeren: KararVeren | null;
};

export type KararVeren = {
    ad: string;
    soyad: string;
};

export type IzinTalepSahibi = {
    ad: string;
    soyad: string;
    departman: string;
    profilFoto: string | null;
    aktifMi: boolean;
};

export type KararBekleyenIzinTalebi = IzinTalebi & {
    talepSahibi: IzinTalepSahibi;
};

export type IzinTalebiOlusturmaRequest = {
    izinTuruId: number;
    baslangicTarihi: string;
    bitisTarihi: string;
    yoneticiyeNot: string | null;
};

export type IzinTalepleriSayfasi = {
    icerik: IzinTalebi[];
    sayfa: number;
    sayfaBoyutu: number;
    toplamKayit: number;
    toplamSayfa: number;
};

export type KararBekleyenIzinTalepleriSayfasi = Omit<IzinTalepleriSayfasi, "icerik"> & {
    icerik: KararBekleyenIzinTalebi[];
};

export type IzinTalepleriFiltreleri = {
    page: number;
    size: number;
    durum?: IzinTalepDurumu;
};

export type IzinTalebiFormu = {
    izinTuruId: string;
    baslangicTarihi: string;
    bitisTarihi: string;
    yoneticiyeNot: string;
};
