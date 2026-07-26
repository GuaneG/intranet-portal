package com.jforce.backend.model.enums;

//Audit'lenen olay türleri, üstüne eklemeler yapabilirsin.

public enum AuditEylem {
    LOGIN_BASARILI,
    LOGIN_BASARISIZ,
    LOGOUT,
    TOKEN_REUSE_ALARM
}
