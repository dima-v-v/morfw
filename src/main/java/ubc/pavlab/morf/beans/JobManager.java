/*
 * The morf project
 * 
 * Copyright (c) 2015 University of British Columbia
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package ubc.pavlab.morf.beans;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.log4j.Logger;
import org.omnifaces.cdi.Eager;

import ubc.pavlab.morf.models.Job;
import ubc.pavlab.morf.models.PurgeOldJobs;
import ubc.pavlab.morf.models.ValidationResult;
import ubc.pavlab.morf.service.SessionIdentifierGenerator;

/**
 * TODO Document Me
 * 
 * @author mjacobson
 * @version $Id$
 */
@Named
@Eager
@ApplicationScoped
public class JobManager {

    private static final Logger log = Logger.getLogger( JobManager.class );

    @Inject
    private MailSender mailSender;

    @Inject
    private SettingsCache settingsCache;

    // Contains map of random token to saved job for future viewing
    private Map<String, Job> savedJobs = new HashMap<>();

    // Used to create new save tokens
    private final SessionIdentifierGenerator sig = new SessionIdentifierGenerator();

    // Used to periodically purge the old saved jobs
    private ScheduledExecutorService scheduler;
    public static long PURGE_AFTER = 86400000;

    // Contains a representation of the internal queue of jobs
    private LinkedList<Job> jobQueueMirror = new LinkedList<Job>();

    // private ExecutorService processJob;
    // private ThreadPoolExecutor executor;
    private ExecutorService executor;

    private Map<String, Queue<Job>> waitingList = new ConcurrentHashMap<>();

    private static int MAX_JOBS_IN_QUEUE = 2;

    private static final int SESSION_MAX_JOBS = 200;
    private static final int MINIMUM_SEQUENCE_SIZE = 26;

    // File used to validate fasta content
    private String ffile;

    // Job Queue info;
    private int residuesInQueue = 0;

    private Object clientResiduesLock = new Object();
    private Map<String, Integer> clientResidues = new ConcurrentHashMap<>();

    private Integer jobIdIncrementer = 0;

    public int getNewJobId() {
        synchronized ( jobIdIncrementer ) {
            return jobIdIncrementer++;
        }
    }

    @PostConstruct
    public void init() {
        PURGE_AFTER = settingsCache.getJobPurgeTime() * 60 * 60 * 1000;
        executor = Executors.newSingleThreadExecutor();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        // old after 1 day, checks every hour
        scheduler.scheduleAtFixedRate( new PurgeOldJobs( savedJobs ), 0, 1, TimeUnit.HOURS );

        ffile = settingsCache.getProperty( "morf.validate" );
        // executor = (ThreadPoolExecutor) Executors.newSingleThreadExecutor();
    }

    @PreDestroy
    public void destroy() {
        log.info( "JobManager destroyed" );
        // processJob.shutdownNow();
        executor.shutdownNow();
        scheduler.shutdownNow();
    }

    public Job createJob( String sessionId, String ipAddress, String content, boolean trainOnFullData,
            String email ) {

        ValidationResult vr = validate( content );

        String textStr[] = content.split( "\\r?\\n" );

        String label = "unknown";
        int sequenceSize = 0;

        Job job;

        if ( vr.isSuccess() ) {

            String newContent = "";

            if ( textStr.length > 1 ) {
                label = textStr[0];
                newContent += label + "\r\n";
                // if ( label.startsWith( ">" ) ) {
                // label = label.substring( 1 );
                // }

                for ( int i = 1; i < textStr.length; i++ ) {
                    String line = textStr[i].replaceAll( "\\s+", "" );
                    sequenceSize += line.length();
                    newContent += line + "\r\n";

                }

            }

            if ( sequenceSize < MINIMUM_SEQUENCE_SIZE ) {
                job = new Job( sessionId, label, getNewJobId(), content, 0, ipAddress, trainOnFullData, email );
                job.setComplete( true );
                job.setFailed( true );
                job.setStatus( "Error report:\nError(s) found\n" + label + "\nError : "
                        + "Sequence too small; must be at least " + MINIMUM_SEQUENCE_SIZE + " residues" );
            } else {

                job = new Job( sessionId, label, getNewJobId(), newContent, sequenceSize, ipAddress, trainOnFullData,
                        email );
                boolean success = submitToWaitingList( job );

                if ( !success ) {
                    return null;
                }
            }

        } else {
            job = new Job( sessionId, label, getNewJobId(), content, 0, ipAddress, trainOnFullData, email );
            job.setComplete( true );
            job.setFailed( true );
            job.setStatus( vr.getContent() );
        }

        return job;

    }

