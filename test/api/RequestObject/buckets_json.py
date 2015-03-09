#!/usr/bin/env python2

"""
Base class for CREPO Bucket JSON related services
"""

__author__ = 'jgray@plos.org'

from ...Base.base_service_test import BaseServiceTest
from ...Base.Config import API_BASE_URL
from ...Base.api import needs

BUCKETS_API = API_BASE_URL + '/v1/buckets'
DEFAULT_HEADERS = {'Accept': 'application/json'}
HEADER = '-H'



class BucketsJson(BaseServiceTest):

  def get_buckets(self):
    """
    Calls CREPO API to get bucket list
    :param None
    :return:JSON response
    """
    header = {'header': HEADER}
    self.doGet('%s' % BUCKETS_API, header, DEFAULT_HEADERS)
    self.parse_response_as_json()

  @needs('parsed', 'parse_response_as_json()')
  def verify_buckets(self, bucketname):
    """
    Verifies a valid response to api request GET /buckets
    by validating the corpus bucket specific to either our
    development or performance stack environments.

    :param bucketname
    :return: Success or Error msg on Failure
    """
    print ('Validating buckets...'),
    actual_buckets = self.parsed.get_bucketName()
    self.assertTrue(bucketname in actual_buckets, bucketname + ' not found in ' + unicode(actual_buckets))


  def get_bucket_bucketname(self, bucketname):
    """
    Calls CREPO API to get information on a specific bucket
    :param bucketname
    :return: Success or Error msg on failure
    """
    header = {'header': HEADER}
    self.doGet('%s/%s' % (BUCKETS_API, bucketname), header, DEFAULT_HEADERS)
    self.parse_response_as_json()

  def verify_specific_bucket(self, bucketname):
    """
    Verifies a valid response to api request GET /buckets/{bucketName}

    :return: Success or Error msg on failure.
    """
    bucket_id = self.parsed.get_bucketID()
    bucket_name = self.parsed.get_bucketName()
    bucket_time_stamp = self.parsed.get_bucketTimestamp()
    bucket_creation_date = self.parsed.get_bucketCreationDate()
    bucket_active_objects = self.parsed.get_bucketActiveObjects()
    bucket_total_objects = self.parsed.get_bucketTotalObjects()
    print('bucketID: ' + unicode(bucket_id), 'bucketName: ' + str(bucket_name), 'timestamp: ' + str(bucket_time_stamp), 'creationDate: ' + str(bucket_creation_date), 'activeObjects: ' + unicode(bucket_active_objects), 'totalObjects: ' + unicode(bucket_total_objects))

