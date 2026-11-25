package com.jforce.backend.model.dto.response;
/**
 * Yalnız {@code id} ve {@code ad} alanları yeterli olan genel lookup DTO'su.
 * Ek sözleşme alanı gereken kataloglar kendi response DTO'larını kullanır.
 */
public record LookUpResponse(
        Integer id,
        String ad
) {
}
