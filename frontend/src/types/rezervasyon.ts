export type Oda = {
    id: number;
    ad: string;
    kapasite: number;
};

export type RezervasyonOlusturan = {
    ad: string;
    soyad: string;
};

export type Rezervasyon = {
    id: string;
    oda: Oda;
    tarih: string;
    baslangicSaat: number;
    bitisSaat: number;
    olusturan: RezervasyonOlusturan;
    benMiOlusturdum: boolean;
    baslik: string | null;
    katilimciSayisi: number | null;
};

export type RezervasyonDurumu = "AKTIF" | "IPTAL_EDILDI";

export type RezervasyonOlusturmaIstek = {
    odaId: number;
    tarih: string;
    baslangicSaat: number;
    bitisSaat: number;
    baslik: string;
    katilimciSayisi: number;
};

export type RezervasyonGecmisi = Omit<Rezervasyon, "benMiOlusturdum"> & {
    durum: RezervasyonDurumu;
    gecmisMi: boolean;
    olusturmaZamani: string;
    iptalZamani: string | null;
    iptalEden: RezervasyonOlusturan | null;
    iptalNedeni: string | null;
};

export type RezervasyonGecmisiSayfasi = {
    icerik: RezervasyonGecmisi[];
    sayfa: number;
    sayfaBoyutu: number;
    toplamKayit: number;
    toplamSayfa: number;
};

export type OdaDoluAralik = {
    baslangicSaat: number;
    bitisSaat: number;
};

export type OdaDoluluk = {
    odaId: number;
    odaAd: string;
    kapasite: number;
    anlikDurum: "MUSAIT" | "DOLU" | "KAPALI";
    dolulukYuzdesi: number;
    doluAraliklar: OdaDoluAralik[];
};
