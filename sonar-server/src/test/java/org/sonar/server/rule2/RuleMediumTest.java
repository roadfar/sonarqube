/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.rule2;

import com.google.common.collect.Iterables;
import com.sun.org.apache.xerces.internal.util.SynchronizedSymbolTable;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rule.RuleStatus;
import org.sonar.api.rule.Severity;
import org.sonar.api.utils.DateUtils;
import org.sonar.check.Cardinality;
import org.sonar.core.persistence.DbSession;
import org.sonar.core.persistence.MyBatis;
import org.sonar.core.rule.RuleDto;
import org.sonar.server.search.Hit;
import org.sonar.server.search.QueryOptions;
import org.sonar.server.search.Results;
import org.sonar.server.tester.ServerTester;

import java.util.Arrays;

import static org.fest.assertions.Assertions.assertThat;

public class RuleMediumTest {

  @ClassRule
  public static ServerTester tester = new ServerTester();

  @After
  public void clear_data_store() {
    tester.clearDataStores();
  }

  @Test
  public void insert_in_db_and_index_in_es() {
    // insert db
    RuleKey ruleKey = RuleKey.of("javascript", "S001");
    RuleDao dao = tester.get(RuleDao.class);
    dao.insert(newRuleDto(ruleKey));

    // verify that rule is persisted in db
    RuleDto persistedDto = dao.selectByKey(ruleKey);
    assertThat(persistedDto).isNotNull();
    assertThat(persistedDto.getId()).isGreaterThanOrEqualTo(0);
    assertThat(persistedDto.getRuleKey()).isEqualTo(ruleKey.rule());
    assertThat(persistedDto.getLanguage()).isEqualTo("js");

    // verify that rule is indexed in es
    RuleIndex index = tester.get(RuleIndex.class);
    index.refresh();

    Hit hit = index.getByKey(ruleKey);
    assertThat(hit).isNotNull();
    assertThat(hit.getFieldAsString(RuleNormalizer.RuleField.REPOSITORY.key())).isEqualTo(ruleKey.repository());
    assertThat(hit.getFieldAsString(RuleNormalizer.RuleField.KEY.key())).isEqualTo(ruleKey.rule());
    assertThat(hit.getFieldAsString(RuleNormalizer.RuleField.LANGUAGE.key())).isEqualTo("js");
    assertThat(hit.getFieldAsString(RuleNormalizer.RuleField.NAME.key())).isEqualTo("Rule S001");
    assertThat(hit.getFieldAsString(RuleNormalizer.RuleField.DESCRIPTION.key())).isEqualTo("Description S001");
    assertThat(hit.getFieldAsString(RuleNormalizer.RuleField.STATUS.key())).isEqualTo(RuleStatus.READY.toString());
  }

  @Test
  public void search_rule_field_allowed(){
    String lang = "java";

    RuleDao dao = tester.get(RuleDao.class);
    dao.insert(newRuleDto(RuleKey.of("javascript", "S001"))
      .setLanguage(lang));

    RuleService service = tester.get(RuleService.class).refresh();

    RuleQuery query = service.newRuleQuery();

    QueryOptions options = new QueryOptions();
    options.getFieldsToReturn().add(RuleNormalizer.RuleField.LANGUAGE.key());

    Results results = service.search(query, options);

    assertThat(results.getTotal()).isEqualTo(1);
    assertThat(results.getHits()).hasSize(1);
    assertThat(Iterables.getFirst(results.getHits(), null).getFieldAsString("key")).isEqualTo("S001");
    assertThat(Iterables.getFirst(results.getHits(), null)
      .getFieldAsString(RuleNormalizer.RuleField.LANGUAGE.key()))
      .isNotNull();
    assertThat(Iterables.getFirst(results.getHits(), null)
      .getFieldAsString(RuleNormalizer.RuleField.LANGUAGE.key()))
      .isEqualTo(lang);
  }

  @Test
  public void search_rule_no_field_selected() {
    String lang = "java";

    RuleDao dao = tester.get(RuleDao.class);
    dao.insert(newRuleDto(RuleKey.of("javascript", "S001"))
      .setLanguage(lang));

    RuleService service = tester.get(RuleService.class).refresh();

    RuleQuery query = service.newRuleQuery();

    QueryOptions options = new QueryOptions();

    Results results = service.search(query, options);

    assertThat(results.getTotal()).isEqualTo(1);
    assertThat(results.getHits()).hasSize(1);
    assertThat(Iterables.getFirst(results.getHits(), null).getFieldAsString("key")).isEqualTo("S001");
    assertThat(Iterables.getFirst(results.getHits(), null)
      .getFieldAsString(RuleNormalizer.RuleField.LANGUAGE.key()))
      .isNull();
  }

