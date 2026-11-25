/** Yönetici ilişkisi etiketi, ilişkiyi görme hakkı olan roller için gösterilir. */
export function yoneticiEtiketiGorunurMu(rol) {
    return rol === "PERSONEL" || rol === "YONETICI" || rol === "HR";
}

/** Personel departman sınırını değiştiremez; diğer roller filtreyi değiştirebilir. */
export function departmanFiltresiSecilebilirMi(rol) {
    return rol === "YONETICI" || rol === "HR" || rol === "ADMIN";
}

/** Her rol, backend'in kendi yetki kapsamıyla döndürdüğü hiyerarşiyi görür. */
export function hiyerarsiTablosuGosterilmeli(rol) {
    return (
        rol === "PERSONEL" ||
        rol === "YONETICI" ||
        rol === "HR" ||
        rol === "ADMIN"
    );
}
