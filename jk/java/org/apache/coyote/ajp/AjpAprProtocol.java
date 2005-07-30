/*
 *  Copyright 1999-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.coyote.ajp;

import java.net.InetAddress;
import java.net.URLEncoder;
import java.util.Hashtable;
import java.util.Iterator;

import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.commons.modeler.Registry;
import org.apache.coyote.ActionCode;
import org.apache.coyote.ActionHook;
import org.apache.coyote.Adapter;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.RequestGroupInfo;
import org.apache.coyote.RequestInfo;
import org.apache.tomcat.util.net.AprEndpoint;
import org.apache.tomcat.util.net.AprEndpoint.Handler;
import org.apache.tomcat.util.res.StringManager;


/**
 * Abstract the protocol implementation, including threading, etc.
 * Processor is single threaded and specific to stream-based protocols,
 * will not fit Jk protocols like JNI.
 *
 * @author Remy Maucherat
 * @author Costin Manolache
 */
public class AjpAprProtocol implements ProtocolHandler, MBeanRegistration
{
    public AjpAprProtocol() {
        cHandler = new AjpConnectionHandler( this );
        setSoLinger(Constants.DEFAULT_CONNECTION_LINGER);
        setSoTimeout(Constants.DEFAULT_CONNECTION_TIMEOUT);
        //setServerSoTimeout(Constants.DEFAULT_SERVER_SOCKET_TIMEOUT);
        setTcpNoDelay(Constants.DEFAULT_TCP_NO_DELAY);
    }

    /**
     * The string manager for this package.
     */
    protected static StringManager sm =
        StringManager.getManager(Constants.Package);

    /** Pass config info
     */
    public void setAttribute( String name, Object value ) {
        if( log.isTraceEnabled())
            log.trace(sm.getString("ajpprotocol.setattribute", name, value));

        attributes.put(name, value);
    }

    public Object getAttribute( String key ) {
        if( log.isTraceEnabled())
            log.trace(sm.getString("ajpprotocol.getattribute", key));
        return attributes.get(key);
    }

    public Iterator getAttributeNames() {
        return attributes.keySet().iterator();
    }

    /**
     * Set a property.
     */
    public void setProperty(String name, String value) {
        setAttribute(name, value);
    }

    /**
     * Get a property
     */
    public String getProperty(String name) {
        return (String)getAttribute(name);
    }

    /** The adapter, used to call the connector
     */
    public void setAdapter(Adapter adapter) {
        this.adapter=adapter;
    }

    public Adapter getAdapter() {
        return adapter;
    }


    /** Start the protocol
     */
    public void init() throws Exception {
        ep.setName(getName());
        ep.setHandler(cHandler);
        ep.setUseSendfile(false);

        // XXX get domain from registration
        try {
            ep.init();
        } catch (Exception ex) {
            log.error(sm.getString("ajpprotocol.endpoint.initerror"), ex);
            throw ex;
        }
        if(log.isInfoEnabled())
            log.info(sm.getString("ajpprotocol.init", getName()));

    }

    ObjectName tpOname;
    ObjectName rgOname;

    public void start() throws Exception {
        if( this.domain != null ) {
            try {
                // XXX We should be able to configure it separately
                // XXX It should be possible to use a single TP
                tpOname=new ObjectName
                    (domain + ":" + "type=ThreadPool,name=" + getName());
                Registry.getRegistry(null, null)
                .registerComponent(ep, tpOname, null );
            } catch (Exception e) {
                log.error("Can't register threadpool" );
            }
            rgOname=new ObjectName
                (domain + ":type=GlobalRequestProcessor,name=" + getName());
            Registry.getRegistry(null, null).registerComponent
                ( cHandler.global, rgOname, null );
        }

        try {
            ep.start();
        } catch (Exception ex) {
            log.error(sm.getString("ajpprotocol.endpoint.starterror"), ex);
            throw ex;
        }
        if(log.isInfoEnabled())
            log.info(sm.getString("ajpprotocol.start", getName()));
    }

