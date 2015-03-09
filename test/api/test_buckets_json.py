#!/usr/bin/env python2

__author__ = 'jgray@plos.org'

'''
Test cases for Content Repo Bucket requests.
'''
from ..api.RequestObject.buckets_json import BucketsJson
from ..Base.Config import API_BASE_URL

expected_bucket_prod = u'mogilefs-prod-repo'
expected_bucket_dev = u'corpus'
expected_bucket = ''

if(API_BASE_URL == 'http://sfo-perf-plosrepo01.int.plos.org:8002'):
  expected_bucket = expected_bucket_prod
elif(API_BASE_URL == 'http://rwc-prod-plosrepo.int.plos.org:8002'):
  expected_bucket = expected_bucket_prod
else:
  expected_bucket = expected_bucket_dev


class GetBuckets(BucketsJson):
  def test_buckets(self):
    """
    Get Buckets API call
    """
    self.get_buckets()
    self.verify_buckets(expected_bucket)

  def test_get_bucket_info(self):
    """
    Get BUCKETS/{bucketName} API call

    :return:
    """
    self.get_bucket_bucketname(expected_bucket)
    self.verify_specific_bucket(expected_bucket)

if __name__ == '__main__':
    BucketsJson._run_tests_randomly()
