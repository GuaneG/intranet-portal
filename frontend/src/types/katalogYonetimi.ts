/**
 * Yönetim ekranındaki iki bağımsız lookup kataloğunu ayırt eder.
 */
export type KatalogTuru = "departman" | "yetenek";

/**
 * Backend LookUpResponse sözleşmesiyle bire bir katalog kaydı.
 */
export type KatalogKayit = {
    id: number;
    ad: string;
};
