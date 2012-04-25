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
package slavetest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.neo4j.helpers.collection.MapUtil;
import org.neo4j.kernel.AbstractGraphDatabase;
import org.neo4j.kernel.HAGraphDb;
import org.neo4j.kernel.HaConfig;
import org.neo4j.kernel.ha.zookeeper.NeoStoreUtil;
import org.neo4j.kernel.ha.zookeeper.ZooKeeperClusterClient;
import org.neo4j.kernel.impl.nioneo.store.StoreId;
import org.neo4j.test.TargetDirectory;
import org.neo4j.test.ha.LocalhostZooKeeperCluster;

public class TestClusterNames
{
    private LocalhostZooKeeperCluster zoo;

    @Before
    public void up() throws Exception
    {
        zoo = LocalhostZooKeeperCluster.singleton().clearDataAndVerifyConnection();
    }

    @Test
    public void makeSureStoreIdInStoreMatchesZKData() throws Exception
    {
        AbstractGraphDatabase db0 = db( 0, HaConfig.CONFIG_DEFAULT_HA_CLUSTER_NAME, HaConfig.CONFIG_DEFAULT_PORT );
        AbstractGraphDatabase db1 = db( 1, HaConfig.CONFIG_DEFAULT_HA_CLUSTER_NAME, HaConfig.CONFIG_DEFAULT_PORT );
        awaitStarted( db0 );
        awaitStarted( db1 );
        db1.shutdown();
        db0.shutdown();

        ZooKeeperClusterClient cm = new ZooKeeperClusterClient( zoo.getConnectionString() );
        cm.waitForSyncConnected();
        StoreId zkStoreId = StoreId.deserialize( cm.getZooKeeper( false ).getData( "/" + HaConfig.CONFIG_DEFAULT_HA_CLUSTER_NAME, false, null ) );
        StoreId storeId = new NeoStoreUtil( db0.getStoreDir() ).asStoreId();
        assertEquals( storeId, zkStoreId );
    }