    private boolean submitToWaitingList( Job job ) {
        log.info( "Submitting job (" + job.getId() + ") for session: (" + job.getSessionId() + ") and IP: ("
                + job.getIpAddress() + ")" );

        Queue<Job> jobs = waitingList.get( job.getSessionId() );

        if ( jobs == null ) {
            jobs = new LinkedList<Job>();
            waitingList.put( job.getSessionId(), jobs );
            log.info( "new session" );
        }

        if ( jobs.size() > SESSION_MAX_JOBS ) {
            log.info( "Too many jobs (" + job.getId() + ") for session: (" + job.getSessionId() + ") and IP: ("
                    + job.getIpAddress() + ")" );
            return false;
        }

        synchronized ( jobs ) {

            if ( !jobs.contains( job ) ) {
                jobs.add( job );
                job.setStatus( "Pending" );
                saveJob( job );
                log.info( job.getSavedKey() );
                synchronized ( clientResiduesLock ) {
                    Integer clientResidue = clientResidues.get( job.getSessionId() );
                    if ( clientResidue == null ) {
                        clientResidue = 0;
                    }
                    clientResidue += job.getSequenceSize();
                    clientResidues.put( job.getSessionId(), clientResidue );
                }
                submitJobsFromWaitingList( job.getSessionId(), jobs );
            }
        }
        return true;

    }

    private void submitJobsFromWaitingList( String session ) {

        Queue<Job> jobs = waitingList.get( session );

        if ( jobs != null ) {
            submitJobsFromWaitingList( session, jobs );
        }

    }

    private void submitJobsFromWaitingList( String session, Queue<Job> jobs ) {
        int cnt = 0;
        synchronized ( jobQueueMirror ) {

            for ( Job job : jobQueueMirror ) {
                if ( job.getSessionId().equals( session ) ) cnt++;
            }
        }

        if ( cnt < MAX_JOBS_IN_QUEUE ) {
            Job job;
            synchronized ( jobs ) {
                job = jobs.poll();
            }
            if ( job != null ) {
                job.setSubmittedDate( new Date() );
                submit( job );
            }
        }

    }

    private void submit( Job job ) {
        // TODO is synchronized necessary?
        synchronized ( jobQueueMirror ) {
            log.info( "Submitting job (" + job.getId() + ") for session: (" + job.getSessionId() + ") and IP: ("
                    + job.getIpAddress() + ")" );
            job.setJobManager( this );
            Future<String> future = executor.submit( job );
            job.setFuture( future );
            jobQueueMirror.add( job );
            job.setStatus( "Position: " + Integer.toString( jobQueueMirror.size() ) );
            residuesInQueue += job.getSequenceSize();
            synchronized ( clientResiduesLock ) {
                Integer clientResidue = clientResidues.get( job.getSessionId() );
                if ( clientResidue == null ) {
                    // This shouldn't happen
                    clientResidue = 0;
                    log.error(
                            "Somehow, we are submitting a job who's residues were never counted towards the total Client Queue residues." );
                } else {
                    clientResidue -= job.getSequenceSize();
                }
                clientResidues.put( job.getSessionId(), clientResidue );

            }
        }
    }

