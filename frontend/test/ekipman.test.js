import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

import * as api from "../src/lib/api.js";

function testOturumunuKur() {
    const oncekiFetch = globalThis.fetch;
    const oncekiLocalStorage = globalThis.localStorage;
    const istekler = [];

    globalThis.localStorage = {
        getItem(anahtar) {
            return anahtar === "token" ? "test-access-token" : null;
        },
    };
    globalThis.fetch = async (url, secenekler = {}) => {
        istekler.push({ url, secenekler });
        return {
            ok: true,
            status: secenekler.method === "POST" ? 201 : 200,
            json: async () => ({ icerik: [], sayfa: 0, sayfaBoyutu: 20, toplamKayit: 0, toplamSayfa: 0 }),
        };
    };

    return {
        istekler,
        geriYukle() {
            globalThis.fetch = oncekiFetch;
            globalThis.localStorage = oncekiLocalStorage;
        },
    };
}

test("ekipman talebi ve zimmet istemcileri boş filtreleri URL'ye eklemez", async () => {
    assert.equal(typeof api.ekipmanTipleriniGetir, "function");
    assert.equal(typeof api.ekipmanTalebiOlustur, "function");
    assert.equal(typeof api.ekipmanTaleplerimiGetir, "function");
    assert.equal(typeof api.zimmetlerimiGetir, "function");

    const ortam = testOturumunuKur();
    const payload = { ekipmanTipiId: 1, aciklama: "Proje geliştirme için gerekli." };

    try {
        await api.ekipmanTipleriniGetir();
        await api.ekipmanTalebiOlustur(payload);
        await api.ekipmanTaleplerimiGetir({ page: 0, size: 20, durum: "", ekipmanTipiId: "" });
        await api.zimmetlerimiGetir({ page: 1, size: 20 });
    } finally {
        ortam.geriYukle();
    }

    assert.deepEqual(
        ortam.istekler.map(({ url, secenekler }) => ({
            url,
            method: secenekler.method,
            body: secenekler.body,
        })),
        [
            { url: "http://localhost:8080/api/equipment-types", method: undefined, body: undefined },
            { url: "http://localhost:8080/api/equipment-requests", method: "POST", body: JSON.stringify(payload) },
            { url: "http://localhost:8080/api/equipment-requests/my?page=0&size=20", method: undefined, body: undefined },
            { url: "http://localhost:8080/api/assignments/my?page=1&size=20", method: undefined, body: undefined },
        ],
    );
});

test("bekleyen ekipman talebi güncellenir ve body olmadan iptal edilir", async () => {
    assert.equal(typeof api.ekipmanTalebiniGuncelle, "function");
    assert.equal(typeof api.ekipmanTalebiniIptalEt, "function");

    const ortam = testOturumunuKur();
    const talepId = "8f198ffe-3334-4ec0-9dc6-a28c14ca966f";
    const payload = { ekipmanTipiId: 2, aciklama: null };

    try {
        await api.ekipmanTalebiniGuncelle(talepId, payload);
        await api.ekipmanTalebiniIptalEt(talepId);
    } finally {
        ortam.geriYukle();
    }

    assert.deepEqual(
        ortam.istekler.map(({ url, secenekler }) => ({
            url,
            method: secenekler.method,
            body: secenekler.body,
        })),
        [
            { url: `http://localhost:8080/api/equipment-requests/${talepId}`, method: "PATCH", body: JSON.stringify(payload) },
            { url: `http://localhost:8080/api/equipment-requests/${talepId}/cancel`, method: "PATCH", body: undefined },
        ],
    );
});

test("ekipman talep formu aktif tip seçimini ve açıklama sınırını doğrular", async () => {
    const ekipmanTalebi = await import("../src/lib/ekipmanTalebi.js").catch(() => null);

    assert.notEqual(ekipmanTalebi, null);
    assert.equal(ekipmanTalebi.ekipmanTalepFormunuDogrula({ ekipmanTipiId: "", aciklama: "" }), "Ekipman tipi seçmelisiniz.");
    assert.equal(ekipmanTalebi.ekipmanTalepFormunuDogrula({ ekipmanTipiId: "1", aciklama: "a".repeat(256) }), "Açıklama en fazla 255 karakter olabilir.");
    assert.equal(ekipmanTalebi.ekipmanTalepFormunuDogrula({ ekipmanTipiId: "1", aciklama: "  Proje geliştirme için gerekli.  " }), null);
    assert.deepEqual(
        ekipmanTalebi.ekipmanTalepIstekGövdesiniOlustur({ ekipmanTipiId: "1", aciklama: "  " }),
        { ekipmanTipiId: 1, aciklama: null },
    );
});

