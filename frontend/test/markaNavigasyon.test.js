import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import {
    rotaIcinBreadcrumbOgeleri,
    sayfaBasliginiBicimlendir,
} from "../src/lib/navigasyon.js";

test("sayfa başlıkları eski eklerden bağımsız olarak JForce markasıyla biçimlenir", () => {
    assert.equal(
        sayfaBasliginiBicimlendir("İzin Taleplerim | Intranet Portal"),
        "İzin Taleplerim | JForce İntranet",
    );
    assert.equal(
        sayfaBasliginiBicimlendir("Giriş Yap"),
        "Giriş Yap | JForce İntranet",
    );
});

test("breadcrumb yalnız gerçek ara route'ları linkleyerek Türkçe hiyerarşi kurar", () => {
    assert.deepEqual(
        rotaIcinBreadcrumbOgeleri("/talepler/izinlerim", "İzin Taleplerim"),
        [
            { etiket: "Ana Sayfa", yol: "/" },
            { etiket: "Talepler" },
            { etiket: "İzin Taleplerim" },
        ],
    );
    assert.deepEqual(
        rotaIcinBreadcrumbOgeleri(
            "/yonetim/personel/123/duzenle",
            "Personel Düzenle",
        ),
        [
            { etiket: "Ana Sayfa", yol: "/" },
            { etiket: "Yönetim" },
            { etiket: "Personel Yönetimi", yol: "/yonetim/personel" },
            { etiket: "Personel Düzenle" },
        ],
    );
});

test("uygulama kabuğu ürün markasını kullanır ve sahte header kontrollerini göstermez", async () => {
    const [html, header, sidebar, app, pageMeta] = await Promise.all([
        readFile(new URL("../index.html", import.meta.url), "utf8"),
        readFile(new URL("../src/layout/AppHeader.tsx", import.meta.url), "utf8"),
        readFile(new URL("../src/layout/AppSidebar.tsx", import.meta.url), "utf8"),
        readFile(new URL("../src/App.tsx", import.meta.url), "utf8"),
        readFile(new URL("../src/components/common/PageMeta.tsx", import.meta.url), "utf8"),
    ]);

    assert.match(html, /<html lang="tr">/);
    assert.match(html, /<title>JForce İntranet<\/title>/);
    assert.match(html, /href="\/images\/logo\/jforce-icon\.svg"/);
    assert.match(sidebar, /src="\/images\/logo\/jforce-icon\.svg"/);
    assert.match(pageMeta, /document\.title\s*=\s*sayfaBasligi/);
    assert.doesNotMatch(header, /Search or type command|NotificationDropdown/);
    assert.doesNotMatch(app, /path="\/(?:blank|form-elements|basic-tables|alerts|avatars|badge|buttons|images|videos|line-chart|bar-chart)"/);
});
