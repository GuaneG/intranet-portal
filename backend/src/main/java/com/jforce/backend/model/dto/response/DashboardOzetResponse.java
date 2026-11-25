package com.jforce.backend.model.dto.response;

import java.util.List;

public record DashboardOzetResponse(
        List<DogumGunuItem>     bugunDogumGunleri,
        long                    aktifEkipUyesiSayisi,
        List<EkipDiziniResponse> ekipDiziniOzeti,
        List<DuyuruBaslikItem> sonDuyurular,
        IzinBakiyesiOzetResponse izinBakiyesi,
        Integer                 bugunIzinliSayisi,
        List<OdaDolulukItem>    odaDoluluk,
        List<TakdirItem>        sonTakdirler,
        List<BekleyenTalepItem> bekleyenTaleplerim
) {
}