test("ekipman talebi red notu boşluklardan arındırılmış 1–500 karakter olmalıdır", async () => {
    const ekipmanTalebi = await import("../src/lib/ekipmanTalebi.js");

    assert.equal(ekipmanTalebi.ekipmanKararNotunuDogrula("   "), "Red nedeni zorunludur.");
    assert.equal(ekipmanTalebi.ekipmanKararNotunuDogrula("a".repeat(501)), "Red nedeni en fazla 500 karakter olabilir.");
    assert.equal(ekipmanTalebi.ekipmanKararNotunuDogrula("  Stok planlaması nedeniyle uygun değil.  "), null);
});

test("ekipman ekranı role göre kendi talep veya karar kapsamını tek route'ta bağlar", async () => {
    const [app, sidebar, page, kararPage] = await Promise.all([
        readFile(new URL("../src/App.tsx", import.meta.url), "utf8"),
        readFile(new URL("../src/layout/AppSidebar.tsx", import.meta.url), "utf8"),
        readFile(new URL("../src/pages/EkipmanTaleplerim.tsx", import.meta.url), "utf8").catch(() => ""),
        readFile(new URL("../src/pages/EkipmanKararListesi.tsx", import.meta.url), "utf8").catch(() => ""),
    ]);

    assert.match(app, /path="\/talepler\/ekipman"/);
    assert.match(app, /roller=\{\["PERSONEL", "YONETICI", "HR", "ADMIN"\]\}/);
    assert.match(sidebar, /name: "Ekipman Taleplerim"[\s\S]*?path: "\/talepler\/ekipman"/);
    assert.match(page, /EkipmanTalebiFormu/);
    assert.match(page, /EkipmanTalepleriTablosu/);
    assert.match(page, /ZimmetlerimTablosu/);
    assert.match(page, /EkipmanKararListesi kapsam="EKIP"/);
    assert.match(kararPage, /EkipmanOnayModali/);
    assert.match(kararPage, /EkipmanRedModali/);
});

test("yönetici ve admin ekipman karar istemcileri ayrı kapsam endpointlerini kullanır", async () => {
    assert.equal(typeof api.ekipEkipmanTalepleriniGetir, "function");
    assert.equal(typeof api.adminEkipmanTalepleriniGetir, "function");
    assert.equal(typeof api.uygunEkipmanlariGetir, "function");
    assert.equal(typeof api.ekipmanTalebiniOnayla, "function");
    assert.equal(typeof api.ekipmanTalebiniReddet, "function");

    const ortam = testOturumunuKur();
    const talepId = "8f198ffe-3334-4ec0-9dc6-a28c14ca966f";
    const ekipmanId = "2766ad43-5bb0-45f5-9f98-9153390dabc0";

    try {
        await api.ekipEkipmanTalepleriniGetir({ page: 0, size: 20, durum: "", ekipmanTipiId: "" });
        await api.adminEkipmanTalepleriniGetir({ page: 1, size: 20, durum: "REDDEDILDI", ekipmanTipiId: 2 });
        await api.uygunEkipmanlariGetir(talepId, { page: 0, size: 20 });
        await api.ekipmanTalebiniOnayla(talepId, ekipmanId);
        await api.ekipmanTalebiniReddet(talepId, "Stok planlaması nedeniyle reddedildi.");
    } finally {
        ortam.geriYukle();
    }

    assert.deepEqual(
        ortam.istekler.map(({ url, secenekler }) => ({ url, method: secenekler.method, body: secenekler.body })),
        [
            { url: "http://localhost:8080/api/equipment-requests/team?page=0&size=20", method: undefined, body: undefined },
            { url: "http://localhost:8080/api/admin/equipment-requests?page=1&size=20&durum=REDDEDILDI&ekipmanTipiId=2", method: undefined, body: undefined },
            { url: `http://localhost:8080/api/equipment-requests/${talepId}/available-equipment?page=0&size=20`, method: undefined, body: undefined },
            { url: `http://localhost:8080/api/equipment-requests/${talepId}/approve`, method: "PATCH", body: JSON.stringify({ ekipmanId }) },
            { url: `http://localhost:8080/api/equipment-requests/${talepId}/reject`, method: "PATCH", body: JSON.stringify({ kararNotu: "Stok planlaması nedeniyle reddedildi." }) },
        ],
    );
});

