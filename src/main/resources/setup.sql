
CREATE TABLE IF NOT EXISTS buckets (
  bucketId INTEGER GENERATED BY DEFAULT AS IDENTITY(START WITH 0) PRIMARY KEY,
  bucketName VARCHAR(1000) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS assets (
  id INTEGER GENERATED BY DEFAULT AS IDENTITY(START WITH 0) PRIMARY KEY,
  bucketId INTEGER REFERENCES buckets NOT NULL,
  key VARCHAR (1000) NOT NULL,
  checksum VARCHAR (1000) NOT NULL,
  timestamp timestamp NOT NULL,
  url VARCHAR (1000), -- servable path to asset mostly for mogile
  downloadName VARCHAR (1000),  -- the name of the file when downloaded
  contentType VARCHAR (200), -- the mime type
  size INTEGER NOT NULL,  -- the file size in bytes
  tag VARCHAR (200),
  status TINYINT DEFAULT 0 NOT NULL, -- 0: used, 1: deleted
  versionNumber INTEGER NOT NULL
);

DROP INDEX keysum IF EXISTS;
CREATE UNIQUE INDEX keysum ON assets(bucketId, key, versionNumber);
