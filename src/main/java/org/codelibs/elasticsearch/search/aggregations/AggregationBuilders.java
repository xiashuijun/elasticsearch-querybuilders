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
package org.codelibs.elasticsearch.search.aggregations;

import org.codelibs.elasticsearch.common.geo.GeoDistance;
import org.codelibs.elasticsearch.common.geo.GeoPoint;
import org.codelibs.elasticsearch.index.query.QueryBuilder;
import org.codelibs.elasticsearch.search.aggregations.bucket.children.Children;
import org.codelibs.elasticsearch.search.aggregations.bucket.children.ChildrenAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.codelibs.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.bucket.filters.Filters;
import org.codelibs.elasticsearch.search.aggregations.bucket.filters.FiltersAggregator.KeyedFilter;
import org.codelibs.elasticsearch.search.aggregations.bucket.filters.FiltersAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.bucket.geogrid.GeoGridAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.bucket.geogrid.GeoHashGrid;
import org.codelibs.elasticsearch.search.aggregations.bucket.global.Global;
import org.codelibs.elasticsearch.search.aggregations.bucket.global.GlobalAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.codelibs.elasticsearch.search.aggregations.bucket.histogram.HistogramAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.bucket.missing.Missing;
import org.codelibs.elasticsearch.search.aggregations.bucket.missing.MissingAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.bucket.nested.Nested;
import org.codelibs.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.bucket.nested.ReverseNested;
import org.codelibs.elasticsearch.search.aggregations.bucket.nested.ReverseNestedAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.bucket.range.Range;
import org.codelibs.elasticsearch.search.aggregations.bucket.range.RangeAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.bucket.range.date.DateRangeAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.bucket.range.geodistance.GeoDistanceAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.bucket.range.ip.IpRangeAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.bucket.sampler.DiversifiedAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.bucket.sampler.Sampler;
import org.codelibs.elasticsearch.search.aggregations.bucket.sampler.SamplerAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.bucket.significant.SignificantTerms;
import org.codelibs.elasticsearch.search.aggregations.bucket.significant.SignificantTermsAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.codelibs.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.metrics.avg.Avg;
import org.codelibs.elasticsearch.search.aggregations.metrics.avg.AvgAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.metrics.cardinality.Cardinality;
import org.codelibs.elasticsearch.search.aggregations.metrics.cardinality.CardinalityAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.metrics.geobounds.GeoBounds;
import org.codelibs.elasticsearch.search.aggregations.metrics.geobounds.GeoBoundsAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.metrics.geocentroid.GeoCentroid;
import org.codelibs.elasticsearch.search.aggregations.metrics.geocentroid.GeoCentroidAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.metrics.max.Max;
import org.codelibs.elasticsearch.search.aggregations.metrics.max.MaxAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.metrics.min.Min;
import org.codelibs.elasticsearch.search.aggregations.metrics.min.MinAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.metrics.percentiles.PercentileRanks;
import org.codelibs.elasticsearch.search.aggregations.metrics.percentiles.PercentileRanksAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.metrics.percentiles.Percentiles;
import org.codelibs.elasticsearch.search.aggregations.metrics.percentiles.PercentilesAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.metrics.scripted.ScriptedMetric;
import org.codelibs.elasticsearch.search.aggregations.metrics.scripted.ScriptedMetricAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.metrics.stats.Stats;
import org.codelibs.elasticsearch.search.aggregations.metrics.stats.StatsAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.metrics.stats.extended.ExtendedStats;
import org.codelibs.elasticsearch.search.aggregations.metrics.stats.extended.ExtendedStatsAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.metrics.sum.Sum;
import org.codelibs.elasticsearch.search.aggregations.metrics.sum.SumAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.metrics.tophits.TopHits;
import org.codelibs.elasticsearch.search.aggregations.metrics.tophits.TopHitsAggregationBuilder;
import org.codelibs.elasticsearch.search.aggregations.metrics.valuecount.ValueCount;
import org.codelibs.elasticsearch.search.aggregations.metrics.valuecount.ValueCountAggregationBuilder;

/**
 * Utility class to create aggregations.
 */
public class AggregationBuilders {

    private AggregationBuilders() {
    }

    /**
     * Create a new {ValueCount} aggregation with the given name.
     */
    public static ValueCountAggregationBuilder count(String name) {
        return new ValueCountAggregationBuilder(name, null);
    }

    /**
     * Create a new {Avg} aggregation with the given name.
     */
    public static AvgAggregationBuilder avg(String name) {
        return new AvgAggregationBuilder(name);
    }

    /**
     * Create a new {Max} aggregation with the given name.
     */
    public static MaxAggregationBuilder max(String name) {
        return new MaxAggregationBuilder(name);
    }

    /**
     * Create a new {Min} aggregation with the given name.
     */
    public static MinAggregationBuilder min(String name) {
        return new MinAggregationBuilder(name);
    }

    /**
     * Create a new {Sum} aggregation with the given name.
     */
    public static SumAggregationBuilder sum(String name) {
        return new SumAggregationBuilder(name);
    }

    /**
     * Create a new {Stats} aggregation with the given name.
     */
    public static StatsAggregationBuilder stats(String name) {
        return new StatsAggregationBuilder(name);
    }

    /**
     * Create a new {ExtendedStats} aggregation with the given name.
     */
    public static ExtendedStatsAggregationBuilder extendedStats(String name) {
        return new ExtendedStatsAggregationBuilder(name);
    }

