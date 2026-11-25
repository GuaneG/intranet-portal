import type { FormEvent } from "react";
import type { IzinTalebiFormu as IzinTalebiFormuDegerleri, IzinTuru } from "../../types/izinTalebi";

type IzinTalebiFormuProps = {
    form: IzinTalebiFormuDegerleri;
    izinTurleri: IzinTuru[];
    izinTurleriYukleniyor: boolean;
    izinTurleriHatasi: string | null;
    onDegisiklik: (alan: keyof IzinTalebiFormuDegerleri, deger: string) => void;
    onIzinTurleriniTekrarDene: () => void;
    onSubmit: (event: FormEvent<HTMLFormElement>) => void;
    isGunuSayisi: number | null;
    onizlemeYukleniyor: boolean;
    onizlemeHatasi: string | null;
    gonderimSuruyor: boolean;
    formHatasi: string | null;
    bugun: string;
};

function gunSayisiniBicimlendir(deger: number) {
    return new Intl.NumberFormat("tr-TR", { maximumFractionDigits: 1 }).format(deger);
}

/** İzin oluşturma alanlarını ve backend'in hesapladığı iş günü önizlemesini sunar. */
export default function IzinTalebiFormu({
    form,
    izinTurleri,
    izinTurleriYukleniyor,
    izinTurleriHatasi,
    onDegisiklik,
    onIzinTurleriniTekrarDene,
    onSubmit,
    isGunuSayisi,
    onizlemeYukleniyor,
    onizlemeHatasi,
    gonderimSuruyor,
    formHatasi,
    bugun,
}: IzinTalebiFormuProps) {
    const izinTuruSecimiDevreDisi = gonderimSuruyor || izinTurleriYukleniyor || izinTurleriHatasi !== null;

    return <section className="rounded-2xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-white/[0.03]">
        <div className="border-b border-gray-100 px-5 py-5 dark:border-gray-800 sm:px-6">
            <h3 className="text-base font-semibold text-gray-800 dark:text-white/90">Yeni izin talebi</h3>
            <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">Tarihleri seçtiğinizde iş günü süresi tatiller ve hafta sonları dikkate alınarak hesaplanır.</p>
        </div>

        <form onSubmit={onSubmit} className="space-y-5 p-5 sm:p-6">
            <div className="grid gap-5 md:grid-cols-3">
                <div>
                    <label htmlFor="izin-turu" className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">İzin türü</label>
                    <select id="izin-turu" value={form.izinTuruId} disabled={izinTuruSecimiDevreDisi} onChange={(event) => onDegisiklik("izinTuruId", event.target.value)} className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90" required>
                        <option value="">{izinTurleriYukleniyor ? "İzin türleri yükleniyor…" : "İzin türü seçin"}</option>
                        {izinTurleri.map((izinTuru) => <option key={izinTuru.id} value={izinTuru.id}>{izinTuru.ad}</option>)}
                    </select>
                    {izinTurleriHatasi && <button type="button" disabled={gonderimSuruyor} onClick={onIzinTurleriniTekrarDene} className="mt-2 text-sm font-medium text-brand-500 underline disabled:cursor-not-allowed disabled:opacity-50">İzin türlerini tekrar dene</button>}
                </div>

                <div>
                    <label htmlFor="izin-baslangic" className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">Başlangıç tarihi</label>
                    <input id="izin-baslangic" type="date" min={bugun} value={form.baslangicTarihi} disabled={gonderimSuruyor} onChange={(event) => onDegisiklik("baslangicTarihi", event.target.value)} className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90" required />
                </div>

                <div>
                    <label htmlFor="izin-bitis" className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">Bitiş tarihi</label>
                    <input id="izin-bitis" type="date" min={form.baslangicTarihi || bugun} value={form.bitisTarihi} disabled={gonderimSuruyor} onChange={(event) => onDegisiklik("bitisTarihi", event.target.value)} className="h-11 w-full rounded-lg border border-gray-300 bg-transparent px-4 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90" required />
                </div>
            </div>

            <div>
                <label htmlFor="yoneticiye-not" className="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">Yöneticiye not <span className="font-normal text-gray-500 dark:text-gray-400">(opsiyonel)</span></label>
                <textarea id="yoneticiye-not" rows={3} maxLength={1000} value={form.yoneticiyeNot} disabled={gonderimSuruyor} onChange={(event) => onDegisiklik("yoneticiyeNot", event.target.value)} className="w-full rounded-lg border border-gray-300 bg-transparent px-4 py-3 text-sm text-gray-800 shadow-theme-xs outline-none focus:border-brand-300 focus:ring-3 focus:ring-brand-500/10 disabled:cursor-not-allowed disabled:opacity-50 dark:border-gray-700 dark:bg-gray-900 dark:text-white/90" />
                <p className="mt-1.5 text-right text-xs text-gray-500 dark:text-gray-400">{form.yoneticiyeNot.length}/1000</p>
            </div>

            <div className="rounded-xl border border-brand-100 bg-brand-50/60 px-4 py-3 dark:border-brand-500/20 dark:bg-brand-500/10">
                {onizlemeYukleniyor ? <p role="status" className="text-sm text-brand-700 dark:text-brand-300">İş günü hesaplanıyor…</p> : onizlemeHatasi ? <p role="alert" className="text-sm text-error-600 dark:text-error-400">{onizlemeHatasi}</p> : isGunuSayisi === null ? <p className="text-sm text-gray-600 dark:text-gray-300">İş günü sayısını görmek için başlangıç ve bitiş tarihini seçin.</p> : <p className="text-sm text-brand-800 dark:text-brand-200"><span className="font-semibold">İş günü önizlemesi:</span> {gunSayisiniBicimlendir(isGunuSayisi)} gün</p>}
            </div>

            {formHatasi && <p role="alert" className="text-sm text-error-600 dark:text-error-400">{formHatasi}</p>}

            <button type="submit" disabled={gonderimSuruyor || izinTuruSecimiDevreDisi} className="rounded-lg bg-brand-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-brand-600 disabled:cursor-not-allowed disabled:opacity-50">{gonderimSuruyor ? "Talep oluşturuluyor…" : "Talep oluştur"}</button>
        </form>
    </section>;
}
