package com.chesscoach.web.dto;

public record CredentialsRequest(String nickname, String password, String inviteCode) {
}
