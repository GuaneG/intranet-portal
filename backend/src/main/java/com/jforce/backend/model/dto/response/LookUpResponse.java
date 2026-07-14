package com.jforce.backend.model.dto.response;
/*
GET	    /api/leave-types
GET	    /api/equipment-types
GET	    /api/departments
GET	    /api/skills	tüm roller
POST	/api/skills	tüm roller
bu methodların karşılığını bu tek DTO sağlıyor
bir dropdown listesi olarak işimizi görücek "<option value="{id}">{ad}</option>"
*/
public record LookUpResponse(
        Integer id,
        String ad
) {
}
