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

package org.sonar.server.measure.ws;

import com.google.common.base.Throwables;
import java.io.IOException;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.server.ws.WebService.Param;
import org.sonar.api.web.UserRole;
import org.sonar.core.util.stream.Collectors;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.SnapshotDto;
import org.sonar.db.metric.MetricDto;
import org.sonar.server.component.ComponentFinder;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.TestRequest;
import org.sonar.server.ws.WsActionTester;
import org.sonarqube.ws.Common.Paging;
import org.sonarqube.ws.MediaTypes;
import org.sonarqube.ws.WsMeasures.SearchHistoryResponse;
import org.sonarqube.ws.WsMeasures.SearchHistoryResponse.HistoryMeasure;
import org.sonarqube.ws.WsMeasures.SearchHistoryResponse.HistoryValue;
import org.sonarqube.ws.client.measure.SearchHistoryRequest;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.sonar.api.utils.DateUtils.formatDateTime;
import static org.sonar.core.util.Protobuf.setNullable;
import static org.sonar.db.component.ComponentTesting.newFileDto;
import static org.sonar.db.component.ComponentTesting.newProjectDto;
import static org.sonar.db.component.SnapshotDto.STATUS_UNPROCESSED;
import static org.sonar.db.component.SnapshotTesting.newAnalysis;
import static org.sonar.db.measure.MeasureTesting.newMeasureDto;
import static org.sonar.db.metric.MetricTesting.newMetricDto;
import static org.sonarqube.ws.client.measure.MeasuresWsParameters.PARAM_COMPONENT;
import static org.sonarqube.ws.client.measure.MeasuresWsParameters.PARAM_FROM;
import static org.sonarqube.ws.client.measure.MeasuresWsParameters.PARAM_METRICS;

public class SearchHistoryActionTest {

  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone();
  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  @Rule
  public DbTester db = DbTester.create();
  private DbClient dbClient = db.getDbClient();
  private DbSession dbSession = db.getSession();

  private WsActionTester ws = new WsActionTester(new SearchHistoryAction(dbClient, new ComponentFinder(dbClient), userSession));

  private ComponentDto project;
  private SnapshotDto analysis;
  private List<String> metrics;
  private MetricDto complexityMetric;
  private MetricDto nclocMetric;
  private MetricDto newViolationMetric;
  private SearchHistoryRequest.Builder wsRequest;

  @Before
  public void setUp() {
    project = newProjectDto(db.getDefaultOrganization());
    analysis = db.components().insertProjectAndSnapshot(project);
    userSession.addProjectUuidPermissions(UserRole.USER, project.uuid());
    nclocMetric = insertNclocMetric();
    complexityMetric = insertComplexityMetric();
    newViolationMetric = insertNewViolationMetric();
    metrics = newArrayList(nclocMetric.getKey(), complexityMetric.getKey(), newViolationMetric.getKey());
    wsRequest = SearchHistoryRequest.builder().setComponent(project.getKey()).setMetrics(metrics);
  }

  @Test
  public void empty_response() {
    wsRequest.setMetrics(singletonList(complexityMetric.getKey()));

    SearchHistoryResponse result = call();

    assertThat(result.getMeasuresList()).hasSize(1);
    assertThat(result.getMeasures(0).getHistoryCount()).isEqualTo(0);

    assertThat(result.getPaging()).extracting(Paging::getPageIndex, Paging::getPageSize, Paging::getTotal)
      // pagination is applied to the number of analyses
      .containsExactly(1, 100, 1);
  }

  @Test
  public void return_metrics() {
    dbClient.measureDao().insert(dbSession, newMeasureDto(complexityMetric, project, analysis).setValue(42.0d));
    db.commit();

    SearchHistoryResponse result = call();

    assertThat(result.getMeasuresList()).hasSize(3)
      .extracting(HistoryMeasure::getMetric)
      .containsExactly(complexityMetric.getKey(), nclocMetric.getKey(), newViolationMetric.getKey());
  }

