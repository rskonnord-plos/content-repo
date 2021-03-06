#!/usr/bin/env python2
# -*- coding: utf-8 -*-

# Copyright (c) 2017 Public Library of Science
#
# Permission is hereby granted, free of charge, to any person obtaining a
# copy of this software and associated documentation files (the "Software"),
# to deal in the Software without restriction, including without limitation
# the rights to use, copy, modify, merge, publish, distribute, sublicense,
# and/or sell copies of the Software, and to permit persons to whom the
# Software is furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
# THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
# FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
# DEALINGS IN THE SOFTWARE.

"""
Class for accessing XML data, returning a dom representation

"""

__author__ = 'jgray@plos.org'

import Config
import urllib
import xml.etree.ElementTree as ET

xmlurl = str(Config.repo_config['transport']) + '://' + str(Config.repo_config['host']) + ':' + \
    str(Config.repo_config['port']) + str(Config.repo_config['path']) + '/objects/'


class ParseXML(object):

  def get_auths(self, bucket, article):
    authors = []
    try:
      xmlpath = xmlurl + bucket + '?key=' + article + '.XML'
      article_xml = urllib.urlopen(xmlpath)
      root = ET.parse(article_xml).getroot()
      for author in root.findall(".//contrib[@contrib-type='author']"):
        fullname = ""
        fname = author.find('./name/given-names')
        lname = author.find('./name/surname')
        collab = author.find('./collab')
        if fname is not None and lname is not None:
          fullname = fname.text + ' ' + lname.text
        else:
          fullname = collab.text
          print(fullname + ' has name issue')
          print("Author with no first or last name: " + fullname)
        authors.append(fullname)
    except Exception as e:
      print(e)
    return authors

  def get_corresp_auths(self, bucket, article):
    corresp_auth = []
    try:
      xmlpath = xmlurl + bucket + '?key=' + article + '.XML'
      article_xml = urllib.urlopen(xmlpath)
      root = ET.parse(article_xml).getroot()
      for contrib in root.findall(".//contrib[@contrib-type='author']"):
        fullname = ""
        corresp = contrib.find("./xref[@ref-type='corresp']")
        if corresp is not None:
          fname_node = contrib.find('./name/given-names')
          lname_node = contrib.find('./name/surname')
          if fname_node is not None and lname_node is not None:
            fullname = fname_node.text + ' ' + lname_node.text
          else:
            print(corresp + ' has name issue')
            fullname = contrib.find('./collab')
            print("Author with no first or last name: " + fullname)
          corresp_auth.append(fullname)
    except Exception as e:
      print(e)
    return corresp_auth

  def get_cocontributing_auths(self, bucket, article):
    cocontrib_auth = []
    try:
      xmlpath = xmlurl + bucket + '?key=' + article + '.XML'
      article_xml = urllib.urlopen(xmlpath)
      root = ET.parse(article_xml).getroot()
      for contrib in root.findall(".//contrib[@equal-contrib='yes']"):
        fullname = ""
        if contrib is not None:
          fname_node = contrib.find('./name/given-names')
          lname_node = contrib.find('./name/surname')
          if fname_node is not None and lname_node is not None:
            fullname = fname_node.text + ' ' + lname_node.text
          else:
            fullname = contrib.find('./collab')
            print("Author with no first or last name: " + fullname)
            print(contrib + ' has name issue')
          cocontrib_auth.append(fullname)
    except Exception as e:
      print(e)
    return cocontrib_auth

  def get_customfootnote_auths(self, bucket, article):
    customfootnote_auth = []
    try:
      xmlpath = xmlurl + bucket + '?key=' + article + '.XML'
      article_xml = urllib.urlopen(xmlpath)
      root = ET.parse(article_xml).getroot()
      for contrib in root.findall(".//contrib[@contrib-type='author']"):
        fullname = ""
        customfootnote = contrib.find("./xref[@ref-type='fn']")
        if customfootnote is not None:
          fname_node = contrib.find('./name/given-names')
          lname_node = contrib.find('./name/surname')
          if fname_node is not None and lname_node is not None:
            fullname = fname_node.text + ' ' + lname_node.text
          else:
            fullname = contrib.find('./collab')
            print("Author with no first or last name: " + fullname)
            print(customfootnote + ' has name issue')
          customfootnote_auth.append(fullname)
    except Exception as e:
      print(e)
    return customfootnote_auth

  def get_article_sections(self, bucket, article):
    article_sections = []
    patient_summary = False
    try:
      xmlpath = xmlurl + bucket + '?key=' + article + '.XML'
      article_xml = urllib.urlopen(xmlpath)
      root = ET.parse(article_xml).getroot()

      for abstract in root.findall(".//front/article-meta/abstract"):
        if not abstract.attrib:
          article_sections.append('Abstract')
        else:
          if str(abstract.attrib['abstract-type']):
            if str(abstract.attrib['abstract-type']) == 'toc':
              continue
            else:
              if str(abstract.attrib['abstract-type']) == 'patient':
                patient_summary = True
              else:
                article_sections.append(abstract.find("./title").text)

      for section in root.findall(".//body/sec"):
        title = section.find("./title")
        #print(str(title.text))
        if str(title.text) == "None":
          continue
        else:
          article_sections.append(title.text)

      if root.findall(".//back/ack"):
        article_sections.append('Acknowledgments')

      if root.findall(".//front/article-meta/author-notes/fn[@fn-type='con']"):
        article_sections.append('Author Contributions')

      for refs in root.findall(".//back/ref-list"):
        title = refs.find("./title")
        article_sections.append(title.text)

      if patient_summary:
        article_sections.append('Patient Summary')

    except Exception as e:
      print(e)
    return(article_sections)
