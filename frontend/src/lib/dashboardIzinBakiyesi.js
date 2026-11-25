export function izinBakiyesiYuzdesi({ toplamHak, kalan }) {
    return toplamHak > 0 ? (kalan / toplamHak) * 100 : 0;
}

export function izinGununuBicimlendir(deger) {
    return new Intl.NumberFormat("tr-TR", {
        maximumFractionDigits: 2,
    }).format(deger);
}

export function bugunIzinEtiketi(durum) {
    if (durum === "IZINLI") return "İzinli";
    if (durum === "YARIM_GUN_IZINLI") return "Yarım gün izinli";
    return null;
}

export function bugunIzinRozetRengi(durum) {
    return durum === "YARIM_GUN_IZINLI" ? "warning" : "info";
}
