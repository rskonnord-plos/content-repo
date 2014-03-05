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

package org.plos.repo.rest;

import com.google.common.base.Joiner;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.commons.io.IOUtils;
import org.plos.repo.models.*;
import org.plos.repo.models.Object;
import org.plos.repo.service.ObjectStore;
import org.plos.repo.service.HsqlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;

@Controller
@RequestMapping("/objects")
public class ObjectCrudController {

  private static final Logger log = LoggerFactory.getLogger(ObjectCrudController.class);

  private static final Joiner REPROXY_URL_JOINER = Joiner.on(' ');

  private static final int REPROXY_CACHE_FOR_VALUE = 6 * 60 * 60; // TODO: Make configurable

  private static final String REPROXY_CACHE_FOR_HEADER =
      REPROXY_CACHE_FOR_VALUE + "; Last-Modified Content-Type Content-Disposition";

  @Autowired
  private ObjectStore objectStore;

  @Autowired
  private HsqlService hsqlService;


  // TODO: check at startup that db is in sync with objectStore ?

  private static Gson gson = new Gson();

  private String objectToJsonString(Object object, boolean includeVersions) {
    JsonObject jsonObject = gson.toJsonTree(object).getAsJsonObject();

    if (includeVersions)
      jsonObject.add("versions", gson.toJsonTree(hsqlService.listObjectVersions(object)).getAsJsonArray());

    return jsonObject.toString();
  }

  @RequestMapping(method=RequestMethod.GET)
  public @ResponseBody List<Object> listAllObjects() throws Exception {
    return hsqlService.listAllObject();
  }

//  @RequestMapping(value="{bucketName}", method=RequestMethod.GET)
//  public @ResponseBody List<Object> listObjectsInBucket(@PathVariable String bucketName) throws Exception {
//    return hsqlService.listObjectsInBucket(bucketName);
//  }

  private boolean clientSupportsReproxy(HttpServletRequest request) {
    Enumeration<?> headers = request.getHeaders("X-Proxy-Capabilities");
    if (headers == null) {
      return false;
    }
    while (headers.hasMoreElements()) {
      if ("reproxy-file".equals(headers.nextElement())) {
        return true;
      }
    }
    return false;
  }

  @RequestMapping(value="{bucketName:.+}", method=RequestMethod.GET)
  public @ResponseBody
  void read(@PathVariable String bucketName,
            @RequestParam(required = true) String key,
            @RequestParam(required = false) Integer version,
            @RequestParam(required = false) Boolean fetchMetadata,
            HttpServletRequest request,
            HttpServletResponse response) throws Exception {

    org.plos.repo.models.Object object;
    if (version == null)
      object = hsqlService.getObject(bucketName, key);
    else
      object = hsqlService.getObject(bucketName, key, version);

    if (object == null) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    // if they want the metadata

    if (fetchMetadata != null && fetchMetadata) {
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      try {
        response.getWriter().write(objectToJsonString(object, version == null));
        response.setStatus(HttpServletResponse.SC_OK);
      } catch (IOException e) {
        log.error("Error reading metadata", e);
        response.setStatus(HttpServletResponse.SC_EXPECTATION_FAILED);
      }
      return;
    }

    // if they want redirect URLs

    if (clientSupportsReproxy(request) && objectStore.hasXReproxy()) {

      // if the urls are in the database use that first
      String urls = object.urls;

      // if not, check the filestore
      if (object.urls == null || object.urls.isEmpty())
        urls = REPROXY_URL_JOINER.join(objectStore.getRedirectURLs(object));

      response.setHeader("X-Reproxy-URL", urls);
      response.setHeader("X-Reproxy-Cache-For", REPROXY_CACHE_FOR_HEADER);
      response.setStatus(HttpServletResponse.SC_OK);

      return;
    }

    // else assume they want the binary data

    String exportFileName = "content";

    if (object.downloadName != null)
      exportFileName = object.downloadName;
    else if (ObjectStore.isValidFileName(object.key))
      exportFileName = object.key;

    try {

      if (object.contentType == null || object.contentType.isEmpty())
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
      else
        response.setContentType(object.contentType);
      response.setHeader("Content-Disposition", "inline; filename=" + exportFileName);

      InputStream is = objectStore.getInputStream(object);
      IOUtils.copy(is, response.getOutputStream());
      response.setStatus(HttpServletResponse.SC_OK);
      response.flushBuffer();
      is.close();
    } catch (Exception ex) {
      response.setStatus(HttpServletResponse.SC_EXPECTATION_FAILED);
      log.info("Error writing file to output stream.", ex);
    }

  }

  @RequestMapping(value="{bucketName:.+}", method=RequestMethod.DELETE)
  public ResponseEntity<String> delete(@PathVariable String bucketName,
                                       @RequestParam String key,
                                       @RequestParam int version) throws Exception {

    if (hsqlService.markObjectDeleted(key, bucketName, version) == 0)
      return new ResponseEntity<>("Error: Can not find object in database.", HttpStatus.NOT_FOUND);

    // NOTE: we no longer delete objects from the object store

    // delete it from the object store if it is no longer referenced in the database
//    if (!hsqlService.objectInUse(bucketName, checksum) && !objectStore.deleteObject(objectStore.getObjectLocationString(bucketName, checksum)))
//      return new ResponseEntity<>("Error: There was a problem deleting the object from the filesystem.", HttpStatus.NOT_MODIFIED);

    return new ResponseEntity<>(key + " version " + version + " deleted", HttpStatus.OK);
  }

