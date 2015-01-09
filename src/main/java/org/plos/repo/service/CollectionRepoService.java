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

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.hsqldb.lib.StringUtil;
import org.plos.repo.models.Bucket;
import org.plos.repo.models.RepoCollection;
import org.plos.repo.models.RepoObject;
import org.plos.repo.models.Status;
import org.plos.repo.models.input.ElementFilter;
import org.plos.repo.models.input.InputCollection;
import org.plos.repo.models.input.InputObject;
import org.plos.repo.models.validator.InputCollectionValidator;
import org.plos.repo.models.validator.JsonStringValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

/**
 * This service handles all communication for collections with sqlservice
 */
public class CollectionRepoService extends BaseRepoService {

  private static final Logger log = LoggerFactory.getLogger(CollectionRepoService.class);

  @Inject
  private InputCollectionValidator inputCollectionValidator;


  /**
   * Returns a list of collections meta data for the given bucket name <code>bucketName</code>. In case pagination
   * parameters <code>offset</code> and <code>limit</code> are not present, it loads the default pagination data.
   * @param bucketName a single String representing the bucket name in where to look the collection
   * @param offset an Integer used to determine the offset of the response
   * @param limit an Integer used to determine the limit of the response
   * @param includeDeleted a boolean value that defines whether to include deleted collections or not
   * @param tag a single String used to filter the response when collection's tag matches the given param
   * @return a list of {@link org.plos.repo.models.RepoCollection}
   * @throws org.plos.repo.service.RepoException
   */
  public List<RepoCollection> listCollections(String bucketName, Integer offset, Integer limit, Boolean includeDeleted, String tag) throws RepoException {

    if (offset == null)
      offset = 0;
    if (limit == null)
      limit = DEFAULT_PAGE_SIZE;

    try {

      validatePagination(offset, limit);

      sqlService.getConnection();

      if (StringUtil.isEmpty(bucketName)){
        throw new RepoException(RepoException.Type.NoBucketEntered);
      }

      if (bucketName != null && sqlService.getBucket(bucketName) == null)
        throw new RepoException(RepoException.Type.BucketNotFound);

      return sqlService.listCollectionsMetaData(bucketName, offset, limit, includeDeleted, tag);

    } catch (SQLException e) {
      throw new RepoException(e);
    } finally {
      sqlReleaseConnection();
    }

  }

  /**
   * Returns a collection identified by <code>bucketName</code> and <code>key</code>. If no filer <code>collectionFilter</code> is specified,
   * it returns the latest version available. If only tag filter is specified, and there is more than one collection with that tag, it returns
   * the last one.
   * @param bucketName a single String representing the bucket name in where to look the collection
   * @param key a single String identifying the collection key
   * @param elementFilter a collection filter object used to uniquely identify the collection
   * @return a collection {@link org.plos.repo.models.RepoCollection} or null is the desired collection does not exists
   * @throws RepoException
   */
  public RepoCollection getCollection(String bucketName, String key, ElementFilter elementFilter) throws RepoException {

    RepoCollection repoCollection;

    try {
      sqlService.getConnection();

      if (StringUtil.isEmpty(key))
        throw new RepoException(RepoException.Type.NoCollectionKeyEntered);

      if (elementFilter == null || elementFilter.isEmpty()) // no filters defined
        repoCollection = sqlService.getCollection(bucketName, key);
      else
        repoCollection = sqlService.getCollection(bucketName, key, elementFilter.getVersion(), elementFilter.getTag(), elementFilter.getVersionChecksum());

      if (repoCollection == null)
        throw new RepoException(RepoException.Type.CollectionNotFound);

    } catch (SQLException e) {
      throw new RepoException(e);
    } finally {
      sqlReleaseConnection();
    }

    return repoCollection;

  }

  /**
   * Returns a list of all collection versions for the given <code>collection</code>
   * @param collection a single {@link org.plos.repo.models.RepoCollection}
   * @return a list of {@link org.plos.repo.models.RepoCollection}
   * @throws org.plos.repo.service.RepoException
   */

  /**
   * Returns a list of all versions for the given <code>bucketName</code> and <code>key</code>
   * @param bucketName a single a single String identifying the bucket name where the collection is.
   * @param key a single String identifying the collection key
   * @return a list of {@link org.plos.repo.models.RepoCollection}
   * @throws org.plos.repo.service.RepoException
   */
  public List<RepoCollection> getCollectionVersions(String bucketName, String key) throws RepoException {

    try {
      sqlService.getConnection();

      if (StringUtil.isEmpty(bucketName)){
        throw new RepoException(RepoException.Type.NoBucketEntered);
      }

      if (StringUtil.isEmpty(key)){
        throw new RepoException(RepoException.Type.NoCollectionKeyEntered);
      }

      List<RepoCollection> repoCollections = sqlService.listCollectionVersions(bucketName, key);

      if (repoCollections == null || repoCollections.size() == 0){
        throw new RepoException(RepoException.Type.CollectionNotFound);
      }

      return repoCollections;

    } catch (SQLException e) {
      throw new RepoException(e);
    } finally {
      sqlReleaseConnection();
    }
  }

