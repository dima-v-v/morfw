#!/usr/bin/env python
__author__ = 'mjacobson'


import logging
import logging.config
from morf import process_fasta
import fileinput
from contextlib import contextmanager
import sys

# Example logging
logging.config.fileConfig('logging.conf', disable_existing_loggers=False)
logging.addLevelName(logging.WARNING, "\033[1;31m%s\033[1;0m" % logging.getLevelName(logging.WARNING))
logging.addLevelName(logging.ERROR, "\033[1;41m%s\033[1;0m" % logging.getLevelName(logging.ERROR))
log = logging.getLogger()

default_input = "input.fasta"
default_output = "output.txt"

output_file = None


@contextmanager
def file_or_stdout(file_name):
    if file_name is None:
        yield sys.stdout
    else:
        with open(file_name, 'w+') as out_file:
            yield out_file


def callback(job):
    with file_or_stdout(output_file) as wfile:
        for tag, data in zip(job.labels, job.results):
            wfile.write("{0}\t{1}\n".format(tag, "\t".join(map(str, data))))


def process(input_fasta):
    job, thr = process_fasta(input_fasta, callback, 10, True)
    thr.join()  # Don't want asychronicity, join thread to main thread

if __name__ == '__main__':
    # import argparse

    if len(sys.argv[1:]) == 0:
        # No arguments use defaults
        log.info("Using Default input/output files ({0}, {1})".format(default_input, default_output))
        output_file = default_output
        with open(default_input, 'r') as myfile:
            input_data = "".join(myfile.read())
    else:
        input_data = "".join([line for line in fileinput.input()]).strip()
        output_file = None  # Will output to stdout

    process(input_data)