  @Test
  public void search_rule_field_not_allowed(){
    RuleDao dao = tester.get(RuleDao.class);
    dao.insert(newRuleDto(RuleKey.of("javascript", "S001"))
      .setConfigKey("I should not see this"));

    RuleService service = tester.get(RuleService.class).refresh();

    RuleQuery query = service.newRuleQuery();
    QueryOptions options = new QueryOptions();
    options.getFieldsToReturn().add(RuleNormalizer.RuleField.INTERNAL_KEY.key());

    Results results = service.search(query, options);

    assertThat(results.getTotal()).isEqualTo(1);
    assertThat(results.getHits()).hasSize(1);
    assertThat(Iterables.getFirst(results.getHits(), null).getFieldAsString("key")).isEqualTo("S001");
    assertThat(Iterables.getFirst(results.getHits(), null)
      .getFieldAsString(RuleNormalizer.RuleField.INTERNAL_KEY.key()))
      .isNull();
  }

  @Test
  public void search_rules_partial_match() throws InterruptedException {
    RuleDao dao = tester.get(RuleDao.class);
    dao.insert(newRuleDto(RuleKey.of("javascript", "S001"))
    .setName("testing the partial match and matching of rule"));

    RuleService service = tester.get(RuleService.class).refresh();

    RuleQuery query = service.newRuleQuery().setQueryText("test");
    Results results = service.search(query, new QueryOptions());

    assertThat(results.getTotal()).isEqualTo(1);
    assertThat(results.getHits()).hasSize(1);
    assertThat(Iterables.getFirst(results.getHits(), null).getFieldAsString("key")).isEqualTo("S001");

  }


  @Test
  public void search_rules_no_filters() throws InterruptedException {
    RuleDao dao = tester.get(RuleDao.class);
    dao.insert(newRuleDto(RuleKey.of("javascript", "S001")));

    RuleService service = tester.get(RuleService.class).refresh();

    RuleQuery query = service.newRuleQuery();
    Results results = service.search(query, new QueryOptions());

    assertThat(results.getTotal()).isEqualTo(1);
    assertThat(results.getHits()).hasSize(1);
    assertThat(Iterables.getFirst(results.getHits(), null).getFieldAsString("key")).isEqualTo("S001");

  }

  @Test
  public void search_rules_by_repositories() throws InterruptedException {
    RuleDao dao = tester.get(RuleDao.class);
    dao.insert(newRuleDto(RuleKey.of("javascript", "S001")));
    dao.insert(newRuleDto(RuleKey.of("java", "S002")));

    RuleService service = tester.get(RuleService.class).refresh();

    RuleQuery query = service.newRuleQuery().setRepositories(Arrays.asList("findbugs", "java"));
    Results results = service.search(query, new QueryOptions());

    assertThat(results.getTotal()).isEqualTo(1);
    assertThat(results.getHits()).hasSize(1);
    assertThat(Iterables.getFirst(results.getHits(), null).getFieldAsString("key")).isEqualTo("S002");
  }

  private RuleDto newRuleDto(RuleKey ruleKey) {
    return new RuleDto()
      .setRuleKey(ruleKey.rule())
      .setRepositoryKey(ruleKey.repository())
      .setName("Rule " + ruleKey.rule())
      .setDescription("Description " + ruleKey.rule())
      .setStatus(RuleStatus.READY.toString())
      .setConfigKey("InternalKey" + ruleKey.rule())
      .setSeverity(Severity.INFO)
      .setCardinality(Cardinality.SINGLE)
      .setLanguage("js")
      .setRemediationFunction("linear")
      .setDefaultRemediationFunction("linear_offset")
      .setRemediationCoefficient("1h")
      .setDefaultRemediationCoefficient("5d")
      .setRemediationOffset("5min")
      .setDefaultRemediationOffset("10h")
      .setEffortToFixDescription(ruleKey.repository() + "." + ruleKey.rule() + ".effortToFix")
      .setCreatedAt(DateUtils.parseDate("2013-12-16"))
      .setUpdatedAt(DateUtils.parseDate("2013-12-17"));
  }
}
