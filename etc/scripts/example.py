#!/usr/bin/env python
__author__ = 'mjacobson'


import logging
import logging.config
from morf import process_fasta

# Example logging
logging.config.fileConfig('logging.conf', disable_existing_loggers=False)
logging.addLevelName(logging.WARNING, "\033[1;31m%s\033[1;0m" % logging.getLevelName(logging.WARNING))
logging.addLevelName(logging.ERROR, "\033[1;41m%s\033[1;0m" % logging.getLevelName(logging.ERROR))
log = logging.getLogger()


def callback(job):
    log.info("Name: {0}".format(job.name))
    log.info("Size: {0}".format(job.size))
    log.info("Status: {0}".format(job.status))
    log.info("Results Size: {0}".format(len(job.results)))
    log.info("Results: {0}...".format(job.results))


def main():
    job = process_fasta(">Example2\nMKEFYLTVEQIGDSIFERYIDSNGRERTREVEYKPSLFAHCPESQATKYFDIYGKPCTRKLFANMRDASQWIKRMEDIGLEALGMDDFKLAYLSDTYNYEIKYDHTKIRVANFDIEVTSPDGFPE", callback, 10, True)

if __name__ == '__main__':
    main()
