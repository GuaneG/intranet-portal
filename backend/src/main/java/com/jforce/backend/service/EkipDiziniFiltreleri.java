package com.jforce.backend.service;

import com.jforce.backend.exception.GecersizIstekException;

public record EkipDiziniFiltreleri(
        String arama,
        Integer departmanId,
        Integer yetenekId
) {

    public static EkipDiziniFiltreleri olustur(
            String arama,
            Integer departmanId,
            Integer yetenekId
    ) {
        if (departmanId != null && departmanId <= 0) {
            throw new GecersizIstekException(
                    "Departman ID pozitif olmalıdır."
            );
        }

        if (yetenekId != null && yetenekId <= 0) {
            throw new GecersizIstekException(
                    "Yetenek ID pozitif olmalıdır."
            );
        }

        String normalizeArama = arama == null ? null : arama.trim();
        if (normalizeArama != null && normalizeArama.isEmpty()) {
            normalizeArama = null;
        }

        return new EkipDiziniFiltreleri(
                normalizeArama,
                departmanId,
                yetenekId
        );
    }

    public boolean aktifMi() {
        return arama != null || departmanId != null || yetenekId != null;
    }
}
