package org.sonar.server.component.index;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ComponentIndexerCamelTest {

  @Test
  public void test() {
    assertCamelized("J", "J");
  }

  @Test
  public void test2() {
    assertCamelized("Ja", "J Ja");
  }

  @Test
  public void test3() {
    assertCamelized("Java", "J Ja Jav Java");
  }

  @Test
  public void test4() {
    assertCamelized("JavaFi", "J Ja Jav Java JF JaF JavF JavaF JFi JaFi JavFi JavaFi");
  }

  @Test
  public void test5() {
    assertCamelized("JaFiN", "J Ja JF JaF JFi JaFi JFN JaFN JFiN JaFN JaFiN");
  }

  @Test
  public void test6() {
    assertCamelized("JaFiNa", "J Ja JF JaF JFi JaFi JFN JaFN JFiN JaFN JaFiN JFNa JaFNa JFiNa JaFiNa");
  }

  @Test
  public void test7() {
    assertCamelized("JaFiNaX", "J Ja JF JaF JFi JaFi JFN JaFN JFiN JaFN JaFiN JFNa JaFNa JFiNa JaFiNa JFNX JaFNX JFiNX JaFNX JaFiNX JFNaX JaFNaX JFiNaX JaFiNaX");
  }

  @Test
  public void testX() {
    System.out.println(ComponentIndexer.camelize("SonarLintExtraArguments"));
  }

  @Test
  public void test8() {
    assertCamelMatch("MapBasedRawMeasureRepository", "MBRMR", "MapBRMR", "MBRaw");
  }

  @Test
  public void test_start_with_lowercase() {
    assertCamelized("lowercaseStart", "");
  }

  private void assertCamelized(String input, String output) {
    assertThat(ComponentIndexer.camelize(input).split(" ")).containsOnly(output.split(" "));
  }

  private void assertCamelMatch(String input, String... matches) {
    assertThat(ComponentIndexer.camelize(input).split(" ")).contains(matches);
  }

}
