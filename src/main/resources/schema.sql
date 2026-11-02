CREATE TABLE IF NOT EXISTS players (
    player_key VARCHAR(40) PRIMARY KEY,
    nickname   VARCHAR(40) NOT NULL,
    salt       VARCHAR(64) NOT NULL,
    hash       VARCHAR(128) NOT NULL
);
CREATE TABLE IF NOT EXISTS friendships (
    player VARCHAR(40) NOT NULL,
    friend VARCHAR(40) NOT NULL,
    PRIMARY KEY (player, friend)
);
CREATE TABLE IF NOT EXISTS friend_requests (
    from_player VARCHAR(40) NOT NULL,
    to_player   VARCHAR(40) NOT NULL,
    PRIMARY KEY (from_player, to_player)
);
CREATE TABLE IF NOT EXISTS sessions (
    token_hash VARCHAR(64) PRIMARY KEY,
    player     VARCHAR(40) NOT NULL,
    created_at BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS online_games (
    id          VARCHAR(36) PRIMARY KEY,
    white       VARCHAR(40) NOT NULL,
    black       VARCHAR(40) NOT NULL,
    resigned_by VARCHAR(40),
    moves       TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS archived_games (
    id           VARCHAR(36) PRIMARY KEY,
    owner_white  VARCHAR(40),
    owner_black  VARCHAR(40),
    white_name   VARCHAR(60) NOT NULL,
    black_name   VARCHAR(60) NOT NULL,
    level        VARCHAR(20),
    status       VARCHAR(20) NOT NULL,
    resigned_by  VARCHAR(40),
    moves        TEXT NOT NULL,
    plies        INT NOT NULL,
    analysis     TEXT,
    saved_at     BIGINT NOT NULL
);
