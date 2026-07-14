package com.jforce.backend.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OnayTalepResponse(
        String id,                  //talep id
        String personelAdi,         //ortak "hangi personelin talebi"
        TalepTuru tur,              //talep_turu identifier
        LocalDateTime islemTarihi,  //ortak
        LocalDateTime talepTarihi,  //ortak
        String talepNotu,           //ortak
        String durum,               //ortak
        LocalDate baslangicTarihi,  //izin
        LocalDate bitisTarihi,      //izin
        String izinTuru,            //izin
        String ekipmanTipi          //ekipman
) {
}