test("admin envanter ve zimmet istemcileri boş filtreleri dışarıda bırakır", async () => {
    assert.equal(typeof api.adminEkipmanTipleriniGetir, "function");
    assert.equal(typeof api.adminEkipmanlariGetir, "function");
    assert.equal(typeof api.adminZimmetleriGetir, "function");
    assert.equal(typeof api.ekipmanTipiOlustur, "function");
    assert.equal(typeof api.ekipmanDurumunuGuncelle, "function");
    assert.equal(typeof api.zimmetiIadeAl, "function");

    const ortam = testOturumunuKur();
    const zimmetId = "8f198ffe-3334-4ec0-9dc6-a28c14ca966f";
    const ekipmanId = "2766ad43-5bb0-45f5-9f98-9153390dabc0";

    try {
        await api.adminEkipmanTipleriniGetir({ page: 0, size: 20, aktif: "", arama: "" });
        await api.adminEkipmanlariGetir({ page: 0, size: 20, ekipmanTipiId: "", durum: "", arama: "" });
        await api.adminZimmetleriGetir({ page: 1, size: 20, aktif: "", ekipmanTipiId: "" });
        await api.ekipmanTipiOlustur({ ad: "Laptop" });
        await api.ekipmanDurumunuGuncelle(ekipmanId, { durum: "KULLANIM_DISI", neden: "Bakım bekliyor" });
        await api.zimmetiIadeAl(zimmetId);
    } finally {
        ortam.geriYukle();
    }

    assert.deepEqual(
        ortam.istekler.map(({ url, secenekler }) => ({ url, method: secenekler.method, body: secenekler.body })),
        [
            { url: "http://localhost:8080/api/admin/equipment-types?page=0&size=20", method: undefined, body: undefined },
            { url: "http://localhost:8080/api/admin/equipment?page=0&size=20", method: undefined, body: undefined },
            { url: "http://localhost:8080/api/admin/assignments?page=1&size=20", method: undefined, body: undefined },
            { url: "http://localhost:8080/api/admin/equipment-types", method: "POST", body: JSON.stringify({ ad: "Laptop" }) },
            { url: `http://localhost:8080/api/admin/equipment/${ekipmanId}/status`, method: "PATCH", body: JSON.stringify({ durum: "KULLANIM_DISI", neden: "Bakım bekliyor" }) },
            { url: `http://localhost:8080/api/admin/assignments/${zimmetId}/return`, method: "PATCH", body: undefined },
        ],
    );
});

test("admin ekipman güncelleme PATCH istekleri başarılı 204 yanıtta JSON parse etmez", async () => {
    const oncekiFetch = globalThis.fetch;
    const oncekiLocalStorage = globalThis.localStorage;
    const istekler = [];
    globalThis.localStorage = { getItem: () => "test-access-token" };
    globalThis.fetch = async (url, secenekler = {}) => {
        istekler.push({ url, secenekler });
        return {
            ok: true,
            status: 204,
            json: async () => { throw new SyntaxError("Unexpected end of JSON input"); },
        };
    };

    try {
        await assert.doesNotReject(api.ekipmanTipiniGuncelle(1, { ad: "Dizüstü Bilgisayar", aktifMi: true }));
        await assert.doesNotReject(api.ekipmanBilgileriniGuncelle("ekipman-uuid", { marka: "Dell" }));
        await assert.doesNotReject(api.ekipmanDurumunuGuncelle("ekipman-uuid", { durum: "DEPODA" }));
    } finally {
        globalThis.fetch = oncekiFetch;
        globalThis.localStorage = oncekiLocalStorage;
    }

    assert.equal(istekler.length, 3);
});

test("admin envanter ve zimmet yönetimi yalnız yönetim route ve sidebar bağlantısından açılır", async () => {
    const [app, sidebar, page] = await Promise.all([
        readFile(new URL("../src/App.tsx", import.meta.url), "utf8"),
        readFile(new URL("../src/layout/AppSidebar.tsx", import.meta.url), "utf8"),
        readFile(new URL("../src/pages/EnvanterZimmetYonetimi.tsx", import.meta.url), "utf8"),
    ]);

    assert.match(app, /path="\/yonetim\/envanter-zimmet"/);
    assert.match(app, /roller=\{\["ADMIN"\]\}/);
    assert.match(sidebar, /name: "Envanter ve Zimmet"[\s\S]*?path: "\/yonetim\/envanter-zimmet"/);
    assert.match(page, /Ekipman tipleri/);
    assert.match(page, /Fiziksel envanter/);
    assert.match(page, /Zimmet yönetimi/);
});

test("tablo başlığı açıkça istenen hizalamayı varsayılan başlangıç hizasıyla ezmez", async () => {
    const tablo = await readFile(new URL("../src/components/ui/table/index.tsx", import.meta.url), "utf8");

    assert.match(tablo, /function hasTextAlignmentClass\(className\?: string\)/);
    assert.match(tablo, /isHeader && !hasTextAlignmentClass\(className\) \? "text-start" : ""/);
});
