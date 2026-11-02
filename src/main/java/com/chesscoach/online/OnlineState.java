package com.chesscoach.online;

import java.util.List;

/** Всё, что игрок видит на вкладке «Онлайн». */
public record OnlineState(String nickname, List<Friend> friends, List<String> incomingRequests,
                          List<String> outgoingRequests, List<String> incomingChallenges,
                          List<String> outgoingChallenges, boolean searching, String activeGameId,
                          String activeOpponent) {

    public record Friend(String nickname, boolean online, boolean playing) {
    }
}
