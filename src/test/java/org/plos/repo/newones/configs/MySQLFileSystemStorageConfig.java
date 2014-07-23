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

package org.plos.repo.newones.configs;

import org.plos.repo.service.ObjectStore;
import org.plos.repo.service.RepoInfoService;
import org.plos.repo.service.RepoService;
import org.plos.repo.service.SqlService;
import org.springframework.context.annotation.Bean;

/**
 * Created by jkrzemien on 7/22/14.
 */

public class MySQLFileSystemStorageConfig extends TestConfig {

    @Bean
    public RepoInfoService repoInfoService() {
        return new RepoInfoService();
    }

    @Bean
    public RepoService repoService() {
        return new RepoService();
    }

    @Bean
    public SqlService sqlServiceDependency() throws Exception {
        return getMySQLService();
    }

    @Bean
    public ObjectStore objectStoreDependency() throws Exception {
        return getFileSystemObjectStore();
    }

}

