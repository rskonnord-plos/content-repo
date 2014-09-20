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

package org.plos.repo.models;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.sql.Timestamp;
import java.util.List;

@XmlRootElement
public class Object {

  public Integer id; // assigned by the db
  public String key; // what the user specifies
  public String checksum;  // of the file contents

  @XmlJavaTypeAdapter(TimestampAdapter.class)
  public Timestamp timestamp;   // last modification time
  public String downloadName;
  public String contentType;
  public Long size;
  public String tag;
  public Integer bucketId;
  public String bucketName;
  public Integer versionNumber;
  public Status status;
  public Timestamp creationDate;

  public List<Object> versions;

  // empty constructor required for JAXB mapping
  private Object() {
  }

  public Object(Integer id, String key, String checksum, Timestamp timestamp, String downloadName, String contentType, Long size, String tag, Integer bucketId, String bucketName, Integer versionNumber, Status status, Timestamp creationDate) {
    this.id = id;
    this.key = key;
    this.checksum = checksum;
    this.timestamp = timestamp;
    this.downloadName = downloadName;
    this.contentType = contentType;
    this.size = size;
    this.tag = tag;
    this.bucketId = bucketId;
    this.bucketName = bucketName;
    this.versionNumber = versionNumber;
    this.status = status;
    this.creationDate = creationDate;
  }

  public Boolean areSimilar(Object object){

      // TODO : verify fields

      Boolean equals = this.key.equals(object.key) &&
                        this.bucketName.equals(object.bucketName) &&
                        this.status.equals(object.status);


    if (equals) {
      if ((this.contentType != null && object.contentType == null) && (this.contentType == null && object.contentType != null)) {
        equals = equals && this.contentType.equals(object.contentType);
      } else {
        equals = false;
      }
    }

    if (equals) {
      if ((this.downloadName != null && object.downloadName == null) && (this.downloadName == null && object.downloadName != null)) {
        equals = equals && this.downloadName.equals(object.downloadName);
      } else {
        return false;
      }
    }

    if (equals){
      if ((this.checksum != null && object.checksum == null) && (this.checksum == null && object.checksum != null)) {
        equals = equals && this.checksum.equals(object.checksum);
      } else {
        return false;
      }
    }

    return equals;

  }

}
