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
package org.neo4j.kernel.ha2.protocol.tokenring;

import java.util.Map;

import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.helpers.collection.MapUtil;
import org.neo4j.kernel.ConfigProxy;
import org.neo4j.kernel.LifeSupport;
import org.neo4j.kernel.ha2.Server;
import org.neo4j.kernel.ha2.protocol.RingParticipant;
import org.neo4j.kernel.impl.util.StringLogger;

/**
 * TODO
 */
public class TokenRingNetworkTest
{
    @Before
    public void setupLogging()
    {
        for( Handler handler : Logger.getLogger( "" ).getHandlers() )
        {
            Logger.getLogger( "" ).removeHandler( handler );
        }

        Logger.getLogger( "" ).addHandler( new Handler()
        {
            @Override
            public void publish( LogRecord record )
            {
                StringLogger.SYSTEM.logMessage( record.getMessage(), true );
            }

            @Override
            public void flush()
            {
            }

            @Override
            public void close()
                throws SecurityException
            {
            }
        } );
    }

    @Test
    public void testSendReceive()
    {
        Map<String, String> config = MapUtil.stringMap( "port", "1234-1244" );

        Server.Configuration configuration = ConfigProxy.config( config, Server.Configuration.class );
        LifeSupport life = new LifeSupport();
        Server server1 = new Server( configuration );
        life.add( server1 );
        life.add(new Server( configuration ));
        life.add(new Server( configuration ));
        life.start();

        try
        {
            Thread.sleep( 3000 );
        }
        catch( InterruptedException e )
        {
            e.printStackTrace();
        }

        Logger logger = Logger.getLogger( "" );
        logger.info( "Find ring participants:" );
        Iterable<RingParticipant> participants = server1.newClient( TokenRing.class ).getParticipants();
        for( RingParticipant ringParticipant : participants )
        {
            logger.info(ringParticipant.toString());
        }

        logger.info( "Shutting down" );
        life.shutdown();
    }
}