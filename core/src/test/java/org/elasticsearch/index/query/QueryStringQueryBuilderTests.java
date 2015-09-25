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

package org.elasticsearch.index.query;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.util.automaton.TooComplexToDeterminizeException;
import org.elasticsearch.common.lucene.all.AllTermQuery;
import org.hamcrest.Matchers;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.elasticsearch.index.query.QueryBuilders.queryStringQuery;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertBooleanSubQuery;
import static org.hamcrest.CoreMatchers.either;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.Matchers.*;

public class QueryStringQueryBuilderTests extends AbstractQueryTestCase<QueryStringQueryBuilder> {

    @Override
    protected QueryStringQueryBuilder doCreateTestQueryBuilder() {
        int numTerms = randomIntBetween(0, 5);
        String query = "";
        for (int i = 0; i < numTerms; i++) {
            //min length 4 makes sure that the text is not an operator (AND/OR) so toQuery won't break
            query += (randomBoolean() ? STRING_FIELD_NAME + ":" : "") + randomAsciiOfLengthBetween(4, 10) + " ";
        }
        QueryStringQueryBuilder queryStringQueryBuilder = new QueryStringQueryBuilder(query);
        if (randomBoolean()) {
            queryStringQueryBuilder.defaultField(randomBoolean() ? STRING_FIELD_NAME : randomAsciiOfLengthBetween(1, 10));
        }
        if (randomBoolean()) {
            int numFields = randomIntBetween(1, 5);
            for (int i = 0; i < numFields; i++) {
                String fieldName = randomBoolean() ? STRING_FIELD_NAME : randomAsciiOfLengthBetween(1, 10);
                if (randomBoolean()) {
                    queryStringQueryBuilder.field(fieldName);
                } else {
                    queryStringQueryBuilder.field(fieldName, randomFloat());
                }
            }
        }
        if (randomBoolean()) {
            queryStringQueryBuilder.defaultOperator(randomFrom(Operator.values()));
        }
        if (randomBoolean()) {
            //we only use string fields (either mapped or unmapped)
            queryStringQueryBuilder.fuzziness(randomFuzziness(STRING_FIELD_NAME));
        }
        if (randomBoolean()) {
            queryStringQueryBuilder.analyzer(randomAnalyzer());
        }
        if (randomBoolean()) {
            queryStringQueryBuilder.quoteAnalyzer(randomAnalyzer());
        }
        if (randomBoolean()) {
            queryStringQueryBuilder.allowLeadingWildcard(randomBoolean());
        }
        if (randomBoolean()) {
            queryStringQueryBuilder.analyzeWildcard(randomBoolean());
        }
        if (randomBoolean()) {
            queryStringQueryBuilder.maxDeterminizedStates(randomIntBetween(1, 100));
        }
        if (randomBoolean()) {
            queryStringQueryBuilder.lowercaseExpandedTerms(randomBoolean());
        }
        if (randomBoolean()) {
            queryStringQueryBuilder.autoGeneratePhraseQueries(randomBoolean());
        }
        if (randomBoolean()) {
            queryStringQueryBuilder.enablePositionIncrements(randomBoolean());
        }
        if (randomBoolean()) {
            queryStringQueryBuilder.lenient(randomBoolean());
        }
        if (randomBoolean()) {
            queryStringQueryBuilder.escape(randomBoolean());
        }
        if (randomBoolean()) {
            queryStringQueryBuilder.phraseSlop(randomIntBetween(0, 10));
        }
        if (randomBoolean()) {
            queryStringQueryBuilder.fuzzyMaxExpansions(randomIntBetween(0, 100));
        }
        if (randomBoolean()) {
            queryStringQueryBuilder.fuzzyPrefixLength(randomIntBetween(0, 10));
        }
        if (randomBoolean()) {
            queryStringQueryBuilder.fuzzyRewrite(getRandomRewriteMethod());
        }
        if (randomBoolean()) {
            queryStringQueryBuilder.rewrite(getRandomRewriteMethod());
        }
        if (randomBoolean()) {
            queryStringQueryBuilder.quoteFieldSuffix(randomAsciiOfLengthBetween(1, 3));
        }
        if (randomBoolean()) {
            queryStringQueryBuilder.tieBreaker(randomFloat());
        }
        if (randomBoolean()) {
            queryStringQueryBuilder.minimumShouldMatch(randomMinimumShouldMatch());
        }
        if (randomBoolean()) {
            queryStringQueryBuilder.useDisMax(randomBoolean());
        }
        if (randomBoolean()) {
            queryStringQueryBuilder.locale(randomLocale(getRandom()));
        }
        if (randomBoolean()) {
            queryStringQueryBuilder.timeZone(randomTimeZone());
        }
        return queryStringQueryBuilder;
    }

