import type { AuthRole } from "../../context/AuthContext";
import type { PersonelYonetimListeOgesi } from "../../types/personelYonetim";
import Badge from "../ui/badge/Badge";
import { Link } from "react-router";
import {
    Table,
    TableBody,
    TableCell,
    TableHeader,
    TableRow,
} from "../ui/table";

type PersonelYonetimTablosuProps = {
    personeller: PersonelYonetimListeOgesi[];
};

// Backend rol kodlarının kullanıcıya gösterilecek karşılıkları.
const ROL_GORUNUMU = {
    PERSONEL: {
        etiket: "Personel",
        renk: "light",
    },
    YONETICI: {
        etiket: "Yönetici",
        renk: "info",
    },
    HR: {
        etiket: "İnsan Kaynakları",
        renk: "warning",
    },
    ADMIN: {
        etiket: "Admin",
        renk: "primary",
    },
} as const satisfies Record<
    AuthRole,
    {
        etiket: string;
        renk: "primary" | "warning" | "info" | "light";
    }
>;

export default function PersonelYonetimTablosu({personeller,}: PersonelYonetimTablosuProps) {
    // Bu component yalnızca aldığı listeyi gösterir.
    // API ve state yönetimi sayfa component'ında kalır.
    return (
        <div className="overflow-hidden rounded-xl border border-gray-200 bg-white dark:border-white/[0.05] dark:bg-white/[0.03]">
            <div className="max-w-full overflow-x-auto">
                <Table className="min-w-[840px]">
                    <TableHeader className="border-b border-gray-100 dark:border-white/[0.05]">
                        <TableRow>
                            <TableCell
                                isHeader
                                className="px-5 py-3 text-start text-theme-xs font-medium text-gray-500 dark:text-gray-400"
                            >
                                Personel
                            </TableCell>

                            <TableCell
                                isHeader
                                className="px-5 py-3 text-start text-theme-xs font-medium text-gray-500 dark:text-gray-400"
                            >
                                Departman
                            </TableCell>

                            <TableCell
                                isHeader
                                className="px-5 py-3 text-start text-theme-xs font-medium text-gray-500 dark:text-gray-400"
                            >
                                Rol
                            </TableCell>

                            <TableCell
                                isHeader
                                className="px-5 py-3 text-start text-theme-xs font-medium text-gray-500 dark:text-gray-400"
                            >
                                Durum
                            </TableCell>
                            <TableCell
                                isHeader
                                className="px-5 py-3 text-start text-theme-xs font-medium text-gray-500 dark:text-gray-400"
                            >
                                İşlem
                            </TableCell>
                        </TableRow>
                    </TableHeader>

                    <TableBody className="divide-y divide-gray-100 dark:divide-white/[0.05]">
                        {personeller.map((personel) => {
                            const rolGorunumu = ROL_GORUNUMU[personel.rol];

                            return (
                                <TableRow key={personel.personelId}>
                                    <TableCell className="px-5 py-4 text-start sm:px-6">
                    <span className="block text-theme-sm font-medium text-gray-800 dark:text-white/90">
                      {personel.ad} {personel.soyad}
                    </span>
                                        <span className="mt-1 block text-theme-xs text-gray-500 dark:text-gray-400">
                      @{personel.kullaniciAdi}
                    </span>
                                    </TableCell>

                                    <TableCell className="px-5 py-4 text-start text-theme-sm text-gray-500 dark:text-gray-400">
                                        {personel.departmanAdi}
                                    </TableCell>

                                    <TableCell className="px-5 py-4 text-start">
                                        <Badge size="sm" color={rolGorunumu.renk}>
                                            {rolGorunumu.etiket}
                                        </Badge>
                                    </TableCell>

                                    <TableCell className="px-5 py-4 text-start">
                                        <Badge
                                            size="sm"
                                            color={personel.aktif ? "success" : "error"}
                                        >
                                            {personel.aktif ? "Aktif" : "Pasif"}
                                        </Badge>
                                    </TableCell>
                                    <TableCell className="px-5 py-4 text-start">
                                        <Link
                                            to={`/yonetim/personel/${encodeURIComponent(
                                                personel.personelId,
                                            )}`}
                                            className="text-sm font-medium text-brand-500 hover:text-brand-600 dark:text-brand-400 dark:hover:text-brand-300"
                                        >
                                            Detayı Gör
                                        </Link>
                                    </TableCell>
                                </TableRow>
                            );
                        })}
                    </TableBody>
                </Table>
            </div>
        </div>
    );
}