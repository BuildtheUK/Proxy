CREATE TABLE IF NOT EXISTS unique_data
(
    data_key    VARCHAR(256)    NOT NULL,
    data_value  VARCHAR(256)    NOT NULL,
    PRIMARY KEY(data_key)
);

CREATE TABLE IF NOT EXISTS server_data
(
    name        VARCHAR(64)   NOT NULL,
    type        ENUM('PLOT','EARTH',
    'LOBBY','TUTORIAL')     NOT NULL,
    online      TINYINT(1)      NULL DEFAULT 0,
    PRIMARY KEY(name)
);

CREATE TABLE IF NOT EXISTS coordinates
(
    id          INT         AUTO_INCREMENT,
    server      VARCHAR(64) NOT NULL,
    world       VARCHAR(64) NOT NULL,
    x           DOUBLE      NOT NULL,
    y           DOUBLE      NULL DEFAULT 0.0,
    z           DOUBLE      NOT NULL,
    yaw         FLOAT       NULL DEFAULT 0.0,
    pitch       FLOAT       NULL DEFAULT 0.0,
    PRIMARY KEY(id),
    CONSTRAINT fk_coordinates_1 FOREIGN KEY(server) REFERENCES server_data(name)
);

CREATE TABLE IF NOT EXISTS player_data
(
    uuid       CHAR(36)      NOT NULL,
    name      VARCHAR(16)       NOT NULL,
    last_online    BIGINT       NOT NULL,
    last_submit    BIGINT       NOT NULL,
    navigator   TINYINT(1)      NOT NULL DEFAULT 1,
    teleport_enabled    TINYINT(1)  NOT NULL DEFAULT 1,
    nightvision_enabled TINYINT(1)  NOT NULL DEFAULT 0,
    chat_channel    VARCHAR(64)  NOT NULL DEFAULT 'global',
    previous_coordinate INT     NOT NULL DEFAULT 0,
    player_skin     TEXT    NULL DEFAULT NULL,
    tips_enabled    TINYINT(1)  NOT NULL DEFAULT 1,
    display_name    TEXT    NULL DEFAULT NULL,
    PRIMARY KEY (uuid)
);

CREATE TABLE IF NOT EXISTS messages
(
    id          INT         AUTO_INCREMENT,
    recipient   CHAR(36)    NOT NULL,
    message     TEXT        NOT NULL,
    PRIMARY KEY(id),
    CONSTRAINT fk_messages_1 FOREIGN KEY(recipient) REFERENCES player_data(uuid)
);

CREATE TABLE IF NOT EXISTS join_events
(
    uuid        CHAR(36)        NOT NULL,
    event       VARCHAR(256)    NOT NULL,
    message     VARCHAR(256)    NULL DEFAULT NULL,
    PRIMARY KEY(uuid),
    CONSTRAINT fk_join_events_1 FOREIGN KEY(uuid) REFERENCES player_data(uuid)
);

CREATE TABLE IF NOT EXISTS server_events
(
    uuid        CHAR(36)        NULL DEFAULT NULL,
    server      VARCHAR(64)     NOT NULL,
    event       VARCHAR(256)    NOT NULL,
    message     VARCHAR(256)    NULL DEFAULT NULL,
    UNIQUE(uuid,event),
    CONSTRAINT fk_server_events_1 FOREIGN KEY(uuid) REFERENCES player_data(uuid)
);

CREATE TABLE IF NOT EXISTS statistics
(
    uuid        CHAR(36)        NOT NULL,
    on_date     DATE            NOT NULL,
    playtime    BIGINT          NULL DEFAULT 0,
    messages    INT             NULL DEFAULT 0,
    tpll        INT             NULL DEFAULT 0,
    PRIMARY KEY(uuid,on_date),
    CONSTRAINT fk_statistics_1 FOREIGN KEY(uuid) REFERENCES player_data(uuid)
);

