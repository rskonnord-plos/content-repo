#!/usr/bin/env python2

"""
Base class for CREPO Bucket JSON related services
"""

__author__ = 'jgray@plos.org'

from ...Base.base_service_test import BaseServiceTest
from ...Base.Config import API_BASE_URL
from ...Base.api import needs

BUCKETS_API = API_BASE_URL + '/buckets'
DEFAULT_HEADERS = {'Accept': 'application/json'}
HEADER = '-H'

# get BUCKET_NAME based on whether API_BASE_URL is on prod or not.
BUCKET_NAME = u'corpus'
if API_BASE_URL.split('/')[2] in ('sfo-perf-plosrepo01.int.plos.org:8002', 'rwc-prod-plosrepo.int.plos.org:8002'):
  BUCKET_NAME = u'mogilefs-prod-repo'

# Http Codes
OK = 200
CREATED = 201
BAD_REQUEST = 400
NOT_FOUND = 404


class BucketsJson(BaseServiceTest):

  def get_buckets(self):
    """
    Calls CREPO API to get buckets list
    GET /buckets
    """
    header = {'header': HEADER}
    self.doGet('%s' % BUCKETS_API, header, DEFAULT_HEADERS)
    self.parse_response_as_json()
    self.buckets = [x['bucketName'] for x in self.parsed.get_buckets()]

  def post_bucket(self, name=None):
    """
    Calls CREPO API to create bucket by name.
    POST /buckets form: name={name}

    :param name: optional bucket name.
    """
    data = None
    if name:
      data = {'name': name}
    self.doPost('%s' % BUCKETS_API, data, None, DEFAULT_HEADERS)
    self.parse_response_as_json()

  def get_bucket(self, name):
    """
    Calls CREPO API to get a bucket by name
    GET /buckets/{name}

    :param name: bucket name.
    """
    self.doGet('%s/%s' % (BUCKETS_API, name), None, DEFAULT_HEADERS)
    self.parse_response_as_json()

  def delete_bucket(self, name):
    """
    Calls CREPO API to get a bucket by name
    DELETE /buckets/{name}

    :param name: bucket name.
    """
    self.doDelete('%s/%s' % (BUCKETS_API, name), None, DEFAULT_HEADERS)

  @needs('parsed', 'parse_response_as_json()')
  def verify_get_buckets(self):
    """
    Verifies a valid response for GET /buckets and fills self.buckets.
    """
    self.verify_http_status(OK)

  def verify_has_bucket(self, name):
    """
    Verifies that the name exists in buckets.
    """
    self.assertTrue(name in self.buckets, '%r not found in %r' % (name, self.buckets))

  def verify_no_bucket(self, name):
    """
    Verifies that the name does not exists in buckets.
    """
    self.assertTrue(name not in self.buckets, '%r found in %r' % (name, self.buckets))

  @needs('parsed', 'parse_response_as_json()')
  def verify_get_bucket(self, name):
    """
    Verifies a valid response to api request GET /buckets/{name}
    """
    actual_bucket = self.parsed.get_bucketName()
    self.assertTrue(name in actual_bucket, '%r is not in %r' % (name, actual_bucket))

  @needs('parsed', 'parse_response_as_json()')
  def verify_post_bucket(self, name):
    """
    Verifies a valid response to api request POST /buckets
    """
    self.verify_http_status(CREATED)
    actual_bucket = self.parsed.get_bucketName()
    self.assertTrue(name in actual_bucket, '%r is not in %r' % (name, actual_bucket))

  @needs('parsed', 'parse_response_as_json()')
  def verify_http_status(self, httpCode):
    """
    Verifies API response according to http response code
    :param
    :return: Error msg on Failure
    """
    self.verify_http_code_is(httpCode)
    if httpCode == OK or httpCode == CREATED:
      self.assertIsNotNone(self.parsed.get_bucketName())
      self.assertIsNotNone(self.parsed.get_bucketCreationDate())
      self.assertIsNotNone(self.parsed.get_bucketTimestamp())
    else:
      self.assertIsNotNone(self.parsed.get_repoErrorCode())
      self.assertIsNotNone(self.parsed.get_message())

  def verify_default_bucket(self):
    """
    Verify that the default bucket has active objects
    """
    self.assertTrue(self.parsed.get_bucketActiveObjects() > 0,
                    '%r is not valid' % (self.parsed.get_bucketActiveObjects()))

  @staticmethod
  def get_bucket_name():
    """
    Get the bucketName according our development or performance stack environments
    :return: bucket name string
    """
    return BUCKET_NAME
