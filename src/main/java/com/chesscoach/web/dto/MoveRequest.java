package com.chesscoach.web.dto;

import jakarta.validation.constraints.NotBlank;

public record MoveRequest(@NotBlank String uci) {
}
