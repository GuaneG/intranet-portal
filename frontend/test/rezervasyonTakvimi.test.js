import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import * as api from "../src/lib/api.js";

test("oda ve rezervasyon API istemcileri backend sırasını ve opsiyonel oda filtresini korur", async () => {
    assert.equal(typeof api.odalariGetir, "function");
    assert.equal(typeof api.rezervasyonlariGetir, "function");

    const oncekiFetch = globalThis.fetch;
    const oncekiLocalStorage = globalThis.localStorage;
    const istekler = [];
    globalThis.localStorage = { getItem: () => "test-access-token" };
    globalThis.fetch = async (url, options = {}) => {
        istekler.push({ url, options });
        return { ok: true, status: 200, json: async () => [] };
    };

    try {
        await api.odalariGetir();
        await api.rezervasyonlariGetir("2026-09-01", "2026-09-30");
        await api.rezervasyonlariGetir("2026-09-01", "2026-09-30", 1);
    } finally {
        globalThis.fetch = oncekiFetch;
        globalThis.localStorage = oncekiLocalStorage;
    }

    assert.deepEqual(istekler.map(({ url, options }) => ({ url, method: options.method })), [
        { url: "http://localhost:8080/api/rooms", method: undefined },
        { url: "http://localhost:8080/api/reservations?baslangicTarihi=2026-09-01&bitisTarihi=2026-09-30", method: undefined },
        { url: "http://localhost:8080/api/reservations?baslangicTarihi=2026-09-01&bitisTarihi=2026-09-30&odaId=1", method: undefined },
    ]);
});

test("rezervasyon takvim event'i yerel saat dilimi dönüşümü yapmadan salt okunur kalır", async () => {
    const rezervasyonTakvimi = await import("../src/lib/rezervasyonTakvimi.js");
    const rezervasyon = {
        id: "rezervasyon-uuid",
        oda: { id: 1, ad: "Büyük Salon", kapasite: 12 },
        tarih: "2026-09-03",
        baslangicSaat: 9,
        bitisSaat: 11,
        olusturan: { ad: "Ayşe", soyad: "Yılmaz" },
        benMiOlusturdum: false,
        baslik: null,
        katilimciSayisi: null,
    };

    assert.equal(rezervasyonTakvimi.saatiBicimlendir(9), "09:00");
    assert.equal(rezervasyonTakvimi.saatAraliginiBicimlendir(8), "08:00–09:00");
    assert.equal(
        rezervasyonTakvimi.rezervasyonEtiketi(rezervasyon),
        "09:00–11:00 · Büyük Salon · Dolu · Ayşe Yılmaz",
    );
    assert.deepEqual(rezervasyonTakvimi.rezervasyonuTakvimOlayinaDonustur(rezervasyon), {
        id: "rezervasyon-uuid",
        title: "09:00–11:00 · Büyük Salon · Dolu · Ayşe Yılmaz",
        start: "2026-09-03T09:00:00",
        end: "2026-09-03T11:00:00",
        allDay: false,
        editable: false,
        startEditable: false,
        durationEditable: false,
        classNames: ["event-fc-color", "fc-bg-primary"],
        extendedProps: {
            tur: "REZERVASYON",
            rezervasyon,
        },
    });
});

test("şirket takvimi geçmiş oda rezervasyonlarını gizler, bugün ve sonrası görünür", async () => {
    const rezervasyonTakvimi = await import("../src/lib/rezervasyonTakvimi.js");

    assert.equal(rezervasyonTakvimi.rezervasyonTakvimdeGorunurMu({ tarih: "2026-08-31" }, "2026-09-01"), false);
    assert.equal(rezervasyonTakvimi.rezervasyonTakvimdeGorunurMu({ tarih: "2026-09-01" }, "2026-09-01"), true);
    assert.equal(rezervasyonTakvimi.rezervasyonTakvimdeGorunurMu({ tarih: "2026-09-02" }, "2026-09-01"), true);
});

test("rezervasyon altyapısı ayrı bir route veya sidebar menüsü oluşturmaz", async () => {
    const [app, sidebar] = await Promise.all([
        readFile(new URL("../src/App.tsx", import.meta.url), "utf8"),
        readFile(new URL("../src/layout/AppSidebar.tsx", import.meta.url), "utf8"),
    ]);

    assert.doesNotMatch(app, /RezervasyonTakvimi|\/talepler\/rezervasyon-takvimi/);
    assert.doesNotMatch(sidebar, /name: "Rezervasyon Takvimi"/);
});

