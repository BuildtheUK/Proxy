CREATE TABLE IF NOT EXISTS regions
(
	region		VARCHAR(16)		NOT NULL,
	status      ENUM('default','public','locked','open','blocked','plot',
	            'inactive')     NOT NULL,
	PRIMARY KEY (region)
);

CREATE TABLE IF NOT EXISTS region_members
(
    region      VARCHAR(16)     NOT NULL,
    uuid        CHAR(36)        NOT NULL,
    is_owner    TINYINT(1)      NULL DEFAULT 0,
    last_enter  BIGINT          NOT NULL,
    tag         VARCHAR(64)     NULL DEFAULT NULL,
    coordinate_id   INT         NOT NULL,
    pinned      TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY(region,uuid)
);

CREATE TABLE IF NOT EXISTS region_logs
(
    region      VARCHAR(16)     NOT NULL,
    uuid        CHAR(36)        NOT NULL,
    is_owner    TINYINT(1)      NULL DEFAULT 0,
    start_time  BIGINT          NOT NULL,
    end_time    BIGINT          NULL DEFAULT 0,
    PRIMARY KEY(region,uuid,start_time)
);

CREATE TABLE IF NOT EXISTS region_requests
(
    region      VARCHAR(16)     NOT NULL,
    uuid        CHAR(36)        NOT NULL,
    owner       CHAR(36)        NULL DEFAULT NULL,
    staff_accept    TINYINT(1)  NULL DEFAULT 1,
    owner_accept    TINYINT(1)  NULL DEFAULT 1,
    coordinate_id   INT         NOT NULL,
    UNIQUE(region,uuid)
);

CREATE TABLE IF NOT EXISTS region_invites
(
    region      VARCHAR(16)     NOT NULL,
    owner       CHAR(36)        NOT NULL,
    uuid        CHAR(36)        NOT NULL,
    PRIMARY KEY(region,uuid)
);