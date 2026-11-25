import { useEffect, useState } from "react";
import { Modal } from "../ui/modal";
import type { KararEkipmanTalebi, SayfaliUygunEkipmanResponse } from "../../types/ekipman";

type Props = {
    isOpen: boolean;
    talep: KararEkipmanTalebi | null;
    uygunEkipmanSayfasi: SayfaliUygunEkipmanResponse | null;
    yukleniyor: boolean;
    hata: string | null;
    islemSuruyor: boolean;
    onKapat: () => void;
    onTekrarDene: () => void;
    onOnayla: (ekipmanId: string) => void;
};

/** Onay işlemini backend'in sunduğu uygun envanter kaydıyla sınırlar. */
export default function EkipmanOnayModali({ isOpen, talep, uygunEkipmanSayfasi, yukleniyor, hata, islemSuruyor, onKapat, onTekrarDene, onOnayla }: Props) {
    const [ekipmanId, setEkipmanId] = useState("");
    const ekipmanler = uygunEkipmanSayfasi?.icerik ?? [];

    useEffect(() => { if (isOpen) setEkipmanId(""); }, [isOpen, talep?.id]);

    const kapat = () => { if (!islemSuruyor) onKapat(); };
    const gonder = () => { if (ekipmanId) onOnayla(ekipmanId); };
    const personelAdi = talep ? `${talep.talepSahibi.ad} ${talep.talepSahibi.soyad}` : "";

    return <Modal isOpen={isOpen} onClose={kapat} showCloseButton={!islemSuruyor} className="max-w-lg p-6 sm:p-8">
        <div><h2 className="text-lg font-semibold text-gray-800 dark:text-white/90">Ekipman talebini onayla</h2><p className="mt-2 text-sm text-gray-500 dark:text-gray-400"><span className="font-medium text-gray-700 dark:text-gray-200">{personelAdi}</span> için uygun ekipmanı seçin. Zimmet kaydı backend tarafından oluşturulur.</p></div>
        <div className="mt-6">
            <label htmlFor="uygun-ekipman" className="mb-2 block text-sm font-medium text-gray-700 dark:text-gray-300">Uygun ekipman</label>
            {yukleniyor ? <p role="status" className="rounded-lg border border-gray-200 px-3 py-2.5 text-sm text-gray-500 dark:border-gray-700 dark:text-gray-400">Uygun ekipmanlar yükleniyor…</p> : hata ? <div className="rounded-lg border border-error-200 bg-error-50 px-3 py-3 text-sm text-error-700 dark:border-error-500/20 dark:bg-error-500/10 dark:text-error-400"><p role="alert">{hata}</p><button type="button" onClick={onTekrarDene} className="mt-2 font-medium underline">Tekrar dene</button></div> : ekipmanler.length === 0 ? <p className="rounded-lg border border-gray-200 px-3 py-2.5 text-sm text-gray-500 dark:border-gray-700 dark:text-gray-400">Bu talep için uygun, aktif ve boşta ekipman bulunmuyor.</p> : <select id="uygun-ekipman" value={ekipmanId} disabled={islemSuruyor} onChange={(event) => setEkipmanId(event.target.value)} className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-3 text-sm text-gray-800 outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90"><option value="">Ekipman seçin</option>{ekipmanler.map((ekipman) => <option key={ekipman.id} value={ekipman.id}>{talep?.ekipmanTipi} · {ekipman.marka}{ekipman.model ? ` ${ekipman.model}` : ""} · {ekipman.seriNo}</option>)}</select>}
        </div>
        <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end"><button type="button" onClick={kapat} disabled={islemSuruyor} className="rounded-lg border border-gray-300 px-4 py-2.5 text-sm font-medium text-gray-700 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:text-gray-300">Vazgeç</button><button type="button" onClick={gonder} disabled={yukleniyor || Boolean(hata) || ekipmanler.length === 0 || !ekipmanId || islemSuruyor} className="rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-brand-600 disabled:cursor-not-allowed disabled:opacity-50">{islemSuruyor ? "Onaylanıyor…" : "Talebi onayla"}</button></div>
    </Modal>;
}
