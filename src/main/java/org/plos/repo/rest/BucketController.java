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
import org.plos.repo.models.Bucket;
import org.plos.repo.service.RepoException;
import org.plos.repo.service.RepoInfoService;
import org.plos.repo.service.RepoService;

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("/buckets")
@Api(value="/buckets")
public class BucketController {

  @Inject
  private RepoService repoService;

  @Inject
  private RepoInfoService repoInfoService;

  @GET
  @ApiOperation(value = "List buckets", response = Bucket.class, responseContainer = "List")
  @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
  public Response list() {

    try {
      return Response.status(Response.Status.OK).entity(
          new GenericEntity<List<Bucket>>(repoService.listBuckets()) {
          }).build();
    } catch (RepoException e) {
      return ObjectController.handleError(e);
    }

  }

  @GET @Path("/{bucketName}")
  @ApiOperation(value = "Info about the bucket", response = Bucket.class)
  @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
  public Response info(@PathParam("bucketName") String bucketName) {

    try {
      return Response.status(Response.Status.OK).entity(
          repoInfoService.bucketInfo(bucketName)
      ).build();
    } catch (RepoException e) {
      return ObjectController.handleError(e);
    }

  }

  @POST
  @ApiOperation(value = "Create a bucket")
  @ApiResponses(value = {
    @ApiResponse(code = HttpStatus.SC_BAD_REQUEST, message = "The bucket was unable to be created (see response text for more details)"),
    @ApiResponse(code = HttpStatus.SC_INTERNAL_SERVER_ERROR, message = "Server error")
  })
  public Response create(@ApiParam(required = true) @FormParam("name") String name) {

    try {
      repoService.createBucket(name);
      return Response.status(Response.Status.CREATED)
          .entity("Created bucket " + name).type(MediaType.TEXT_PLAIN_TYPE).build();
    } catch (RepoException e) {
      return ObjectController.handleError(e);
    }

  }

  @DELETE
  @Path("/{name}")
  @ApiOperation(value = "Delete a bucket")
  @ApiResponses(value = {
    @ApiResponse(code = HttpStatus.SC_NOT_FOUND, message = "The bucket was not found"),
    @ApiResponse(code = HttpStatus.SC_BAD_REQUEST, message = "The bucket was unable to be deleted (see response text for more details)"),
    @ApiResponse(code = HttpStatus.SC_INTERNAL_SERVER_ERROR, message = "Server error")
  })
  public Response delete(@PathParam("name") String name) {

    try {
      repoService.deleteBucket(name);
      return Response.status(Response.Status.OK)
          .entity("Deleted bucket " + name).type(MediaType.TEXT_PLAIN_TYPE).build();
    } catch (RepoException e) {
      return ObjectController.handleError(e);
    }

  }

}
