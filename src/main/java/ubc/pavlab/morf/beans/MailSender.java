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

import java.util.Properties;

import javax.activation.DataHandler;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.log4j.Logger;

/**
 * TODO Document Me
 * 
 * @author mjacobson
 * @version $Id$
 */
@Named
@ApplicationScoped
public class MailSender {

    private static final Logger log = Logger.getLogger( MailSender.class );

    @Inject
    private SettingsCache settingsCache;

    private Properties props;

    private String host;
    private InternetAddress fromEmail;
    private String port;
    private String username;
    private String password;

    @PostConstruct
    public void init() {
        log.info( "MailSender init" );
        username = settingsCache.getProperty( "morf.mail.username" );
        password = settingsCache.getProperty( "morf.mail.password" );
        host = settingsCache.getProperty( "morf.mail.host" );
        try {
            fromEmail = new InternetAddress( settingsCache.getProperty( "morf.mail.fromEmail" ) );
        } catch ( AddressException e ) {
            log.warn( "fromEmail Property (" + settingsCache.getProperty( "morf.mail.fromEmail" ) + ") not valid." );
        }
        port = settingsCache.getProperty( "morf.mail.port" );

        log.info( "MailSender Configured - host: " + host + ", fromEmail: " + fromEmail + ", port: " + port );

        props = new Properties();
        props.put( "mail.smtp.starttls.enable", "true" );
        // props.put( "mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory" );
        props.put( "mail.smtp.auth", "true" );
        props.put( "mail.smtp.host", host );
        props.put( "mail.smtp.port", port );
    }

    @PreDestroy
    public void destroy() {
        log.info( "MailSender destroyed" );
    }

    public void sendMail( String recipientEmail, String subject, String content ) {
        Session session = Session.getInstance( props, new javax.mail.Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication( username, password );
            }
        } );
        // Session session = Session.getInstance( props, null );
        try {

            Message message = new MimeMessage( session );
            message.setFrom( fromEmail );
            message.setRecipients( Message.RecipientType.TO, InternetAddress.parse( recipientEmail ) );
            message.setSubject( subject );
            message.setContent( content, "text/html" );

            Transport.send( message );

            log.info( "Email Sent To: " + recipientEmail );

        } catch ( MessagingException e ) {
            log.error( e );
        }
    }

    public boolean sendMail( String recipientEmail, String subject, String content, String attachmentName,
            String attachment ) {
        Session session = Session.getInstance( props, new javax.mail.Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication( username, password );
            }
        } );
        // Session session = Session.getInstance( props, null );
        try {

            Message message = new MimeMessage( session );
            message.setFrom( fromEmail );
            message.setRecipients( Message.RecipientType.TO, InternetAddress.parse( recipientEmail ) );
            message.setSubject( subject );

            Multipart mp = new MimeMultipart();

            // HTML Part

            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent( content, "text/html" );
            mp.addBodyPart( htmlPart );

            // Attachment Part

            MimeBodyPart attachmentPart = new MimeBodyPart();
            attachmentPart.setFileName( attachmentName );
            attachmentPart.setDataHandler( new DataHandler( attachment, "text/html" ) );
            // attachmentPart.setContent( attachment, "text/plain" );
            mp.addBodyPart( attachmentPart );

            message.setContent( mp );

            Transport.send( message );

            log.info( "Email Sent To: " + recipientEmail );

            return true;

        } catch ( MessagingException e ) {
            log.error( e );
            return false;
        }
    }

}