CREATE TABLE IF NOT EXISTS online_users
(
    uuid            CHAR(36)        NOT NULL,
    join_time       BIGINT          NOT NULL,
    last_ping       BIGINT          NOT NULL,
    server          VARCHAR(64)     NOT NULL,
    primary_role    VARCHAR(64) NOT NULL,
    display_name    VARCHAR(64) NOT NULL,
    PRIMARY KEY(uuid),
    CONSTRAINT fk_online_users_1 FOREIGN KEY(uuid) REFERENCES player_data(uuid),
    CONSTRAINT fk_online_users_2 FOREIGN KEY(server) REFERENCES server_data(name)
);

CREATE TABLE IF NOT EXISTS location_subcategory
(
    id          INT             AUTO_INCREMENT,
    name        VARCHAR(128)    NOT NULL,
    category    ENUM('ENGLAND',
    'SCOTLAND','WALES',
    'NORTHERN_IRELAND','OTHER') NOT NULL,
    PRIMARY KEY(id)
);

CREATE TABLE IF NOT EXISTS location_data
(
    location    VARCHAR(128)        NOT NULL,
    category    ENUM('ENGLAND',
        'SCOTLAND','WALES',
        'NORTHERN_IRELAND','OTHER') NOT NULL,
    subcategory INT                 NULL DEFAULT NULL,
    coordinate  INT                 NOT NULL,
    suggested   TINYINT(1)          NULL DEFAULT 0,
    PRIMARY KEY(location),
    CONSTRAINT fk_location_data_1 FOREIGN KEY(coordinate) REFERENCES coordinates(id),
    CONSTRAINT fk_location_data_2 FOREIGN KEY(subcategory) REFERENCES location_subcategory(id)
);

CREATE TABLE IF NOT EXISTS location_marker
(
    id              INT             AUTO_INCREMENT,
    location        VARCHAR(128)    NULL DEFAULT NULL,
    subcategory     INT             NULL DEFAULT NULL,
    coordinate_id   INT             NOT NULL,
    PRIMARY KEY(id),
    CONSTRAINT fk_location_marker_1 FOREIGN KEY(location) REFERENCES location_data(location),
    CONSTRAINT fk_location_marker_2 FOREIGN KEY(subcategory) REFERENCES location_subcategory(id),
    CONSTRAINT fk_location_marker_3 FOREIGN KEY(coordinate_id) REFERENCES coordinates(id)
);

CREATE TABLE IF NOT EXISTS location_requests
(
    location    VARCHAR(128)            NOT NULL,
    category    ENUM('ENGLAND',
            'SCOTLAND','WALES',
            'NORTHERN_IRELAND','OTHER') NOT NULL,
    subcategory INT                     NULL DEFAULT NULL,
    coordinate  INT                     NOT NULL,
    PRIMARY KEY(location),
    CONSTRAINT fk_location_requests_1 FOREIGN KEY(coordinate) REFERENCES coordinates(id),
    CONSTRAINT fk_location_requests_2 FOREIGN KEY(subcategory) REFERENCES location_subcategory(id)
);

CREATE TABLE IF NOT EXISTS moderation
(
    uuid        VARCHAR(36)     NOT NULL,
    start_time  BIGINT          NOT NULL,
    end_time    BIGINT          NULL DEFAULT 9223372036854775807,
    reason      VARCHAR(256)    NOT NULL,
    type        ENUM('ban',
    'mute')                     NOT NULL,
    PRIMARY KEY(uuid,start_time),
    CONSTRAINT fk_moderation_1 FOREIGN KEY(uuid) REFERENCES player_data(uuid)
);

CREATE TABLE IF NOT EXISTS coins
(
    uuid        VARCHAR(36)     NOT NULL,
    coins       INT             NULL DEFAULT 0,
    PRIMARY KEY(uuid),
    CONSTRAINT fk_coins_1 FOREIGN KEY(uuid) REFERENCES player_data(uuid)
);

CREATE TABLE IF NOT EXISTS discord
(
    uuid        VARCHAR(36)     NOT NULL,
    discord_id  BIGINT          NOT NULL,
    PRIMARY KEY(uuid),
    CONSTRAINT fk_discord_1 FOREIGN KEY(uuid) REFERENCES player_data(uuid)
);

