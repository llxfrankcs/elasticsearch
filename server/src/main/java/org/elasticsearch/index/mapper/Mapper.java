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

package org.elasticsearch.index.mapper;

import org.elasticsearch.Version;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.time.DateFormatter;
import org.elasticsearch.common.xcontent.ToXContentFragment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.IndexAnalyzers;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.index.similarity.SimilarityProvider;
import org.elasticsearch.script.ScriptService;

import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class Mapper implements ToXContentFragment, Iterable<Mapper> {

    public static class BuilderContext {
        private final Settings indexSettings;
        private final ContentPath contentPath;

        public BuilderContext(Settings indexSettings, ContentPath contentPath) {
            Objects.requireNonNull(indexSettings, "indexSettings is required");
            this.contentPath = contentPath;
            this.indexSettings = indexSettings;
        }

        public ContentPath path() {
            return this.contentPath;
        }

        public Settings indexSettings() {
            return this.indexSettings;
        }

        public Version indexCreatedVersion() {
            return Version.indexCreated(indexSettings);
        }
    }

    public abstract static class Builder {

        public String name;

        protected Builder(String name) {
            this.name = name;
        }

        public String name() {
            return this.name;
        }

        /** Returns a newly built mapper. */
        public abstract Mapper build(BuilderContext context);
    }

    public interface TypeParser {

        class ParserContext {

            private final Function<String, SimilarityProvider> similarityLookupService;
            private final Function<String, TypeParser> typeParsers;
            private final Version indexVersionCreated;
            private final Supplier<QueryShardContext> queryShardContextSupplier;
            private final DateFormatter dateFormatter;
            private final ScriptService scriptService;
            private final IndexAnalyzers indexAnalyzers;
            private final IndexSettings indexSettings;
            private final BooleanSupplier idFieldDataEnabled;

            public ParserContext(Function<String, SimilarityProvider> similarityLookupService,
                                 Function<String, TypeParser> typeParsers,
                                 Version indexVersionCreated,
                                 Supplier<QueryShardContext> queryShardContextSupplier,
                                 DateFormatter dateFormatter,
                                 ScriptService scriptService,
                                 IndexAnalyzers indexAnalyzers,
                                 IndexSettings indexSettings,
                                 BooleanSupplier idFieldDataEnabled) {
                this.similarityLookupService = similarityLookupService;
                this.typeParsers = typeParsers;
                this.indexVersionCreated = indexVersionCreated;
                this.queryShardContextSupplier = queryShardContextSupplier;
                this.dateFormatter = dateFormatter;
                this.scriptService = scriptService;
                this.indexAnalyzers = indexAnalyzers;
                this.indexSettings = indexSettings;
                this.idFieldDataEnabled = idFieldDataEnabled;
            }

            public IndexAnalyzers getIndexAnalyzers() {
                return indexAnalyzers;
            }

            public IndexSettings getIndexSettings() {
                return indexSettings;
            }

            public BooleanSupplier isIdFieldDataEnabled() {
                return idFieldDataEnabled;
            }

            public Settings getSettings() {
                return indexSettings.getSettings();
            }

            public SimilarityProvider getSimilarity(String name) {
                return similarityLookupService.apply(name);
            }

            public TypeParser typeParser(String type) {
                return typeParsers.apply(type);
            }

            public Version indexVersionCreated() {
                return indexVersionCreated;
            }

            public Supplier<QueryShardContext> queryShardContextSupplier() {
                return queryShardContextSupplier;
            }

            /**
             * Gets an optional default date format for date fields that do not have an explicit format set
             *
             * If {@code null}, then date fields will default to {@link DateFieldMapper#DEFAULT_DATE_TIME_FORMATTER}.
             */
            public DateFormatter getDateFormatter() {
                return dateFormatter;
            }

            public boolean isWithinMultiField() { return false; }

            protected Function<String, TypeParser> typeParsers() { return typeParsers; }

            protected Function<String, SimilarityProvider> similarityLookupService() { return similarityLookupService; }

            /**
             * The {@linkplain ScriptService} to compile scripts needed by the {@linkplain Mapper}.
             */
            public ScriptService scriptService() {
                return scriptService;
            }

            public ParserContext createMultiFieldContext(ParserContext in) {
                return new MultiFieldParserContext(in);
            }

            static class MultiFieldParserContext extends ParserContext {
                MultiFieldParserContext(ParserContext in) {
                    super(in.similarityLookupService, in.typeParsers, in.indexVersionCreated, in.queryShardContextSupplier,
                        in.dateFormatter, in.scriptService, in.indexAnalyzers, in.indexSettings, in.idFieldDataEnabled);
                }

                @Override
                public boolean isWithinMultiField() { return true; }
            }
        }

        Mapper.Builder parse(String name, Map<String, Object> node, ParserContext parserContext) throws MapperParsingException;
    }

    private final String simpleName;

    public Mapper(String simpleName) {
        Objects.requireNonNull(simpleName);
        this.simpleName = simpleName;
    }

    /** Returns the simple name, which identifies this mapper against other mappers at the same level in the mappers hierarchy
     * TODO: make this protected once Mapper and FieldMapper are merged together */
    public final String simpleName() {
        return simpleName;
    }

    /** Returns the canonical name which uniquely identifies the mapper against other mappers in a type. */
    public abstract String name();

    /**
     * Returns a name representing the type of this mapper.
     */
    public abstract String typeName();

    /** Return the merge of {@code mergeWith} into this.
     *  Both {@code this} and {@code mergeWith} will be left unmodified. */
    public abstract Mapper merge(Mapper mergeWith);

    /**
     * Validate any cross-field references made by this mapper
     * @param mappers a {@link MappingLookup} that can produce references to other mappers
     */
    public abstract void validate(MappingLookup mappers);

}
