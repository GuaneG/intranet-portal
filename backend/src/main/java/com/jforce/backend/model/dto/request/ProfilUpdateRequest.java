package com.jforce.backend.model.dto.request;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record ProfilUpdateRequest(
        @Size(max = 50)
        String ad,
        @Size(max = 50)
        String soyad,
        LocalDate dogumTarihi,
        @Size (max = 100)
        @Email
        String ePosta,
        @Size(max = 100)
        String profilFoto
) {
}
