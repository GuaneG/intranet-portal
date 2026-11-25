package com.jforce.backend.model.dto.response;

import com.jforce.backend.model.enums.OdaAnlikDurum;

import java.util.List;

public record OdaDolulukItem(
        Integer odaId,
        String odaAd,
        Integer kapasite,
        OdaAnlikDurum anlikDurum,
        Integer dolulukYuzdesi,
        List<OdaDoluAralikResponse> doluAraliklar
) {
}
