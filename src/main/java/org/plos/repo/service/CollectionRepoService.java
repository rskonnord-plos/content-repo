/*
 * Copyright (c) 2006-2014 by Public Library of Science
 * http://plos.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.plos.repo.service;

import org.plos.repo.models.*;
import org.plos.repo.models.Object;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * This service handles all communication for collections with sqlservice
 */
public class CollectionRepoService extends BaseRepoService {

  private static final Logger log = LoggerFactory.getLogger(CollectionRepoService.class);

  /**
   * Returns a list of collections for the given bucket name <code>bucketName</code>. In case pagination
   * parameters <code>offset</code> and <code>limit</code> are not present, it loads the default pagination data.
   * @param bucketName a single String representing the bucket name in where to look the collection
   * @param offset an Integer used to determine the offset of the response
   * @param limit an Integer used to determine the limit of the response
   * @return a list of {@link org.plos.repo.models.Collection}
   * @throws org.plos.repo.service.RepoException
   */
  public List<Collection> listCollections(String bucketName, Integer offset, Integer limit) throws RepoException {

    if (offset == null)
      offset = 0;
    if (limit == null)
      limit = DEFAULT_PAGE_SIZE;

    try {

      validatePagination(offset, limit);

      sqlService.getConnection();

      validateBucketData(bucketName, sqlService);

      return sqlService.listCollections(bucketName, offset, limit);

    } catch (SQLException e) {
      throw new RepoException(e);
    } finally {
      sqlReleaseConnection();
    }

  }

  /**
   * Returns a collection identiied by <code>bucketName</code> and <code>key</code>. If <code>version</code> is null, it returns the latest
   * version available, if it is not, it returns the requested version.
   * @param bucketName a single String representing the bucket name in where to look the collection
   * @param key key a single String identifying the collection key
   * @param version an int value representing the version number of the collection
   * @return a collection {@link org.plos.repo.models.Collection} or null is the desired collection does not exists
   * @throws org.plos.repo.service.RepoException
   */
  public Collection getCollection(String bucketName, String key, Integer version) throws RepoException {

    Collection collection;

    try {
      sqlService.getConnection();

      if (key == null)
        throw new RepoException(RepoException.Type.NoCollectionKeyEntered);

      if (version == null)
        collection = sqlService.getCollection(bucketName, key);
      else
        collection = sqlService.getCollection(bucketName, key, version);

      if (collection == null)
        throw new RepoException(RepoException.Type.CollectionNotFound);

      collection.setVersions(this.getCollectionVersions(collection));

      return collection;

    } catch (SQLException e) {
      throw new RepoException(e);
    } finally {
      sqlReleaseConnection();
    }

  }

  /**
   * Returns a list of all collection versions for the given <code>collection</code>
   * @param collection a single {@link org.plos.repo.models.Collection}
   * @return a list of {@link org.plos.repo.models.Collection}
   * @throws org.plos.repo.service.RepoException
   */
  public List<Collection> getCollectionVersions(Collection collection) throws RepoException {

    try {
      sqlService.getConnection();
      return sqlService.listCollectionVersions(collection);
    } catch (SQLException e) {
      throw new RepoException(e);
    } finally {
      sqlReleaseConnection();
    }
  }

  /**
   * Deletes the collection define by <code>bucketName</code> , <code>key</code> , <code>version</code>
   * @param bucketName a single String identifying the bucket name where the collection is.
   * @param key a single String identifying the collection key
   * @param version an int value representing the version number of the collection
   * @throws org.plos.repo.service.RepoException
   */
  public void deleteCollection(String bucketName, String key, Integer version) throws RepoException {

    boolean rollback = false;

    try {

      sqlService.getConnection();

      if (key == null)
        throw new RepoException(RepoException.Type.NoCollectionKeyEntered);

      if (version == null)
        throw new RepoException(RepoException.Type.NoCollectionVersionEntered);

      rollback = true;

      if (sqlService.markCollectionDeleted(key, bucketName, version) == 0)
        throw new RepoException(RepoException.Type.CollectionNotFound);

      sqlService.transactionCommit();
      rollback = false;

    } catch (SQLException e) {
      throw new RepoException(e);
    } finally {

      if (rollback) {
        sqlRollback("object " + bucketName + ", " + key + ", " + version);
      }

      sqlReleaseConnection();

    }
  }

  protected void validatePagination(Integer offset, Integer limit) throws RepoException {
    if (offset < 0)
      throw new RepoException(RepoException.Type.InvalidOffset);

    if (limit <= 0 || limit > MAX_PAGE_SIZE)
      throw new RepoException(RepoException.Type.InvalidLimit);
  }

  protected void validateBucketData(String bucketName, SqlService sqlService) throws RepoException, SQLException{
    if (bucketName != null && sqlService.getBucket(bucketName) == null)
      throw new RepoException(RepoException.Type.BucketNotFound);
  }

