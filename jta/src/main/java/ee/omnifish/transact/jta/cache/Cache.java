/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package ee.omnifish.transact.jta.cache;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

/**
 * Cache Generic cache interface
 */
public interface Cache {

    /**
     * initialize the cache
     *
     * @param maxEntries maximum number of entries expected in the cache
     * @param loadFactor the load factor
     * @param props opaque list of properties for a given cache implementation
     * @throws Exception a generic Exception if the initialization failed
     */
    void init(int maxEntries, float loadFactor, Properties props) throws Exception;

    /**
     * initialize the cache with the default load factor (0.75)
     *
     * @param maxEntries maximum number of entries expected in the cache
     * @param props opaque list of properties for a given cache implementation
     * @throws Exception a generic Exception if the initialization failed
     */
    void init(int maxEntries, Properties props) throws Exception;

    /**
     * add the cache module listener
     *
     * @param listener <code>CacheListener</code> implementation
     */
    void addCacheListener(CacheListener listener);

    /**
     * get the index of the item given a key
     *
     * @param key of the entry
     * @return the index to be used in the cache
     */
    int getIndex(Object key);

    /**
     * get the item stored at the key.
     *
     * @param key lookup key
     * @return the item stored at the key; null if not found.
     *
     * This function returns first value, for a multi-valued key.
     */
    Object get(Object key);

    /**
     * get all the items with the given key.
     *
     * @param key lookup key
     * @return an Iterator over the items with the given key
     */
    Iterator getAll(Object key);

    /**
     * check if the cache contains the item at the key
     *
     * @param key lookup key
     * @return true if there is an item stored at the key; false if not.
     */
    boolean contains(Object key);

    /**
     * get an Iterator for the keys stored in the cache
     *
     * @return an Iterator
     */
    Iterator keys();

    /**
     * get an Enumeration for the keys stored in the cache
     *
     * @return an Enumeration XXX: should use Iterator which is based on Collections
     */
    Enumeration elements();

    /**
     * get an Iterator for the values stored in the cache
     *
     * @return an Iterator
     */
    Iterator values();

    /**
     * cache the given value at the specified key and return previous value
     *
     * @param key lookup key
     * @param value item value to be stored
     * @return the previous item stored at the key; null if not found.
     *
     * This function replaces first value, for a multi-valued key.
     */
    Object put(Object key, Object value);

    /**
     * cache the given value at the specified key and return previous value
     *
     * @param key lookup key
     * @param value item value to be stored
     * @param size in bytes of the value being cached
     * @return the previous item stored at the key; null if not found.
     *
     * This function replaces first value, for a multi-valued key.
     */
    Object put(Object key, Object value, int size);

    /**
     * add the given value to the cache at the specified key
     *
     * @param key lookup key
     * @param value item value to be stored
     *
     * This function is suitable for multi-valued keys.
     */
    void add(Object key, Object value);

    /**
     * add the given value with specified size to the cache at specified key
     *
     * @param key lookup key
     * @param value item value to be stored
     * @param size in bytes of the value being added
     *
     * This function is suitable for multi-valued keys.
     */
    void add(Object key, Object value, int size);

    /**
     * remove the item with the given key.
     *
     * @param key lookup key
     * @return the item stored at the key; null if not found.
     *
     * This function removes first value, for a multi-valued key.
     */
    Object remove(Object key);

    /**
     * remove the given value stored at the key.
     *
     * @param key lookup key
     * @param value to match (for multi-valued keys)
     * @return the item stored at the key; null if not found.
     */
    Object remove(Object key, Object value);

    /**
     * remove all the item with the given key.
     *
     * @param key lookup key
     */
    void removeAll(Object key);

    /**
     * wait for a refresh on the object associated with the key
     *
     * @param index index of the entry. The index must be obtained via one of the <code>getIndex()</code> methods.
     * @return <code>true</code> on successful notification, or <code>false</code> if there is no thread refreshing this
     * entry.
     */
    boolean waitRefresh(int index);

    /**
     * notify threads waiting for a refresh on the object associated with the key
     *
     * @param index index of the entry. The index must be obtained via one of the <code>getIndex()</code> methods.
     */
    void notifyRefresh(int index);

    /**
     * clear all the entries from the cache.
     *
     * @return the number of entries cleared from the cache
     */
    int clear();

    /**
     * is this cache empty?
     *
     * @return true if the cache is empty; false otherwise.
     */
    boolean isEmpty();

    /**
     * get the number of entries in the cache
     *
     * @return the number of entries the cache currently holds
     */
    int getEntryCount();

    /**
     * get the stats map
     */
    /**
     * get the desired statistic counter
     *
     * @param key to corresponding stat
     * @return an Object corresponding to the stat See also: Constant.java for the key
     */
    Object getStatByName(String key);

    /**
     * get the stats snapshot
     *
     * @return a Map of stats See also: Constant.java for the keys
     */
    Map getStats();

    /**
     * clear all stats
     */
    void clearStats();

    /**
     * trim the expired entries from the cache.
     *
     * @param maxCount maximum number of invalid entries to trim specify Integer.MAX_VALUE to trim all timedout entries
     *
     * This call is to be scheduled by a thread managed by the container.
     */
    void trimExpiredEntries(int maxCount);

    /**
     * Destroys this cache. This method should perform final clean ups.
     */
    void destroy();
}
