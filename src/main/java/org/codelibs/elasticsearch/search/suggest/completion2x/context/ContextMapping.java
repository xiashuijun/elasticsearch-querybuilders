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

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.util.automaton.Automata;
import org.apache.lucene.util.automaton.Automaton;
import org.apache.lucene.util.automaton.Operations;
import org.apache.lucene.util.fst.FST;
import org.codelibs.elasticsearch.ElasticsearchParseException;
import org.codelibs.elasticsearch.common.xcontent.ToXContent;
import org.codelibs.elasticsearch.common.xcontent.XContentBuilder;
import org.codelibs.elasticsearch.common.xcontent.XContentParser;
import org.codelibs.elasticsearch.common.xcontent.XContentParser.Token;
import org.codelibs.elasticsearch.common.xcontent.json.JsonXContent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;

/**
 * A {ContextMapping} is used t define a context that may used
 * in conjunction with a suggester. To define a suggester that depends on a
 * specific context derived class of {ContextMapping} will be
 * used to specify the kind of additional information required in order to make
 * suggestions.
 */
public abstract class ContextMapping implements ToXContent {

    /** Character used to separate several contexts */
    public static final char SEPARATOR = '\u001D';

    /** Dummy Context Mapping that should be used if no context is used*/
    public static final SortedMap<String, ContextMapping> EMPTY_MAPPING = Collections.emptySortedMap();

    /** Dummy Context Config matching the Dummy Mapping by providing an empty context*/
    public static final SortedMap<String, ContextConfig> EMPTY_CONFIG = Collections.emptySortedMap();

    /** Dummy Context matching the Dummy Mapping by not wrapping a {TokenStream} */
    public static final Context EMPTY_CONTEXT = new Context(EMPTY_CONFIG);

    public static final String FIELD_VALUE = "value";
    public static final String FIELD_MISSING = "default";
    public static final String FIELD_TYPE = "type";

    protected final String type; // Type of the Contextmapping
    protected final String name;

    /**
     * Define a new context mapping of a specific type
     *
     * @param type
     *            name of the new context mapping
     */
    protected ContextMapping(String type, String name) {
        super();
        this.type = type;
        this.name = name;
    }

    /**
     * @return the type name of the context
     */
    protected String type() {
        return type;
    }

    /**
     * @return the name/id of the context
     */
    public String name() {
        return name;
    }

