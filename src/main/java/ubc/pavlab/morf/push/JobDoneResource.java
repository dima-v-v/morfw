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

package ubc.pavlab.morf.push;

import org.primefaces.push.EventBus;
import org.primefaces.push.RemoteEndpoint;
import org.primefaces.push.annotation.OnClose;
import org.primefaces.push.annotation.OnMessage;
import org.primefaces.push.annotation.OnOpen;
import org.primefaces.push.annotation.PushEndpoint;
import org.primefaces.push.impl.JSONEncoder;

/**
 * TODO Document Me
 * 
 * @author mjacobson
 * @version $Id$
 */
@PushEndpoint("/jobDone")
public class JobDoneResource {
    @OnMessage(encoders = { JSONEncoder.class })
    public String onMessage( String msg ) {
        return msg;
    }

    @OnOpen
    public void onOpen( RemoteEndpoint r, EventBus eventBus ) {
    }

    @OnClose
    public void onClose( RemoteEndpoint r, EventBus eventBus ) {
    }
}
