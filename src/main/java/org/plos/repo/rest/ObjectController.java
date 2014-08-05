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

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import org.apache.http.HttpStatus;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.plos.repo.models.Object;
import org.plos.repo.models.RepoError;
import org.plos.repo.service.RepoException;
import org.plos.repo.service.RepoInfoService;
import org.plos.repo.service.RepoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;


@Path("/objects")
@Api(value="/objects")
public class ObjectController {

  private static final Logger log = LoggerFactory.getLogger(ObjectController.class);

  private static final String RFC1123_DATE_TIME_FORMAT =  "EEE, dd MMM yyyy HH:mm:ss z";

  @Inject
  private RepoService repoService;

  @Inject
  private RepoInfoService repoInfoService;


  public static Response handleError(RepoException e) {

    Response.Status status = Response.Status.BAD_REQUEST;

    switch (e.getType()) {

      case BucketNotFound:
      case ObjectNotFound:
        status = Response.Status.NOT_FOUND;
        break;

      case ServerError:
        status = Response.Status.INTERNAL_SERVER_ERROR;
        log.error(e.getType().toString(), e);
        break;
    }

    return Response.status(status).entity(new RepoError(e)).build();

  }


  @GET
  @ApiOperation(value = "List objects", response = Object.class, responseContainer = "List")
  @ApiResponses(value = {
    @ApiResponse(code = HttpStatus.SC_OK, message = "Success"),
    @ApiResponse(code = HttpStatus.SC_NOT_FOUND, message = "Bucket not found"),
    @ApiResponse(code = HttpStatus.SC_BAD_REQUEST, message = "Bad request (see message)"),
    @ApiResponse(code = HttpStatus.SC_INTERNAL_SERVER_ERROR, message = "Server error")
  })
  @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
  public Response listObjects(
      @ApiParam(required = false) @QueryParam("bucketName") String bucketName,
      @ApiParam(required = false) @QueryParam("offset") Integer offset,
      @ApiParam(required = false) @QueryParam("limit") Integer limit,
      @ApiParam(required = false) @DefaultValue("false") @QueryParam("includeDeleted") boolean includeDeleted
  ) {

    try {
      return Response.status(Response.Status.OK).entity(
          new GenericEntity<List<Object>>(
              repoService.listObjects(bucketName, offset, limit, includeDeleted)
          ) {}).build();
    } catch (RepoException e) {
      return handleError(e);
    }

  }

  @GET @Path("/meta/{bucketName}")
  @ApiOperation(value = "Fetch info about an object and its versions", response = Object.class)
  @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
  public Response readMetadata(
      @ApiParam(required = true)  @PathParam("bucketName") String bucketName,
      @ApiParam(required = true)  @QueryParam("key") String key,
      @ApiParam(required = false, value="When empty gets the latest version. When 'all' lists all versions even if deleted.") @QueryParam("version") String version
  ) {

    try {

      Object object;

      try {
        object = repoService.getObject(bucketName, key, Integer.parseInt(version));
      } catch (NumberFormatException e) {

        if (version == null)
          object = repoService.getObject(bucketName, key, null);
        else if (version.equals("all"))
          object = repoService.getObject(bucketName, key, -1);
        else
          throw new RepoException(RepoException.Type.InvalidVersionParameter);
      }

      if (version != null && version.equals("all"))
        object.versions = repoService.getObjectVersions(object);

      return Response.status(Response.Status.OK).entity(object).build();
      
    } catch (RepoException e) {
      return handleError(e);
    }

  }

