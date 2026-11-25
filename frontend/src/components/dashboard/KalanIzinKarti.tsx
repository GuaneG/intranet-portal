import Chart from "react-apexcharts";
import type { ApexOptions } from "apexcharts";
import { Link } from "react-router";
import { izinBakiyesiYuzdesi, izinGununuBicimlendir } from "../../lib/dashboardIzinBakiyesi";
import type { IzinBakiyesi } from "../../types/dashboard";

type KalanIzinKartiProps = {
    izinBakiyesi: IzinBakiyesi | null;
    yukleniyor: boolean;
};

/** Dashboard özetindeki backend hesaplı izin bakiyesini gösterir. */
export default function KalanIzinKarti({ izinBakiyesi, yukleniyor }: KalanIzinKartiProps) {
    if (!yukleniyor && izinBakiyesi === null) return null;

    if (yukleniyor) {
        return <div aria-label="İzin bakiyesi yükleniyor" className="animate-pulse rounded-2xl border border-gray-200 bg-white p-6 dark:border-gray-800 dark:bg-white/[0.03]"><div className="h-5 w-40 rounded bg-gray-100 dark:bg-white/[0.08]" /><div className="mx-auto mt-6 size-44 rounded-full bg-gray-100 dark:bg-white/[0.08]" /><div className="mt-6 grid grid-cols-3 gap-3">{Array.from({ length: 3 }, (_, index) => <div key={index} className="h-10 rounded bg-gray-100 dark:bg-white/[0.08]" />)}</div></div>;
    }

    const yuzde = izinBakiyesiYuzdesi(izinBakiyesi);
    const options: ApexOptions = {
        colors: ["#465FFF"],
        chart: { fontFamily: "Outfit, sans-serif", type: "radialBar", height: 230, sparkline: { enabled: true } },
        plotOptions: { radialBar: { startAngle: -90, endAngle: 90, hollow: { size: "72%" }, track: { background: "#E4E7EC", strokeWidth: "100%", margin: 5 }, dataLabels: { name: { show: false }, value: { fontSize: "30px", fontWeight: "600", offsetY: -28, color: "#1D2939", formatter: () => `%${izinGununuBicimlendir(yuzde)}` } } } },
        fill: { type: "solid", colors: ["#465FFF"] },
        stroke: { lineCap: "round" },
    };

    const alanlar = [
        { etiket: "Toplam Hak", deger: izinBakiyesi.toplamHak },
        { etiket: "Kullanılan", deger: izinBakiyesi.kullanilan },
        { etiket: "Kalan", deger: izinBakiyesi.kalan },
    ];

    return <Link to="/talepler/izinlerim" aria-label="İzin taleplerimi aç" className="group block rounded-2xl border border-gray-200 bg-white transition-colors hover:border-brand-300 hover:bg-brand-50/30 focus:outline-none focus:ring-3 focus:ring-brand-500/10 dark:border-gray-800 dark:bg-white/[0.03] dark:hover:border-brand-500/50 dark:hover:bg-brand-500/5">
        <div className="px-5 pb-5 pt-5 sm:px-6 sm:pt-6">
            <div className="flex flex-wrap items-start justify-between gap-3"><div><h2 className="text-lg font-semibold text-gray-800 dark:text-white/90">Kalan İzin Hakkın</h2><p className="mt-1 text-sm text-gray-500 dark:text-gray-400">Toplam izin hakkına göre kullanılabilir bakiyen.</p></div><span className="rounded-full bg-warning-50 px-3 py-1 text-xs font-medium text-warning-600 dark:bg-warning-500/15 dark:text-orange-400">Onay bekleyen: {izinGununuBicimlendir(izinBakiyesi.onayBekleyen)} gün</span></div>
            <div className="mx-auto max-w-[260px]"><Chart options={options} series={[yuzde]} type="radialBar" height={230} /></div>
        </div>
        <div className="grid grid-cols-3 border-t border-gray-100 dark:border-gray-800">{alanlar.map((alan) => <div key={alan.etiket} className="px-3 py-4 text-center sm:px-4"><p className="text-xs text-gray-500 dark:text-gray-400">{alan.etiket}</p><p className="mt-1 text-base font-semibold text-gray-800 dark:text-white/90">{izinGununuBicimlendir(alan.deger)} gün</p></div>)}</div>
    </Link>;
}
