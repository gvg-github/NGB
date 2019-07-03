CREATE TABLE IF NOT EXISTS CATGENOME.SHORT_URLS (
    ID    VARCHAR(50)   NOT NULL,
    CREATED_DATE      TIMESTAMP,
    URL    TEXT    NOT NULL
    );
CREATE INDEX SHORT_URLS_IDX ON CATGENOME.SHORT_URLS(CREATED_DATE);