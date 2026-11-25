const AUDIT_EYLEM_ETIKETLERI = {
    LOGIN_BASARILI: "Başarılı giriş",
    LOGIN_BASARISIZ: "Başarısız giriş",
    LOGOUT: "Çıkış yapıldı",
    TOKEN_REUSE_ALARM: "Token yeniden kullanım alarmı",
    ROL_DEGISTIRILDI: "Rol değiştirildi",
    PERSONEL_EKLENDI: "Personel eklendi",
    PERSONEL_GUNCELLENDI: "Personel güncellendi",
    PERSONEL_PASIFE_ALINDI: "Personel pasife alındı",
    PERSONEL_AKTIFLESTIRILDI: "Personel aktifleştirildi",
    PAROLA_DEGISTIRILDI: "Parola değiştirildi",
    PAROLA_SIFIRLANDI: "Parola sıfırlandı",
    PROFIL_GUNCELLENDI: "Profil güncellendi",
    PROFIL_FOTOGRAFI_GUNCELLENDI: "Profil fotoğrafı güncellendi",
    PROFIL_FOTOGRAFI_SILINDI: "Profil fotoğrafı silindi",
    YETENEK_EKLENDI: "Yetenek eklendi",
    YETENEK_GUNCELLENDI: "Yetenek güncellendi",
    YETENEK_SILINDI: "Yetenek silindi",
    DEPARTMAN_EKLENDI: "Departman eklendi",
    DEPARTMAN_GUNCELLENDI: "Departman güncellendi",
    DEPARTMAN_SILINDI: "Departman silindi",
    TATIL_EKLENDI: "Tatil eklendi",
    TATIL_GUNCELLENDI: "Tatil güncellendi",
    TATIL_SILINDI: "Tatil silindi",
    IZIN_TALEBI_OLUSTURULDU: "İzin talebi oluşturuldu",
    IZIN_TALEBI_IPTAL_EDILDI: "İzin talebi iptal edildi",
    IZIN_TALEBI_ONAYLANDI: "İzin talebi onaylandı",
    IZIN_TALEBI_REDDEDILDI: "İzin talebi reddedildi",
    REZERVASYON_OLUSTURULDU: "Rezervasyon oluşturuldu",
    REZERVASYON_IPTAL_EDILDI: "Rezervasyon iptal edildi",
    EKIPMAN_TIPI_EKLENDI: "Ekipman tipi eklendi",
    EKIPMAN_TIPI_GUNCELLENDI: "Ekipman tipi güncellendi",
    EKIPMAN_EKLENDI: "Ekipman eklendi",
    EKIPMAN_GUNCELLENDI: "Ekipman güncellendi",
    EKIPMAN_TALEBI_OLUSTURULDU: "Ekipman talebi oluşturuldu",
    EKIPMAN_TALEBI_IPTAL_EDILDI: "Ekipman talebi iptal edildi",
    EKIPMAN_TALEBI_ONAYLANDI: "Ekipman talebi onaylandı",
    EKIPMAN_TALEBI_REDDEDILDI: "Ekipman talebi reddedildi",
    ZIMMET_IADE_EDILDI: "Zimmet iade edildi",
};

export const AUDIT_EYLEM_SECENEKLERI = Object.entries(AUDIT_EYLEM_ETIKETLERI).map(([deger, etiket]) => ({ deger, etiket }));

export function auditEylemEtiketi(eylemTipi) {
    return AUDIT_EYLEM_ETIKETLERI[eylemTipi] ?? eylemTipi;
}

export function auditAktoruEtiketi(aktor) {
    return aktor?.adSoyad || aktor?.kullaniciAdi || "Bilinmeyen aktör";
}

export function auditHedefEtiketi(hedef) {
    return hedef?.adSoyad || "—";
}

export function auditHataMesaji(status, mesaj) {
    if (status === 403) return "Bu sayfayı görüntüleme yetkiniz yok.";
    if (status === 404) return "Oturum bilginiz güncel değil. Lütfen tekrar giriş yapın.";
    return mesaj || "Audit kayıtları alınamadı. Lütfen tekrar deneyin.";
}

export function auditTarihiniTurkiyeSaatindeBicimlendir(tarih) {
    return new Intl.DateTimeFormat("tr-TR", {
        timeZone: "Europe/Istanbul",
        dateStyle: "medium",
        timeStyle: "short",
    }).format(new Date(tarih));
}
