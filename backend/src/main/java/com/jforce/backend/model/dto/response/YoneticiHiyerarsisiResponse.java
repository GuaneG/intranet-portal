package com.jforce.backend.model.dto.response;

import java.util.List;

public record YoneticiHiyerarsisiResponse(
        LookUpResponse aktorDepartman,
        List<HiyerarsiDugumResponse> kokler
) {
}