test("rezervasyon API istemcisi oluşturma, geçmiş ve iptal sözleşmelerini doğru gönderir", async () => {
    assert.equal(typeof api.rezervasyonOlustur, "function");
    assert.equal(typeof api.rezervasyonGecmisimiGetir, "function");
    assert.equal(typeof api.adminRezervasyonGecmisiniGetir, "function");
    assert.equal(typeof api.rezervasyonuIptalEt, "function");

    const oncekiFetch = globalThis.fetch;
    const oncekiLocalStorage = globalThis.localStorage;
    const istekler = [];
    globalThis.localStorage = { getItem: () => "test-access-token" };
    globalThis.fetch = async (url, options = {}) => {
        istekler.push({ url, options });
        return { ok: true, status: 201, json: async () => ({ id: "rezervasyon-uuid" }) };
    };

    try {
        await api.rezervasyonOlustur({ odaId: 1, tarih: "2026-09-10", baslangicSaat: 10, bitisSaat: 12, baslik: "Sprint Planlama", katilimciSayisi: 8 });
        await api.rezervasyonGecmisimiGetir({ page: 0, size: 20, durum: "AKTIF", odaId: 1, baslangic: "2026-09-01", bitis: "2026-09-30" });
        await api.adminRezervasyonGecmisiniGetir({ page: 0, size: 20 });
        await api.rezervasyonuIptalEt("rezervasyon-uuid");
        await api.rezervasyonuIptalEt("rezervasyon-uuid", "Toplantı iptal edildi.");
    } finally {
        globalThis.fetch = oncekiFetch;
        globalThis.localStorage = oncekiLocalStorage;
    }

    assert.deepEqual(istekler.map(({ url, options }) => ({ url, method: options.method, body: options.body })), [
        { url: "http://localhost:8080/api/reservations", method: "POST", body: JSON.stringify({ odaId: 1, tarih: "2026-09-10", baslangicSaat: 10, bitisSaat: 12, baslik: "Sprint Planlama", katilimciSayisi: 8 }) },
        { url: "http://localhost:8080/api/reservations/my?page=0&size=20&durum=AKTIF&odaId=1&baslangic=2026-09-01&bitis=2026-09-30", method: undefined, body: undefined },
        { url: "http://localhost:8080/api/admin/reservations?page=0&size=20", method: undefined, body: undefined },
        { url: "http://localhost:8080/api/reservations/rezervasyon-uuid/cancel", method: "PATCH", body: undefined },
        { url: "http://localhost:8080/api/reservations/rezervasyon-uuid/cancel", method: "PATCH", body: JSON.stringify({ iptalNedeni: "Toplantı iptal edildi." }) },
    ]);
});

test("rezervasyon formu çalışma saatini, süreyi, tarihi ve kapasiteyi backend'e gitmeden doğrular", async () => {
    const rezervasyon = await import("../src/lib/rezervasyon.js");
    const tabanForm = { odaId: "1", tarih: "2026-09-10", baslangicSaat: "10", bitisSaat: "12", baslik: "Sprint planlama", katilimciSayisi: "8" };

    assert.equal(rezervasyon.rezervasyonFormunuDogrula(tabanForm, { bugun: "2026-08-27", mevcutSaat: 9, odaKapasitesi: 12, tatilGunSuresi: null }), null);
    assert.equal(rezervasyon.rezervasyonFormunuDogrula({ ...tabanForm, bitisSaat: "15" }, { bugun: "2026-08-27", mevcutSaat: 9, odaKapasitesi: 12, tatilGunSuresi: null }), "Rezervasyon en fazla 4 saat sürebilir.");
    assert.equal(rezervasyon.rezervasyonFormunuDogrula({ ...tabanForm, katilimciSayisi: "13" }, { bugun: "2026-08-27", mevcutSaat: 9, odaKapasitesi: 12, tatilGunSuresi: null }), "Katılımcı sayısı oda kapasitesini aşamaz.");
    assert.equal(rezervasyon.rezervasyonFormunuDogrula({ ...tabanForm, tarih: "2026-11-26" }, { bugun: "2026-08-27", mevcutSaat: 9, odaKapasitesi: 12, tatilGunSuresi: null }), "Rezervasyon en fazla 90 gün ileri tarih için oluşturulabilir.");
});

