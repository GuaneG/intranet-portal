export const JFORCE_MARKASI = "JForce İntranet";

const ESKI_MARKA_EKI = /\s*\|\s*(?:JForce\s+İntranet|JForce\s+Intranet|Intranet Portal|TailAdmin.*)$/iu;

export function sayfaBasliginiBicimlendir(baslik) {
    const sayfaBasligi = baslik.replace(ESKI_MARKA_EKI, "").trim();

    return sayfaBasligi && sayfaBasligi !== JFORCE_MARKASI
        ? `${sayfaBasligi} | ${JFORCE_MARKASI}`
        : JFORCE_MARKASI;
}

export function rotaIcinBreadcrumbOgeleri(pathname, pageTitle) {
    const anaSayfa = { etiket: "Ana Sayfa", yol: "/" };

    if (pathname.startsWith("/talepler/")) {
        return [anaSayfa, { etiket: "Talepler" }, { etiket: pageTitle }];
    }

    if (pathname === "/profil/parola") {
        return [
            anaSayfa,
            { etiket: "Profilim", yol: "/profile" },
            { etiket: pageTitle },
        ];
    }

    if (/^\/yonetim\/personel\/[^/]+(?:\/duzenle)?$/u.test(pathname)) {
        return [
            anaSayfa,
            { etiket: "Yönetim" },
            { etiket: "Personel Yönetimi", yol: "/yonetim/personel" },
            { etiket: pageTitle },
        ];
    }

    if (pathname === "/yonetim/personel/yeni") {
        return [
            anaSayfa,
            { etiket: "Yönetim" },
            { etiket: "Personel Yönetimi", yol: "/yonetim/personel" },
            { etiket: pageTitle },
        ];
    }

    if (pathname.startsWith("/yonetim/")) {
        return [anaSayfa, { etiket: "Yönetim" }, { etiket: pageTitle }];
    }

    return [anaSayfa, { etiket: pageTitle }];
}
