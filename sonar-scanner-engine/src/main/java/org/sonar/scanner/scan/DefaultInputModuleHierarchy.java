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
package org.sonar.scanner.scan;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.CheckForNull;

import org.sonar.api.Startable;
import org.sonar.api.batch.bootstrap.ProjectDefinition;
import org.sonar.api.batch.fs.InputModule;
import org.sonar.api.batch.fs.internal.DefaultInputModule;
import org.sonar.api.batch.fs.internal.InputModuleHierarchy;
import org.sonar.api.scan.filesystem.PathResolver;
import org.sonar.scanner.scan.filesystem.BatchIdGenerator;
import org.sonar.scanner.util.TreeNode;

public class DefaultInputModuleHierarchy implements InputModuleHierarchy, Startable {
  private final PathResolver pathResolver = new PathResolver();
  private final ImmutableProjectReactor projectReactor;
  private final DefaultComponentTree componentTree;
  private final BatchIdGenerator batchIdGenerator;

  private TreeNode<DefaultInputModule> root;
  private Map<DefaultInputModule, TreeNode<DefaultInputModule>> index;

  public DefaultInputModuleHierarchy(ImmutableProjectReactor projectReactor, DefaultComponentTree componentTree, BatchIdGenerator batchIdGenerator) {
    this.projectReactor = projectReactor;
    this.componentTree = componentTree;
    this.batchIdGenerator = batchIdGenerator;
  }

  @Override
  public void start() {
    doStart(projectReactor.getRoot());
  }

  void doStart(ProjectDefinition rootProjectDefinition) {
    index = new HashMap<>();
    DefaultInputModule rootModule = new DefaultInputModule(rootProjectDefinition, batchIdGenerator.get());
    root = new TreeNode<>(rootModule);
    index.put(rootModule, root);

    createChildren(rootProjectDefinition, root);
  }

  private void createChildren(ProjectDefinition parentDef, TreeNode<DefaultInputModule> parent) {
    for (ProjectDefinition def : parentDef.getSubProjects()) {
      DefaultInputModule childModule = new DefaultInputModule(def, batchIdGenerator.get());
      TreeNode<DefaultInputModule> childNode = new TreeNode<>(childModule);
      childNode.setParent(parent.value());
      parent.addChild(childNode.value());
      index.put(childModule, childNode);
      componentTree.index(childModule, parent.value());
      createChildren(def, childNode);
    }
  }

  @Override
  public DefaultInputModule root() {
    return root.value();
  }

  @Override
  public Collection<DefaultInputModule> children(InputModule component) {
    TreeNode<DefaultInputModule> node = index.get(component);
    if (node == null) {
      return Collections.emptyList();
    }
    return node.children();
  }

  @Override
  public DefaultInputModule parent(InputModule component) {
    TreeNode<DefaultInputModule> node = index.get(component);
    if (node == null) {
      return null;
    }

    return node.parent();
  }

  @Override
  public boolean isRoot(InputModule module) {
    return root.value().equals(module);
  }

  @Override
  @CheckForNull
  public String relativePath(InputModule module) {
    DefaultInputModule parent = parent(module);
    if (parent == null) {
      return null;
    }
    DefaultInputModule inputModule = (DefaultInputModule) module;

    ProjectDefinition parentDefinition = parent.definition();
    Path parentBaseDir = parentDefinition.getBaseDir().toPath();
    ProjectDefinition moduleDefinition = inputModule.definition();
    Path moduleBaseDir = moduleDefinition.getBaseDir().toPath();

    return pathResolver.relativePath(parentBaseDir, moduleBaseDir);
  }

  @Override
  public void stop() {
    // nothing to do
  }
}