    public boolean requestStopJob( Job job ) {
        log.info( "Requesting job stop (" + job.getId() + ") for session: (" + job.getSessionId() + ") and IP: ("
                + job.getIpAddress() + ")" );
        boolean canceled = false;

        Queue<Job> jobs = waitingList.get( job.getSessionId() );

        synchronized ( jobs ) {
            if ( jobs.contains( job ) ) {
                // Not yet submitted, just remove it from waiting list
                jobs.remove( job );
                removeSaveJob( job );
                submitJobsFromWaitingList( job.getSessionId(), jobs );
                return true;
            }
        }
        synchronized ( jobQueueMirror ) {
            if ( jobQueueMirror.contains( job ) ) {
                if ( job.getComplete() ) {
                    canceled = removeJob( job );
                } else if ( job.getRunning() ) {
                    canceled = false;
                    // canceled = jobManager.cancelJob( job ); // Off for now because doesn't work if job is running
                } else {
                    canceled = cancelJob( job );

                }
            } else if ( job.getComplete() ) {
                // Job complete and already removed from jobQueueMirror
                canceled = true;
            }

        }
        if ( canceled ) {
            jobs.remove( job );
            removeSaveJob( job );
            submitJobsFromWaitingList( job.getSessionId(), jobs );
        }
        return canceled;

    }

    private boolean cancelJob( Job job ) {
        boolean canceled = false;
        synchronized ( jobQueueMirror ) {
            Future<String> future = job.getFuture();
            canceled = future.cancel( true );

            if ( canceled ) {
                jobQueueMirror.remove( job );
            }
        }
        return canceled;
    }

    /**
     * Attempts to remove job from jobs list, if job is not yet complete this will fail. See @cancelJob
     * 
     * @param job
     * @return True if job is removed
     */
    private boolean removeJob( Job job ) {
        boolean removed = false;
        if ( job.getComplete() ) {
            synchronized ( jobQueueMirror ) {
                jobQueueMirror.remove( job );
                removed = true;
            }
        }
        return removed;
    }

    // RIght HERE

    public void updatePositions( String sessionId ) {
        synchronized ( jobQueueMirror ) {
            int idx = 1;
            int residues = 0;

            for ( Iterator<Job> iterator = jobQueueMirror.iterator(); iterator.hasNext(); ) {
                Job job = iterator.next();

                if ( job.getRunning() ) {
                    job.setStatus( "Processing" );
                    idx++;
                    residues += job.getSequenceSize();
                } else if ( job.getComplete() ) {
                    job.setStatus( "Completed in " + job.getExecutionTime() + "s" );
                    iterator.remove();
                } else {
                    job.setStatus( "Position: " + Integer.toString( idx ) );
                    idx++;
                    residues += job.getSequenceSize();
                }

            }

            // This happens before force submit so that we can add the residues of the new jobs
            residuesInQueue = residues;
        }

        // Add new job for given session
        submitJobsFromWaitingList( sessionId );
        log.info( String.format( "Jobs in queue: %d", jobQueueMirror.size() ) );

    }

    public boolean emailJobCompletion( Job job, String attachment ) {
        if ( job.getEmail() != null ) {

            String recipientEmail = job.getEmail();
            String subject = "Job Complete";

            StringBuilder content = new StringBuilder();
            content.append( "<p>Job Complete</p>" );
            content.append( "<p>Label: " + job.getName() + "</p>" );
            content.append( "<p>Size: " + job.getSequenceSize() + "</p>" );
            content.append( "<p>Training: " + ( job.isTrainOnFullData() ? "Full" : "Training" ) + "</p>" );
            content.append( "<p>Submitted: " + job.getSubmittedDate() + "</p>" );
            content.append( "<p>Status: " + job.getStatus() + "</p>" );
            if ( job.isSaved() ) {
                content.append( "<p>Saved Link: " + "<a href='http://" + settingsCache.getBaseUrl()
                        + "savedJob.xhtml?key=" + job.getSavedKey() + "' target='_blank'>http://"
                        + settingsCache.getBaseUrl() + "savedJob.xhtml?key=" + job.getSavedKey() + "'</a></p>" );
            }
            String attachmentName = job.getName() + ".txt";

            return mailSender.sendMail( recipientEmail, subject, content.toString(), attachmentName, attachment );
        }

        return false;

    }

