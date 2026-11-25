export function ekipmanTalepFormunuDogrula(form) {
    if (!form.ekipmanTipiId) return "Ekipman tipi seçmelisiniz.";
    if (form.aciklama.trim().length > 255) return "Açıklama en fazla 255 karakter olabilir.";
    return null;
}

export function ekipmanTalepIstekGövdesiniOlustur(form) {
    const aciklama = form.aciklama.trim();

    return {
        ekipmanTipiId: Number(form.ekipmanTipiId),
        aciklama: aciklama || null,
    };
}

export function ekipmanKararNotunuDogrula(kararNotu) {
    const temizlenmisNot = kararNotu.trim();

    if (!temizlenmisNot) return "Red nedeni zorunludur.";
    if (temizlenmisNot.length > 500) return "Red nedeni en fazla 500 karakter olabilir.";
    return null;
}