  @GET @Path("/{bucketName}")
  @ApiOperation(value = "Fetch an object or its metadata", response = Object.class)
  @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
  public Response read(
      @ApiParam(required = true) @PathParam("bucketName") String bucketName,
      @ApiParam(required = true) @QueryParam("key") String key,
      @QueryParam("version") Integer version,
      @QueryParam("fetchMetadata") Boolean fetchMetadata,  // TODO: deprecate this somehow
      @HeaderParam("If-Modified-Since") String ifModifiedSinceStr
  ) {

    Object object;

    boolean notModifiedSince = false;

    try {
      object = repoService.getObject(bucketName, key, version);

      if (ifModifiedSinceStr != null) {
        Date ifModifiedSince = new SimpleDateFormat(RFC1123_DATE_TIME_FORMAT).parse(ifModifiedSinceStr);
        notModifiedSince = object.timestamp.compareTo(ifModifiedSince) <= 0;
      }

    } catch (ParseException e) {
      return handleError(new RepoException(RepoException.Type.CouldNotParseTimestamp));
    } catch (RepoException e) {
      return handleError(e);
    }

    repoInfoService.incrementReadCount();

    // if they want the metadata

    if (fetchMetadata != null && fetchMetadata) {
      try {
        object.versions = repoService.getObjectVersions(object);
        return Response.status(Response.Status.OK)
            .lastModified(object.timestamp)
            .entity(object).build();
      } catch (RepoException e) {
        return handleError(e);
      }
    }


    // else assume they want the binary data

    try {

      if (notModifiedSince)
        return Response.notModified().lastModified(object.timestamp).build();

      String exportFileName = repoService.getObjectExportFileName(object);
      String contentType = repoService.getObjectContentType(object);
      InputStream is = repoService.getObjectInputStream(object);

      return Response.ok(is, contentType)
          .lastModified(object.timestamp)
          .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + exportFileName).build();

      // the container closes this input stream

    } catch (RepoException e) {
      return handleError(e);
    }

  }

  @DELETE
  @Path("/{bucketName}")
  @ApiOperation(value = "Delete an object")
  @ApiResponses(value = {
    @ApiResponse(code = HttpStatus.SC_OK, message = "Object successfully deleted"),
    @ApiResponse(code = HttpStatus.SC_NOT_FOUND, message = "The object was not found"),
    @ApiResponse(code = HttpStatus.SC_BAD_REQUEST, message = "The object was unable to be deleted (see response text for more details)"),
    @ApiResponse(code = HttpStatus.SC_INTERNAL_SERVER_ERROR, message = "Server error")
  })
  public Response delete(
      @ApiParam(required = true) @PathParam("bucketName") String bucketName,
      @ApiParam(required = true) @QueryParam("key") String key,
      @ApiParam(required = true) @QueryParam("version") Integer version
  ) {

    try {
      repoService.deleteObject(bucketName, key, version);
      return Response.status(Response.Status.OK).build();
    } catch (RepoException e) {
      return handleError(e);
    }

  }

  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @ApiOperation(value = "Create a new object or a new version of an existing object",
      notes = "Set the create field to 'new' object if the object you are inserting is not already in the repo. If you want to create a new version of an existing object set create to 'version'. Setting create to 'auto' automagically determines if the object should be new or versioned. However 'auto' should only be used by the ambra-file-store. In addition you may optionally specify a timestamp for object creation time. This feature is for migrating from an existing content store. Note that the timestamp must conform to this format: yyyy-[m]m-[d]d hh:mm:ss[.f...]")
  @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
  @ApiResponses(value = {
    @ApiResponse(code = HttpStatus.SC_CREATED, message = "Object successfully created"),
    @ApiResponse(code = HttpStatus.SC_NOT_FOUND, message = "The object not found"),
    @ApiResponse(code = HttpStatus.SC_BAD_REQUEST, message = "The object was unable to be created (see response text for more details)"),
    @ApiResponse(code = HttpStatus.SC_INTERNAL_SERVER_ERROR, message = "Server error")
  })
  public Response createOrUpdate(
      @ApiParam(required = true) @FormDataParam("key") String key,
      @ApiParam(required = true) @FormDataParam("bucketName") String bucketName,
      @ApiParam(value = "MIME type") @FormDataParam("contentType") String contentType,
      @ApiParam(value = "name of file when downloaded", required = false)
        @FormDataParam("downloadName") String downloadName,
      @ApiParam(value = "creation method", allowableValues = "new,version,auto", defaultValue = "new",
required = true)
        @FormDataParam("create") String create,
      @ApiParam(value = "creation time", required = false)
        @FormDataParam("timestamp") String timestampString,
      @ApiParam(required = false)
        @FormDataParam("file") InputStream uploadedInputStream
  ) {

    try {

      RepoService.CreateMethod method;

      if (create == null)
        throw new RepoException(RepoException.Type.NoCreationMethodEntered);

      try {
        method = RepoService.CreateMethod.valueOf(create.toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new RepoException(RepoException.Type.InvalidCreationMethod);
      }

      Timestamp timestamp = new Timestamp(new Date().getTime());

      if (timestampString != null) {
        try {
          timestamp = Timestamp.valueOf(timestampString);
        } catch (IllegalArgumentException e) {
          throw new RepoException(RepoException.Type.CouldNotParseTimestamp);
        }
      }

      repoInfoService.incrementWriteCount();

      return Response.status(Response.Status.CREATED).entity(repoService.createObject(method, key, bucketName, contentType, downloadName, timestamp, uploadedInputStream)).build();

    } catch (RepoException e) {
      return handleError(e);
    }

  }

}
