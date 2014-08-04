package org.plos.repo.newones.configs;

import org.plos.repo.service.ObjectStore;
import org.plos.repo.service.SqlService;
import org.springframework.context.annotation.Bean;

/**
 * Created by jkrzemien on 8/4/14.
 */
public class MySQLS3StorageConfig extends TestConfig {

  @Bean
  public SqlService sqlServiceDependency() throws Exception {
    return getMySQLService();
  }

  @Bean
  public ObjectStore objectStoreDependency() throws Exception {
    return getS3ObjectStore();
  }

}
