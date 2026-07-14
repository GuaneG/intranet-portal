package com.jforce.backend.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public record DashboardOzetResponse(
        List<DogumGunuItem>     bugunDogumGunleri,
        List<DuyuruBaslikItem> sonDuyurular,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Integer                 kalanIzin,          // yönetici/admin'de null,canlı hesapla
        Integer                 bugunIzinliSayisi,
        List<OdaDolulukItem>    odaDoluluk,
        List<TakdirItem>        sonTakdirler,
        List<BekleyenTalepItem> bekleyenTaleplerim
) {
}