    /**
     * Create a new {Filter} aggregation with the given name.
     */
    public static FilterAggregationBuilder filter(String name, QueryBuilder filter) {
        return new FilterAggregationBuilder(name, filter);
    }

    /**
     * Create a new {Filters} aggregation with the given name.
     */
    public static FiltersAggregationBuilder filters(String name, KeyedFilter... filters) {
        return new FiltersAggregationBuilder(name, filters);
    }

    /**
     * Create a new {Filters} aggregation with the given name.
     */
    public static FiltersAggregationBuilder filters(String name, QueryBuilder... filters) {
        return new FiltersAggregationBuilder(name, filters);
    }

    /**
     * Create a new {Sampler} aggregation with the given name.
     */
    public static SamplerAggregationBuilder sampler(String name) {
        return new SamplerAggregationBuilder(name);
    }

    /**
     * Create a new {Sampler} aggregation with the given name.
     */
    public static DiversifiedAggregationBuilder diversifiedSampler(String name) {
        return new DiversifiedAggregationBuilder(name);
    }

    /**
     * Create a new {Global} aggregation with the given name.
     */
    public static GlobalAggregationBuilder global(String name) {
        return new GlobalAggregationBuilder(name);
    }

    /**
     * Create a new {Missing} aggregation with the given name.
     */
    public static MissingAggregationBuilder missing(String name) {
        return new MissingAggregationBuilder(name, null);
    }

    /**
     * Create a new {Nested} aggregation with the given name.
     */
    public static NestedAggregationBuilder nested(String name, String path) {
        return new NestedAggregationBuilder(name, path);
    }

    /**
     * Create a new {ReverseNested} aggregation with the given name.
     */
    public static ReverseNestedAggregationBuilder reverseNested(String name) {
        return new ReverseNestedAggregationBuilder(name);
    }

    /**
     * Create a new {Children} aggregation with the given name.
     */
    public static ChildrenAggregationBuilder children(String name, String childType) {
        return new ChildrenAggregationBuilder(name, childType);
    }

    /**
     * Create a new {GeoDistance} aggregation with the given name.
     */
    public static GeoDistanceAggregationBuilder geoDistance(String name, GeoPoint origin) {
        return new GeoDistanceAggregationBuilder(name, origin);
    }

    /**
     * Create a new {Histogram} aggregation with the given name.
     */
    public static HistogramAggregationBuilder histogram(String name) {
        return new HistogramAggregationBuilder(name);
    }

    /**
     * Create a new {GeoHashGrid} aggregation with the given name.
     */
    public static GeoGridAggregationBuilder geohashGrid(String name) {
        return new GeoGridAggregationBuilder(name);
    }

    /**
     * Create a new {SignificantTerms} aggregation with the given name.
     */
    public static SignificantTermsAggregationBuilder significantTerms(String name) {
        return new SignificantTermsAggregationBuilder(name, null);
    }

    /**
     * Create a new {DateHistogramAggregationBuilder} aggregation with the given
     * name.
     */
    public static DateHistogramAggregationBuilder dateHistogram(String name) {
        return new DateHistogramAggregationBuilder(name);
    }

    /**
     * Create a new {Range} aggregation with the given name.
     */
    public static RangeAggregationBuilder range(String name) {
        return new RangeAggregationBuilder(name);
    }

    /**
     * Create a new {DateRangeAggregationBuilder} aggregation with the
     * given name.
     */
    public static DateRangeAggregationBuilder dateRange(String name) {
        return new DateRangeAggregationBuilder(name);
    }

    /**
     * Create a new {IpRangeAggregationBuilder} aggregation with the
     * given name.
     */
    public static IpRangeAggregationBuilder ipRange(String name) {
        return new IpRangeAggregationBuilder(name);
    }

    /**
     * Create a new {Terms} aggregation with the given name.
     */
    public static TermsAggregationBuilder terms(String name) {
        return new TermsAggregationBuilder(name, null);
    }

    /**
     * Create a new {Percentiles} aggregation with the given name.
     */
    public static PercentilesAggregationBuilder percentiles(String name) {
        return new PercentilesAggregationBuilder(name);
    }

    /**
     * Create a new {PercentileRanks} aggregation with the given name.
     */
    public static PercentileRanksAggregationBuilder percentileRanks(String name) {
        return new PercentileRanksAggregationBuilder(name);
    }

    /**
     * Create a new {Cardinality} aggregation with the given name.
     */
    public static CardinalityAggregationBuilder cardinality(String name) {
        return new CardinalityAggregationBuilder(name, null);
    }

    /**
     * Create a new {TopHits} aggregation with the given name.
     */
    public static TopHitsAggregationBuilder topHits(String name) {
        return new TopHitsAggregationBuilder(name);
    }

    /**
     * Create a new {GeoBounds} aggregation with the given name.
     */
    public static GeoBoundsAggregationBuilder geoBounds(String name) {
        return new GeoBoundsAggregationBuilder(name);
    }

    /**
     * Create a new {GeoCentroid} aggregation with the given name.
     */
    public static GeoCentroidAggregationBuilder geoCentroid(String name) {
        return new GeoCentroidAggregationBuilder(name);
    }

    /**
     * Create a new {ScriptedMetric} aggregation with the given name.
     */
    public static ScriptedMetricAggregationBuilder scriptedMetric(String name) {
        return new ScriptedMetricAggregationBuilder(name);
    }
}
