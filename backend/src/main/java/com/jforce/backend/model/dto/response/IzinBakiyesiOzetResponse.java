package com.jforce.backend.model.dto.response;

import java.math.BigDecimal;

public record IzinBakiyesiOzetResponse(
        BigDecimal toplamHak,
        BigDecimal kullanilan,
        BigDecimal onayBekleyen,
        BigDecimal kalan
) {
}
