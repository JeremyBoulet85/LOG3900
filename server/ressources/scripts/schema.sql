DROP SCHEMA IF EXISTS LOG3900 CASCADE;

CREATE SCHEMA IF NOT EXISTS LOG3900;

CREATE TABLE IF NOT EXISTS LOG3900.Account (
    id          SERIAL PRIMARY KEY NOT NULL,
    username    VARCHAR(20) NOT NULL,
    hashPwd     VARCHAR(100) NOT NULL,
    FirstName   VARCHAR(100) NOT NULL,
    LastName    VARCHAR(100) NOT NULL,
    avatar      TEXT DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS LOG3900.Game (
    id          SERIAL PRIMARY KEY NOT NULL,
    gamemode    VARCHAR(20) NOT NULL,
    date        VARCHAR(30) NOT NULL,
    elapsedTime INT NOT NULL,
    winner      INT NOT NULL REFERENCES LOG3900.Account
);

CREATE TABLE IF NOT EXISTS LOG3900.AccountGame (
    account_id  INT REFERENCES LOG3900.Account ON DELETE CASCADE,
    game_id     INT REFERENCES LOG3900.Game ON DELETE CASCADE,
    score       INT
);

CREATE TABLE IF NOT EXISTS LOG3900.Connection (
    account_id  INT REFERENCES LOG3900.Account ON DELETE CASCADE,
    is_login    BOOLEAN,
    times       VARCHAR(30)
);

CREATE TABLE IF NOT EXISTS LOG3900.Channel (
    id      VARCHAR(20) PRIMARY KEY NOT NULL,
    ts      TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS  LOG3900.AccountChannel (
    account_id  INT         REFERENCES LOG3900.Account ON DELETE CASCADE,
    channel_id  VARCHAR(20) REFERENCES LOG3900.Channel ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS LOG3900.Messages (
    id          INT         PRIMARY KEY GENERATED BY DEFAULT AS IDENTITY,
    parent_id   INTEGER     REFERENCES LOG3900.Messages,
    ts          VARCHAR(8)  NOT NULL,
    content     TEXT        NOT NULL,
    channel_id  VARCHAR(20) NOT NULL REFERENCES LOG3900.Channel ON DELETE CASCADE,
    account_id  INT         NOT NULL REFERENCES LOG3900.Account
);
CREATE INDEX ON LOG3900.Messages (parent_id, id);