test("günlük oda grid'i tatil ve dolu saatleri seçime kapatır", async () => {
    const rezervasyon = await import("../src/lib/rezervasyon.js");
    assert.equal(typeof rezervasyon.gunlukOdaGridiniOlustur, "function");

    const odalar = [{ id: 1, ad: "Büyük Salon", kapasite: 12 }];
    const kayitlar = [{
        id: "r-1",
        oda: odalar[0],
        tarih: "2026-09-10",
        baslangicSaat: 10,
        bitisSaat: 12,
        olusturan: { ad: "Ayşe", soyad: "Yılmaz" },
        benMiOlusturdum: false,
        baslik: null,
        katilimciSayisi: null,
    }];

    const yarimGun = rezervasyon.gunlukOdaGridiniOlustur(odalar, kayitlar, {
        tarih: "2026-09-10",
        tatilGunSuresi: "YARIM_GUN",
        bugun: "2026-09-01",
        mevcutSaat: 9,
    });
    assert.deepEqual(yarimGun[0].hucreler.filter((hucre) => hucre.baslangicSaat >= 13).map((hucre) => hucre.durum), ["KAPALI", "KAPALI", "KAPALI", "KAPALI", "KAPALI"]);
    assert.equal(yarimGun[0].hucreler.find((hucre) => hucre.baslangicSaat === 10)?.durum, "DOLU");
    assert.equal(yarimGun[0].hucreler.find((hucre) => hucre.baslangicSaat === 12)?.durum, "MUSAIT");

    const tamGun = rezervasyon.gunlukOdaGridiniOlustur(odalar, [], {
        tarih: "2026-09-10",
        tatilGunSuresi: "TAM_GUN",
        bugun: "2026-09-01",
        mevcutSaat: 9,
    });
    assert.equal(tamGun[0].hucreler.every((hucre) => hucre.durum === "KAPALI"), true);

    const tatildekiKayit = rezervasyon.gunlukOdaGridiniOlustur(odalar, kayitlar, {
        tarih: "2026-09-10",
        tatilGunSuresi: "TAM_GUN",
        bugun: "2026-09-01",
        mevcutSaat: 9,
    });
    assert.equal(tatildekiKayit[0].hucreler.find((hucre) => hucre.baslangicSaat === 10)?.durum, "DOLU");
});

test("günlük grid iki bitişik boş hücreyi iki saatlik form aralığına çevirir", async () => {
    const rezervasyon = await import("../src/lib/rezervasyon.js");
    assert.equal(typeof rezervasyon.gridSecimindenRezervasyonAraligiOlustur, "function");

    const hucreler = [
        { baslangicSaat: 8, durum: "MUSAIT" },
        { baslangicSaat: 9, durum: "MUSAIT" },
        { baslangicSaat: 10, durum: "DOLU" },
        { baslangicSaat: 11, durum: "MUSAIT" },
    ];

    assert.deepEqual(
        rezervasyon.gridSecimindenRezervasyonAraligiOlustur(1, 8, 9, hucreler),
        { odaId: "1", baslangicSaat: "8", bitisSaat: "10" },
    );
    assert.equal(
        rezervasyon.gridSecimindenRezervasyonAraligiOlustur(1, 8, 10, hucreler),
        null,
    );
});

test("rezervasyon formundaki geçerli saat aralığı günlük grid seçimine yansır", async () => {
    const rezervasyon = await import("../src/lib/rezervasyon.js");
    const odalar = [{ id: 1, ad: "Büyük Salon", kapasite: 12 }];
    const satirlar = rezervasyon.gunlukOdaGridiniOlustur(odalar, [], {
        tarih: "2026-09-10",
        tatilGunSuresi: null,
        bugun: "2026-09-01",
        mevcutSaat: 9,
    });

    assert.deepEqual(
        rezervasyon.formdanGridSecimiOlustur(
            { odaId: "1", tarih: "2026-09-10", baslangicSaat: "8", bitisSaat: "11" },
            "2026-09-10",
            satirlar,
        ),
        { odaId: 1, ilkSaat: 8, sonSaat: 10 },
    );
    assert.equal(
        rezervasyon.formdanGridSecimiOlustur(
            { odaId: "1", tarih: "2026-09-10", baslangicSaat: "8", bitisSaat: "13" },
            "2026-09-10",
            satirlar,
        ),
        null,
    );
});

test("şirket takvimi tatiller ve doğum günlerinin yanında rezervasyon eventlerini de kullanır", async () => {
    const takvim = await readFile(new URL("../src/pages/Calendar.tsx", import.meta.url), "utf8");

    assert.match(takvim, /rezervasyonlariGetir/);
    assert.match(takvim, /rezervasyonuTakvimOlayinaDonustur/);
    assert.match(takvim, /rezervasyonTakvimdeGorunurMu/);
    assert.match(takvim, /REZERVASYON/);
});

test("rezervasyon talepleri mevcut menüde tek route olarak etkinleşir ve dashboard oda doluluğunu kullanır", async () => {
    const [app, sidebar, home, dashboard] = await Promise.all([
        readFile(new URL("../src/App.tsx", import.meta.url), "utf8"),
        readFile(new URL("../src/layout/AppSidebar.tsx", import.meta.url), "utf8"),
        readFile(new URL("../src/pages/Dashboard/Home.tsx", import.meta.url), "utf8"),
        readFile(new URL("../src/types/dashboard.ts", import.meta.url), "utf8"),
    ]);

    assert.match(app, /path="\/talepler\/rezervasyonlar"[\s\S]*?<RezervasyonTaleplerim/);
    assert.match(sidebar, /name: "Rezervasyon Taleplerim",[\s\S]*?path: "\/talepler\/rezervasyonlar"/);
    assert.match(home, /odaDoluluk/);
    assert.match(dashboard, /odaDoluluk/);
});
