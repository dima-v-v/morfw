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

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.log4j.Logger;
import org.mindrot.jbcrypt.BCrypt;

/**
 * TODO Document Me
 * 
 * @author mjacobson
 * @version $Id$
 */
@Named
@ApplicationScoped
public class Security {

    private static final Logger log = Logger.getLogger( Security.class );
    private String passwordHash;

    @Inject
    private SettingsCache settingsCache;

    public Security() {
        log.info( "Security created" );
    }

    @PostConstruct
    public void init() {
        log.info( "Security init" );
        loadPassword();
    }

    public boolean checkPassword( String password ) {
        return BCrypt.checkpw( password, passwordHash );
    }

    private void loadPassword() {
        passwordHash = BCrypt.hashpw( settingsCache.getProperty( "morf.password" ), BCrypt.gensalt() );
    }

}