  @Test
  public void return_measures() {
    SnapshotDto laterAnalysis = dbClient.snapshotDao().insert(dbSession, newAnalysis(project).setCreatedAt(analysis.getCreatedAt() + 42_000));
    ComponentDto file = db.components().insertComponent(newFileDto(project));
    dbClient.measureDao().insert(dbSession,
      newMeasureDto(complexityMetric, project, analysis).setValue(101d),
      newMeasureDto(complexityMetric, project, laterAnalysis).setValue(100d),
      newMeasureDto(complexityMetric, file, analysis).setValue(42d),
      newMeasureDto(nclocMetric, project, analysis).setValue(201d),
      newMeasureDto(newViolationMetric, project, analysis).setVariation(1, 5d),
      newMeasureDto(newViolationMetric, project, laterAnalysis).setVariation(1, 10d));
    db.commit();

    SearchHistoryResponse result = call();

    assertThat(result.getPaging()).extracting(Paging::getPageIndex, Paging::getPageSize, Paging::getTotal)
      .containsExactly(1, 100, 2);
    assertThat(result.getMeasuresList()).extracting(HistoryMeasure::getMetric).hasSize(3)
      .containsExactly(complexityMetric.getKey(), nclocMetric.getKey(), newViolationMetric.getKey());
    String analysisDate = formatDateTime(analysis.getCreatedAt());
    String laterAnalysisDate = formatDateTime(laterAnalysis.getCreatedAt());
    // complexity measures
    HistoryMeasure complexityMeasures = result.getMeasures(0);
    assertThat(complexityMeasures.getMetric()).isEqualTo(complexityMetric.getKey());
    assertThat(complexityMeasures.getHistoryList()).extracting(HistoryValue::getDate, HistoryValue::getValue)
      .containsExactly(tuple(analysisDate, "101"), tuple(laterAnalysisDate, "100"));
    // ncloc measures
    HistoryMeasure nclocMeasures = result.getMeasures(1);
    assertThat(nclocMeasures.getMetric()).isEqualTo(nclocMetric.getKey());
    assertThat(nclocMeasures.getHistoryList()).extracting(HistoryValue::getDate, HistoryValue::getValue)
      .containsExactly(tuple(analysisDate, "201"));
    // new_violation measures
    HistoryMeasure newViolationMeasures = result.getMeasures(2);
    assertThat(newViolationMeasures.getMetric()).isEqualTo(newViolationMetric.getKey());
    assertThat(newViolationMeasures.getHistoryList()).extracting(HistoryValue::getDate, HistoryValue::getValue)
      .containsExactly(tuple(analysisDate, "5"), tuple(laterAnalysisDate, "10"));
  }

  @Test
  public void pagination_applies_to_analyses() {
    project = db.components().insertProject();
    userSession.addProjectUuidPermissions(UserRole.USER, project.uuid());
    List<String> analysisDates = LongStream.rangeClosed(1, 9)
      .mapToObj(i -> dbClient.snapshotDao().insert(dbSession, newAnalysis(project).setCreatedAt(i * 1_000_000_000)))
      .peek(a -> dbClient.measureDao().insert(dbSession, newMeasureDto(complexityMetric, project, a).setValue(101d)))
      .map(a -> formatDateTime(a.getCreatedAt()))
      .collect(Collectors.toList());
    db.commit();
    wsRequest.setComponent(project.getKey()).setPage(2).setPageSize(3);

    SearchHistoryResponse result = call();

    assertThat(result.getPaging()).extracting(Paging::getPageIndex, Paging::getPageSize, Paging::getTotal).containsExactly(2, 3, 9);
    assertThat(result.getMeasures(0).getHistoryList()).extracting(HistoryValue::getDate).containsExactly(
      analysisDates.get(3), analysisDates.get(4), analysisDates.get(5));
  }

  @Test
  public void do_not_return_unprocessed_analyses() {
    dbClient.snapshotDao().insert(dbSession, newAnalysis(project).setStatus(STATUS_UNPROCESSED));
    db.commit();

    SearchHistoryResponse result = call();

    // one analysis in setUp method
    assertThat(result.getPaging().getTotal()).isEqualTo(1);
  }

