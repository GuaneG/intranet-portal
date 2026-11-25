import assert from "node:assert/strict";
import test from "node:test";

import { yoneticiZinciriniAgacaDonustur } from "../src/lib/yoneticiHiyerarsisi.js";
import { hiyerarsiSatirlariniOlustur } from "../src/lib/yoneticiHiyerarsisi.js";
import { hiyerarsiDisiKisileriGetir } from "../src/lib/yoneticiHiyerarsisi.js";

test("yönetici zinciri en üst yöneticiden başlayacak ağaç sırasına çevrilir", () => {
    const kokler = yoneticiZinciriniAgacaDonustur([
        {
            seviye: 1,
            ad: "Can",
            soyad: "Gere",
            departman: "Bilgi Teknolojileri",
            rol: "YONETICI",
            profilFoto: null,
        },
        {
            seviye: 2,
            ad: "Demo",
            soyad: "Yönetici",
            departman: "Bilgi Teknolojileri",
            rol: "YONETICI",
            profilFoto: null,
        },
    ]);

    assert.deepEqual(kokler, [
        {
            ad: "Demo",
            soyad: "Yönetici",
            departman: "Bilgi Teknolojileri",
            rol: "YONETICI",
            profilFoto: null,
            yoneticinMi: false,
            dogrudanBaglilar: [
                {
                    ad: "Can",
                    soyad: "Gere",
                    departman: "Bilgi Teknolojileri",
                    rol: "YONETICI",
                    profilFoto: null,
                    yoneticinMi: true,
                    dogrudanBaglilar: [],
                },
            ],
        },
    ]);
});

test("açık yönetici satırının doğrudan bağlıları aynı tabloya eklenir", () => {
    const kokler = [
        {
            ad: "Demo",
            soyad: "Yönetici",
            dogrudanBaglilar: [
                {
                    ad: "Can",
                    soyad: "Gere",
                    dogrudanBaglilar: [],
                },
            ],
        },
    ];

    assert.deepEqual(hiyerarsiSatirlariniOlustur(kokler, new Set(["0"])), [
        { dugum: kokler[0], yol: "0", seviye: 0 },
        { dugum: kokler[0].dogrudanBaglilar[0], yol: "0-0", seviye: 1 },
    ]);
});

test("hiyerarşide yer almayan ekip üyeleri tablo için korunur", () => {
    const kokler = [
        {
            ad: "Demo",
            soyad: "Yönetici",
            departman: "Bilgi Teknolojileri",
            dogrudanBaglilar: [],
        },
    ];
    const kisiler = [
        {
            ad: "Demo",
            soyad: "Yönetici",
            departman: "Bilgi Teknolojileri",
        },
        {
            ad: "Ekip",
            soyad: "Dizin",
            departman: "Bilgi Teknolojileri",
        },
    ];

    assert.deepEqual(hiyerarsiDisiKisileriGetir(kisiler, kokler), [kisiler[1]]);
});
