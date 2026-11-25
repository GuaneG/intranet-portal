export type AuditEylem = string;

export type AuditAktoru = {
    personelId: string | null;
    adSoyad: string | null;
    kullaniciAdi: string | null;
};

export type AuditHedefi = {
    personelId: string;
    adSoyad: string;
};

export type AuditKaydi = {
    auditId: string;
    eylemTipi: AuditEylem;
    olusmaZamani: string;
    aktor: AuditAktoru | null;
    hedef: AuditHedefi | null;
    detay: string;
};

export type AuditLogSayfasi = {
    icerik: AuditKaydi[];
    sayfa: number;
    sayfaBoyutu: number;
    toplamKayit: number;
    toplamSayfa: number;
};

export type AuditLogFiltreleri = {
    baslangicTarihi?: string;
    bitisTarihi?: string;
    eylemTipi?: AuditEylem;
    aktorPersonelId?: string;
    hedefPersonelId?: string;
    page: number;
    size: number;
};
