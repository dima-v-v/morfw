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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.log4j.Logger;

/**
 * Handles collecting and formatting data from the results of a job to be handled in the front-end by HighCharts
 * 
 * @author mjacobson
 * @version $Id$
 */
public class Chart {

    private static final Logger log = Logger.getLogger( Chart.class );

    private final String name;
    private final boolean ready;

    private final List<double[]> values = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    private final List<String> titles = new ArrayList<>();

    public Chart( Job job ) {

        if ( job != null ) {

            String name = job.getName();
            name = name.startsWith( ">" ) ? name.substring( 1 ) : name;
            this.name = name;

            if ( job.getComplete() && !job.getFailed() ) {

                String res = null;
                try {
                    res = job.getFuture().get( 1, TimeUnit.SECONDS );
                } catch ( InterruptedException | ExecutionException | TimeoutException e ) {
                    log.error( e );
                }

                boolean columnsComplete = false;
                boolean inColumnSection = false;

                String textStr[] = res.split( "\\r?\\n" );
                for ( int i = 0; i < textStr.length; i++ ) {
                    String[] split = textStr[i].split( "\t" );
                    if ( split[0].startsWith( "#" ) ) {
                        // Comments

                        if ( !columnsComplete ) {

                            if ( inColumnSection ) {
                                // In column section

                                // Check if out of column section
                                if ( split[0].trim().equals( "#" ) ) {
                                    inColumnSection = false;
                                    columnsComplete = true;
                                } else {
                                    // This should contain a column
                                    try {
                                        String columnName = split[1].trim();
                                        titles.add( columnName );
                                    } catch ( IndexOutOfBoundsException | NullPointerException e ) {
                                        titles.add( "Unknown" );
                                        log.warn( "Malformed Output Syntax: " + Arrays.toString( split ) );
                                    }

                                }
                            } else if ( split[0].contains( "Column" ) ) {
                                inColumnSection = true;
                            }

                        }
                    } else if ( split[0].startsWith( ">" ) ) {
                        // Label?
                    } else {

                        // Just to be sure
                        inColumnSection = false;
                        columnsComplete = true;

                        try {
                            int pos = Integer.valueOf( split[0] );

                            labels.add( split[1] );

                            double[] vals = new double[titles.size() - 2];

                            for ( int j = 0; j < vals.length; j++ ) {
                                vals[j] = Double.valueOf( split[j + 2] );
                            }

                            values.add( vals );

                        } catch ( IndexOutOfBoundsException | NumberFormatException e ) {
                            log.error( e );
                        }
                    }

                }

                if ( values.size() > 0 ) {
                    ready = true;
                } else {
                    ready = false;
                }

            } else {
                log.info( "Job contains no data" );
                ready = false;
            }
        } else {
            this.name = "";
            this.ready = false;
        }

    }

    public String getName() {
        return name;
    }

    public boolean isReady() {
        return ready;
    }

    public List<double[]> getValues() {
        return values;
    }

    public List<String> getLabels() {
        return labels;
    }

    public List<String> getTitles() {
        return titles;
    }

}
