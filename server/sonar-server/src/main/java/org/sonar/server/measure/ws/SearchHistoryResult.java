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

import java.util.List;
import org.sonar.db.component.SnapshotDto;
import org.sonar.db.measure.MeasureDto;
import org.sonar.db.metric.MetricDto;
import org.sonarqube.ws.Common;
import org.sonarqube.ws.client.measure.SearchHistoryRequest;

import static java.util.Objects.requireNonNull;
import static org.sonar.api.utils.Paging.offset;
import static org.sonar.core.util.stream.Collectors.toList;

class SearchHistoryResult {
  private final SearchHistoryRequest request;
  private List<SnapshotDto> analyses;
  private List<MetricDto> metrics;
  private List<MeasureDto> measures;
  private Common.Paging paging;

  SearchHistoryResult(SearchHistoryRequest request) {
    this.request = request;
  }

  boolean hasResults() {
    return !analyses.isEmpty();
  }

  List<SnapshotDto> getAnalyses() {
    return requireNonNull(analyses);
  }

  SearchHistoryResult setAnalyses(List<SnapshotDto> analyses) {
    this.paging = Common.Paging.newBuilder().setPageIndex(request.getPage()).setPageSize(request.getPageSize()).setTotal(analyses.size()).build();
    this.analyses = analyses.stream().skip(offset(request.getPage(), request.getPageSize())).limit(request.getPageSize()).collect(toList());
    return this;
  }

  List<MetricDto> getMetrics() {
    return requireNonNull(metrics);
  }

  SearchHistoryResult setMetrics(List<MetricDto> metrics) {
    this.metrics = metrics;
    return this;
  }

  List<MeasureDto> getMeasures() {
    return requireNonNull(measures);
  }

  SearchHistoryResult setMeasures(List<MeasureDto> measures) {
    this.measures = measures;
    return this;
  }

  Common.Paging getPaging() {
    return requireNonNull(paging);
  }
}
