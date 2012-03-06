/**
 * Copyright (c) 2002-2012 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.ha2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.logging.Logger;
import org.neo4j.kernel.ha2.protocol.tokenring.TokenRing;
import org.neo4j.kernel.ha2.statemachine.StateTransitionListener;
import org.neo4j.kernel.ha2.statemachine.message.Message;

/**
 * TODO
 */
public class NetworkMock
{
    Map<String, TestServer> participants = new HashMap<String, TestServer>();
    
    public TestServer addServer( String serverId, StateTransitionListener verifier )
    {
        TestServer server = new TestServer( serverId );

        debug( serverId, "joins ring" );

        participants.put( serverId, server );

        return server;
    }

    private void debug( String participant, String string )
    {
        Logger.getLogger("").info( "===" + participant + " " + string );
    }

    public void removeServer( String serverId )
    {
        debug( serverId, "leaves ring" );
        TestServer server = participants.get(serverId);
        server.newClient( TokenRing.class ).leaveRing();
        tickUntilDone();
        server.stop();

        participants.remove( serverId );
    }
    
    public int tick()
    {
        // Get all messages from all test servers
        List<TestServer.TestMessage> messages = new ArrayList<TestServer.TestMessage>(  );
        for( TestServer testServer : participants.values() )
        {
            testServer.sendMessages( messages );
        }
        
        // Now send them
        int nrOfReceivedMessages = 0;
        for( TestServer.TestMessage message : messages )
        {
            if (message.getTo().equals( "*" ))
            {
                for( Map.Entry<String, TestServer> testServer : participants.entrySet() )
                {
                    if (!testServer.getKey().equals( ((Message)message.getMessage()).getHeader( Message.FROM ) ))
                    {
                        Logger.getLogger("").info( "Broadcast to "+testServer.getKey()+": "+message.getMessage());
                        testServer.getValue().receive(message.getMessage());
                        nrOfReceivedMessages++;
                    }
                }
            } else
            {
                TestServer server = participants.get( message.getTo() );
                Logger.getLogger("").info( "Send to "+message.getTo()+": "+message.getMessage());
                server.receive( message.getMessage() );
                nrOfReceivedMessages++;
            }
        }
        return nrOfReceivedMessages;
    }
    
    public void tickUntilDone()
    {
        while (tick()>0){}
    }
}