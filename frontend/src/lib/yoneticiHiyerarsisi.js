/**
 * Backend'in en yakından en uzağa verdiği yönetici zincirini,
 * en üst yöneticiden başlayacak görüntüleme ağacına dönüştürür.
 */
export function yoneticiZinciriniAgacaDonustur(yoneticiler) {
    return [...yoneticiler]
        .sort((ilk, ikinci) => ilk.seviye - ikinci.seviye)
        .reduce(
            (altDugumler, yonetici) => [
                {
                    ad: yonetici.ad,
                    soyad: yonetici.soyad,
                    departman: yonetici.departman,
                    rol: yonetici.rol,
                    profilFoto: yonetici.profilFoto,
                    yoneticinMi: yonetici.seviye === 1,
                    dogrudanBaglilar: altDugumler,
                },
            ],
            [],
        );
}

/**
 * Açık düğümlerin altındakileri, girinti seviyesiyle birlikte tablo satırına dönüştürür.
 */
export function hiyerarsiSatirlariniOlustur(kokler, acikYollar) {
    const satirlar = [];

    function dugumleriEkle(dugumler, ustYol, seviye) {
        dugumler.forEach((dugum, index) => {
            const yol = ustYol === "" ? String(index) : `${ustYol}-${index}`;

            satirlar.push({ dugum, yol, seviye });

            if (
                acikYollar.has(yol) &&
                dugum.dogrudanBaglilar.length > 0
            ) {
                dugumleriEkle(dugum.dogrudanBaglilar, yol, seviye + 1);
            }
        });
    }

    dugumleriEkle(kokler, "", 0);
    return satirlar;
}

function kisiAnahtariniOlustur(kisi) {
    return `${kisi.ad}\u0000${kisi.soyad}\u0000${kisi.departman}`;
}

/** Hiyerarşide zaten gösterilen kişileri düz ekip listesinden çıkarır. */
export function hiyerarsiDisiKisileriGetir(kisiler, kokler) {
    const hiyerarsidekiKisiler = new Set();

    function dugumleriKaydet(dugumler) {
        dugumler.forEach((dugum) => {
            hiyerarsidekiKisiler.add(kisiAnahtariniOlustur(dugum));
            dugumleriKaydet(dugum.dogrudanBaglilar);
        });
    }

    dugumleriKaydet(kokler);
    return kisiler.filter(
        (kisi) => !hiyerarsidekiKisiler.has(kisiAnahtariniOlustur(kisi)),
    );
}