  @RequestMapping(method=RequestMethod.POST)
  public ResponseEntity<String> createOrUpdate(@RequestParam String key,
                                               @RequestParam String bucketName,
                                               @RequestParam(required = false) String contentType,
                                               @RequestParam(required = false) String downloadName,
                                               @RequestParam(required = false) MultipartFile file,
                                               @RequestParam(required = true) boolean newObject
  ) throws Exception {

    if (newObject)
      return create(key, bucketName, contentType, downloadName, file);

    return update(key, bucketName, contentType, downloadName, file);
  }

  private ResponseEntity<String> create(String key,
                          String bucketName,
                          String contentType,
                          String downloadName,
                          MultipartFile file) throws Exception {

    Integer bucketId = hsqlService.getBucketId(bucketName);

    if (file == null)
      return new ResponseEntity<>("Error: A file must be specified for uploading.", HttpStatus.PRECONDITION_FAILED);

    if (bucketId == null)
      return new ResponseEntity<>("Error: Can not find bucket " + bucketName, HttpStatus.INSUFFICIENT_STORAGE);

    Object existingObject = hsqlService.getObject(bucketName, key);

    if (existingObject != null)
      return new ResponseEntity<>("Error: Attempting to create an object with a key that already exists.", HttpStatus.CONFLICT);

    ObjectStore.UploadInfo uploadInfo;
    try {
      uploadInfo = objectStore.uploadTempObject(file);
    } catch (Exception e) {
      log.error("Error during upload", e);
      return new ResponseEntity<>("Error: A problem occurred while uploading the file.", HttpStatus.PRECONDITION_FAILED);
    }

    HttpStatus status = HttpStatus.CREATED; // note: status indicates if it made it to the DB, not the object store

    Integer versionNumber = hsqlService.getNextAvailableVersionNumber(bucketName, key);

    Object object = new Object(null, key, uploadInfo.getChecksum(), new Timestamp(new Date().getTime()), downloadName, contentType, uploadInfo.getSize(), null, null, bucketId, bucketName, versionNumber, Object.Status.USED);

    // determine if the object should be added to the store or not
    if (objectStore.objectExists(object)) {

//      if (FileUtils.contentEquals(tempFile, new File(objectStore.getObjectLocationString(bucketName, checksum)))) {
//        log.info("not adding object to store since content exists");
//      } else {
//        log.info("checksum collision!!");
//        status = HttpStatus.CONFLICT;
//      }

      // dont bother storing the file since the data already exists in the system
      objectStore.deleteTempUpload(uploadInfo);
    } else {
      objectStore.saveUploadedObject(new Bucket(bucketName), uploadInfo, object);
      object.urls = REPROXY_URL_JOINER.join(objectStore.getRedirectURLs(object));
    }

    // add a record to the DB

    hsqlService.insertObject(object); // TODO: deal with 0 return values

    return new ResponseEntity<>(objectToJsonString(object, false), status);
  }

  private ResponseEntity<String> update(String key,
                                        String bucketName,
                                        String contentType,
                                        String downloadName,
                                        MultipartFile file) throws Exception {

    Integer bucketId = hsqlService.getBucketId(bucketName);

    if (bucketId == null)
      return new ResponseEntity<>("Error: Can not find bucket " + bucketName, HttpStatus.INSUFFICIENT_STORAGE);

    Object object = hsqlService.getObject(bucketName, key);

    if (object == null)
      return new ResponseEntity<>("Error: Attempting to create a new version of an non-existing object.", HttpStatus.NOT_ACCEPTABLE);

    // copy over values from previous object, if they are not specified in the request
    if (contentType != null)
      object.contentType = contentType;

    if (downloadName != null)
      object.downloadName = downloadName;

    // TODO: wrap this in a transaction since versionNumber is being updated ?

    object.timestamp = new Timestamp(new Date().getTime());
    object.versionNumber++;
    object.id = null;  // remove this since it refers to the old object

    if (file == null) {
      object.urls = REPROXY_URL_JOINER.join(objectStore.getRedirectURLs(object));
      hsqlService.insertObject(object); // TODO: deal with 0 return values

      return new ResponseEntity<>(objectToJsonString(object, false), HttpStatus.OK);
    }

    ObjectStore.UploadInfo uploadInfo;
    try {
      uploadInfo = objectStore.uploadTempObject(file);
    } catch (Exception e) {
      log.error("Error during upload", e);
      return new ResponseEntity<>("Error: A problem occurred while uploading the file.", HttpStatus.PRECONDITION_FAILED);
    }

    HttpStatus status = HttpStatus.OK; // note: different return value from 'create'

    object.urls = "";

    // determine if the object should be added to the store or not
    if (objectStore.objectExists(object)) {
      objectStore.deleteTempUpload(uploadInfo);
    } else {
      object.checksum = uploadInfo.getChecksum();
      object.size = uploadInfo.getSize();
      objectStore.saveUploadedObject(new Bucket(bucketName), uploadInfo, object);
    }

    object.urls = REPROXY_URL_JOINER.join(objectStore.getRedirectURLs(object));

    // add a record to the DB
    hsqlService.insertObject(object); // TODO: deal with 0 return values

    return new ResponseEntity<>(objectToJsonString(object, false), status);
  }

}
