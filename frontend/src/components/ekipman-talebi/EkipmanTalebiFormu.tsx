import type { FormEvent } from "react";
import type { EkipmanTalebiFormu as FormDegerleri, EkipmanTipi } from "../../types/ekipman";

type Props = {
    form: FormDegerleri;
    ekipmanTipleri: EkipmanTipi[];
    tiplerYukleniyor: boolean;
    tiplerHatasi: string | null;
    formHatasi: string | null;
    gonderimSuruyor: boolean;
    duzenlemeModunda: boolean;
    onDegisiklik: (alan: keyof FormDegerleri, deger: string) => void;
    onTekrarDene: () => void;
    onIptal: () => void;
    onSubmit: (event: FormEvent<HTMLFormElement>) => void;
};

/** Personel ve HR'nin yalnız kendi adına ekipman talebi oluşturduğu veya güncellediği form. */
export default function EkipmanTalebiFormu({ form, ekipmanTipleri, tiplerYukleniyor, tiplerHatasi, formHatasi, gonderimSuruyor, duzenlemeModunda, onDegisiklik, onTekrarDene, onIptal, onSubmit }: Props) {
    const tipSecimiDevreDisi = tiplerYukleniyor || tiplerHatasi !== null || ekipmanTipleri.length === 0;

    return <section className="rounded-2xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-white/[0.03]">
        <div className="border-b border-gray-100 px-5 py-5 sm:px-6 dark:border-gray-800">
            <h3 className="text-base font-semibold text-gray-800 dark:text-white/90">{duzenlemeModunda ? "Ekipman talebini düzenle" : "Yeni ekipman talebi"}</h3>
            <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">İhtiyacınız olan aktif ekipman tipini seçin; açıklama eklemek opsiyoneldir.</p>
        </div>
        <form onSubmit={onSubmit} className="space-y-5 px-5 py-5 sm:px-6">
            <div>
                <label htmlFor="ekipman-tipi" className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">Ekipman tipi</label>
                <select id="ekipman-tipi" value={form.ekipmanTipiId} disabled={gonderimSuruyor || tipSecimiDevreDisi} onChange={(event) => onDegisiklik("ekipmanTipiId", event.target.value)} className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90">
                    <option value="">{tiplerYukleniyor ? "Ekipman tipleri yükleniyor…" : "Ekipman tipi seçin"}</option>
                    {ekipmanTipleri.map((tip) => <option key={tip.id} value={tip.id}>{tip.ad}</option>)}
                </select>
                {tiplerHatasi && <p role="alert" className="mt-2 text-sm text-error-600 dark:text-error-400">{tiplerHatasi} <button type="button" onClick={onTekrarDene} disabled={gonderimSuruyor} className="font-medium underline disabled:cursor-not-allowed disabled:opacity-50">Tekrar dene</button></p>}
                {!tiplerYukleniyor && !tiplerHatasi && ekipmanTipleri.length === 0 && <p className="mt-2 text-sm text-gray-500 dark:text-gray-400">Talep oluşturulabilecek aktif ekipman tipi bulunmuyor.</p>}
            </div>
            <div>
                <label htmlFor="ekipman-aciklama" className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">Açıklama <span className="font-normal text-gray-500 dark:text-gray-400">(opsiyonel)</span></label>
                <textarea id="ekipman-aciklama" rows={3} maxLength={255} value={form.aciklama} disabled={gonderimSuruyor} onChange={(event) => onDegisiklik("aciklama", event.target.value)} className="w-full rounded-lg border border-gray-300 bg-transparent px-4 py-3 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90" />
                <p className="mt-1.5 text-right text-xs text-gray-500 dark:text-gray-400">{form.aciklama.length}/255</p>
            </div>
            {formHatasi && <p role="alert" className="text-sm text-error-600 dark:text-error-400">{formHatasi}</p>}
            <div className="flex flex-col-reverse gap-3 sm:flex-row sm:items-center">
                {duzenlemeModunda && <button type="button" onClick={onIptal} disabled={gonderimSuruyor} className="rounded-lg border border-gray-300 bg-white px-4 py-2.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300">Vazgeç</button>}
                <button type="submit" disabled={gonderimSuruyor || tipSecimiDevreDisi} className="rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-brand-600 disabled:cursor-not-allowed disabled:opacity-50">{gonderimSuruyor ? "Kaydediliyor…" : duzenlemeModunda ? "Talebi güncelle" : "Talep oluştur"}</button>
            </div>
        </form>
    </section>;
}
