/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.api.store;

import org.neo4j.cursor.Cursor;
import org.neo4j.kernel.impl.locking.Lock;
import org.neo4j.kernel.impl.locking.LockService;
import org.neo4j.kernel.impl.store.NeoStores;
import org.neo4j.storageengine.api.BatchingLongProgression;
import org.neo4j.storageengine.api.CursorPools;
import org.neo4j.storageengine.api.Direction;
import org.neo4j.storageengine.api.NodeItem;
import org.neo4j.storageengine.api.PropertyItem;
import org.neo4j.storageengine.api.RelationshipGroupItem;
import org.neo4j.storageengine.api.RelationshipItem;
import org.neo4j.storageengine.api.txstate.NodeTransactionStateView;
import org.neo4j.storageengine.api.txstate.PropertyContainerState;
import org.neo4j.storageengine.api.txstate.ReadableTransactionState;

public class GlobalCursorPools implements CursorPools
{
    private final CursorPool<NodeCursor> nodeCursor;
    private final CursorPool<StoreSingleRelationshipCursor> singleRelationshipCursor;
    private final CursorPool<StoreIteratorRelationshipCursor> iteratorRelationshipCursor;
    private final CursorPool<StoreNodeRelationshipCursor> nodeRelationshipsCursor;
    private final CursorPool<StorePropertyCursor> propertyCursor;
    private final CursorPool<StoreSinglePropertyCursor> singlePropertyCursor;
    private final CursorPool<RelationshipGroupCursor> relationshipGroupCursorCache;
    private final CursorPool<DenseNodeDegreeCounter> degreeCounter;
    private final NeoStores neoStores;

    public GlobalCursorPools( NeoStores neoStores, LockService lockService )
    {
        this.neoStores = neoStores;
        this.nodeCursor =
                new CursorPool<>( 10, cache -> new NodeCursor( neoStores.getNodeStore(), cache, lockService ) );
        this.singleRelationshipCursor = new CursorPool<>( 10,
                cache -> new StoreSingleRelationshipCursor( neoStores.getRelationshipStore(), cache, lockService ) );
        this.iteratorRelationshipCursor = new CursorPool<>( 10,
                cache -> new StoreIteratorRelationshipCursor( neoStores.getRelationshipStore(), cache, lockService ) );
        this.nodeRelationshipsCursor = new CursorPool<>( 10,
                cache -> new StoreNodeRelationshipCursor( neoStores.getRelationshipStore(),
                        neoStores.getRelationshipGroupStore(), cache, lockService ) );
        this.propertyCursor =
                new CursorPool<>( 10, cache -> new StorePropertyCursor( neoStores.getPropertyStore(), cache ) );
        this.singlePropertyCursor =
                new CursorPool<>( 10, cache -> new StoreSinglePropertyCursor( neoStores.getPropertyStore(), cache ) );
        this.degreeCounter = new CursorPool<>( 10,
                cache -> new DenseNodeDegreeCounter( neoStores.getRelationshipStore(),
                        neoStores.getRelationshipGroupStore(), cache ) );
        this.relationshipGroupCursorCache = new CursorPool<>( 10,
                cache -> new RelationshipGroupCursor( neoStores.getRelationshipGroupStore(), cache ) );
    }

    @Override
    public Cursor<NodeItem> acquireNodeCursor( BatchingLongProgression progression, NodeTransactionStateView stateView )
    {
        neoStores.assertOpen();
        return nodeCursor.get().init( progression, stateView );
    }

    @Override
    public Cursor<RelationshipItem> acquireSingleRelationshipCursor( long relId, ReadableTransactionState state )
    {
        neoStores.assertOpen();
        return singleRelationshipCursor.get().init( relId, state );
    }

    @Override
    public Cursor<RelationshipItem> acquireNodeRelationshipCursor( boolean isDense, long nodeId, long relationshipId,
            Direction direction, int[] relTypes, ReadableTransactionState state )
    {
        neoStores.assertOpen();
        return relTypes == null
               ? nodeRelationshipsCursor.get().init( isDense, relationshipId, nodeId, direction, state )
               : nodeRelationshipsCursor.get().init( isDense, relationshipId, nodeId, direction, relTypes, state );
    }

    @Override
    public Cursor<RelationshipItem> relationshipsGetAllCursor( ReadableTransactionState state )
    {
        neoStores.assertOpen();
        return iteratorRelationshipCursor.get().init( new AllIdIterator( neoStores.getRelationshipStore() ), state );
    }

    @Override
    public Cursor<PropertyItem> acquirePropertyCursor( long propertyId, Lock lock, PropertyContainerState state )
    {
        neoStores.assertOpen();
        return propertyCursor.get().init( propertyId, lock, state );
    }

    @Override
    public Cursor<PropertyItem> acquireSinglePropertyCursor( long propertyId, int propertyKeyId, Lock lock,
            PropertyContainerState state )
    {
        neoStores.assertOpen();
        return singlePropertyCursor.get().init( propertyKeyId, propertyId, lock, state );
    }

    @Override
    public Cursor<RelationshipGroupItem> acquireRelationshipGroupCursor( long relationshipGroupId )
    {
        neoStores.assertOpen();
        return relationshipGroupCursorCache.get().init( relationshipGroupId );
    }

    @Override
    public NodeDegreeCounter acquireNodeDegreeCounter( long nodeId, long relationshipGroupId )
    {
        neoStores.assertOpen();
        return degreeCounter.get().init( nodeId, relationshipGroupId );
    }

    @Override
    public void dispose()
    {
        nodeCursor.close();
        singleRelationshipCursor.close();
        iteratorRelationshipCursor.close();
        nodeRelationshipsCursor.close();
        propertyCursor.close();
        singlePropertyCursor.close();
        relationshipGroupCursorCache.close();
        degreeCounter.close();
    }
}
