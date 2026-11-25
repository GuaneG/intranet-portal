import ParolaDegistirForm from "../components/auth/ParolaDegistirForm";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";

export default function ProfilParola() {
    return (
        <>
            <PageMeta
                title="Parola Değiştir | İntranet Portal"
                description="İntranet Portal hesap parolanızı güvenli biçimde değiştirin."
            />

            <PageBreadcrumb pageTitle="Parola Değiştir" />

            <div className="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] lg:p-6">
                <div className="w-full max-w-xl">
                    <h3 className="mb-2 text-lg font-semibold text-gray-800 dark:text-white/90">
                        Hesap Güvenliği
                    </h3>

                    <p className="mb-6 text-sm leading-6 text-gray-500 dark:text-gray-400">
                        Parolanız başarıyla değiştirildiğinde güvenliğiniz için
                        oturumunuz kapatılır ve yeniden giriş yapmanız istenir.
                    </p>

                    <ParolaDegistirForm />
                </div>
            </div>
        </>
    );
}