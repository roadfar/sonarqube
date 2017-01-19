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

package org.sonar.server.favorite.ws;

import com.google.common.base.Throwables;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.server.ws.WebService.Param;
import org.sonar.api.web.UserRole;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.permission.UserPermissionDto;
import org.sonar.server.exceptions.UnauthorizedException;
import org.sonar.server.favorite.FavoriteFinder;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.TestRequest;
import org.sonar.server.ws.WsActionTester;
import org.sonarqube.ws.Common.Paging;
import org.sonarqube.ws.Favorites.Favorite;
import org.sonarqube.ws.Favorites.SearchResponse;
import org.sonarqube.ws.MediaTypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.sonar.api.resources.Qualifiers.FILE;
import static org.sonar.api.resources.Qualifiers.PROJECT;
import static org.sonar.core.util.Protobuf.setNullable;
import static org.sonar.db.component.ComponentTesting.newFileDto;
import static org.sonar.db.component.ComponentTesting.newProjectDto;
import static org.sonar.test.JsonAssert.assertJson;
import static org.sonarqube.ws.client.WsRequest.Method.POST;

public class SearchActionTest {
  private static final int USER_ID = 123;
  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone().login().setUserId(USER_ID);
  @Rule
  public DbTester db = DbTester.create();
  private DbClient dbClient = db.getDbClient();
  private DbSession dbSession = db.getSession();

  private FavoriteFinder favoriteFinder = new FavoriteFinder(dbClient, userSession);

  private WsActionTester ws = new WsActionTester(new SearchAction(favoriteFinder, dbClient, userSession));

  @Test
  public void return_favorites() {
    ComponentDto project = newProjectDto(db.getDefaultOrganization(), "P1").setKey("K1").setName("N1");
    addComponent(project);
    addComponent(newFileDto(project).setKey("K11").setName("N11"));
    addComponent(newProjectDto(db.getDefaultOrganization(), "P2").setKey("K2").setName("N2"));

    SearchResponse result = call();

    assertThat(result.getPaging())
      .extracting(Paging::getPageIndex, Paging::getPageSize, Paging::getTotal)
      .containsExactly(1, 100, 3);
    assertThat(result.getFavoritesList())
      .extracting(Favorite::getKey, Favorite::getName, Favorite::getQualifier)
      .containsOnly(
        tuple("K1", "N1", PROJECT),
        tuple("K11", "N11", FILE),
        tuple("K2", "N2", PROJECT));
  }

  @Test
  public void empty_list() {
    SearchResponse result = call();

    assertThat(result.getFavoritesCount()).isEqualTo(0);
    assertThat(result.getFavoritesList()).isEmpty();
  }

  @Test
  public void filter_authorized_components() {
    OrganizationDto organizationDto = db.organizations().insert();
    addComponent(newProjectDto(organizationDto).setKey("K1"));
    ComponentDto unauthorizedProject = db.components().insertComponent(newProjectDto(organizationDto));
    db.favorites().add(unauthorizedProject, USER_ID);

    SearchResponse result = call();

    assertThat(result.getFavoritesCount()).isEqualTo(1);
    assertThat(result.getFavorites(0).getKey()).isEqualTo("K1");
  }

  @Test
  public void paginate_results() {
    IntStream.rangeClosed(1, 9)
      .forEach(i -> addComponent(newProjectDto(db.getDefaultOrganization()).setKey("K" + i).setName("N" + i)));
    ComponentDto unauthorizedProject = db.components().insertComponent(newProjectDto(db.getDefaultOrganization()));
    db.favorites().add(unauthorizedProject, USER_ID);

    SearchResponse result = call(2, 3);

    assertThat(result.getFavoritesCount()).isEqualTo(3);
    assertThat(result.getFavoritesList())
      .extracting(Favorite::getKey)
      .containsExactly("K4", "K5", "K6");

  }

  @Test
  public void return_only_users_favorite() {
    OrganizationDto organizationDto = db.organizations().insert();
    addComponent(newProjectDto(organizationDto).setKey("K1"));
    ComponentDto otherUserFavorite = newProjectDto(organizationDto).setKey("K42");
    db.components().insertComponent(otherUserFavorite);
    db.favorites().add(otherUserFavorite, 42L);
    dbClient.userPermissionDao().insert(dbSession, new UserPermissionDto(organizationDto.getUuid(), UserRole.USER, 42L, otherUserFavorite.getId()));
    db.commit();

    SearchResponse result = call();

    assertThat(result.getFavoritesList()).extracting(Favorite::getKey).containsExactly("K1");
  }

  @Test
  public void favorites_ordered_by_name() {
    OrganizationDto organizationDto = db.organizations().insert();
    addComponent(newProjectDto(organizationDto).setName("N2"));
    addComponent(newProjectDto(organizationDto).setName("N3"));
    addComponent(newProjectDto(organizationDto).setName("N1"));

    SearchResponse result = call();

    assertThat(result.getFavoritesList()).extracting(Favorite::getName)
      .containsExactly("N1", "N2", "N3");
  }

  @Test
  public void json_example() {
    addComponent(newProjectDto(db.getDefaultOrganization()).setKey("K1").setName("Samba"));
    addComponent(newProjectDto(db.getDefaultOrganization()).setKey("K2").setName("Apache HBase"));
    addComponent(newProjectDto(db.getDefaultOrganization()).setKey("K3").setName("JDK9"));

    String result = ws.newRequest().execute().getInput();

    assertJson(result).isSimilarTo(getClass().getResource("search-example.json"));
  }

  @Test
  public void definition() {
    WebService.Action definition = ws.getDef();

    assertThat(definition.key()).isEqualTo("search");
    assertThat(definition.responseExampleAsString()).isNotEmpty();
  }

  @Test
  public void fail_if_not_authenticated() {
    userSession.anonymous();

    expectedException.expect(UnauthorizedException.class);

    call();
  }

  private void addComponent(ComponentDto component) {
    db.components().insertComponent(component);
    db.favorites().add(component, USER_ID);
    dbClient.userPermissionDao().insert(dbSession, new UserPermissionDto(component.getOrganizationUuid(), UserRole.USER, USER_ID, component.getId()));
    db.commit();
  }

  private SearchResponse call(@Nullable Integer page, @Nullable Integer pageSize) {
    TestRequest request = ws.newRequest()
      .setMediaType(MediaTypes.PROTOBUF)
      .setMethod(POST.name());
    setNullable(page, p -> request.setParam(Param.PAGE, p.toString()));
    setNullable(pageSize, ps -> request.setParam(Param.PAGE_SIZE, ps.toString()));

    InputStream response = request.execute().getInputStream();

    try {
      return SearchResponse.parseFrom(response);
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }

  private SearchResponse call() {
    return call(null, null);
  }
}
