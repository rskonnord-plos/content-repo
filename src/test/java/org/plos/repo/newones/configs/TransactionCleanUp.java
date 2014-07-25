package org.plos.repo.newones.configs;

import com.google.common.base.Preconditions;
import org.plos.repo.models.Bucket;
import org.plos.repo.service.SqlService;

import java.sql.SQLException;
import java.util.List;

/**
 * Created by jfinger on 7/24/14.
 */
public class TransactionCleanUp {

  private SqlService sqlService;
  private List<Bucket> toClean;

  public TransactionCleanUp(SqlService sqlService,List<Bucket> list) {
    this.sqlService = sqlService;
    this.toClean = list;
  }

  public void cleanup() {
    for (Bucket b : Preconditions.checkNotNull(toClean)) {
      try {
        sqlService.deleteBucket(b.bucketName);
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
    toClean.clear();
  }
}
