package com.jforce.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;

public record YorumRequest(
        //hem patch,hem post
        @NotBlank
        String icerik
) {
}
