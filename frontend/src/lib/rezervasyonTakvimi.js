export const REZERVASYON_EVENT_TURU = "REZERVASYON";

export function saatiBicimlendir(saat) {
    return `${String(saat).padStart(2, "0")}:00`;
}

/** Grid sütununun temsil ettiği bir saatlik zaman aralığını açıkça gösterir. */
export function saatAraliginiBicimlendir(baslangicSaat) {
    return `${saatiBicimlendir(baslangicSaat)}–${saatiBicimlendir(baslangicSaat + 1)}`;
}

export function rezervasyonOlusturanAdi(rezervasyon) {
    return `${rezervasyon.olusturan.ad} ${rezervasyon.olusturan.soyad}`;
}

export function rezervasyonEtiketi(rezervasyon) {
    return `${saatiBicimlendir(rezervasyon.baslangicSaat)}–${saatiBicimlendir(rezervasyon.bitisSaat)} · ${rezervasyon.oda.ad} · Dolu · ${rezervasyonOlusturanAdi(rezervasyon)}`;
}

/** Geçmiş rezervasyonlar takvimde hatırlatıcı olarak tutulmaz; bugün ve sonrası görünür. */
export function rezervasyonTakvimdeGorunurMu(rezervasyon, bugun) {
    return rezervasyon.tarih >= bugun;
}

/** API tarihi takvim günüdür; Date nesnesine çevrilmeden FullCalendar'a aktarılır. */
export function rezervasyonuTakvimOlayinaDonustur(rezervasyon) {
    return {
        id: rezervasyon.id,
        title: rezervasyonEtiketi(rezervasyon),
        start: `${rezervasyon.tarih}T${saatiBicimlendir(rezervasyon.baslangicSaat)}:00`,
        end: `${rezervasyon.tarih}T${saatiBicimlendir(rezervasyon.bitisSaat)}:00`,
        allDay: false,
        editable: false,
        startEditable: false,
        durationEditable: false,
        classNames: ["event-fc-color", "fc-bg-primary"],
        extendedProps: {
            tur: REZERVASYON_EVENT_TURU,
            rezervasyon,
        },
    };
}

export function takvimTarihiniBicimlendir(tarih) {
    const [yil, ay, gun] = tarih.split("-");
    return `${gun}.${ay}.${yil}`;
}
