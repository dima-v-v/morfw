#
# The morf project
#
# Copyright (c) 2015 University of British Columbia
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
#

"""Functional API for MoRF Chibi Web RESTful web service"""

__author__ = 'mjacobson'

import logging
import json
import urllib2
import threading
import time

log = logging.getLogger(__name__)
# log.addHandler(logging.NullHandler())

BASE_URL = "http://morfw.chibi.ubc.ca:8080/morf/rest/job/post"


class Job:
    """Represents a Job sent to MoRF Chibi Web RESTful web services
       Most likely not thread safe outside of internal use!"""

    def __init__(self, content):
        self.content = content.strip()

        rows = self.content.splitlines()
        self.name = rows[0]
        self.fasta = "".join(rows[1:])
        self.size = len(self.fasta)
        self.location = ""

        self.http_status = ""
        self.status = ""
        self.submitted = None
        self.complete = False
        self.eta = None
        self.labels = None
        self.results = None
        self.titles = None

    def _populate(self, response_json):
        """For use by morf library"""
        self.http_status = response_json.setdefault('httpstatus', "")
        self.submitted = response_json.setdefault('submitted', "")
        self.status = response_json.setdefault('status', "")
        self.complete = response_json.setdefault('complete', False)
        self.eta = response_json.setdefault('eta', None)
        self.labels = response_json.setdefault('labels', None)
        self.results = response_json.setdefault('results', None)
        self.titles = response_json.setdefault('titles', None)


def process_fasta(content, callback, max_polling_time=10, verbose=False):
    """Sends fasta sequence to MoRF Chibi Web RESTful web services to be processed.

       Asynchronous.

       Creates thread that periodically polls for job completion on MoRF Chibi Web servers,
       callback will be executed with the Job as the only parameter upon completion.

       Returns Job instance which will be populated with results upon job completion.
    """

    job = Job(content)

    if verbose:
        log.info("Name: {0}".format(job.name))
        log.info("Size: {0}".format(job.size))

    data = {"fasta": job.content}
    response = __send_request(BASE_URL, data)

    try:
        success = response['success']

        if not success:
            log.warn("Job Creation Failed: {0}".format(response['message']))
            return

        location = response['location']
        job.location = location

    except KeyError, e:
        log.error(e)
        log.error(response)
        return

    if verbose:
        log.info("Job Status available at {0}".format(location))

    thr = threading.Thread(target=__wait_for_job, args=(location, max_polling_time, job, callback, verbose), kwargs={})
    thr.daemon = True
    thr.start()

    return job, thr


def __wait_for_job(location, max_polling_time, job, callback, verbose=False):
    """Asynchronous job to be executed by process_fasta, periodically polls
       MoRF Chibi Web RESTful web services and mutates Job object"""
    polling_time = 1
    while True:
        complete, res = __is_job_complete(location)

        if not res['success']:
            log.warn(res['message'])
            break

        job._populate(res)
        if verbose:
            log.info(job.status)
        if complete:
            break

        time.sleep(polling_time)
        polling_time = min(polling_time+1, max_polling_time)

    if callback:
        callback(job)


def __is_job_complete(location):
    """MoRF Chibi Web RESTful web services poller"""
    response = __send_request(location)
    return response['complete'], response


def __send_request(location, content_dict=None):
    """Send HTTP Requests"""
    req = urllib2.Request(location)
    req.add_header('Content-Type', 'application/json')
    try:
        if content_dict is not None:
            response = urllib2.urlopen(req, json.dumps(content_dict))
        else:
            response = urllib2.urlopen(req)
        contents = response.read()
    except urllib2.HTTPError, error:
        contents = error.read()

    try:
        json_response = json.loads(contents)
    except ValueError:
        log.warning("Response could not be parsed as JSON", contents)
        return

    return json_response
