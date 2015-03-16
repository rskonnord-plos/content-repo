
#
# Start tracking the Content Repo Schema Versions
# The string in schema_ver will indicate the last 
# migration script that was executed in the database.
# New versions are added with INSERT so an audit 
# trail of migration scripts will be created in 
# temporal ordering.
#
CREATE TABLE IF NOT EXISTS CREPO_SCHEMA_INFO (
    timestamp timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    schema_ver VARCHAR (100) NOT NULL
);

#
# Create the table representing Collections of Objects.
#
CREATE TABLE IF NOT EXISTS collections (
    id INTEGER NOT NULL AUTO_INCREMENT,
    bucketId INTEGER NOT NULL,
    collkey VARCHAR (255) NOT NULL,
    versionChecksum VARCHAR (255) NOT NULL,
    timestamp timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    versionNumber VARCHAR (255) NOT NULL,
    status TINYINT DEFAULT 0 NOT NULL,
    tag VARCHAR (200),
    creationDate TIMESTAMP DEFAULT 0,
    UNIQUE KEY keySum (bucketId, collkey, versionChecksum),
    PRIMARY KEY (id),
    FOREIGN KEY (bucketId) REFERENCES buckets(bucketId)
);

#
# Create a table to track Object IDs associated with
# a collection.
#
CREATE TABLE IF NOT EXISTS collectionObject (
    collectionId INTEGER NOT NULL,
    objectId INTEGER NOT NULL,
    UNIQUE KEY keySum (collectionId, objectId),
    FOREIGN KEY (collectionId) REFERENCES collections(id),
    FOREIGN KEY (objectId) REFERENCES objects(id)
);

#
# Buckets are being update with creation dates and last mod dates.
#
ALTER TABLE buckets 
  ADD COLUMN timestamp timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE buckets 
  ADD COLUMN creationDate TIMESTAMP DEFAULT 0;

UPDATE buckets SET creationDate = CURRENT_TIMESTAMP;

#
# Objects are getting creation dates and a version checksum.
#
ALTER TABLE objects 
  ADD COLUMN creationDate TIMESTAMP DEFAULT 0;

ALTER TABLE objects 
  ADD COLUMN versionChecksum VARCHAR (255) NOT NULL;

#
# updateCreationDate - procedure to update the 
#     newly created creation date with the current
#     timestamp value;
#
# updateVerChecksum - procedure to backfill the new
#    version checksum with a sha1 checksum.
#

DELIMITER //
DROP PROCEDURE IF EXISTS updateCreationDate//
CREATE PROCEDURE `updateCreationDate` ()

BEGIN
    DECLARE v_finished INTEGER DEFAULT 0;
    DECLARE ts_ TIMESTAMP;
    DECLARE id_ INT;
    DEClARE ts_cursor CURSOR FOR 
       SELECT id, timestamp FROM objects;

    DECLARE CONTINUE HANDLER 
       FOR NOT FOUND SET v_finished = 1;
    
    OPEN ts_cursor;
    update_cDate: LOOP
        FETCH ts_cursor into id_, ts_;

        IF v_finished = 1 THEN 
           LEAVE update_cDate;
        END IF;
      
        UPDATE objects SET creationDate=ts_, timestamp=ts_ where id = id_;

    END LOOP update_cDate;
    CLOSE ts_cursor;
END //

DROP PROCEDURE IF EXISTS updateVerChecksum//
CREATE PROCEDURE `updateVerChecksum` ()

BEGIN
    DECLARE v_finished INTEGER DEFAULT 0;
    DECLARE ts_ TIMESTAMP;
    DECLARE cd_ TIMESTAMP;
    DECLARE id_ INT;
    DECLARE tg_ VARCHAR(200);
    DECLARE ct_ VARCHAR(200);
    DECLARE dn_ VARCHAR(1000);
    DECLARE cs_ VARCHAR(255);
    DECLARE ky_ VARCHAR(255);
    DEClARE ts_cursor CURSOR FOR 
       SELECT id, timestamp, objkey, creationDate, tag, contentType, downloadName, checksum FROM objects;

    DECLARE CONTINUE HANDLER 
       FOR NOT FOUND SET v_finished = 1;
    
    OPEN ts_cursor;
    update_cDate: LOOP
        FETCH ts_cursor into id_, ts_, ky_, cd_, tg_, ct_, dn_, cs_;

        IF v_finished = 1 THEN 
           LEAVE update_cDate;
        END IF;
        IF ISNULL(tg_)  THEN
          SET tg_ = "";
        END IF;
        IF ISNULL(ct_) THEN
          SET ct_ = "";
        END IF;  
        IF ISNULL(dn_) THEN
          SET dn_ = "";
        END IF;     
        UPDATE objects SET versionChecksum=SHA1(CONCAT(ky_, cd_, tg_, ct_, dn_, cs_)), timestamp=ts_ where id = id_;

    END LOOP update_cDate;
    CLOSE ts_cursor;
END //
DELIMITER ;

CALL updateCreationDate();
CALL updateVerChecksum();

# INSERT the version string. This should happen last.
# The temporal order will indicate which scripts have been 
# run to update this database.
INSERT CREPO_SCHEMA_INFO SET schema_ver = '01-add-collections';


    