    @Override
    public final XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(name);
        builder.field(FIELD_TYPE, type);
        toInnerXContent(builder, params);
        builder.endObject();
        return builder;
    }

    public abstract ContextConfig defaultConfig();

    /**
     * Parse a query according to the context. Parsing starts at parsers <b>current</b> position
     *
     * @param name name of the context
     * @param parser {XContentParser} providing the data of the query
     *
     * @return {ContextQuery} according to this mapping
     *
     */
    public abstract ContextQuery parseQuery(String name, XContentParser parser) throws IOException, ElasticsearchParseException;

    /**
     * Since every context mapping is assumed to have a name given by the field name of an context object, this
     * method is used to build the value used to serialize the mapping
     *
     * @param builder builder to append the mapping to
     * @param params parameters passed to the builder
     *
     * @return the builder used
     *
     */
    protected abstract XContentBuilder toInnerXContent(XContentBuilder builder, Params params) throws IOException;

    /**
     * Test equality of two mapping
     *
     * @param thisMappings first mapping
     * @param otherMappings second mapping
     *
     * @return true if both arguments are equal
     */
    public static boolean mappingsAreEqual(SortedMap<String, ? extends ContextMapping> thisMappings,
                                           SortedMap<String, ? extends ContextMapping> otherMappings) {
        return Objects.equals(thisMappings, otherMappings);
    }

    @Override
    public String toString() {
        try {
            return toXContent(JsonXContent.contentBuilder(), ToXContent.EMPTY_PARAMS).string();
        } catch (IOException e) {
            return super.toString();
        }
    }

    /**
     * A collection of {ContextMapping}s, their {ContextConfig}uration and a
     * Document form a complete {Context}. Since this Object provides all information used
     * to setup a suggestion, it can be used to wrap the entire {TokenStream} used to build a
     * path within the {FST}.
     */
    public static class Context {

        final SortedMap<String, ContextConfig> contexts;

        public Context(SortedMap<String, ContextConfig> contexts) {
            super();
            this.contexts = contexts;
        }

        /**
         * Wrap the {TokenStream} according to the provided informations of {ContextConfig}
         *
         * @param tokenStream {TokenStream} to wrap
         *
         * @return wrapped token stream
         */
        public TokenStream wrapTokenStream(TokenStream tokenStream) {
            throw new UnsupportedOperationException("querybuilders does not support this operation.");
        }
    }

    /**
     *  A {ContextMapping} combined with the information provided by a document
     *  form a {ContextConfig} which is used to build the underlying {FST}. This class hold
     *  a simple method wrapping a {TokenStream} by provided document informations.
     */
    public abstract static class ContextConfig {
    }

    /**
     * A {ContextQuery} defines the context information for a specific {ContextMapping}
     * defined within a suggestion request. According to the parameters set in the request and the
     * {ContextMapping} such a query is used to wrap the {TokenStream} of the actual
     * suggestion request into a {TokenStream} with the context settings
     */
    public abstract static class ContextQuery implements ToXContent {

        protected final String name;

        protected ContextQuery(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        /**
         * Create a automaton for a given context query this automaton will be used
         * to find the matching paths with the fst
         *
         * @param preserveSep set an additional char (<code>XAnalyzingSuggester.SEP_LABEL</code>) between each context query
         * @param queries list of {ContextQuery} defining the lookup context
         *
         * @return Automaton matching the given Query
         */
        public static Automaton toAutomaton(boolean preserveSep, Iterable<ContextQuery> queries) {
            Automaton a = Automata.makeEmptyString();

            Automaton gap = Automata.makeChar(ContextMapping.SEPARATOR);
            if (preserveSep) {
                // if separators are preserved the fst contains a SEP_LABEL
                // behind each gap. To have a matching automaton, we need to
                // include the SEP_LABEL in the query as well
//                gap = Operations.concatenate(gap, Automata.makeChar(XAnalyzingSuggester.SEP_LABEL));
            }

            for (ContextQuery query : queries) {
                a = Operations.concatenate(Arrays.asList(query.toAutomaton(), gap, a));
            }

            // TODO: should we limit this?  Do any of our ContextQuery impls really create exponential regexps?
            // GeoQuery looks safe (union of strings).
            return Operations.determinize(a, Integer.MAX_VALUE);
        }

        /**
         * Build a LookUp Automaton for this context.
         * @return LookUp Automaton
         */
        protected abstract Automaton toAutomaton();

        /**
         * Parse a set of {ContextQuery} according to a given mapping
         * @param mappings List of mapping defined y the suggest field
         * @param parser parser holding the settings of the queries. The parsers
         *        current token is assumed hold an array. The number of elements
         *        in this array must match the number of elements in the mappings.
         * @return List of context queries
         *
         * @throws IOException if something unexpected happened on the underlying stream
         * @throws ElasticsearchParseException if the list of queries could not be parsed
         */
        public static List<ContextQuery> parseQueries(Map<String, ContextMapping> mappings, XContentParser parser)
                throws IOException, ElasticsearchParseException {

            Map<String, ContextQuery> querySet = new HashMap<>();
            Token token = parser.currentToken();
            if(token == Token.START_OBJECT) {
                while ((token = parser.nextToken()) != Token.END_OBJECT) {
                    String name = parser.currentName();
                    ContextMapping mapping = mappings.get(name);
                    if (mapping == null) {
                        throw new ElasticsearchParseException("no mapping defined for [{}]", name);
                    }
                    parser.nextToken();
                    querySet.put(name, mapping.parseQuery(name, parser));
                }
            }

            List<ContextQuery> queries = new ArrayList<>(mappings.size());
            for (ContextMapping mapping : mappings.values()) {
                queries.add(querySet.get(mapping.name));
            }
            return queries;
        }

        @Override
        public String toString() {
            try {
                return toXContent(JsonXContent.contentBuilder(), ToXContent.EMPTY_PARAMS).string();
            } catch (IOException e) {
                return super.toString();
            }
        }
    }
}
