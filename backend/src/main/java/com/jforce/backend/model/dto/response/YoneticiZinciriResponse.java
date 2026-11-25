package com.jforce.backend.model.dto.response;

import java.util.List;

public record YoneticiZinciriResponse(
        List<YoneticiZinciriElemaniResponse> yoneticiler
) {
}
