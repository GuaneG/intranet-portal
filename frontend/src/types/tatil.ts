export type TatilKategori =
    | "RESMI_SABIT"
    | "RESMI_DEGISKEN"
    | "SIRKET";

export type TatilGunSuresi = "TAM_GUN" | "YARIM_GUN";

export type Tatil = {
    tatilId: number;
    ad: string;
    tarih: string;
    bitisTarihi: string;
    kategori: TatilKategori;
    gunSuresi: TatilGunSuresi;
};

export type TatilKaydetRequest = Omit<Tatil, "tatilId">;

/** Takvim yılına düşen doğum günü occurrence'ı; personel kimliği içermez. */
export type DogumGunu = {
    adSoyad: string;
    tarih: string;
    departman: string;
};

export type IsGunuOnizlemeResponse = {
    isGunuSayisi: number;
};
