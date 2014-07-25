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

import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;
import org.hsqldb.jdbc.JDBCDataSource;
import org.plos.repo.models.*;
import org.plos.repo.service.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static java.lang.System.out;

/**
 * Created by jkrzemien on 7/22/14.
 */

public abstract class TestConfig {

    public static String getMySQLURL() {
        return "jdbc:mysql://localhost:3306/plosrepo_unittest";
    }

    public static String getFileSystemObjectStorePath() {
        return "/tmp/repo_unittest";
    }

    public static String[] getMogileFSLocations() {
        return new String[]{"localhost:7001"};
    }

    protected SqlService getHSQLService() throws SQLException, IOException {
        out.println("Creating HSQL service...");
        JDBCDataSource ds = new JDBCDataSource();

        Random rand = new Random();

        ds.setUrl("jdbc:hsqldb:mem:plosrepo-unittest-hsqldb" + rand.nextLong() + ";shutdown=true");
        ds.setUser("");
        ds.setPassword("");

        Connection connection = ds.getConnection();

        SqlService service = new HsqlService();
        Resource sqlFile = new ClassPathResource("setup.hsql");

        ScriptRunner scriptRunner = new ScriptRunner(connection, false, true);
        scriptRunner.runScript(new BufferedReader(new FileReader(sqlFile.getFile())));

        service.setDataSource(ds);

        return service;
    }

    protected SqlService getMySQLService() throws SQLException, IOException {
        out.println("Creating MySQL service...");
        MysqlDataSource ds = new MysqlDataSource();
        ds.setUrl(getMySQLURL());
        ds.setUser("root");
        ds.setPassword("");

        Connection connection = ds.getConnection();

        SqlService service = new MysqlService();
        Resource sqlFile = new ClassPathResource("setup.mysql");
        ScriptRunner scriptRunner = new ScriptRunner(connection, false, true);
        scriptRunner.runScript(new BufferedReader(new FileReader(sqlFile.getFile())));
        service.setDataSource(ds);

        return service;
    }

    protected ObjectStore getMogileObjectStore() throws Exception {
        out.println("Creating Mogile FS service...");
        return new MogileStoreService("toast", getMogileFSLocations(), 1, 1, 100);
    }

    protected ObjectStore getInMemoryObjectStore() throws Exception {
        out.println("Creating In-Memory service...");
        return new InMemoryFileStoreService();
    }

    protected ObjectStore getFileSystemObjectStore() throws Exception {
        out.println("Creating FS service...");
        return new FileSystemStoreService(getFileSystemObjectStorePath(), "");
    }

    @Bean
    public RepoInfoService repoInfoService() {
        return new RepoInfoService();
    }

    @Bean
    public RepoService repoService() {
        return new RepoService();
    }

    @Bean
    public TransactionInterceptor transactionInterceptor(SqlService sqlService, ObjectStore objectStore) {
        return new TransactionInterceptor(sqlService, objectStore);
    }

}

