export function bugununTarihi() {
    const tarih = new Date();
    const yil = tarih.getFullYear();
    const ay = String(tarih.getMonth() + 1).padStart(2, "0");
    const gun = String(tarih.getDate()).padStart(2, "0");

    return `${yil}-${ay}-${gun}`;
}

export function izinTalepFormunuDogrula(form, bugun = bugununTarihi()) {
    if (!form.izinTuruId) return "İzin türü seçmelisiniz.";
    if (!form.baslangicTarihi || !form.bitisTarihi) {
        return "Başlangıç ve bitiş tarihi zorunludur.";
    }
    if (form.baslangicTarihi < bugun) {
        return "Geçmiş tarih için izin talebi oluşturulamaz.";
    }
    if (form.bitisTarihi < form.baslangicTarihi) {
        return "Bitiş tarihi başlangıç tarihinden önce olamaz.";
    }
    if (form.yoneticiyeNot.trim().length > 1000) {
        return "Yönetici notu en fazla 1000 karakter olabilir.";
    }

    return null;
}

/** Form state'inden yalnız backend'in kabul ettiği alanları üretir. */
export function izinTalepIstekGövdesiniOlustur(form) {
    const yoneticiyeNot = form.yoneticiyeNot.trim();

    return {
        izinTuruId: Number(form.izinTuruId),
        baslangicTarihi: form.baslangicTarihi,
        bitisTarihi: form.bitisTarihi,
        yoneticiyeNot: yoneticiyeNot || null,
    };
}

/** Red kararındaki boşlukları backend ile aynı biçimde değerlendirir. */
export function kararNotunuDogrula(kararNotu) {
    const temizlenmisNot = kararNotu.trim();

    if (!temizlenmisNot) return "Red notu zorunludur.";
    if (temizlenmisNot.length > 500) {
        return "Red notu en fazla 500 karakter olabilir.";
    }

    return null;
}
