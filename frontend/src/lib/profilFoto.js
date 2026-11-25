const PROFIL_FOTO_ANAHTARI_DESENI =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(?:jpe?g|png)$/i;

/**
 * Sunucunun varsayılan avatar işaretini ve kanonik olmayan değerleri
 * görüntüleme isteğine dönüştürmeden önce ayıklar.
 */
export function profilFotoAnahtariGecerliMi(profilFoto) {
    return (
        typeof profilFoto === "string" &&
        PROFIL_FOTO_ANAHTARI_DESENI.test(profilFoto)
    );
}

export function profilFotoBasHarfleriniOlustur(ad, soyad) {
    const adBasHarfi = typeof ad === "string" ? ad.trim().charAt(0) : "";
    const soyadBasHarfi =
        typeof soyad === "string" ? soyad.trim().charAt(0) : "";
    const basHarfler = `${adBasHarfi}${soyadBasHarfi}`.trim();

    return basHarfler
        ? basHarfler.toLocaleUpperCase("tr-TR")
        : "?";
}