    @Override
    protected void doAssertLuceneQuery(QueryStringQueryBuilder queryBuilder, Query query, QueryShardContext context) throws IOException {
        if ("".equals(queryBuilder.queryString())) {
            assertThat(query, instanceOf(MatchNoDocsQuery.class));
        } else {
            assertThat(query, either(instanceOf(TermQuery.class)).or(instanceOf(AllTermQuery.class))
                    .or(instanceOf(BooleanQuery.class)).or(instanceOf(DisjunctionMaxQuery.class)));
        }
    }

    @Test
    public void testIllegalArguments() {
        try {
            new QueryStringQueryBuilder(null);
            fail("null is not allowed");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testToQueryMatchAllQuery() throws Exception {
        Query query = queryStringQuery("*:*").toQuery(createShardContext());
        assertThat(query, instanceOf(MatchAllDocsQuery.class));
    }

    @Test
    public void testToQueryTermQuery() throws IOException {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        Query query = queryStringQuery("test").defaultField(STRING_FIELD_NAME).toQuery(createShardContext());
        assertThat(query, instanceOf(TermQuery.class));
        TermQuery termQuery = (TermQuery) query;
        assertThat(termQuery.getTerm(), equalTo(new Term(STRING_FIELD_NAME, "test")));
    }

    @Test
    public void testToQueryPhraseQuery() throws IOException {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        Query query = queryStringQuery("\"term1 term2\"").defaultField(STRING_FIELD_NAME).phraseSlop(3).toQuery(createShardContext());
        assertThat(query, instanceOf(DisjunctionMaxQuery.class));
        DisjunctionMaxQuery disjunctionMaxQuery = (DisjunctionMaxQuery) query;
        assertThat(disjunctionMaxQuery.getDisjuncts().size(), equalTo(1));
        assertThat(disjunctionMaxQuery.getDisjuncts().get(0), instanceOf(PhraseQuery.class));
        PhraseQuery phraseQuery = (PhraseQuery)disjunctionMaxQuery.getDisjuncts().get(0);
        assertThat(phraseQuery.getTerms().length, equalTo(2));
        assertThat(phraseQuery.getTerms()[0], equalTo(new Term(STRING_FIELD_NAME, "term1")));
        assertThat(phraseQuery.getTerms()[1], equalTo(new Term(STRING_FIELD_NAME, "term2")));
        assertThat(phraseQuery.getSlop(), equalTo(3));
    }

    @Test
    public void testToQueryBoosts() throws Exception {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        QueryShardContext shardContext = createShardContext();
        QueryStringQueryBuilder queryStringQuery = queryStringQuery(STRING_FIELD_NAME + ":boosted^2");
        Query query = queryStringQuery.toQuery(shardContext);
        assertThat(query, instanceOf(BoostQuery.class));
        BoostQuery boostQuery = (BoostQuery) query;
        assertThat(boostQuery.getBoost(), Matchers.equalTo(2.0f));
        assertThat(boostQuery.getQuery(), instanceOf(TermQuery.class));
        assertThat(((TermQuery) boostQuery.getQuery()).getTerm(), equalTo(new Term(STRING_FIELD_NAME, "boosted")));
        queryStringQuery.boost(2.0f);
        query = queryStringQuery.toQuery(shardContext);
        assertThat(query, instanceOf(BoostQuery.class));
        assertThat(((BoostQuery) query).getBoost(), Matchers.equalTo(4.0f));

        queryStringQuery = queryStringQuery("((" + STRING_FIELD_NAME + ":boosted^2) AND (" + STRING_FIELD_NAME + ":foo^1.5))^3");
        query = queryStringQuery.toQuery(shardContext);
        assertThat(query, instanceOf(BoostQuery.class));
        boostQuery = (BoostQuery) query;
        assertThat(boostQuery.getBoost(), equalTo(3.0f));
        BoostQuery boostQuery1 = assertBooleanSubQuery(boostQuery.getQuery(), BoostQuery.class, 0);
        assertThat(boostQuery1.getBoost(), equalTo(2.0f));
        assertThat(boostQuery1.getQuery(), instanceOf(TermQuery.class));
        assertThat(((TermQuery)boostQuery1.getQuery()).getTerm(), equalTo(new Term(STRING_FIELD_NAME, "boosted")));
        BoostQuery boostQuery2 = assertBooleanSubQuery(boostQuery.getQuery(), BoostQuery.class, 1);
        assertThat(boostQuery2.getBoost(), equalTo(1.5f));
        assertThat(boostQuery2.getQuery(), instanceOf(TermQuery.class));
        assertThat(((TermQuery)boostQuery2.getQuery()).getTerm(), equalTo(new Term(STRING_FIELD_NAME, "foo")));
        queryStringQuery.boost(2.0f);
        query = queryStringQuery.toQuery(shardContext);
        assertThat(query, instanceOf(BoostQuery.class));
        boostQuery = (BoostQuery) query;
        assertThat(boostQuery.getBoost(), equalTo(6.0f));
    }

    @Test
    public void testToQueryMultipleTermsBooleanQuery() throws Exception {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        Query query = queryStringQuery("test1 test2").field(STRING_FIELD_NAME).useDisMax(false).toQuery(createShardContext());
        assertThat(query, instanceOf(BooleanQuery.class));
        BooleanQuery bQuery = (BooleanQuery) query;
        assertThat(bQuery.clauses().size(), equalTo(2));
        assertThat(assertBooleanSubQuery(query, TermQuery.class, 0).getTerm(), equalTo(new Term(STRING_FIELD_NAME, "test1")));
        assertThat(assertBooleanSubQuery(query, TermQuery.class, 1).getTerm(), equalTo(new Term(STRING_FIELD_NAME, "test2")));
    }

    @Test
    public void testToQueryMultipleFieldsBooleanQuery() throws Exception {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        Query query = queryStringQuery("test").field(STRING_FIELD_NAME).field(STRING_FIELD_NAME_2).useDisMax(false).toQuery(createShardContext());
        assertThat(query, instanceOf(BooleanQuery.class));
        BooleanQuery bQuery = (BooleanQuery) query;
        assertThat(bQuery.clauses().size(), equalTo(2));
        assertThat(assertBooleanSubQuery(query, TermQuery.class, 0).getTerm(), equalTo(new Term(STRING_FIELD_NAME, "test")));
        assertThat(assertBooleanSubQuery(query, TermQuery.class, 1).getTerm(), equalTo(new Term(STRING_FIELD_NAME_2, "test")));
    }

    @Test
    public void testToQueryMultipleFieldsDisMaxQuery() throws Exception {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        Query query = queryStringQuery("test").field(STRING_FIELD_NAME).field(STRING_FIELD_NAME_2).useDisMax(true).toQuery(createShardContext());
        assertThat(query, instanceOf(DisjunctionMaxQuery.class));
        DisjunctionMaxQuery disMaxQuery = (DisjunctionMaxQuery) query;
        List<Query> disjuncts = disMaxQuery.getDisjuncts();
        assertThat(((TermQuery) disjuncts.get(0)).getTerm(), equalTo(new Term(STRING_FIELD_NAME, "test")));
        assertThat(((TermQuery) disjuncts.get(1)).getTerm(), equalTo(new Term(STRING_FIELD_NAME_2, "test")));
    }

    @Test
    public void testToQueryFieldsWildcard() throws Exception {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        Query query = queryStringQuery("test").field("mapped_str*").useDisMax(false).toQuery(createShardContext());
        assertThat(query, instanceOf(BooleanQuery.class));
        BooleanQuery bQuery = (BooleanQuery) query;
        assertThat(bQuery.clauses().size(), equalTo(2));
        assertThat(assertBooleanSubQuery(query, TermQuery.class, 0).getTerm(), equalTo(new Term(STRING_FIELD_NAME, "test")));
        assertThat(assertBooleanSubQuery(query, TermQuery.class, 1).getTerm(), equalTo(new Term(STRING_FIELD_NAME_2, "test")));
    }

    @Test
    public void testToQueryDisMaxQuery() throws Exception {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        Query query = queryStringQuery("test").field(STRING_FIELD_NAME, 2.2f).field(STRING_FIELD_NAME_2).useDisMax(true).toQuery(createShardContext());
        assertThat(query, instanceOf(DisjunctionMaxQuery.class));
        DisjunctionMaxQuery disMaxQuery = (DisjunctionMaxQuery) query;
        List<Query> disjuncts = disMaxQuery.getDisjuncts();
        assertThat(((TermQuery) disjuncts.get(0)).getTerm(), equalTo(new Term(STRING_FIELD_NAME, "test")));
        assertThat((double) disjuncts.get(0).getBoost(), closeTo(2.2, 0.01));
        assertThat(((TermQuery) disjuncts.get(1)).getTerm(), equalTo(new Term(STRING_FIELD_NAME_2, "test")));
        assertThat((double) disjuncts.get(1).getBoost(), closeTo(1, 0.01));
    }

    @Test
    public void testToQueryRegExpQuery() throws Exception {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        Query query = queryStringQuery("/foo*bar/").defaultField(STRING_FIELD_NAME).maxDeterminizedStates(5000).toQuery(createShardContext());
        assertThat(query, instanceOf(RegexpQuery.class));
        RegexpQuery regexpQuery = (RegexpQuery) query;
        assertTrue(regexpQuery.toString().contains("/foo*bar/"));
    }

    @Test(expected = TooComplexToDeterminizeException.class)
    public void testToQueryRegExpQueryTooComplex() throws Exception {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        queryStringQuery("/[ac]*a[ac]{50,200}/").defaultField(STRING_FIELD_NAME).toQuery(createShardContext());
    }

    @Test
    public void testToQueryNumericRangeQuery() throws Exception {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        Query query = queryStringQuery("12~0.2").defaultField(INT_FIELD_NAME).toQuery(createShardContext());
        NumericRangeQuery fuzzyQuery = (NumericRangeQuery) query;
        assertThat(fuzzyQuery.getMin().longValue(), equalTo(12l));
        assertThat(fuzzyQuery.getMax().longValue(), equalTo(12l));

    }

    @Test
    public void testTimezone() throws Exception {
        assumeTrue("test runs only when at least a type is registered", getCurrentTypes().length > 0);
        String queryAsString = "{\n" +
                "    \"query_string\":{\n" +
                "        \"time_zone\":\"Europe/Paris\",\n" +
                "        \"query\":\"" + DATE_FIELD_NAME + ":[2012 TO 2014]\"\n" +
                "    }\n" +
                "}";
        QueryBuilder<?> queryBuilder = parseQuery(queryAsString);
        assertThat(queryBuilder, instanceOf(QueryStringQueryBuilder.class));
        QueryStringQueryBuilder queryStringQueryBuilder = (QueryStringQueryBuilder) queryBuilder;
        assertThat(queryStringQueryBuilder.timeZone(), equalTo(DateTimeZone.forID("Europe/Paris")));

        try {
            queryAsString = "{\n" +
                    "    \"query_string\":{\n" +
                    "        \"time_zone\":\"This timezone does not exist\",\n" +
                    "        \"query\":\"" + DATE_FIELD_NAME + ":[2012 TO 2014]\"\n" +
                    "    }\n" +
                    "}";
            parseQuery(queryAsString);
            fail("we expect a ParsingException as we are providing an unknown time_zome");
        } catch (IllegalArgumentException e) {
            // We expect this one
        }
    }
}
