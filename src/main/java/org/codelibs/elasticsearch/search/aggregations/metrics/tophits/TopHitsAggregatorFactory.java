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

package org.codelibs.elasticsearch.search.aggregations.metrics.tophits;

import org.codelibs.elasticsearch.search.aggregations.Aggregator;
import org.codelibs.elasticsearch.search.aggregations.AggregatorFactories;
import org.codelibs.elasticsearch.search.aggregations.AggregatorFactory;
import org.codelibs.elasticsearch.search.aggregations.InternalAggregation.Type;
import org.codelibs.elasticsearch.search.aggregations.pipeline.PipelineAggregator;
import org.codelibs.elasticsearch.search.fetch.StoredFieldsContext;
import org.codelibs.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.codelibs.elasticsearch.search.fetch.subphase.ScriptFieldsContext;
import org.codelibs.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.codelibs.elasticsearch.search.internal.SearchContext;
import org.codelibs.elasticsearch.search.sort.SortAndFormats;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TopHitsAggregatorFactory extends AggregatorFactory<TopHitsAggregatorFactory> {

    public TopHitsAggregatorFactory(String name, Type type, int from, int size, boolean explain, boolean version, boolean trackScores,
            Optional<SortAndFormats> sort, HighlightBuilder highlightBuilder, StoredFieldsContext storedFieldsContext,
            List<String> docValueFields, List<ScriptFieldsContext.ScriptField> scriptFields, FetchSourceContext fetchSourceContext,
            SearchContext context, AggregatorFactory<?> parent, AggregatorFactories.Builder subFactories, Map<String, Object> metaData)
            throws IOException {
        super(name, type, context, parent, subFactories, metaData);
    }

    @Override
    public Aggregator createInternal(Aggregator parent, boolean collectsFromSingleBucket, List<PipelineAggregator> pipelineAggregators,
            Map<String, Object> metaData) throws IOException {
        throw new UnsupportedOperationException("querybuilders does not support this operation.");
    }

}
