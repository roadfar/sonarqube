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

import com.google.common.collect.Lists;
import org.sonar.api.batch.ScannerSide;
import org.sonar.api.batch.Sensor;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.fs.internal.DefaultInputModule;
import org.sonar.api.batch.fs.internal.InputModuleHierarchy;
import org.sonar.api.resources.Project;
import org.sonar.scanner.bootstrap.ScannerExtensionDictionnary;
import org.sonar.scanner.events.EventBus;
import java.util.Collection;

@ScannerSide
public class SensorsExecutor {
  private final EventBus eventBus;
  private final DefaultInputModule module;
  private final ScannerExtensionDictionnary selector;
  private final InputModuleHierarchy moduleHierarchy;

  public SensorsExecutor(InputModuleHierarchy moduleHierarchy, ScannerExtensionDictionnary selector, DefaultInputModule module, EventBus eventBus) {
    this.moduleHierarchy = moduleHierarchy;
    this.selector = selector;
    this.eventBus = eventBus;
    this.module = module;
  }

  public void execute(SensorContext context) {
    Collection<Sensor> sensors = selector.select(Sensor.class, module, true, null);
    eventBus.fireEvent(new SensorsPhaseEvent(Lists.newArrayList(sensors), true));

    for (Sensor sensor : sensors) {
      executeSensor(context, sensor);
    }

    eventBus.fireEvent(new SensorsPhaseEvent(Lists.newArrayList(sensors), false));
  }

  private void executeSensor(SensorContext context, Sensor sensor) {
    eventBus.fireEvent(new SensorExecutionEvent(sensor, true));
    sensor.analyse(new Project(module, moduleHierarchy), context);
    eventBus.fireEvent(new SensorExecutionEvent(sensor, false));
  }
}
