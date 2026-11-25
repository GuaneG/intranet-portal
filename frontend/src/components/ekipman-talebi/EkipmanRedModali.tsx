import { useEffect, useState } from "react";
import { Modal } from "../ui/modal";
import { ekipmanKararNotunuDogrula } from "../../lib/ekipmanTalebi";

type Props = {
    isOpen: boolean;
    personelAdi: string;
    islemSuruyor: boolean;
    hata: string | null;
    onKapat: () => void;
    onReddet: (kararNotu: string) => void;
};

/** Ekipman kararındaki zorunlu red nedenini mevcut izin red modalı diliyle sunar. */
export default function EkipmanRedModali({ isOpen, personelAdi, islemSuruyor, hata, onKapat, onReddet }: Props) {
    const [kararNotu, setKararNotu] = useState("");
    const [formHatasi, setFormHatasi] = useState<string | null>(null);

    useEffect(() => {
        if (isOpen) {
            setKararNotu("");
            setFormHatasi(null);
        }
    }, [isOpen]);

    const kapat = () => {
        if (!islemSuruyor) onKapat();
    };

    const gonder = () => {
        const dogrulamaHatasi = ekipmanKararNotunuDogrula(kararNotu);
        if (dogrulamaHatasi) {
            setFormHatasi(dogrulamaHatasi);
            return;
        }

        onReddet(kararNotu.trim());
    };

    return <Modal isOpen={isOpen} onClose={kapat} showCloseButton={!islemSuruyor} className="max-w-lg p-6 sm:p-8">
        <div>
            <h2 className="text-lg font-semibold text-gray-800 dark:text-white/90">Ekipman talebini reddet</h2>
            <p className="mt-2 text-sm text-gray-500 dark:text-gray-400"><span className="font-medium text-gray-700 dark:text-gray-200">{personelAdi}</span> için red nedenini yazın. Bu not talep sahibine gösterilir.</p>
        </div>
        <div className="mt-6">
            <label htmlFor="ekipman-red-karar-notu" className="mb-2 block text-sm font-medium text-gray-700 dark:text-gray-300">Red nedeni</label>
            <textarea id="ekipman-red-karar-notu" value={kararNotu} maxLength={500} disabled={islemSuruyor} onChange={(event) => { setKararNotu(event.target.value); setFormHatasi(null); }} rows={5} className="w-full rounded-lg border border-gray-300 bg-transparent px-3 py-2.5 text-sm text-gray-800 outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90" aria-describedby="ekipman-red-karar-notu-yardim" />
            <div id="ekipman-red-karar-notu-yardim" className="mt-1 flex justify-between text-xs text-gray-500 dark:text-gray-400"><span>Zorunlu · en fazla 500 karakter</span><span>{kararNotu.length}/500</span></div>
            {(formHatasi || hata) && <p role="alert" className="mt-3 text-sm text-error-600 dark:text-error-400">{formHatasi || hata}</p>}
        </div>
        <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
            <button type="button" onClick={kapat} disabled={islemSuruyor} className="rounded-lg border border-gray-300 px-4 py-2.5 text-sm font-medium text-gray-700 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:text-gray-300">Vazgeç</button>
            <button type="button" onClick={gonder} disabled={islemSuruyor} className="rounded-lg bg-error-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-error-600 disabled:cursor-not-allowed disabled:opacity-50">{islemSuruyor ? "Reddediliyor…" : "Talebi reddet"}</button>
        </div>
    </Modal>;
}
