package org.plos.repo.newones;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.plos.repo.rest.BucketController;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.OK;
import static org.junit.Assert.assertEquals;
import static org.plos.repo.newones.configs.TestConfig.getFileSystemObjectStorePath;

/**
 * Created by jkrzemien on 7/22/14.
 */

public class MyBucketControllerTest extends ContentRepoControllerTest {

    private final String bucketName = "plos-bucketunittest-bucket1";
    private final String bucketName2 = "plos-bucketunittest-bucket2";

    @Before
    public void cleanUpObjectStore() throws Exception {
      FileUtils.deleteQuietly(new File(getFileSystemObjectStorePath()));
    }

    @Test
    public void listBuckets() throws Exception {
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
    public void createBucket() throws Exception {
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

//        response = target("/buckets/" + bucketName).request().delete();
//        assertEquals(OK.getStatusCode(), response.getStatus());
//
//        response = target("/buckets/" + bucketName2).request().delete();
//        assertEquals(OK.getStatusCode(), response.getStatus());
    }

    @Override
    public Class<?> getClassUnderTest() {
        return BucketController.class;
    }
}