  /**
   * Deletes the collection define by <code>bucketName</code>, <code>key</code> and <code>elementFilter</code>. If
   * only the tag in element filter is specified, and there is more than one collection matching the filter, it throws an
   * error.
   * @param bucketName a single String identifying the bucket name where the collection is.
   * @param key a single String identifying the collection key
   * @param elementFilter a collection filter object used to uniquely identify the collection
   * @throws org.plos.repo.service.RepoException
   */
  public void deleteCollection(String bucketName, String key, ElementFilter elementFilter) throws RepoException {

    boolean rollback = false;

    try {

      sqlService.getConnection();

      if (StringUtil.isEmpty(key))
        throw new RepoException(RepoException.Type.NoCollectionKeyEntered);

      if (elementFilter == null || (elementFilter.isEmpty())){
        throw new RepoException(RepoException.Type.NoFilterEntered);
      }

      rollback = true;

      if (elementFilter.getTag() != null & elementFilter.getVersionChecksum() == null & elementFilter.getVersion() == null){
        if (sqlService.listCollections(bucketName, 0, 10, false, elementFilter.getTag()).size() > 1){
          throw new RepoException(RepoException.Type.MoreThanOneTaggedCollection);
        }
      }

      if (sqlService.markCollectionDeleted(key, bucketName, elementFilter.getVersion(), elementFilter.getTag(), elementFilter.getVersionChecksum()) == 0)
        throw new RepoException(RepoException.Type.CollectionNotFound);

      sqlService.transactionCommit();
      rollback = false;

    } catch (SQLException e) {
      throw new RepoException(e);
    } finally {

      if (rollback) {
        sqlRollback("object " + bucketName + ", " + key + ", " + elementFilter.toString());
      }

      sqlReleaseConnection();

    }
  }

  /**
   * Creates a new collection. It decides if it creates a collection from scratch or a new version of an existing one, based
   * on <code>method</code> input value.
   * @param method a {@link org.plos.repo.service.BaseRepoService.CreateMethod}
   * @param inputCollection a {@link org.plos.repo.models.input.InputCollection} that holds the information of the new collection
   *                        to be created
   * @return {@link org.plos.repo.models.RepoCollection} created
   * @throws RepoException
   */
  public RepoCollection createCollection(CreateMethod method, InputCollection inputCollection) throws RepoException {

    inputCollectionValidator.validate(inputCollection);

    RepoCollection existingRepoCollection = null;
    boolean rollback = false;
    RepoCollection newRepoCollection = null;

    try {

      // get connection
      sqlService.getConnection();

      existingRepoCollection = sqlService.getCollection(inputCollection.getBucketName(), inputCollection.getKey());

      // creates timestamps
      Timestamp creationDate = inputCollection.getCreationDateTime() != null ?
          Timestamp.valueOf(inputCollection.getCreationDateTime()) : new Timestamp(new Date().getTime());

      Timestamp timestamp = inputCollection.getTimestamp() != null ?
          Timestamp.valueOf(inputCollection.getTimestamp()) : creationDate;

      rollback = true;

      if (CreateMethod.NEW.equals(method)){

        if (existingRepoCollection != null){
          log.debug("Error trying to create a collection that already exists. Key: " + inputCollection.getKey() + " create method : new ");
          throw new RepoException(RepoException.Type.CantCreateNewCollectionWithUsedKey);
        }
        newRepoCollection = createNewCollection(inputCollection, timestamp, creationDate);

      } else if (CreateMethod.VERSION.equals(method)){
        if (existingRepoCollection == null){
          log.debug("Error trying to version a collection that does not exists. Key: " + inputCollection.getKey() + " create method : version ");
          throw new RepoException(RepoException.Type.CantCreateCollectionVersionWithNoOrig);
        }
        newRepoCollection = updateCollection(inputCollection, timestamp, existingRepoCollection, creationDate);

      } else if (CreateMethod.AUTO.equals(method)){
        log.debug("Creation Method: auto. Key: " + inputCollection.getKey() );
        if (existingRepoCollection == null)
          newRepoCollection = createNewCollection(inputCollection, timestamp, creationDate);
        else
          newRepoCollection = updateCollection(inputCollection, timestamp, existingRepoCollection, creationDate);

      } else {
        throw new RepoException(RepoException.Type.InvalidCreationMethod);
      }

      sqlService.transactionCommit();
      rollback = false;

      return newRepoCollection;

    } catch (SQLException e) {
      throw new RepoException(e);
    } finally {

      if (rollback) {
        sqlRollback("collection " + inputCollection.getBucketName() + ", " + inputCollection.getKey());
      }
      sqlReleaseConnection();
    }

  }


