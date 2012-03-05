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

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.neo4j.kernel.ha2.protocol.RingParticipant;
import org.neo4j.kernel.ha2.statemachine.StateMachine;
import org.neo4j.kernel.ha2.statemachine.StateMachineConversations;
import org.neo4j.kernel.ha2.statemachine.StateMachineProxyFactory;
import org.neo4j.kernel.ha2.statemachine.StateTransition;
import org.neo4j.kernel.ha2.statemachine.StateTransitionListener;
import org.neo4j.kernel.ha2.statemachine.StateTransitionLogger;
import org.neo4j.kernel.ha2.statemachine.message.BroadcastMessage;
import org.neo4j.kernel.ha2.statemachine.message.ExpectationMessage;
import org.neo4j.kernel.ha2.statemachine.message.Message;
import org.neo4j.kernel.ha2.statemachine.message.TargetedMessage;

/**
 * TODO
 */
public class NetworkMock
{
    Map<RingParticipant, Server> participants = new HashMap<RingParticipant, Server>();
    
    public Server addParticipant( RingParticipant participant, StateTransitionListener verifier )
    {
        final TokenRingContext context = new TokenRingContext(participant);
        final StateMachine<TokenRingContext, TokenRingMessage> stateMachine = new StateMachine<TokenRingContext, TokenRingMessage>(context, TokenRingMessage.class, TokenRingState.start);

        stateMachine.addStateTransitionListener( verifier );
        stateMachine.addStateTransitionListener( new StateTransitionLogger( participant, Logger.getAnonymousLogger(  ) ) );
        stateMachine.addStateTransitionListener( new StateTransitionListener()
        {
            public void stateTransition( StateTransition transition )
            {
                try
                {
                    while( !context.getSendQueue().isEmpty() )
                    {
                        Message message = context.getSendQueue().poll();
                        message.copyHeaders(transition.getMessage());
                        process( message );
                    }
                }
                catch( Throwable throwable )
                {
                    throwable.printStackTrace();
                }
            }
        } );
        
        debug( participant, "joins ring" );

        Server server = new Server(participant, stateMachine);
        participants.put(participant, server);
        server.newProxy(TokenRing.class).start();

        return server;
    }

    private void debug( RingParticipant participant, String string )
    {
//        System.out.println( "===" + participant + " " + string );
    }

    public void removeParticipant(RingParticipant participant)
    {
        debug( participant, "leaves ring" );
        Server server = participants.get(participant);
        server.newProxy( TokenRing.class ).leaveRing();

        participants.remove( participant );
    }

    private void process( Message message )
    {
        if (message instanceof BroadcastMessage)
        {
            BroadcastMessage broadcastEvent = (BroadcastMessage) message;
            boolean alone = true;
            for (Map.Entry<RingParticipant, Server> ringParticipantStateMachineEntry : participants.entrySet())
            {
                if (!ringParticipantStateMachineEntry.getKey().equals(broadcastEvent.getFrom()))
                {
                    alone = false;
                    ringParticipantStateMachineEntry.getValue().receive(message);
                }
            }

            if (alone)
            {
                participants.get(broadcastEvent.getFrom()).receive(new ExpectationMessage(message.getMessageType().failureMessage(), "No servers founds"));
            }
            return;
        }

        if (message instanceof TargetedMessage)
        {
            TargetedMessage targetedEvent = (TargetedMessage) message;
            Server targetMachine = participants.get(targetedEvent.getTo());
            if (targetMachine == null)
            {
                System.out.println("Target "+targetedEvent.getTo()+" does not exist");
            } else
            {
                targetMachine.receive(message);
            }
            return;
        }


        System.out.println("Unknown message type:"+message.getClass().getName());
    }
    
    public class Server
    {
        private StateMachine stateMachine;
        private StateMachineConversations conversations;
        private StateMachineProxyFactory proxyFactory;

        public Server( RingParticipant participant, StateMachine stateMachine )
        {
            this.stateMachine = stateMachine;
            conversations = new StateMachineConversations();
            proxyFactory = new StateMachineProxyFactory( participant.toString(), TokenRingMessage.class, stateMachine, conversations );
        }
        
        public <T> T newProxy(Class<T> proxyInterface)
        {
            return proxyFactory.newProxy( proxyInterface );
        }

        public void receive( Message message )
        {
            stateMachine.receive( message );
        }
    }
}
