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

package ubc.pavlab.morf.models;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.log4j.Logger;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

import ubc.pavlab.morf.beans.JobManager;

/**
 * TODO Document Me
 * 
 * @author mjacobson
 * @version $Id$
 */
public class Job implements Callable<String> {

    private static final Logger log = Logger.getLogger( Job.class );

    // Static path to resources
    private static String scriptNameA;
    private static String scriptBasePathA;
    private static String pathToInputA;
    private static String pathToOutputA;

    private static String scriptNameB;
    private static String scriptBasePathB;
    private static String pathToInputB;
    private static String pathToOutputB;

    // Information on creation of job
    private String sessionId;
    private String ipAddress;
    private String name;
    private int id;
    private String content;
    private int sequenceSize;
    private Date submittedDate;
    private boolean trainOnFullData;
    private String email;

    // Information on running / completion
    private Boolean running = false;
    private Boolean failed = false;
    private Boolean complete = false;
    private String status;

    // Results
    private Future<String> future;
    private StreamedContent resultFile;
    private long executionTime;

    // Saving Job information / results for later
    private boolean saved = false;
    private String savedKey;
    private Long saveExpiredDate;

    private JobManager jobManager;

    /**
     * @param sessionId
     * @param name
     * @param contents
     */
    public Job( String sessionId, String name, int id, String content, int sequenceSize, String ipAddress,
            boolean trainOnFullData, String email ) {
        super();
        this.sessionId = sessionId;
        this.name = name;
        this.content = content;
        this.ipAddress = ipAddress;
        this.id = id;
        this.sequenceSize = sequenceSize;
        this.trainOnFullData = trainOnFullData;
        this.email = email;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId( String sessionId ) {
        this.sessionId = sessionId;
    }

    public String getName() {
        return name;
    }

    public void setName( String name ) {
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public void setId( int id ) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent( String content ) {
        this.content = content;
    }

    public Future<String> getFuture() {
        return future;
    }

    public void setFuture( Future<String> future ) {
        this.future = future;
    }

    public Boolean getComplete() {
        if ( complete ) {
            return true;
        } else {
            if ( this.future == null ) {
                return false;
            } else {
                return this.future.isDone();
            }
        }
    }

    public void setComplete( Boolean complete ) {
        this.complete = complete;
    }

    // public String getResult() {
    // if ( result != null ) {
    // return result;
    // }
    // if ( getComplete() ) {
    // try {
    // this.result = this.future.get( 1, TimeUnit.SECONDS );
    // return result;
    // } catch ( InterruptedException | ExecutionException | TimeoutException e ) {
    // e.printStackTrace();
    // return null;
    // }
    // } else {
    // return null;
    // }
    //
    // }

    public StreamedContent getFile() {
        if ( getComplete() ) {
            try {
                String res;
                if ( !this.failed ) {
                    res = this.future.get( 1, TimeUnit.SECONDS );
                } else {
                    res = this.status;
                }

                InputStream in = IOUtils.toInputStream( res, "UTF-8" );
                this.resultFile = new DefaultStreamedContent( in, "text/plain", this.name + ".txt" );
                return resultFile;
            } catch ( InterruptedException | ExecutionException | TimeoutException | IOException e ) {
                e.printStackTrace();
                return null;
            }
        } else {
            return null;
        }
    }

    public String getFileString() {
        if ( getComplete() ) {
            try {
                String res;
                if ( !this.failed ) {
                    res = this.future.get( 1, TimeUnit.SECONDS );
                } else {
                    res = this.status;
                }

                return res;
            } catch ( InterruptedException | ExecutionException | TimeoutException e ) {
                log.error( e );
                return null;
            }
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        return "Job [sessionId=" + sessionId + ", ipAddress=" + ipAddress + ", name=" + name + ", id=" + id
                + ", complete=" + complete + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + id;
        result = prime * result + ( ( sessionId == null ) ? 0 : sessionId.hashCode() );
        return result;
    }

    @Override
    public boolean equals( Object obj ) {
        if ( this == obj ) return true;
        if ( obj == null ) return false;
        if ( getClass() != obj.getClass() ) return false;
        Job other = ( Job ) obj;
        if ( id != other.id ) return false;
        if ( sessionId == null ) {
            if ( other.sessionId != null ) return false;
        } else if ( !sessionId.equals( other.sessionId ) ) return false;
        return true;
    }

    @Override
    public String call() throws Exception {
        log.info( "Starting job (" + name + ") for session: (" + sessionId + ") and IP: (" + ipAddress + ")" );

        this.running = true;
        this.status = "Processing";

        String pathToInput = trainOnFullData ? pathToInputA : pathToInputB;
        String scriptName = trainOnFullData ? scriptNameA : scriptNameB;
        String scriptBasePath = trainOnFullData ? scriptBasePathA : scriptBasePathB;
        String pathToOutput = trainOnFullData ? pathToOutputA : pathToOutputB;

        // Write content to input
        File file = new File( pathToInput );

        try (FileOutputStream fop = new FileOutputStream( file )) {

            // if file doesn't exists, then create it
            if ( !file.exists() ) {
                file.createNewFile();
            }

            BufferedWriter writer = new BufferedWriter( new OutputStreamWriter( fop ) );
            writer.write( content );
            writer.newLine();
            writer.flush();
            writer.close();

        } catch ( IOException e ) {
            log.error( e );

        }

        // Execute script
        StopWatch sw = new StopWatch();
        sw.start();
        executeCommand( "./" + scriptName, scriptBasePath );
        sw.stop();
        this.executionTime = sw.getTime() / 1000L;

        // Get output
        InputStream resultFile = new FileInputStream( pathToOutput );

        StringWriter writer = new StringWriter();
        IOUtils.copy( resultFile, writer, "UTF-8" );
        log.info( "Finished job (" + name + ") for session: (" + sessionId + ") and IP: (" + ipAddress + ")" );
        this.running = false;
        this.saveExpiredDate = System.currentTimeMillis() + JobManager.PURGE_AFTER; // Begin save timer
        this.complete = true;
        jobManager.updatePositions( this.sessionId );
        jobManager.emailJobCompletion( this, writer.toString() );
        jobManager = null;
        return writer.toString();
    }

    private String executeCommand( String command, String path ) {

        StringBuffer output = new StringBuffer();

        Process p;
        try {
            p = Runtime.getRuntime().exec( command, null, new File( path ) );
            p.waitFor();
            BufferedReader reader = new BufferedReader( new InputStreamReader( p.getInputStream() ) );

            String line = "";
            while ( ( line = reader.readLine() ) != null ) {
                output.append( line + "\n" );
            }

        } catch ( Exception e ) {
            e.printStackTrace();
        }

        return output.toString();
    }

    public Date getSubmittedDate() {
        return submittedDate;
    }

    public void setSubmittedDate( Date submittedDate ) {
        this.submittedDate = submittedDate;
    }

    public boolean isTrainOnFullData() {
        return trainOnFullData;
    }

    public void setTrainOnFullData( boolean trainOnFullData ) {
        this.trainOnFullData = trainOnFullData;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail( String email ) {
        this.email = email;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus( String status ) {
        this.status = status;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress( String ipAddress ) {
        this.ipAddress = ipAddress;
    }

    public int getSequenceSize() {
        return sequenceSize;
    }

    public void setSequenceSize( int sequenceSize ) {
        this.sequenceSize = sequenceSize;
    }

    public long getExecutionTime() {
        return executionTime;
    }

    public Boolean getFailed() {
        return failed;
    }

    public void setFailed( Boolean failed ) {
        this.failed = failed;
    }

    public Boolean getRunning() {
        return running;
    }

    public boolean isSaved() {
        return saved;
    }

    public void setSaved( boolean saved ) {
        this.saved = saved;
    }

    public String getSavedKey() {
        return savedKey;
    }

    public void setSavedKey( String savedKey ) {
        this.savedKey = savedKey;
    }

    public Long getSaveExpiredDate() {
        if ( !saved ) {
            return null;
        }
        return saveExpiredDate;
    }

    public void renewSave() {
        if ( complete ) {
            this.saveExpiredDate = System.currentTimeMillis() + JobManager.PURGE_AFTER;
        }
    }

    public void purgeSaveInfo() {
        this.saveExpiredDate = null;
        this.saved = false;
        this.savedKey = null;
    }

    public Long getSaveTimeLeft() {
        if ( !saved ) {
            return null;
        }
        return complete ? ( ( saveExpiredDate - System.currentTimeMillis() ) ) / 1000 / 60 / 60 : null;
    }

    public void setJobManager( JobManager jobManager ) {
        this.jobManager = jobManager;
    }

    public static void setPathsA( String scriptName, String scriptBasePath, String pathToInput, String pathToOutput ) {
        Job.scriptNameA = scriptName;
        Job.scriptBasePathA = scriptBasePath;
        Job.pathToInputA = pathToInput;
        Job.pathToOutputA = pathToOutput;

    }

    public static void setPathsB( String scriptName, String scriptBasePath, String pathToInput, String pathToOutput ) {
        Job.scriptNameB = scriptName;
        Job.scriptBasePathB = scriptBasePath;
        Job.pathToInputB = pathToInput;
        Job.pathToOutputB = pathToOutput;

    }

}
