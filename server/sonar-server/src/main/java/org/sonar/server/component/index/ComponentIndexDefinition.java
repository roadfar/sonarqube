/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.component.index;

import com.google.common.collect.ImmutableMap;
import org.sonar.api.config.Settings;
import org.sonar.server.es.IndexDefinition;
import org.sonar.server.es.NewIndex;

public class ComponentIndexDefinition implements IndexDefinition {

  public static final String INDEX_COMPONENTS = "components";

  public static final String TYPE_COMPONENT = "component";
  public static final String FIELD_PROJECT_UUID = "project_uuid";
  public static final String FIELD_KEY = "key";
  public static final String FIELD_NAME = "name";
  public static final String FIELD_QUALIFIER = "qualifier";

  public static final String TYPE_AUTHORIZATION = "authorization";
  public static final String FIELD_AUTHORIZATION_GROUPS = "groupNames";
  public static final String FIELD_AUTHORIZATION_USERS = "users";
  public static final String FIELD_AUTHORIZATION_UPDATED_AT = "updatedAt";

  private static final int DEFAULT_NUMBER_OF_SHARDS = 5;

  private final Settings settings;

  public ComponentIndexDefinition(Settings settings) {
    this.settings = settings;
  }

  @Override
  public void define(IndexDefinitionContext context) {
    NewIndex index = context.create(INDEX_COMPONENTS);
    index.refreshHandledByIndexer();
    index.configureShards(settings, DEFAULT_NUMBER_OF_SHARDS);

    // type "component"
    NewIndex.NewIndexType mapping = index.createType(TYPE_COMPONENT);
    mapping.setAttribute("_parent", ImmutableMap.of("type", TYPE_AUTHORIZATION));
    mapping.setAttribute("_routing", ImmutableMap.of("required", "true"));
    mapping.stringFieldBuilder(FIELD_PROJECT_UUID).build();
    mapping.stringFieldBuilder(FIELD_KEY).enableSorting().build();
    mapping.stringFieldBuilder(FIELD_NAME).enableGramSearch().enableFuzzySearch().build();
    mapping.stringFieldBuilder(FIELD_QUALIFIER).build();

    // do not store document but only indexation of information
    mapping.setEnableSource(false);

    // type "authorization"
    NewIndex.NewIndexType authorizationMapping = index.createType(TYPE_AUTHORIZATION);
    authorizationMapping.setAttribute("_routing", ImmutableMap.of("required", "true"));
    authorizationMapping.createDateTimeField(FIELD_AUTHORIZATION_UPDATED_AT);
    authorizationMapping.stringFieldBuilder(FIELD_AUTHORIZATION_GROUPS).disableNorms().build();
    authorizationMapping.stringFieldBuilder(FIELD_AUTHORIZATION_USERS).disableNorms().build();
    authorizationMapping.setEnableSource(false);
  }
}