  @Test
  public void do_not_return_developer_measures() {
    wsRequest.setMetrics(singletonList(complexityMetric.getKey()));
    dbClient.measureDao().insert(dbSession, newMeasureDto(complexityMetric, project, analysis).setDeveloperId(42L));
    db.commit();

    SearchHistoryResponse result = call();

    assertThat(result.getMeasuresCount()).isEqualTo(1);
    assertThat(result.getMeasures(0).getHistoryCount()).isEqualTo(0);
  }

  @Test
  public void fail_if_unknown_metric() {
    wsRequest.setMetrics(newArrayList(complexityMetric.getKey(), nclocMetric.getKey(), "METRIC_42", "42_METRIC"));

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Metrics 42_METRIC, METRIC_42 are not found");

    call();
  }

  @Test
  public void fail_if_not_enough_permissions() {
    userSession.anonymous();

    expectedException.expect(ForbiddenException.class);

    call();
  }

  @Test
  public void fail_if_unknown_component() {
    wsRequest.setComponent("PROJECT_42");

    expectedException.expect(NotFoundException.class);

    call();
  }

  @Test
  public void definition() {
    WebService.Action definition = ws.getDef();

    assertThat(definition.key()).isEqualTo("search_history");
    assertThat(definition.responseExampleAsString()).isNotEmpty();
    assertThat(definition.isPost()).isFalse();
    assertThat(definition.isInternal()).isFalse();
    assertThat(definition.since()).isEqualTo("6.3");
  }

  private SearchHistoryResponse call() {
    SearchHistoryRequest wsRequest = this.wsRequest.build();

    TestRequest request = ws.newRequest()
      .setMediaType(MediaTypes.PROTOBUF);

    request.setParam(PARAM_COMPONENT, wsRequest.getComponent());
    request.setParam(PARAM_METRICS, String.join(",", wsRequest.getMetrics()));
    setNullable(wsRequest.getFrom(), from -> request.setParam(PARAM_FROM, from));
    setNullable(wsRequest.getTo(), to -> request.setParam(PARAM_FROM, to));
    setNullable(wsRequest.getPage(), p -> request.setParam(Param.PAGE, String.valueOf(p)));
    setNullable(wsRequest.getPageSize(), ps -> request.setParam(Param.PAGE_SIZE, String.valueOf(ps)));

    try {
      return SearchHistoryResponse.parseFrom(request.execute().getInputStream());
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
  }

  private static MetricDto newMetricDtoWithoutOptimization() {
    return newMetricDto()
      .setWorstValue(null)
      .setOptimizedBestValue(false)
      .setBestValue(null)
      .setUserManaged(false);
  }

  private MetricDto insertNclocMetric() {
    MetricDto metric = dbClient.metricDao().insert(dbSession, newMetricDtoWithoutOptimization()
      .setKey("ncloc")
      .setShortName("Lines of code")
      .setDescription("Non Commenting Lines of Code")
      .setDomain("Size")
      .setValueType("INT")
      .setDirection(-1)
      .setQualitative(false)
      .setHidden(false)
      .setUserManaged(false));
    db.commit();
    return metric;
  }

  private MetricDto insertComplexityMetric() {
    MetricDto metric = dbClient.metricDao().insert(dbSession, newMetricDtoWithoutOptimization()
      .setKey("complexity")
      .setShortName("Complexity")
      .setDescription("Cyclomatic complexity")
      .setDomain("Complexity")
      .setValueType("INT")
      .setDirection(-1)
      .setQualitative(false)
      .setHidden(false)
      .setUserManaged(false));
    db.commit();
    return metric;
  }

  private MetricDto insertNewViolationMetric() {
    MetricDto metric = dbClient.metricDao().insert(dbSession, newMetricDtoWithoutOptimization()
      .setKey("new_violations")
      .setShortName("New issues")
      .setDescription("New Issues")
      .setDomain("Issues")
      .setValueType("INT")
      .setDirection(-1)
      .setQualitative(true)
      .setHidden(false)
      .setUserManaged(false));
    db.commit();
    return metric;
  }
}
