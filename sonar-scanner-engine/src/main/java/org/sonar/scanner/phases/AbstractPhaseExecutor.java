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
package org.sonar.scanner.phases;

import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.fs.internal.DefaultInputModule;
import org.sonar.api.batch.fs.internal.InputModuleHierarchy;
import org.sonar.scanner.events.BatchStepEvent;
import org.sonar.scanner.events.EventBus;
import org.sonar.scanner.issue.ignore.scanner.IssueExclusionsLoader;
import org.sonar.scanner.rule.QProfileVerifier;
import org.sonar.scanner.scan.filesystem.DefaultModuleFileSystem;
import org.sonar.scanner.scan.filesystem.FileSystemLogger;

public abstract class AbstractPhaseExecutor {

  private final EventBus eventBus;
  private final PostJobsExecutor postJobsExecutor;
  private final InitializersExecutor initializersExecutor;
  private final SensorsExecutor sensorsExecutor;
  private final SensorContext sensorContext;
  private final FileSystemLogger fsLogger;
  private final DefaultModuleFileSystem fs;
  private final QProfileVerifier profileVerifier;
  private final IssueExclusionsLoader issueExclusionsLoader;
  private final InputModuleHierarchy moduleHierarchy;

  public AbstractPhaseExecutor(InitializersExecutor initializersExecutor, PostJobsExecutor postJobsExecutor, SensorsExecutor sensorsExecutor,
    SensorContext sensorContext, InputModuleHierarchy moduleHierarchy,
    EventBus eventBus, FileSystemLogger fsLogger, DefaultModuleFileSystem fs, QProfileVerifier profileVerifier,
    IssueExclusionsLoader issueExclusionsLoader) {
    this.postJobsExecutor = postJobsExecutor;
    this.initializersExecutor = initializersExecutor;
    this.sensorsExecutor = sensorsExecutor;
    this.sensorContext = sensorContext;
    this.eventBus = eventBus;
    this.fsLogger = fsLogger;
    this.fs = fs;
    this.profileVerifier = profileVerifier;
    this.issueExclusionsLoader = issueExclusionsLoader;
    this.moduleHierarchy = moduleHierarchy;
  }

  /**
   * Executed on each module
   */
  public final void execute(DefaultInputModule module) {
    eventBus.fireEvent(new ProjectAnalysisEvent(module, moduleHierarchy, true));

    executeInitializersPhase();

    // Index and lock the filesystem
    indexFs();

    // Log detected languages and their profiles after FS is indexed and languages detected
    profileVerifier.execute();

    // Initialize issue exclusions
    initIssueExclusions();

    sensorsExecutor.execute(sensorContext);

    if (module.definition().getParent() == null) {
      executeOnRoot();
      postJobsExecutor.execute(sensorContext);
    }
    cleanMemory();
    eventBus.fireEvent(new ProjectAnalysisEvent(module, moduleHierarchy, false));
  }

  protected abstract void executeOnRoot();

  private void initIssueExclusions() {
    if (issueExclusionsLoader.shouldExecute()) {
      String stepName = "Init issue exclusions";
      eventBus.fireEvent(new BatchStepEvent(stepName, true));
      issueExclusionsLoader.execute();
      eventBus.fireEvent(new BatchStepEvent(stepName, false));
    }
  }

  private void indexFs() {
    String stepName = "Index filesystem";
    eventBus.fireEvent(new BatchStepEvent(stepName, true));
    fs.index();
    eventBus.fireEvent(new BatchStepEvent(stepName, false));
  }

  private void executeInitializersPhase() {
    initializersExecutor.execute();
    fsLogger.log();
  }

  private void cleanMemory() {
    String cleanMemory = "Clean memory";
    eventBus.fireEvent(new BatchStepEvent(cleanMemory, true));
    //index.clear();
    eventBus.fireEvent(new BatchStepEvent(cleanMemory, false));
  }
}