  private RepoCollection createNewCollection(InputCollection inputCollection, Timestamp timestamp, Timestamp creationDate) throws RepoException {
    Bucket bucket = null;

    try {
      bucket = sqlService.getBucket(inputCollection.getBucketName());

      if (bucket == null) {
        throw new RepoException(RepoException.Type.BucketNotFound);
      }

      return createCollection(inputCollection.getKey(), inputCollection.getBucketName(), timestamp, bucket.getBucketId(), inputCollection.getObjects(), inputCollection.getTag(), creationDate, inputCollection.getUserMetadata());

    } catch(SQLIntegrityConstraintViolationException e){
      log.debug("Error trying to create a collection, key: " + inputCollection.getKey() + " . SQLIntegrityConstraintViolationException:  " + e.getMessage());
      throw new RepoException(RepoException.Type.CantCreateNewCollectionWithUsedKey);
    } catch (SQLException e) {
      log.debug("SQLException:  " + e.getMessage());
      throw new RepoException(e);
    }


  }

  private RepoCollection updateCollection(InputCollection inputCollection, Timestamp timestamp,
                                      RepoCollection existingRepoCollection, Timestamp creationDate) throws RepoException {

    if (areCollectionsSimilar(inputCollection.getKey(), inputCollection.getBucketName(), inputCollection.getObjects(), inputCollection.getTag(), existingRepoCollection)){
      return existingRepoCollection;
    }

    try{
      return createCollection(inputCollection.getKey(), inputCollection.getBucketName(), timestamp, existingRepoCollection.getBucketId(), inputCollection.getObjects(), inputCollection.getTag(), creationDate, inputCollection.getUserMetadata());
    } catch(SQLIntegrityConstraintViolationException e){
      log.debug("Error trying to version a collection, key: " + inputCollection.getKey() + " . SQLIntegrityConstraintViolationException:  " + e.getMessage());
      throw new RepoException(RepoException.Type.CantCreateCollectionVersionWithNoOrig);
    } catch(SQLException e){
      log.debug("SQLException:  " + e.getMessage());
      throw new RepoException(e);
    }

  }

  private Boolean areCollectionsSimilar(String key,
                                        String bucketName,
                                        List<InputObject> objects,
                                        String tag,
                                        RepoCollection existingRepoCollection
  ){

    Boolean similar = existingRepoCollection.getKey().equals(key) &&
        existingRepoCollection.getBucketName().equals(bucketName) &&
        existingRepoCollection.getStatus().equals(Status.USED) &&
        objects.size() == existingRepoCollection.getRepoObjects().size();

    if ( similar &&  ( existingRepoCollection.getTag() != null && tag != null) ) {
      similar = existingRepoCollection.getTag().equals(tag);
    } else {
      similar = similar && !( (existingRepoCollection.getTag() != null && tag == null) || (existingRepoCollection.getTag() == null && tag !=null)) ;
    }

    int i = 0;

    for ( ; i <  objects.size() & similar ; i++){

      InputObject inputObject = objects.get(i);

      int y = 0;
      for( ; y < existingRepoCollection.getRepoObjects().size(); y++ ){
        RepoObject repoObject = existingRepoCollection.getRepoObjects().get(y);
        if (repoObject.getKey().equals(inputObject.getKey()) &&
            repoObject.getVersionChecksum().equals(inputObject.getVersionChecksum())){
          break;

        }
      }

      if ( y == existingRepoCollection.getRepoObjects().size()){
        similar = false;
      }
    }


    return similar;

  }

  private RepoCollection createCollection(String key,
                                      String bucketName,
                                      Timestamp timestamp,
                                      Integer bucketId,
                                      List<InputObject> inputObjects,
                                      String tag,
                                      Timestamp creationDate,
                                      String userMetadata) throws SQLException, RepoException {

    Integer versionNumber;
    RepoCollection repoCollection;

    versionNumber = sqlService.getCollectionNextAvailableVersion(bucketName, key);   // change to support collections

    repoCollection = new RepoCollection(key, bucketId, bucketName, Status.USED);
    repoCollection.setTimestamp(timestamp);
    repoCollection.setVersionNumber(versionNumber);
    repoCollection.setTag(tag);
    repoCollection.setCreationDate(creationDate);
    repoCollection.setUserMetadata(userMetadata);

    List<String> objectsChecksum = Lists.newArrayList(Iterables.transform(inputObjects, typeFunction()));

    repoCollection.setVersionChecksum(checksumGenerator.generateVersionChecksum(repoCollection, objectsChecksum));

    // add a record to the DB
    Integer collId = sqlService.insertCollection(repoCollection);
    if (collId == -1) {
      throw new RepoException("Error saving content to database");
    }

    for (InputObject inputObject : inputObjects){

      if (sqlService.insertCollectionObjects(collId, inputObject.getKey(), bucketName, inputObject.getVersionChecksum()) == 0){
        throw new RepoException(RepoException.Type.ObjectCollectionNotFound);
      }

    }

    return repoCollection;


  }

  private Function<InputObject, String> typeFunction() {
    return new Function<InputObject, String>() {

      @Override
      public String apply(InputObject inputObject) {
        return inputObject.getVersionChecksum();
      }

    };
  }

  @Override
  public Logger getLog() {
    return log;
  }
}
