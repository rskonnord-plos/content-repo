
--DROP TABLE IF EXISTS assets;
--DROP TABLE IF EXISTS buckets;
--DROP INDEX IF EXISTS keysum;

CREATE TABLE IF NOT EXISTS buckets (
  id INTEGER GENERATED BY DEFAULT AS IDENTITY(START WITH 0) PRIMARY KEY,
  name VARCHAR(1000) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS assets (
  id INTEGER GENERATED BY DEFAULT AS IDENTITY(START WITH 0) PRIMARY KEY,
  bucketId INTEGER REFERENCES buckets NOT NULL,
  key VARCHAR (1000) NOT NULL,
  checksum VARCHAR (1000) NOT NULL,
  timestamp timestamp NOT NULL,
  url VARCHAR (1000),
  contentDisposition VARCHAR (1000),  -- the name of the file when downloaded
  contentType VARCHAR (200) NOT NULL, -- the mime type
  size INTEGER NOT NULL,  -- the file size in bytes
  tag VARCHAR (200)
);

DROP INDEX keysum IF EXISTS;
CREATE UNIQUE INDEX keysum ON assets(bucketId, key, checksum);

--INSERT INTO buckets(id, name) VALUES(0, 'production');
--INSERT INTO buckets(id, name) VALUES(1, 'stage');

MERGE INTO buckets USING (VALUES(0, 'production'), (1,'staging'))
  AS vals(id,name) on buckets.id = vals.id
  WHEN NOT MATCHED THEN INSERT VALUES vals.id, vals.name;
