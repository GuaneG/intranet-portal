import { Modal } from "../ui/modal";

type IzinNotuModaliProps = {
    isOpen: boolean;
    personelAdi: string;
    not: string;
    baslik?: string;
    aciklama?: string;
    onKapat: () => void;
};

/** Uzun yönetici notlarını tablo satırını büyütmeden okunabilir biçimde gösterir. */
export default function IzinNotuModali({ isOpen, personelAdi, not, baslik = "Personel notu", aciklama, onKapat }: IzinNotuModaliProps) {
    return <Modal isOpen={isOpen} onClose={onKapat} className="max-w-2xl p-6 sm:p-8">
        <h2 className="text-lg font-semibold text-gray-800 dark:text-white/90">{baslik}</h2>
        <p className="mt-2 text-sm text-gray-500 dark:text-gray-400">{aciklama ?? `${personelAdi} adlı personelin izin talebine eklediği not.`}</p>
        <p className="mt-6 max-h-96 overflow-y-auto whitespace-pre-wrap break-all rounded-xl border border-gray-100 bg-gray-50 px-4 py-3 text-sm leading-6 text-gray-700 dark:border-gray-800 dark:bg-white/[0.03] dark:text-gray-300">{not}</p>
        <div className="mt-6 flex justify-end"><button type="button" onClick={onKapat} className="rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-brand-600">Kapat</button></div>
    </Modal>;
}