    @Test
    public void makeSureMultipleHaClustersCanLiveInTheSameZKCluster() throws Exception
    {
        HAGraphDb db0Cluster1 = null,
                                     db1Cluster1 = null,
                                     db0Cluster2 = null,
                                     db1Cluster2 = null;

        try
        {
            // Here's one cluster
            String cluster1Name = "cluster_1";
            db0Cluster1 = db( 0, cluster1Name, HaConfig.CONFIG_DEFAULT_PORT );
            db1Cluster1 = db( 1, cluster1Name, HaConfig.CONFIG_DEFAULT_PORT );
            awaitStarted( db0Cluster1 );
            awaitStarted( db1Cluster1 );

            // Here's another cluster
            String cluster2Name = "cluster.2";
            db0Cluster2 = db( 0, cluster2Name, HaConfig.CONFIG_DEFAULT_PORT+1 );
            db1Cluster2 = db( 1, cluster2Name, HaConfig.CONFIG_DEFAULT_PORT+1 );
            awaitStarted( db0Cluster2 );
            awaitStarted( db1Cluster2 );

            // Set property in one cluster, make sure it only affects that cluster
            String cluster1PropertyName = "c1";
            setRefNodeName( db1Cluster1, cluster1PropertyName );
            pullUpdates( db0Cluster1, db1Cluster1, db0Cluster2, db1Cluster2 );
            assertEquals( cluster1PropertyName, db0Cluster1.getReferenceNode().getProperty( "name" ) );
            assertEquals( cluster1PropertyName, db1Cluster1.getReferenceNode().getProperty( "name" ) );
            assertNull( db0Cluster2.getReferenceNode().getProperty( "name", null ) );
            assertNull( db1Cluster2.getReferenceNode().getProperty( "name", null ) );

            // Set property in the other cluster, make sure it only affects that cluster
            String cluster2PropertyName = "c2";
            setRefNodeName( db1Cluster2, cluster2PropertyName );
            pullUpdates( db0Cluster1, db1Cluster1, db0Cluster2, db1Cluster2 );
            assertEquals( cluster1PropertyName, db0Cluster1.getReferenceNode().getProperty( "name" ) );
            assertEquals( cluster1PropertyName, db1Cluster1.getReferenceNode().getProperty( "name" ) );
            assertEquals( cluster2PropertyName, db0Cluster2.getReferenceNode().getProperty( "name" ) );
            assertEquals( cluster2PropertyName, db1Cluster2.getReferenceNode().getProperty( "name" ) );

            // Restart an instance and make sure it rejoins the correct cluster again
            db0Cluster1.shutdown();

            pullUpdates( db1Cluster1 );
            setRefNodeName( db1Cluster1, cluster1PropertyName );
            assertTrue( db1Cluster1.isMaster() );
            db0Cluster1 = db( 0, cluster1Name, HaConfig.CONFIG_DEFAULT_PORT );
            pullUpdates( db0Cluster1, db1Cluster1 );
            db1Cluster2.shutdown();
            pullUpdates( db0Cluster2 );
            db1Cluster2 = db( 1, cluster2Name, HaConfig.CONFIG_DEFAULT_PORT+3 );
            pullUpdates( db0Cluster2, db1Cluster2 );

            // Change property in the first cluster, make sure it only affects that cluster
            cluster1PropertyName = "new c1";
            setRefNodeName( db1Cluster1, cluster1PropertyName );
            pullUpdates( db0Cluster1, db1Cluster1, db0Cluster2, db1Cluster2 );
            assertEquals( cluster1PropertyName, db0Cluster1.getReferenceNode().getProperty( "name" ) );
            assertEquals( cluster1PropertyName, db1Cluster1.getReferenceNode().getProperty( "name" ) );
            assertEquals( cluster2PropertyName, db0Cluster2.getReferenceNode().getProperty( "name" ) );
            assertEquals( cluster2PropertyName, db1Cluster2.getReferenceNode().getProperty( "name" ) );

            // Set property in the other cluster, make sure it only affects that cluster
            cluster2PropertyName = "new new c2";
            setRefNodeName( db1Cluster2, cluster2PropertyName );
            pullUpdates( db0Cluster1, db1Cluster1, db0Cluster2, db1Cluster2 );
            assertEquals( cluster1PropertyName, db0Cluster1.getReferenceNode().getProperty( "name" ) );
            assertEquals( cluster1PropertyName, db1Cluster1.getReferenceNode().getProperty( "name" ) );
            assertEquals( cluster2PropertyName, db0Cluster2.getReferenceNode().getProperty( "name" ) );
            assertEquals( cluster2PropertyName, db1Cluster2.getReferenceNode().getProperty( "name" ) );

            db0Cluster1.shutdown();
            db0Cluster1 = null;
            db1Cluster1.shutdown();
            db1Cluster1 = null;

            db0Cluster2.shutdown();
            db0Cluster2 = null;
            db1Cluster2.shutdown();
            db1Cluster2 = null;

        }
        finally
        {
            // Make sure we don't leak databases, even if we failed.
            safelyShutdownDbs( db0Cluster1, db1Cluster1, db1Cluster1, db1Cluster2 );
        }
    }

    private void safelyShutdownDbs( HAGraphDb... dbs )
    {
        for ( HAGraphDb db : dbs )
        {
            try
            {
                if ( db != null )
                    db.shutdown();
            }
            catch ( Exception e )
            {
                e.printStackTrace();
            }
        }
    }

    private void pullUpdates( HAGraphDb... dbs )
    {
        for ( HAGraphDb db : dbs ) pullUpdatesWithRetry( db );
    }

    private void pullUpdatesWithRetry( HAGraphDb db )
    {
        try
        {
            db.pullUpdates();
        }
        catch ( RuntimeException e )
        {
            db.pullUpdates();
        }
    }

    private void setRefNodeName( AbstractGraphDatabase db, String name )
    {
        Transaction tx = db.beginTx();
        db.getReferenceNode().setProperty( "name", name );
        tx.success();
        tx.finish();
    }

    private HAGraphDb db( int serverId, String clusterName, int serverPort )
    {
        TargetDirectory dir = TargetDirectory.forTest( getClass() );
        return new HAGraphDb( dir.directory( clusterName + "-" + serverId, true ).getAbsolutePath(), MapUtil.stringMap(
                HaConfig.CONFIG_KEY_SERVER_ID, String.valueOf( serverId ),
                HaConfig.CONFIG_KEY_COORDINATORS, zoo.getConnectionString(),
                HaConfig.CONFIG_KEY_CLUSTER_NAME, clusterName,
                HaConfig.CONFIG_KEY_SERVER, "localhost:" + serverPort,
                HaConfig.CONFIG_KEY_READ_TIMEOUT, String.valueOf( 2 ) ) );
    }

    private void awaitStarted( GraphDatabaseService db )
    {
        while ( true )
        {
            try
            {
                db.getReferenceNode();
                break;
            }
            catch ( Exception e )
            {
            }
        }
    }
}