    public void pause() throws Exception {
        try {
            ep.pause();
        } catch (Exception ex) {
            log.error(sm.getString("ajpprotocol.endpoint.pauseerror"), ex);
            throw ex;
        }
        if(log.isInfoEnabled())
            log.info(sm.getString("ajpprotocol.pause", getName()));
    }

    public void resume() throws Exception {
        try {
            ep.resume();
        } catch (Exception ex) {
            log.error(sm.getString("ajpprotocol.endpoint.resumeerror"), ex);
            throw ex;
        }
        if(log.isInfoEnabled())
            log.info(sm.getString("ajpprotocol.resume", getName()));
    }

    public void destroy() throws Exception {
        if(log.isInfoEnabled())
            log.info(sm.getString("ajpprotocol.stop", getName()));
        ep.destroy();
        if( tpOname!=null )
            Registry.getRegistry(null, null).unregisterComponent(tpOname);
        if( rgOname != null )
            Registry.getRegistry(null, null).unregisterComponent(rgOname);
    }

    // -------------------- Properties--------------------
    protected AprEndpoint ep=new AprEndpoint();
    protected boolean secure;

    protected Hashtable attributes = new Hashtable();

    private int timeout = 300000;   // 5 minutes as in Apache HTTPD server
    protected boolean tomcatAuthentication = true;

    private Adapter adapter;
    private AjpConnectionHandler cHandler;

    // -------------------- Pool setup --------------------

    public int getMaxThreads() {
        return ep.getMaxThreads();
    }

    public void setMaxThreads( int maxThreads ) {
        ep.setMaxThreads(maxThreads);
        setAttribute("maxThreads", "" + maxThreads);
    }

    public void setThreadPriority(int threadPriority) {
      ep.setThreadPriority(threadPriority);
      setAttribute("threadPriority", "" + threadPriority);
    }

    public int getThreadPriority() {
      return ep.getThreadPriority();
    }

    // -------------------- Tcp setup --------------------

    public int getBacklog() {
        return ep.getBacklog();
    }

    public void setBacklog( int i ) {
        ep.setBacklog(i);
        setAttribute("backlog", "" + i);
    }

    public int getPort() {
        return ep.getPort();
    }

    public void setPort( int port ) {
        ep.setPort(port);
        setAttribute("port", "" + port);
    }

    public boolean getUseSendfile() {
        return ep.getUseSendfile();
    }

    public void setUseSendfile(boolean useSendfile) {
        // No sendfile for AJP
    }

    public InetAddress getAddress() {
        return ep.getAddress();
    }

    public void setAddress(InetAddress ia) {
        ep.setAddress( ia );
        setAttribute("address", "" + ia);
    }

    public String getName() {
        String encodedAddr = "";
        if (getAddress() != null) {
            encodedAddr = "" + getAddress();
            if (encodedAddr.startsWith("/"))
                encodedAddr = encodedAddr.substring(1);
            encodedAddr = URLEncoder.encode(encodedAddr) + "-";
        }
        return ("ajp-" + encodedAddr + ep.getPort());
    }

    public boolean getTcpNoDelay() {
        return ep.getTcpNoDelay();
    }

    public void setTcpNoDelay( boolean b ) {
        ep.setTcpNoDelay( b );
        setAttribute("tcpNoDelay", "" + b);
    }

    public boolean getTomcatAuthentication() {
        return tomcatAuthentication;
    }

    public void setTomcatAuthentication(boolean tomcatAuthentication) {
        this.tomcatAuthentication = tomcatAuthentication;
    }

    public int getFirstReadTimeout() {
        return ep.getFirstReadTimeout();
    }

    public void setFirstReadTimeout( int i ) {
        ep.setFirstReadTimeout(i);
        setAttribute("firstReadTimeout", "" + i);
    }

    public int getPollTime() {
        return ep.getPollTime();
    }

    public void setPollTime( int i ) {
        ep.setPollTime(i);
        setAttribute("pollTime", "" + i);
    }

