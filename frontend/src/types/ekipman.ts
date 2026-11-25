export type EkipmanTalepDurumu =
    | "ONAY_BEKLIYOR"
    | "ONAYLANDI"
    | "REDDEDILDI"
    | "IPTAL_EDILDI";

export type EnvanterDurumu = "DEPODA" | "PERSONELDE" | "KULLANIM_DISI";

export type EkipmanTipi = {
    id: number;
    ad: string;
};

export type Zimmet = {
    id: string;
    ekipmanTipiId: number;
    ekipmanTipi: string;
    marka: string;
    model: string | null;
    seriNo: string;
    teslimTarihi: string;
    iadeTarihi: string | null;
};

export type EkipmanTalepZimmeti = {
    id: string;
    ekipmanId: string;
    marka: string;
    model: string | null;
    seriNo: string;
    teslimTarihi: string;
    iadeTarihi: string | null;
};

export type EkipmanTalebi = {
    id: string;
    ekipmanTipiId: number;
    ekipmanTipi: string;
    aciklama: string | null;
    durum: EkipmanTalepDurumu;
    durumAdi: string;
    talepTarihi: string;
    kararNotu: string | null;
    islemTarihi: string | null;
    kararVeren: { ad: string; soyad: string } | null;
    zimmet: EkipmanTalepZimmeti | null;
};

export type EkipmanTalepSahibi = {
    ad: string;
    soyad: string;
    departman: string | null;
    profilFoto: string | null;
    aktifMi: boolean;
};

export type KararEkipmanTalebi = EkipmanTalebi & {
    talepSahibi: EkipmanTalepSahibi;
};

export type UygunEkipman = {
    id: string;
    marka: string;
    model: string | null;
    seriNo: string;
};

export type AdminEkipmanTipi = EkipmanTipi & { aktifMi: boolean };

export type EnvanterEkipmani = {
    id: string;
    ekipmanTipiId: number;
    ekipmanTipi: string;
    marka: string;
    model: string | null;
    seriNo: string;
    durum: EnvanterDurumu;
    kullanimDisiNedeni: string | null;
    acikZimmetVarMi: boolean;
};

export type AdminZimmet = Zimmet & {
    personel: { ad: string; soyad: string; departman: string | null; profilFoto: string | null };
};

export type EkipmanTalebiOlusturmaIstek = {
    ekipmanTipiId: number;
    aciklama: string | null;
};

export type SayfaliEkipmanTalepResponse = {
    icerik: EkipmanTalebi[];
    sayfa: number;
    sayfaBoyutu: number;
    toplamKayit: number;
    toplamSayfa: number;
};

export type SayfaliKararEkipmanTalepResponse = {
    icerik: KararEkipmanTalebi[];
    sayfa: number;
    sayfaBoyutu: number;
    toplamKayit: number;
    toplamSayfa: number;
};

export type SayfaliUygunEkipmanResponse = {
    icerik: UygunEkipman[];
    sayfa: number;
    sayfaBoyutu: number;
    toplamKayit: number;
    toplamSayfa: number;
};

export type SayfaliAdminEkipmanTipiResponse = {
    icerik: AdminEkipmanTipi[];
    sayfa: number;
    sayfaBoyutu: number;
    toplamKayit: number;
    toplamSayfa: number;
};

export type SayfaliEkipmanResponse = {
    icerik: EnvanterEkipmani[];
    sayfa: number;
    sayfaBoyutu: number;
    toplamKayit: number;
    toplamSayfa: number;
};

export type SayfaliAdminZimmetResponse = {
    icerik: AdminZimmet[];
    sayfa: number;
    sayfaBoyutu: number;
    toplamKayit: number;
    toplamSayfa: number;
};

export type SayfaliZimmetResponse = {
    icerik: Zimmet[];
    sayfa: number;
    sayfaBoyutu: number;
    toplamKayit: number;
    toplamSayfa: number;
};

export type EkipmanTalebiFormu = {
    ekipmanTipiId: string;
    aciklama: string;
};
