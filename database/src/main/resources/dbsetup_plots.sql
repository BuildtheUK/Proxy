CREATE TABLE IF NOT EXISTS plot_data
(
	id          INT         AUTO_INCREMENT,
	status      ENUM('unclaimed',
	'claimed','submitted',
	'completed','deleted')  NOT NULL,
	size        INT         NOT NULL,
	difficulty  INT         NOT NULL,
	location    VARCHAR(64) NOT NULL,
	coordinate_id   INT     NOT NULL DEFAULT 0,
	PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS plot_members
(
    id          INT         NOT NULL,
    uuid        CHAR(36)    NOT NULL,
    is_owner    TINYINT(1)  NULL DEFAULT 0,
    last_enter  BIGINT      NOT NULL,
    inactivity_notice  TINYINT(1)  NOT NULL DEFAULT 0,
    PRIMARY KEY(id, uuid)
);

CREATE TABLE IF NOT EXISTS plot_corners
(
    id          INT         NOT NULL,
    corner      INT         NOT NULL,
    x           INT         NOT NULL,
    z           INT         NOT NULL,
    PRIMARY KEY(id,corner)
);

CREATE TABLE IF NOT EXISTS plot_invites
(
    id          INT         NOT NULL,
    owner       CHAR(36)    NOT NULL,
    uuid        CHAR(36)    NOT NULL,
    PRIMARY KEY(id,uuid)
);

CREATE TABLE IF NOT EXISTS plot_submission
(
    plot_id     INT         NOT NULL,
    submit_time BIGINT      NOT NULL,
    status      ENUM('submitted','under review','awaiting verification','under verification') NOT NULL,
    last_query  BIGINT      NULL DEFAULT 0,
    PRIMARY KEY (plot_id),
    CONSTRAINT fk_plot_submission_1 FOREIGN KEY(plot_id) REFERENCES plot_data(id)
);

CREATE TABLE IF NOT EXISTS plot_review
(
    id          INT         AUTO_INCREMENT,
    plot_id     INT         NOT NULL,
    uuid        CHAR(36)    NOT NULL,
    reviewer    CHAR(36)    NOT NULL,
    attempt     INT         NOT NULL,
    review_time BIGINT      NOT NULL,
    accepted    TINYINT(1)  NOT NULL,
    completed   TINYINT(1)  NOT NULL,
    PRIMARY KEY(id),
    CONSTRAINT fk_plot_review_1 FOREIGN KEY(plot_id) REFERENCES plot_data(id)
);

CREATE TABLE IF NOT EXISTS plot_category_feedback
(
    review_id   INT                         NOT NULL,
    category    VARCHAR(64)                 NOT NULL,
    selection   ENUM('GOOD','OK','POOR','NONE')    NOT NULL,
    book_id     INT                         NOT NULL DEFAULT 0,
    PRIMARY KEY(review_id,category),
    CONSTRAINT fk_plot_category_feedback_1 FOREIGN KEY(review_id) REFERENCES plot_review(id)
);

CREATE TABLE IF NOT EXISTS plot_verification
(
    id              INT             AUTO_INCREMENT,
    review_id       INT             NOT NULL,
    verifier        CHAR(36)        NOT NULL,
    accepted_old    TINYINT(1)      NOT NULL,
    accepted_new    TINYINT(1)      NOT NULL,
    PRIMARY KEY(id),
    CONSTRAINT fk_plot_verification_1 FOREIGN KEY(review_id) REFERENCES plot_review(id)
);

CREATE TABLE IF NOT EXISTS plot_verification_category
(
    verification_id     INT                         NOT NULL,
    category            VARCHAR(64)                 NOT NULL,
    selection_old       ENUM('GOOD','OK','POOR')    NOT NULL,
    selection_new       ENUM('GOOD','OK','POOR')    NOT NULL,
    book_id_old         INT                         NOT NULL DEFAULT 0,
    book_id_new         INT                         NOT NULL DEFAULT 0,
    PRIMARY KEY(verification_id,category),
    CONSTRAINT fk_plot_verification_category_1 FOREIGN KEY(verification_id) REFERENCES plot_verification(id)
);

CREATE TABLE IF NOT EXISTS book_data
(
    id          INT         NOT NULL,
    page        INT         NOT NULL,
    contents    VARCHAR(798)    NOT NULL,
    PRIMARY KEY(id, page)
);

CREATE TABLE IF NOT EXISTS tutorial_recommendations
(
    plot_id     INT         NOT NULL,
    recommendation_id INT         NOT NULL,
    PRIMARY KEY (plot_id, recommendation_id),
    CONSTRAINT fk_tutorial_recommendations_1 FOREIGN KEY(plot_id) REFERENCES plot_data(id)
);

CREATE TABLE IF NOT EXISTS location_data
(
    name        VARCHAR(64) NOT NULL,
    alias       VARCHAR(128)    NOT NULL,
    server      VARCHAR(64) NOT NULL,
    coordMin    INT         NOT NULL,
    coordMax    INT         NOT NULL,
    xTransform  INT         NOT NULL,
    zTransform  INT         NOT NULL,
    PRIMARY KEY(name)
);

CREATE TABLE IF NOT EXISTS regions
(
    region      VARCHAR(16) NOT NULL,
    server      VARCHAR(64) NOT NULL,
    location    VARCHAR(64) NOT NULL,
    PRIMARY KEY(region)
);

CREATE TABLE IF NOT EXISTS zones
(
    id          INT         AUTO_INCREMENT,
    location    VARCHAR(64) NOT NULL,
    status      ENUM('open','closed')   NULL DEFAULT 'open',
    expiration  BIGINT      NOT NULL,
    is_public      TINYINT(1)  NULL DEFAULT 0,
    PRIMARY KEY(id)
);

CREATE TABLE IF NOT EXISTS zone_members
(
    id          INT         NOT NULL,
    uuid        CHAR(36)    NOT NULL,
    is_owner    TINYINT(1)  NULL DEFAULT 0,
    PRIMARY KEY(id, uuid)
);

CREATE TABLE IF NOT EXISTS zone_invites
(
    id          INT         NOT NULL,
    owner       CHAR(36)    NOT NULL,
    uuid        CHAR(36)    NOT NULL,
    PRIMARY KEY(id,uuid)
);

CREATE TABLE IF NOT EXISTS zone_corners
(
    id          INT         NOT NULL,
    corner      INT         NOT NULL,
    x           INT         NOT NULL,
    z           INT         NOT NULL,
    PRIMARY KEY(id,corner)
);

CREATE TABLE IF NOT EXISTS reviewers
(
    id          INT         AUTO_INCREMENT,
    uuid        CHAR(36)    NOT NULL,
    reputation  DECIMAL(5,2)    NOT NULL DEFAULT 0,
    PRIMARY KEY(id)
);