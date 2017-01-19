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
package org.sonar.server.project.ws;

import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.component.ComponentDto;
import org.sonar.server.component.ComponentService;
import org.sonar.server.favorite.FavoriteUpdater;
import org.sonar.server.organization.DefaultOrganizationProvider;
import org.sonar.server.permission.PermissionTemplateService;
import org.sonar.server.user.UserSession;
import org.sonarqube.ws.client.project.CreateRequest;
import org.sonarqube.ws.client.project.ProjectsWsParameters;

import static org.sonar.api.resources.Qualifiers.PROJECT;
import static org.sonar.server.component.NewComponent.newComponentBuilder;
import static org.sonar.server.ws.KeyExamples.KEY_PROJECT_EXAMPLE_001;
import static org.sonarqube.ws.client.project.ProjectsWsParameters.ACTION_CREATE;
import static org.sonarqube.ws.client.project.ProjectsWsParameters.PARAM_BRANCH;
import static org.sonarqube.ws.client.project.ProjectsWsParameters.PARAM_NAME;
import static org.sonarqube.ws.client.projectanalysis.ProjectAnalysesWsParameters.PARAM_CATEGORY;

public class CreateAction implements ProjectsWsAction {

  public static final String PARAM_ID = "id";
  public static final String PARAM_KEY = "key";

  private final DbClient dbClient;
  private final UserSession userSession;
  private final ComponentService componentService;
  private final DefaultOrganizationProvider defaultOrganizationProvider;
  private final PermissionTemplateService permissionTemplateService;
  private final FavoriteUpdater favoriteUpdater;

  public CreateAction(DbClient dbClient, UserSession userSession, ComponentService componentService, PermissionTemplateService permissionTemplateService,
    FavoriteUpdater favoriteUpdater, DefaultOrganizationProvider defaultOrganizationProvider) {
    this.dbClient = dbClient;
    this.userSession = userSession;
    this.componentService = componentService;
    this.permissionTemplateService = permissionTemplateService;
    this.favoriteUpdater = favoriteUpdater;
    this.defaultOrganizationProvider = defaultOrganizationProvider;
  }

  @Override
  public void define(WebService.NewController context) {
    WebService.NewAction action = context.createAction(ACTION_CREATE)
      .setDescription("Create a project.<br/>" +
        "Requires 'Create Projects' permission<br/>" +
        "Since 6.3, the response is empty")
      .setSince("4.0")
      .setPost(true)
      .setHandler(this);

    action.createParam(ProjectsWsParameters.PARAM_KEY)
      .setDescription("Key of the project")
      .setRequired(true)
      .setExampleValue(KEY_PROJECT_EXAMPLE_001);

    action.createParam(PARAM_NAME)
      .setDescription("Name of the project")
      .setRequired(true)
      .setExampleValue("SonarQube");

    action.createParam(PARAM_BRANCH)
      .setDescription("SCM Branch of the project. The key of the project will become key:branch, for instance 'SonarQube:branch-5.0'")
      .setExampleValue("branch-5.0");
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    userSession.checkPermission(GlobalPermissions.PROVISIONING);
    String organizationUuid = defaultOrganizationProvider.get().getUuid();

    try (DbSession dbSession = dbClient.openSession(false)) {
      CreateRequest createRequest = toCreateRequest(request);
      ComponentDto componentDto = componentService.create(dbSession, newComponentBuilder()
        .setOrganizationUuid(organizationUuid)
        .setKey(createRequest.getKey())
        .setName(createRequest.getName())
        .setBranch(createRequest.getBranch())
        .setQualifier(PROJECT)
        .build());

      permissionTemplateService.applyDefault(dbSession, organizationUuid, componentDto, userSession.isLoggedIn() ? userSession.getUserId().longValue() : null);
      if (permissionTemplateService.hasDefaultTemplateWithPermissionOnProjectCreator(dbSession, organizationUuid, componentDto)) {
        favoriteUpdater.add(dbSession, componentDto);
        dbSession.commit();
      }
      response.noContent();
    }
  }

  private static CreateRequest toCreateRequest(Request request) {
    return CreateRequest.builder()
      .setKey(request.mandatoryParam(PARAM_KEY))
      .setName(request.mandatoryParam(PARAM_NAME))
      .setBranch(request.param(PARAM_CATEGORY))
      .build();
  }

}