CREATE TABLE IF NOT EXISTS player_count
(
    log_time    BIGINT      NOT NULL,
    players         INT         NOT NULL,
    PRIMARY KEY(log_time)
);

CREATE TABLE IF NOT EXISTS home
(
    coordinate_id   INT     NOT NULL,
    uuid        VARCHAR(36) NOT NULL,
    name        VARCHAR(64) NULL,
    PRIMARY KEY(coordinate_id),
    CONSTRAINT fk_home_1 FOREIGN KEY(coordinate_id) REFERENCES coordinates(id),
    CONSTRAINT fk_home_2 FOREIGN KEY(uuid) REFERENCES player_data(uuid)
);

CREATE TABLE IF NOT EXISTS seasons
(
    id          VARCHAR(64)     NOT NULL,
    active      TINYINT(1)      NOT NULL DEFAULT 0,
    PRIMARY KEY(id)
);

CREATE TABLE IF NOT EXISTS progression
(
    season      VARCHAR(64)     NOT NULL,
    uuid        CHAR(36)        NOT NULL,
    lvl         INT             NOT NULL DEFAULT 1,
    exp         INT             NOT NULL DEFAULT 0,
    PRIMARY KEY(season, uuid),
    CONSTRAINT fk_progression_1 FOREIGN KEY(season) REFERENCES seasons(id),
    CONSTRAINT fk_progression_2 FOREIGN KEY(uuid) REFERENCES player_data(uuid)
);

CREATE TABLE IF NOT EXISTS buildings
(
    building_id    INT          AUTO_INCREMENT,
    coordinate_id  INT          NOT NULL,
    player_id       CHAR(36)      NOT NULL,
    is_public        BOOLEAN      NOT NULL DEFAULT TRUE,
    player_built     BOOLEAN      NOT NULL DEFAULT TRUE,
    time_added       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lat             DOUBLE DEFAULT 0,
    lon             DOUBLE DEFAULT 0,
    PRIMARY KEY(building_id),
    CONSTRAINT fk_buildings_1 FOREIGN KEY(coordinate_id) REFERENCES coordinates(id),
    CONSTRAINT fk_buildings_2 FOREIGN KEY(player_id) REFERENCES player_data(uuid)
    );

CREATE TABLE IF NOT EXISTS test_uknet_global.survey
(
    player VARCHAR(36),
    survey_completed_at TIMESTAMP,
    survey_last_edited TIMESTAMP,
    found_via_btuk BOOLEAN,
    found_via_bte BOOLEAN,
    found_via_btuk_external BOOLEAN,
    found_via_bte_external BOOLEAN,
    found_via_friend BOOLEAN,
    medium_tiktok BOOLEAN,
    medium_youtube_shorts BOOLEAN,
    medium_youtube_longform BOOLEAN,
    medium_instagram BOOLEAN,
    medium_search_engine_browsing BOOLEAN,
    medium_online_news BOOLEAN,
    medium_tvnews BOOLEAN,
    medium_physical_newspaper BOOLEAN,
    socials_tiktok BOOLEAN,
    socials_youtube_shorts BOOLEAN,
    socials_youtube_longform BOOLEAN,
    socials_instagram BOOLEAN,
    PRIMARY KEY(player),
    CONSTRAINT fk_survey_player FOREIGN KEY(player) REFERENCES player_data(uuid)
);

CREATE TABLE IF NOT EXISTS automod_flags
(
    uuid            CHAR(36)        NOT NULL,
    rule_id         VARCHAR(64)     NOT NULL,
    flag_timestamp  BIGINT          NOT NULL,
    message         TEXT            NOT NULL,
    message_word    VARCHAR(256)    NOT NULL,
    flagged_word    VARCHAR(256)    NOT NULL,
    PRIMARY KEY(uuid, flag_timestamp),
    CONSTRAINT fk_automod_flags_1 FOREIGN KEY(uuid) REFERENCES player_data(uuid)
);