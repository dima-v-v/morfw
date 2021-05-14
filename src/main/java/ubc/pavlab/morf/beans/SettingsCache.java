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

import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map.Entry;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.omnifaces.cdi.Eager;

import ubc.pavlab.morf.models.Job;
import ubc.pavlab.morf.utility.PropertiesFile;

/**
 * TODO Document Me
 * 
 * @author mjacobson
 * @version $Id$
 */
@Named
@Eager
@ApplicationScoped
public class SettingsCache implements Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = 3280770517562168465L;

    private static final Logger log = Logger.getLogger( SettingsCache.class );

    private static final String PROPERTIES_PATH = "/opt/morf/";
    private static final String PROPERTIES_BACKUP_PATH = System.getProperty( "user.dir" );
    private static final String PROPERTIES_FILE = "morf.properties";

    private PropertiesFile prop = new PropertiesFile();

    public SettingsCache() {
        log.info( "SettingsCache created" );
    }

    @PostConstruct
    public void init() {
        log.info( "SettingsCache init" );
        prop.load( PROPERTIES_FILE, PROPERTIES_PATH, PROPERTIES_BACKUP_PATH );
        for ( Entry<Object, Object> e : prop.entrySet() ) {
            log.info( e.getKey().toString() + " : " + e.getValue().toString() );
        }

        String scriptPath = prop.getProperty( "morf.script" );
        Path p = Paths.get( scriptPath );

        Job.setPathsA( p.getFileName().toString(), p.getParent().toString(), prop.getProperty( "morf.input" ),
                prop.getProperty( "morf.output" ) );

        scriptPath = prop.getProperty( "morf.scriptB" );
        p = Paths.get( scriptPath );

        Job.setPathsB( p.getFileName().toString(), p.getParent().toString(), prop.getProperty( "morf.inputB" ),
                prop.getProperty( "morf.outputB" ) );

    }

    public String getProperty( String key ) {
        return prop.getProperty( key );
    }

    public String getBaseUrl() {
        String base = prop.getProperty( "morf.baseURL" );
        if ( StringUtils.isBlank( base ) ) {
            return "http://morfw.msl.ubc.ca/";
        } else {
            return base;
        }
    }

    public boolean getShowTraining() {
        return prop.getProperty( "morf.showTraining" ).toLowerCase().equals( "true" );
    }

    public long getJobPurgeTime() {
        String timeInHours = prop.getProperty( "morf.jobPurgeTime" );
        long defaultTime = 24;

        if ( StringUtils.isBlank( timeInHours ) ) {
            return defaultTime;
        } else {
            try {
                return Long.valueOf( timeInHours );
            } catch ( NumberFormatException e ) {
                return defaultTime;
            }
        }

    }

    public boolean contains( String key ) {
        return prop.contains( key );
    }

    public void reload() {
        init();
    }
}
