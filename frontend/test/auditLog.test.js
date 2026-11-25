import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import * as api from "../src/lib/api.js";

test("audit log API istemcisi yalnız dolu filtreleri ve sayfalamayı gönderir", async () => {
    assert.equal(typeof api.auditLoglariniGetir, "function");
    const oncekiFetch = globalThis.fetch;
    const oncekiLocalStorage = globalThis.localStorage;
    const istekler = [];
    globalThis.localStorage = { getItem: () => "test-access-token" };
    globalThis.fetch = async (url, options = {}) => {
        istekler.push({ url, options });
        return { ok: true, status: 200, json: async () => ({ icerik: [], sayfa: 0, sayfaBoyutu: 20, toplamKayit: 0, toplamSayfa: 0 }) };
    };

    try {
        await api.auditLoglariniGetir({ page: 0, size: 20 });
        await api.auditLoglariniGetir({ baslangicTarihi: "2026-08-01", bitisTarihi: "2026-08-26", eylemTipi: "IZIN_TALEBI_ONAYLANDI", aktorPersonelId: "aktor-uuid", hedefPersonelId: "hedef-uuid", page: 2, size: 50 });
    } finally {
        globalThis.fetch = oncekiFetch;
        globalThis.localStorage = oncekiLocalStorage;
    }

    assert.deepEqual(istekler.map(({ url, options }) => ({ url, method: options.method })), [
        { url: "http://localhost:8080/api/admin/audit-logs?page=0&size=20", method: undefined },
        { url: "http://localhost:8080/api/admin/audit-logs?baslangicTarihi=2026-08-01&bitisTarihi=2026-08-26&eylemTipi=IZIN_TALEBI_ONAYLANDI&aktorPersonelId=aktor-uuid&hedefPersonelId=hedef-uuid&page=2&size=50", method: undefined },
    ]);
});

test("audit eylem etiketi bilinmeyen enum değerinde ham değere döner", async () => {
    const audit = await import("../src/lib/auditLog.js");
    assert.equal(audit.auditEylemEtiketi("IZIN_TALEBI_REDDEDILDI"), "İzin talebi reddedildi");
    assert.equal(audit.auditEylemEtiketi("YENI_BIR_EYLEM"), "YENI_BIR_EYLEM");
});

test("rezervasyon audit eylemleri Türkçe pill etiketi ve filtre seçeneğiyle sunulur", async () => {
    const audit = await import("../src/lib/auditLog.js");

    assert.equal(audit.auditEylemEtiketi("REZERVASYON_OLUSTURULDU"), "Rezervasyon oluşturuldu");
    assert.equal(audit.auditEylemEtiketi("REZERVASYON_IPTAL_EDILDI"), "Rezervasyon iptal edildi");
    assert.deepEqual(
        audit.AUDIT_EYLEM_SECENEKLERI.filter(({ deger }) => deger.startsWith("REZERVASYON_")),
        [
            { deger: "REZERVASYON_OLUSTURULDU", etiket: "Rezervasyon oluşturuldu" },
            { deger: "REZERVASYON_IPTAL_EDILDI", etiket: "Rezervasyon iptal edildi" },
        ],
    );
});

test("ekipman ve zimmet audit eylemleri Türkçe pill etiketi ve filtre seçeneğiyle sunulur", async () => {
    const audit = await import("../src/lib/auditLog.js");
    const beklenenSecenekler = [
        { deger: "EKIPMAN_TIPI_EKLENDI", etiket: "Ekipman tipi eklendi" },
        { deger: "EKIPMAN_TIPI_GUNCELLENDI", etiket: "Ekipman tipi güncellendi" },
        { deger: "EKIPMAN_EKLENDI", etiket: "Ekipman eklendi" },
        { deger: "EKIPMAN_GUNCELLENDI", etiket: "Ekipman güncellendi" },
        { deger: "EKIPMAN_TALEBI_OLUSTURULDU", etiket: "Ekipman talebi oluşturuldu" },
        { deger: "EKIPMAN_TALEBI_IPTAL_EDILDI", etiket: "Ekipman talebi iptal edildi" },
        { deger: "EKIPMAN_TALEBI_ONAYLANDI", etiket: "Ekipman talebi onaylandı" },
        { deger: "EKIPMAN_TALEBI_REDDEDILDI", etiket: "Ekipman talebi reddedildi" },
        { deger: "ZIMMET_IADE_EDILDI", etiket: "Zimmet iade edildi" },
    ];

    assert.deepEqual(
        beklenenSecenekler.map(({ deger }) => audit.auditEylemEtiketi(deger)),
        beklenenSecenekler.map(({ etiket }) => etiket),
    );
    assert.deepEqual(
        audit.AUDIT_EYLEM_SECENEKLERI.filter(({ deger }) => deger.startsWith("EKIPMAN_") || deger === "ZIMMET_IADE_EDILDI"),
        beklenenSecenekler,
    );
});

test("audit aktör/hedef fallbackleri ve kontrollü hata mesajları güvenlidir", async () => {
    const audit = await import("../src/lib/auditLog.js");

    assert.equal(audit.auditAktoruEtiketi({ personelId: null, adSoyad: null, kullaniciAdi: "bilinmeyen.kullanici" }), "bilinmeyen.kullanici");
    assert.equal(audit.auditHedefEtiketi(null), "—");
    assert.equal(audit.auditHataMesaji(403, "Ham mesaj"), "Bu sayfayı görüntüleme yetkiniz yok.");
    assert.equal(audit.auditHataMesaji(404, "Personel bulunamadı."), "Oturum bilginiz güncel değil. Lütfen tekrar giriş yapın.");
});

test("audit log route ve menü bağlantısı yalnız ADMIN rolüyle sınırlıdır", async () => {
    const [app, sidebar] = await Promise.all([
        readFile(new URL("../src/App.tsx", import.meta.url), "utf8"),
        readFile(new URL("../src/layout/AppSidebar.tsx", import.meta.url), "utf8"),
    ]);

    assert.match(app, /path="\/yonetim\/audit-log"[\s\S]*?RoleRoute roller=\{\["ADMIN"\]\}/);
    assert.match(sidebar, /name: "Audit Log",[\s\S]*?roller: ADMIN_ROLU,[\s\S]*?path: "\/yonetim\/audit-log"/);
});
