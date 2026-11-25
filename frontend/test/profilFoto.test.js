import assert from "node:assert/strict";
import test from "node:test";

import { profilFotoAnahtariGecerliMi } from "../src/lib/profilFoto.js";

test("profil fotoğrafı yalnız sunucunun ürettiği görsel anahtarında yüklenir", () => {
    assert.equal(profilFotoAnahtariGecerliMi("def_pfp.png"), false);
    assert.equal(profilFotoAnahtariGecerliMi(null), false);
    assert.equal(profilFotoAnahtariGecerliMi("../gizli.png"), false);
    assert.equal(
        profilFotoAnahtariGecerliMi(
            "987e6543-e21b-12d3-a456-426614174000.jpg",
        ),
        true,
    );
});
