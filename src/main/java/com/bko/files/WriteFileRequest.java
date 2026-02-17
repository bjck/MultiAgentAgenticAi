package com.bko.files;

import jakarta.validation.constraints.NotBlank;

public record WriteFileRequest(
        @NotBlank String path,
        String content
) {
}
