package org.plos.repo.newones;

/**
 * Created by jkrzemien on 7/22/14.
 */

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.spring.scope.RequestContextFilter;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.plos.repo.newones.configs.*;
import org.plos.repo.service.RepoException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collection;

import static org.glassfish.jersey.test.TestProperties.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.runners.Parameterized.Parameter;
import static org.junit.runners.Parameterized.Parameters;

/**
 * Created by jkrzemien on 7/22/14.
 */

@RunWith(Parameterized.class)
public abstract class ContentRepoControllerTest {

    @Parameters(name = "{index} - Mode: {0}")
    public static Collection getPersistanceConfigurations() {
        Object[][] data = new Object[][]{
            {HSQLInMemoryStorageConfig.class},
            {HSQLFileSystemStorageConfig.class},
            {HSQLMogileStorageConfig.class},
            {MySQLInMemoryStorageConfig.class},
            {MySQLFileSystemStorageConfig.class},
            {MySQLMogileStorageConfig.class}
        };
        return Arrays.asList(data);
    }

    @Parameter
    public Class<? extends TestConfig> config;

    protected Gson gson = new Gson();

    protected TransactionInterceptor transactions;

    private JerseyTest container;

    protected void assertRepoError(Response response, Response.Status httpStatus, RepoException.Type repoError) {

      JsonObject responseObj = gson.fromJson(response.readEntity(String.class), JsonElement.class).getAsJsonObject();

      assertEquals(response.getStatus(), httpStatus.getStatusCode());

      assertEquals(responseObj.get("repoErrorCode").getAsInt(), repoError.getValue());
      assertTrue(responseObj.get("message").getAsString().equals(repoError.getMessage()));
    }

    protected synchronized WebTarget target(String path) throws Exception {
        if (container == null) {
            this.container = new JerseyTest() {

                @Override
                protected void configureClient(org.glassfish.jersey.client.ClientConfig config) {
                    config.register(MultiPartFeature.class);
                    config.register(RequestContextFilter.class);
                    super.configureClient(config);
                }

                @Override
                protected Application configure() {
                    enable(LOG_TRAFFIC);
                    enable(DUMP_ENTITY);
                    /**
                     * The next line allows randomization of container's port.
                     * Useful for:
                     *  - Running tests in parallel
                     *  - Running tests sequentially but without having failures of the type 'Address is already in use'
                     *  due to a new test thread not waiting for the previous test's container to tearDown.
                     *
                     * If you don't use this option (a.k.a. you set a fixed port) then you will need to handle the
                     * issue described above in your test suite.
                     */
                    forceSet(CONTAINER_PORT, "0");

                    ResourceConfig resourceUnderTest = new ResourceConfig(getClassesUnderTest());
                    resourceUnderTest.register(MultiPartFeature.class);
                    resourceUnderTest.register(RequestContextFilter.class);

                    ApplicationContext springContext = new AnnotationConfigApplicationContext(config);
                    transactions = springContext.getBean(TransactionInterceptor.class);
                    resourceUnderTest.property("contextConfig", springContext);
                    return resourceUnderTest;
                }
            };
            container.setUp();
        }
        return container.target(path);
    }

    abstract public Class<?>[] getClassesUnderTest();
}
