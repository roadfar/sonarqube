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
package org.sonar.scanner.util;

import java.util.LinkedList;
import java.util.List;

import javax.annotation.CheckForNull;

public class TreeNode<T> {
  private T t;
  private T parent;
  private List<T> children = new LinkedList<>();

  public TreeNode(T t) {
    this.t = t;
  }

  public T value() {
    return t;
  }

  @CheckForNull
  public T parent() {
    return parent;
  }

  public List<T> children() {
    return children;
  }

  public void addChild(T child) {
    this.children.add(child);
  }

  public void setParent(T parent) {
    this.parent = parent;
  }
}
