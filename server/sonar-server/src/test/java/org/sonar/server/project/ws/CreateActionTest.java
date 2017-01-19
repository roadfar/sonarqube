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

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.sonar.api.config.MapSettings;
import org.sonar.api.config.Settings;
import org.sonar.api.utils.System2;
import org.sonar.db.DbTester;
import org.sonar.server.component.ComponentService;
import org.sonar.server.component.index.ComponentIndexDefinition;
import org.sonar.server.component.index.ComponentIndexer;
import org.sonar.server.es.EsTester;
import org.sonar.server.favorite.FavoriteUpdater;
import org.sonar.server.measure.index.ProjectMeasuresIndexer;
import org.sonar.server.organization.DefaultOrganizationProvider;
import org.sonar.server.organization.TestDefaultOrganizationProvider;
import org.sonar.server.permission.PermissionTemplateService;
import org.sonar.server.permission.index.PermissionIndexer;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.WsActionTester;

public class CreateActionTest {

  private System2 system2 = System2.INSTANCE;

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Rule
  public DbTester db = DbTester.create(system2);

  @Rule
  public EsTester es = new EsTester(new ComponentIndexDefinition(new MapSettings()));

  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();

  private Settings settings = new MapSettings();

  private DefaultOrganizationProvider defaultOrganizationProvider = TestDefaultOrganizationProvider.from(db);

  private WsActionTester ws = new WsActionTester(
    new CreateAction(
      db.getDbClient(), userSession,
      new ComponentService(db.getDbClient(), null, userSession, system2,
        new ProjectMeasuresIndexer(system2, db.getDbClient(), es.client()),
        new ComponentIndexer(db.getDbClient(), es.client())),
      new PermissionTemplateService(db.getDbClient(), settings, new PermissionIndexer(db.getDbClient(), es.client()), userSession),
      new FavoriteUpdater(db.getDbClient(), userSession),
      defaultOrganizationProvider));

  @Before
  public void setUp() {

  }

}