    public void setPollerSize(int i) {
        ep.setPollerSize(i); 
        setAttribute("pollerSize", "" + i);
    }
    
    public int getPollerSize() {
        return ep.getPollerSize();
    }
    
    public int getSoLinger() {
        return ep.getSoLinger();
    }

    public void setSoLinger( int i ) {
        ep.setSoLinger( i );
        setAttribute("soLinger", "" + i);
    }

    public int getSoTimeout() {
        return ep.getSoTimeout();
    }

    public void setSoTimeout( int i ) {
        ep.setSoTimeout(i);
        setAttribute("soTimeout", "" + i);
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout( int timeouts ) {
        timeout = timeouts;
        setAttribute("timeout", "" + timeouts);
    }

    // --------------------  Connection handler --------------------

    static class AjpConnectionHandler implements Handler {
        AjpAprProtocol proto;
        static int count=0;
        RequestGroupInfo global=new RequestGroupInfo();
        ThreadLocal localProcessor = new ThreadLocal();

        AjpConnectionHandler(AjpAprProtocol proto) {
            this.proto = proto;
        }

        public boolean process(long socket) {
            AjpAprProcessor processor = null;
            try {
                processor = (AjpAprProcessor) localProcessor.get();
                if (processor == null) {
                    processor = new AjpAprProcessor(proto.ep);
                    processor.setAdapter(proto.adapter);
                    processor.setTomcatAuthentication(proto.tomcatAuthentication);
                    localProcessor.set(processor);
                    if (proto.getDomain() != null) {
                        synchronized (this) {
                            try {
                                RequestInfo rp=processor.getRequest().getRequestProcessor();
                                rp.setGlobalProcessor(global);
                                ObjectName rpName=new ObjectName
                                (proto.getDomain() + ":type=RequestProcessor,worker="
                                        + proto.getName() +",name=AjpRequest" + count++ );
                                Registry.getRegistry(null, null).registerComponent( rp, rpName, null);
                            } catch( Exception ex ) {
                                log.warn("Error registering request");
                            }
                        }
                    }
                }

                if (processor instanceof ActionHook) {
                    ((ActionHook) processor).action(ActionCode.ACTION_START, null);
                }

                return processor.process(socket);

            } catch(java.net.SocketException e) {
                // SocketExceptions are normal
                AjpAprProtocol.log.debug
                    (sm.getString
                     ("ajpprotocol.proto.socketexception.debug"), e);
            } catch (java.io.IOException e) {
                // IOExceptions are normal
                AjpAprProtocol.log.debug
                    (sm.getString
                     ("ajpprotocol.proto.ioexception.debug"), e);
            }
            // Future developers: if you discover any other
            // rare-but-nonfatal exceptions, catch them here, and log as
            // above.
            catch (Throwable e) {
                // any other exception or error is odd. Here we log it
                // with "ERROR" level, so it will show up even on
                // less-than-verbose logs.
                AjpAprProtocol.log.error
                    (sm.getString("ajpprotocol.proto.error"), e);
            } finally {
                if (processor instanceof ActionHook) {
                    ((ActionHook) processor).action(ActionCode.ACTION_STOP, null);
                }
            }
            return false;
        }
    }

    protected static org.apache.commons.logging.Log log
        = org.apache.commons.logging.LogFactory.getLog(AjpAprProtocol.class);

    // -------------------- Various implementation classes --------------------

    protected String domain;
    protected ObjectName oname;
    protected MBeanServer mserver;

    public ObjectName getObjectName() {
        return oname;
    }

    public String getDomain() {
        return domain;
    }

    public ObjectName preRegister(MBeanServer server,
                                  ObjectName name) throws Exception {
        oname=name;
        mserver=server;
        domain=name.getDomain();
        return name;
    }

    public void postRegister(Boolean registrationDone) {
    }

    public void preDeregister() throws Exception {
    }

    public void postDeregister() {
    }
}