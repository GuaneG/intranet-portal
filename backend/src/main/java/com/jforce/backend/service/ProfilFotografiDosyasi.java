package com.jforce.backend.service;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

public record ProfilFotografiDosyasi(Resource resource, MediaType mediaType) {
}
