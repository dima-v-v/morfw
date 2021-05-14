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

package ubc.pavlab.morf.rest;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.primefaces.json.JSONException;
import org.primefaces.json.JSONObject;

import ubc.pavlab.morf.beans.JobManager;
import ubc.pavlab.morf.beans.SettingsCache;
import ubc.pavlab.morf.models.Chart;
import ubc.pavlab.morf.models.Job;

/**
 * TODO Document Me
 * 
 * @author mjacobson
 * @version $Id$
 */
@Path("/job")
public class JobEndpoint {

    private static final Logger log = Logger.getLogger( JobEndpoint.class );

    @Inject
    private JobManager jobManager;

    @Inject
    private SettingsCache settingsCache;

    public JobEndpoint() {
        log.info( "Job REST created" );
    }

    @GET
    @Path("/loadInfo")
    public Response getLoadInfo( @Context HttpServletRequest request ) {

        String ipAddress = request.getHeader( "X-FORWARDED-FOR" );
        if ( ipAddress == null ) {
            ipAddress = request.getRemoteAddr();
        }

        JSONObject response = new JSONObject();
        try {
            response.put( "httpstatus", 200 );
            response.put( "jobsInQueue", jobManager.getJobsInQueue() );
            response.put( "residuesInQueue", jobManager.getResiduesInQueue() );
            response.put( "jobsinClientQueue", jobManager.getJobsInClientQueue( ipAddress ) );
            response.put( "residuesInClientQueue", jobManager.getResiduesInClientQueue( ipAddress ) );

        } catch ( JSONException e1 ) {
            log.error( "Malformed JSON", e1 );
        }
        return Response.ok( response.toString(), MediaType.APPLICATION_JSON ).build();

    }

    @GET
    @Path("/{param}")
    public Response getMsg( @Context HttpServletRequest request, @PathParam("param" ) String msg) {

        Job job = jobManager.fetchSavedJob( msg, false );

        if ( job == null ) {
            JSONObject deleted = fail( 404, "Job Not Found" );
            try {
                deleted.put( "complete", true );
            } catch ( JSONException e ) {
                log.error( e );
            }
            return Response.status( 404 ).entity( deleted.toString() ).type( MediaType.APPLICATION_JSON ).build();
        }

        String ipAddress = request.getHeader( "X-FORWARDED-FOR" );
        if ( ipAddress == null ) {
            ipAddress = request.getRemoteAddr();
        }

        JSONObject response = new JSONObject();
        try {
            response.put( "httpstatus", 200 );
            response.put( "name", job.getName() );
            response.put( "size", job.getSequenceSize() );
            response.put( "status", job.getStatus() );
            response.put( "jobsInQueue", jobManager.getJobsInQueue() );
            response.put( "residuesInQueue", jobManager.getResiduesInQueue() );
            response.put( "jobsinClientQueue", jobManager.getJobsInClientQueue( ipAddress ) );
            response.put( "residuesInClientQueue", jobManager.getResiduesInClientQueue( ipAddress ) );
            response.put( "savedTimeLeft", job.getSaveTimeLeft() );
            response.put( "submitted", job.getSubmittedDate() );
            response.put( "success", true );

            // Race condition reasons
            boolean complete = job.getComplete();

            response.put( "complete", complete );
            if ( complete ) {
                Chart chart = new Chart( job );
                response.put( "labels", chart.getLabels() );
                response.put( "results", chart.getValues() );
                response.put( "titles", chart.getTitles() );
            } else {
                response.put( "eta", "Unknown" );
            }
        } catch ( JSONException e1 ) {
            log.error( "Malformed JSON", e1 );
        }
        return Response.ok( response.toString(), MediaType.APPLICATION_JSON ).build();

    }

    @GET
    @Path("/delete/{param}")
    public Response deleteStrMsg( @Context HttpServletRequest request, @PathParam("param" ) String msg) {
        Job job = jobManager.fetchSavedJob( msg, false );
        if ( job == null ) {
            return Response.status( 404 ).entity( fail( 404, "Job Not Found" ).toString() ).type( MediaType.APPLICATION_JSON ).build();
        }
        JSONObject response = new JSONObject();
        try {
            boolean success = jobManager.requestStopJob( job );
            response.put( "httpstatus", 200 );
            response.put( "message", success ? "Job Deleted" : "Failed To Delete Job" );
            response.put( "success", success );

        } catch ( JSONException e1 ) {
            log.error( "Malformed JSON", e1 );
        }
        return Response.ok( response.toString(), MediaType.APPLICATION_JSON ).build();

    }

    @POST
    @Path("/post")
    public Response postStrMsg( @Context HttpServletRequest request, String msg ) {
        log.info( msg );
        String content;
        try {
            JSONObject json = new JSONObject( msg );
            content = json.getString( "fasta" );
            log.info( content );
        } catch ( JSONException e ) {
            //log.warn( "Malformed JSON", e );
            return Response.status( 400 ).entity( fail( 400, "Malformed JSON" ).toString() ).type( MediaType.APPLICATION_JSON ).build();
        }

        if ( StringUtils.isBlank( content ) ) {
            return Response.status( 400 ).entity( fail( 400, "Blank FASTA" ).toString() ).type( MediaType.APPLICATION_JSON ).build();
        }

        String ipAddress = request.getHeader( "X-FORWARDED-FOR" );
        if ( ipAddress == null ) {
            ipAddress = request.getRemoteAddr();
        }

        //String sessionId = request.getSession( true ).getId();
        String sessionId = ipAddress;

        Job job = jobManager.createJob( sessionId, ipAddress, content, true, null );

        if ( job == null ) {
            return Response.status( 429 ).entity( fail( 400, "Too Many Jobs In Queue" ).toString() ).type( MediaType.APPLICATION_JSON ).build();
        }

        if ( !job.getFailed() ) {
            JSONObject response = new JSONObject();
            try {
                response.put( "httpstatus", 202 );
                response.put( "success", true );
                response.put( "message", "Job Accepted" );
                response.put( "name", job.getName() );
                response.put( "size", job.getSequenceSize() );
                response.put( "status", job.getStatus() );
                response.put( "jobsInQueue", jobManager.getJobsInQueue() );
                response.put( "residuesInQueue", jobManager.getResiduesInQueue() );
                response.put( "jobsInClientQueue", jobManager.getJobsInClientQueue( sessionId ) );
                response.put( "residuesInClientQueue", jobManager.getResiduesInClientQueue( sessionId ) );
                response.put( "location", settingsCache.getBaseUrl() + "rest/job/" + job.getSavedKey() );
            } catch ( JSONException e1 ) {
                log.error( "Malformed JSON", e1 );
            }
            return Response.status( 202 ).entity( response.toString() ).type( MediaType.APPLICATION_JSON ).build();
        } else {
            return Response.status( 400 ).entity( fail( 400, job.getStatus() ).toString() ).type( MediaType.APPLICATION_JSON ).build();
        }

    }

    private static JSONObject fail( int httpStatus, String message ) {
        JSONObject response = new JSONObject();
        try {
            response.put( "httpstatus", httpStatus );
            response.put( "success", false );
            response.put( "message", message );
        } catch ( JSONException e1 ) {
            log.error( "Malformed JSON", e1 );
        }
        return response;
    }

}
