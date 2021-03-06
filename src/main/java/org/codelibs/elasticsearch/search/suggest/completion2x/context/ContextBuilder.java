/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.codelibs.elasticsearch.search.suggest.completion2x.context;

import org.codelibs.elasticsearch.ElasticsearchParseException;
import org.codelibs.elasticsearch.Version;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

public abstract class ContextBuilder<E extends ContextMapping> {

    protected String name;

    public ContextBuilder(String name) {
        this.name = name;
    }

    public abstract E build();

    /**
     * Create a new {GeolocationContextMapping}
     */
    public static GeolocationContextMapping.Builder location(String name) {
        return new GeolocationContextMapping.Builder(name);
    }

    /**
     * Create a new {GeolocationContextMapping} with given precision and
     * neighborhood usage
     *
     * @param precision geohash length
     * @param neighbors use neighbor cells
     */
    public static GeolocationContextMapping.Builder location(String name, int precision, boolean neighbors) {
        return new GeolocationContextMapping.Builder(name, neighbors, precision);
    }

    /**
     * Create a new {CategoryContextMapping}
     */
    public static CategoryContextMapping.Builder category(String name) {
        return new CategoryContextMapping.Builder(name, null);
    }

    /**
     * Create a new {CategoryContextMapping} with default category
     *
     * @param defaultCategory category to use, if it is not provided
     */
    public static CategoryContextMapping.Builder category(String name, String defaultCategory) {
        return new CategoryContextMapping.Builder(name, null).addDefaultValue(defaultCategory);
    }

    /**
     * Create a new {CategoryContextMapping}
     *
     * @param fieldname
     *            name of the field to use
     */
    public static CategoryContextMapping.Builder reference(String name, String fieldname) {
        return new CategoryContextMapping.Builder(name, fieldname);
    }

    /**
     * Create a new {CategoryContextMapping}
     *
     * @param fieldname name of the field to use
     * @param defaultValues values to use, if the document not provides
     *        a field with the given name
     */
    public static CategoryContextMapping.Builder reference(String name, String fieldname, Iterable<String> defaultValues) {
        return new CategoryContextMapping.Builder(name, fieldname).addDefaultValues(defaultValues);
    }

    public static SortedMap<String, ContextMapping> loadMappings(Object configuration, Version indexVersionCreated)
            throws ElasticsearchParseException {
        if (configuration instanceof Map) {
            Map<String, Object> configurations = (Map<String, Object>)configuration;
            SortedMap<String, ContextMapping> mappings = new TreeMap<>();
            for (Entry<String,Object> config : configurations.entrySet()) {
                String name = config.getKey();
                mappings.put(name, loadMapping(name, (Map<String, Object>) config.getValue(), indexVersionCreated));
            }
            return mappings;
        } else if (configuration == null) {
            return ContextMapping.EMPTY_MAPPING;
        } else {
            throw new ElasticsearchParseException("no valid context configuration");
        }
    }

    protected static ContextMapping loadMapping(String name, Map<String, Object> config, Version indexVersionCreated)
            throws ElasticsearchParseException {
        throw new UnsupportedOperationException("querybuilders does not support this operation.");
    }
}
