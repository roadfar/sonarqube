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
package org.sonar.server.component.ws;

import com.google.common.io.Resources;
import org.sonar.api.server.ws.RailsHandler;
import org.sonar.api.server.ws.WebService;
import org.sonar.server.ws.RemovedWebServiceHandler;

public class ResourcesWs implements WebService {

  @Override
  public void define(Context context) {
    NewController controller = context.createController("api/resources")
      .setDescription("Get details about components. Deprecated since 5.4.")
      .setSince("2.10");

    defineIndexAction(controller);
    defineSearchAction(controller);

    controller.done();
  }

  private void defineIndexAction(NewController controller) {
    controller.createAction("index")
      .setDescription("The web service is removed and you're invited to use the alternatives: " +
        "<ul>" +
        "<li>if you need one component without measures: api/components/show</li>" +
        "<li>if you need one component with measures: api/measures/component</li>" +
        "<li>if you need several components without measures: api/components/tree</li>" +
        "<li>if you need several components with measures: api/measures/component_tree</li>" +
        "</ul>")
      .setSince("2.10")
      .setDeprecatedSince("5.4")
      .setHandler(RemovedWebServiceHandler.INSTANCE)
      .setResponseExample(Resources.getResource(this.getClass(), "resources-example-index.json"));
  }

  private void defineSearchAction(NewController controller) {
    NewAction action = controller.createAction("search")
      .setDescription("Search for components")
      .setSince("3.3")
      .setDeprecatedSince("5.4")
      .addPagingParams(10)
      .setInternal(true)
      .setHandler(RailsHandler.INSTANCE)
      .setResponseExample(Resources.getResource(this.getClass(), "resources-example-search.json"));

    action.createParam("s")
      .setDescription("To filter on resources containing a specified text in their name")
      .setExampleValue("sonar");

    action.createParam("display_key")
      .setDescription("Return the resource key instead of the resource id")
      .setBooleanPossibleValues()
      .setDefaultValue(String.valueOf(false));

    action.createParam("q")
      .setDescription("Comma-separated list of qualifiers")
      .setExampleValue("TRK,BRC");

    action.createParam("qp")
      .setDescription("Resource Property")
      .setExampleValue("supportsMeasureFilters");

    action.createParam("f")
      .setDescription("If 's2', then it will return a select2 compatible format")
      .setExampleValue("s2");

    RailsHandler.addJsonOnlyFormatParam(action);
  }

}