  public Collection createCollection(CreateMethod method, SmallCollection smallCollection) throws RepoException {

    String key = smallCollection.getKey();
    String bucketName = smallCollection.getBucketName();
    Timestamp timestamp = smallCollection.getTimestamp();
    List<SmallObject> smallObjects = smallCollection.getObjects();

    Collection existingCollection;


    if (key == null)
      throw new RepoException(RepoException.Type.NoKeyEntered);

    if (bucketName == null)
      throw new RepoException(RepoException.Type.NoBucketEntered);

    try {
      existingCollection = getCollection(bucketName, key, null);
    } catch (RepoException e) {
      if (e.getType() == RepoException.Type.CollectionNotFound)
        existingCollection = null;
      else
        throw e;
    }

      verifyObjects(smallObjects);

      switch (method) {

        case NEW:
          if (existingCollection != null)
            throw new RepoException(RepoException.Type.CantCreateNewCollectionWithUsedKey);
          return createNewCollection(key, bucketName, timestamp, smallObjects);

        case VERSION:
          if (existingCollection == null)
            throw new RepoException(RepoException.Type.CantCreateCollectionVersionWithNoOrig);
          return updateCollection(key, bucketName, timestamp, existingCollection, smallObjects);

        case AUTO:
          if (existingCollection == null)
            return createNewCollection(key, bucketName, timestamp, smallCollection.getObjects());
          else
            return updateCollection(key, bucketName, timestamp, existingCollection, smallObjects);

        default:
          throw new RepoException(RepoException.Type.InvalidCreationMethod);
      }

    }

  private void verifyObjects(List<SmallObject> objects) throws RepoException {

    if (objects == null || objects.size() == 0 ){
      throw new RepoException(RepoException.Type.CantCreateCollectionWithNoObjects);
    }
  }

  private Collection createNewCollection(String key,
                                         String bucketName,
                                         Timestamp timestamp,
                                         List<SmallObject> objects) throws RepoException {

    Bucket bucket = null;

    try {
      sqlService.getConnection();
      bucket = sqlService.getBucket(bucketName);
    } catch (SQLException e) {
      throw new RepoException(e);
    } finally {
      sqlReleaseConnection();
    }

    if (bucket == null)
      throw new RepoException(RepoException.Type.BucketNotFound);

    return createCollection(key, bucketName, timestamp, bucket.bucketId, objects);
  }

  private Collection updateCollection(String key,
                                      String bucketName,
                                      Timestamp timestamp,
                                      Collection existingCollection,
                                      List<SmallObject> objects) throws RepoException {

    if (areCollectionsSimilar(key, bucketName, objects, existingCollection)){
      return existingCollection;
    }

    return createCollection(key, bucketName, timestamp, existingCollection.getBucketId(), objects);

  }

  private Boolean areCollectionsSimilar(String key,
                                        String bucketName,
                                        List<SmallObject> objects,
                                        Collection existingCollection){

    Boolean similar = existingCollection.getKey().equals(key) &&
        existingCollection.getBucketName().equals(bucketName) &&
        existingCollection.getStatus().equals(Collection.Status.USED) &&
        objects.size() == existingCollection.getObjects().size();

    int i = 0;

    for ( ; i <  objects.size() & similar ; i++){

      SmallObject smallObject = objects.get(i);

      int y = 0;
      for( ; y < existingCollection.getObjects().size(); y++ ){
        Object object = existingCollection.getObjects().get(y);
        if (object.key.equals(smallObject.getKey()) &&
            object.bucketName.equals(smallObject.getBucketName()) &&
            object.versionNumber.equals(smallObject.getVersionNumber())){
          break;

        }
      }

      if ( y == existingCollection.getObjects().size()){
        similar = false;
      }
    }


    return similar;

  }

  private Collection createCollection(String key,
                                      String bucketName,
                                      Timestamp timestamp,
                                      Integer bucketId,
                                      List<SmallObject> smallObjects) throws RepoException {

    Integer versionNumber;
    Collection collection;
    boolean rollback = false;

    try {

      sqlService.getConnection();

      try {
        versionNumber = sqlService.getCollectionNextAvailableVersion(bucketName, key);   // change to support collections
      } catch (SQLException e) {
        throw new RepoException(e);
      }

      collection = new Collection(null, key, timestamp, bucketId, bucketName, versionNumber, Collection.Status.USED);

      rollback = true;

      // add a record to the DB
      Integer collId = sqlService.insertCollection(collection, smallObjects);
      if (collId == -1) {
        throw new RepoException("Error saving content to database");
      }

      for (SmallObject smallObject : smallObjects){

        if (sqlService.insertCollectionObjects(collId, smallObject.getKey(), smallObject.getBucketName(), smallObject.getVersionNumber()) == 0){
          throw new RepoException(RepoException.Type.ObjectCollectionNotFound);
        }

      }

      sqlService.transactionCommit();

      rollback = false;

      return getCollection(bucketName, key, versionNumber);

    } catch (SQLException e) {
      throw new RepoException(e);
    } finally {

      if (rollback) {
        sqlRollback("collection " + bucketName + ", " + key);
      }
      sqlReleaseConnection();
    }

  }

  @Override
  public Logger getLog() {
    return log;
  }
}
