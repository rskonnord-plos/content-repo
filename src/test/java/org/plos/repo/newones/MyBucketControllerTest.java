package org.plos.repo.newones;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.junit.After;
import org.junit.Test;
import org.plos.repo.rest.BucketController;
import org.plos.repo.service.RepoException;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.SQLException;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.OK;
import static org.junit.Assert.assertEquals;

/**
 * Created by jkrzemien on 7/22/14.
 */

public class MyBucketControllerTest extends ContentRepoControllerTest {

    private final String bucketName = "plos-bucketunittest-bucket1";
    private final String bucketName2 = "plos-bucketunittest-bucket2";

    @After
    public void tearDown() throws SQLException {
        transactions.clearCollectedData();
    }

    @Test
    public void bucketAlreadyExists() throws Exception {
      target("/buckets").request(MediaType.APPLICATION_JSON_TYPE).post(Entity.form(new Form().param("name", bucketName)));

      assertRepoError(
          target("/buckets").request(MediaType.APPLICATION_JSON_TYPE).post(Entity.form(new Form().param("name", bucketName))),
          Response.Status.BAD_REQUEST, RepoException.Type.BucketAlreadyExists);
    }

  @Test
  public void invalidBucketName() throws Exception {
    assertRepoError(
        target("/buckets").request(MediaType.APPLICATION_JSON_TYPE).post(Entity.form(new Form().param("name", bucketName+"-bad?&name"))),
        Response.Status.BAD_REQUEST, RepoException.Type.IllegalBucketName);
  }

  @Test
  public void deleteNonEmptyBucket() throws Exception {

    target("/buckets").request(MediaType.APPLICATION_JSON_TYPE).post(Entity.form(new Form().param("name", bucketName)));

    target("/objects").request().post(Entity.entity(new FormDataMultiPart().field("bucketName", bucketName).field("create", "new").field("key", "object1").field("file", "test", MediaType.TEXT_PLAIN_TYPE), MediaType.MULTIPART_FORM_DATA));

    assertRepoError(
        target("/buckets/" + bucketName)
            .request(MediaType.APPLICATION_JSON_TYPE).delete(),
        Response.Status.BAD_REQUEST, RepoException.Type.CantDeleteNonEmptyBucket);
  }

  @Test
  public void deleteNonExsitingBucket() throws Exception {

    assertRepoError(
        target("/buckets/" + "nonExistingBucket")
            .request(MediaType.APPLICATION_JSON_TYPE).delete(),
        Response.Status.NOT_FOUND, RepoException.Type.BucketNotFound);
  }

    @Test
    public void listZeroBuckets() throws Exception {
        /**
         * Perform actual invocation of method for class under test
         */
        Response response = target("/buckets").request()
                .accept(APPLICATION_JSON_TYPE)
                .get();

        /**
         * Validations section
         */
        assertEquals(OK.getStatusCode(), response.getStatus());
        assertEquals(APPLICATION_JSON_TYPE, response.getMediaType());
        JsonArray jsonArray = gson.fromJson(new InputStreamReader((InputStream) response.getEntity()), JsonElement.class).getAsJsonArray();
        assertEquals(0, jsonArray.size());
    }

    @Test
    public void crudHappyPath() throws Exception {

        // CREATE

        Response response = target("/buckets").request(APPLICATION_JSON_TYPE).post(Entity.form(new Form().param("name", bucketName)));
        assertEquals(CREATED.getStatusCode(), response.getStatus());

        response = target("/buckets").request(APPLICATION_JSON_TYPE).post(Entity.form(new Form().param("name", bucketName2)));
        assertEquals(CREATED.getStatusCode(), response.getStatus());

        // LIST

        String responseString = target("/buckets").request().accept(APPLICATION_JSON_TYPE).get(String.class);
        JsonArray jsonArray = gson.fromJson(responseString, JsonElement.class).getAsJsonArray();
        assertEquals(2, jsonArray.size());

        // DELETE

        response = target("/buckets/" + bucketName).request().delete();
        assertEquals(OK.getStatusCode(), response.getStatus());

//        response = target("/buckets/" + bucketName2).request().delete();
//        assertEquals(OK.getStatusCode(), response.getStatus());
    }

    @Override
    public Class<?> getClassUnderTest() {
        return BucketController.class;
    }
}

