#!/usr/bin/env python2

import requests

__author__    = 'Jono Finger'
__copyright__ = 'Copyright 2014, PLOS'
__version__   = '0.1'


class ContentRepo:

  def __init__(self, repoServer='http://localhost:8080/'):
    self.repoServer = repoServer

    # ping server to make sure it is up, will throw an exception if not found
    requests.get(repoServer + "/info")

  def listBuckets(self):
    url = self.repoServer + '/buckets/'
    r = requests.get(url)
    print (r.text)

  def createBucket(self, bucketName, bucketId=None):

    url = self.repoServer + '/buckets/'

    if bucketId == None:
      r = requests.post(url, data={'name': bucketName})
    else:
      r = requests.post(url, data={'name': bucketName, 'id': bucketId})

    return r.status_code == requests.codes.created

  def deleteBucket(self, bucketName):

    url = self.repoServer + '/buckets/'
    r = requests.delete(url + bucketName)

    return r.status_code == requests.codes.ok

  def uploadObject(self, bucketName, fileLocation, key, contentType, downloadName, create, timestampStr=None):

    # if timestamp == None:
    #   timestampStr = None
    # else:
    #   timestampStr = timestamp.strftime('%Y-%m-%d %X')  # yyyy-[m]m-[d]d hh:mm:ss[.f...]

    url = self.repoServer + '/objects/'

    files = {'file': open(fileLocation, 'rb')}
    values = {
      'key': key, 
      'bucketName': bucketName,
      'contentType': contentType,
      'downloadName': downloadName,
      'timestamp': timestampStr,
      'create': create
    }

    r = requests.post(url, files=files, data=values)

    return r
    #return r.status_code == requests.codes.created

  def _getObjectMetadataRequest(self, bucketName, key, versionNumber):
    url = self.repoServer + '/objects/' + bucketName

    values = {
      'key': key, 
      'version': versionNumber,
      'fetchMetadata': 'true'
    }

    r = requests.get(url, params=values)
    return r

  def objectExists(self, bucketName, key, versionNumber):
    return self._getObjectMetadataRequest(bucketName, key, versionNumber).status_code == requests.codes.ok
