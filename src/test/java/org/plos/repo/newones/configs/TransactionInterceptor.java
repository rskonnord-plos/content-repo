package org.plos.repo.newones.configs;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.plos.repo.models.Bucket;
import org.plos.repo.service.ObjectStore;
import org.plos.repo.service.SqlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import static java.lang.String.format;
import static java.util.Collections.synchronizedList;

/**
 * Created by jkrzemien on 7/24/14.
 */

@Aspect
public class TransactionInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TransactionInterceptor.class);
    private final SqlService sqlService;
    private final ObjectStore objectStore;

    private List<org.plos.repo.models.Object> collectedObjects = synchronizedList(new ArrayList<org.plos.repo.models.Object>());
    private List<Bucket> collectedBuckets = synchronizedList(new ArrayList<Bucket>());
    private List<String> collectedBucketsAsString = synchronizedList(new ArrayList<String>());

    public TransactionInterceptor(SqlService sqlService, ObjectStore objectStore) {
        this.sqlService = sqlService;
        this.objectStore = objectStore;
    }

    @Before("execution(* org.plos.repo.service.SqlService.*(..))")
    private void interceptSqlService(JoinPoint jp) throws Throwable {
        if (jp.getSignature().getName().startsWith("insert")) {
            collectData(jp.getSignature(), jp.getArgs()[0]);
        }
    }

    @Before("execution(* org.plos.repo.service.RepoService.*(..))")
    private void interceptRepoService(JoinPoint jp) throws Throwable {
        String name = jp.getSignature().getName();
        if (name.equals("createBucket") || name.equals("updateObject") ) {
            collectData(jp.getSignature(), jp.getArgs()[0]);
        }
    }

    private void collectData(Signature signature, Object argument) throws Exception {
        if (argument instanceof String) {
            collectedBucketsAsString.add((String) argument);
        } else if (argument instanceof org.plos.repo.models.Object) {
            collectedObjects.add((org.plos.repo.models.Object) argument);
        } else if (argument instanceof Bucket) {
            collectedBuckets.add((Bucket) argument);
        } else {
            throw new Exception("The argument type provided to the test data interceptor is not known!");
        }

        // log it
        String msg = "Intercepted transaction from %s, arguments stored for future removal...";
        log.info(format(msg, signature.toShortString()));
    }

    public void clearCollectedData() throws SQLException {
        log.info("Starting CLEAN UP phase...");
        log.info(format("There were %s objects collected", collectedObjects.size()));
        log.info(format("There were %s buckets collected", collectedBuckets.size()));
        log.info(format("There were %s buckets (as string) collected", collectedBucketsAsString.size()));

        sqlService.getConnection();

        ListIterator<org.plos.repo.models.Object> objectIterator = collectedObjects.listIterator();
        while(objectIterator.hasNext()){
            org.plos.repo.models.Object object = objectIterator.next();
            objectStore.deleteObject(object);
            sqlService.deleteObject(object);
            objectIterator.remove();
        }

        ListIterator<Bucket> bucketIterator = collectedBuckets.listIterator();
        while(bucketIterator.hasNext()){
            Bucket bucket = bucketIterator.next();
            objectStore.deleteBucket(bucket);
            sqlService.deleteBucket(bucket.bucketName);
            collectedBucketsAsString.remove(bucket.bucketName);
            bucketIterator.remove();
        }

        ListIterator<String> bucketNameIterator = collectedBucketsAsString.listIterator();
        while(bucketNameIterator.hasNext()){
            String bucketName = bucketNameIterator.next();
            log.info("There SHOULD be NO buckets (as string) remaining! Do I smell a BUG somewhere?");
            sqlService.deleteBucket(bucketName);
            bucketNameIterator.remove();
        }

        sqlService.transactionCommit();
        sqlService.releaseConnection();

        log.info(format("There are %s objects remaining to clean!", collectedObjects.size()));
        log.info(format("There are %s buckets remaining to clean!", collectedBuckets.size()));
        log.info(format("There are %s buckets (as string) remaining to clean!", collectedBucketsAsString.size()));

    }
}