    public Job fetchSavedJob( String key, boolean remove ) {
        synchronized ( savedJobs ) {
            if ( remove ) {
                return savedJobs.remove( key );
            } else {
                return savedJobs.get( key );
            }
        }
    }

    private String saveJob( Job job ) {
        synchronized ( savedJobs ) {
            String key = sig.nextSessionId();
            job.setSavedKey( key );
            job.setSaved( true );
            savedJobs.put( key, job );
            return key;
        }
    }

    public void renewSaveJob( Job job ) {
        synchronized ( savedJobs ) {
            if ( job.isSaved() ) {
                job.renewSave();
            }

        }
    }

    private void removeSaveJob( Job job ) {
        synchronized ( savedJobs ) {
            savedJobs.remove( job.getSavedKey() );
        }
        if ( job.isSaved() ) {
            job.purgeSaveInfo();
        }
    }

    private synchronized ValidationResult validate( String content ) {

        ProcessBuilder pb = new ProcessBuilder( ffile, "/dev/stdin", "/dev/stdout" );
        pb.redirectErrorStream( true );

        Process process = null;
        try {
            process = pb.start();
        } catch ( IOException e ) {
            log.error( "Couldn't start the validation process.", e );
            return new ValidationResult( false, "ERROR: Something went wrong!" );
        }

        try {
            if ( process != null ) {
                BufferedWriter bw = new BufferedWriter( new OutputStreamWriter( process.getOutputStream() ) );

                // BufferedReader inputFile = new BufferedReader(new InputStreamReader(new FileInputStream(
                // "/home/mjacobson/morf/input.txt")));
                //
                // String currInputLine = null;
                // while ((currInputLine = inputFile.readLine()) != null) {
                // bw.write(currInputLine);
                // bw.newLine();
                // }
                // bw.close();
                // inputFile.close();
                bw.write( content );
                bw.close();
            }
        } catch ( IOException e ) {
            log.error( "Either couldn't read from the input file or couldn't write to the OutputStream.", e );
            return new ValidationResult( false, "ERROR: Something went wrong!" );
        }

        BufferedReader br = new BufferedReader( new InputStreamReader( process.getInputStream() ) );

        String currLine = null;
        boolean res = false;
        StringBuilder resultContent = new StringBuilder();
        try {
            currLine = br.readLine();
            if ( currLine != null ) {
                if ( currLine.startsWith( ">" ) ) {
                    res = true;
                } else {
                    res = false;
                }

                resultContent.append( currLine );
                resultContent.append( System.lineSeparator() );

                while ( ( currLine = br.readLine() ) != null ) {
                    resultContent.append( currLine );
                    resultContent.append( System.lineSeparator() );
                }

            }

            br.close();
        } catch ( IOException e ) {
            log.error( "Couldn't read the output.", e );
            return new ValidationResult( false, "ERROR: Something went wrong!" );
        }

        return new ValidationResult( res, resultContent.toString() );
    }

    public int getJobsInQueue() {
        return jobQueueMirror.size();
    }

    public int getResiduesInQueue() {
        return residuesInQueue;
    }

    public int getJobsInClientQueue( String sessionId ) {
        return waitingList.get( sessionId ).size();
    }

    public int getResiduesInClientQueue( String sessionId ) {
        synchronized ( clientResiduesLock ) {
            Integer clientResidue = clientResidues.get( sessionId );
            return clientResidue == null ? 0 : clientResidue;
        }
    }

}
