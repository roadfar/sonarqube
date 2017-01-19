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
package org.sonar.server.qualityprofile.ws;

import javax.annotation.Nullable;
import org.sonar.db.DbSession;
import org.sonar.server.component.ComponentFinder;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.qualityprofile.QProfile;
import org.sonar.server.qualityprofile.QProfileLookup;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang.StringUtils.isEmpty;

public class ProjectAssociationFinder {

  private static final String BAD_PROFILE_PARAMETERS_ERROR = "Either profileKey or profileName + language must be set";
  private static final String BAD_PROJECT_PARAMETERS_ERROR = "Either projectKey or projectUuid must be set";

  private final QProfileLookup profileLookup;
  private final ComponentFinder componentFinder;

  public ProjectAssociationFinder(QProfileLookup profileLookup, ComponentFinder componentFinder) {
    this.profileLookup = profileLookup;
    this.componentFinder = componentFinder;
  }

  public String getProfileKey(DbSession dbSession, @Nullable String language, @Nullable String profileName, @Nullable String profileKey) {
    checkArgument((!isEmpty(language) && !isEmpty(profileName)) ^ !isEmpty(profileKey), BAD_PROFILE_PARAMETERS_ERROR);
    return profileKey == null ? getProfileKeyFromLanguageAndName(dbSession, language, profileName) : profileKey;
  }

  public String getProjectUuid(DbSession dbSession, @Nullable String projectKey, @Nullable String projectUuid) {
    checkArgument(!isEmpty(projectKey) ^ !isEmpty(projectUuid), BAD_PROJECT_PARAMETERS_ERROR);
    return projectUuid == null ? componentFinder.getByKey(dbSession, projectKey).uuid() : projectUuid;
  }

  private String getProfileKeyFromLanguageAndName(DbSession dbSession, String language, String profileName) {
    QProfile profile = profileLookup.profile(profileName, language, dbSession);
    if (profile == null) {
      throw new NotFoundException(String.format("Unable to find a profile for language '%s' with name '%s'", language, profileName));
    }
    return profile.key();
  }

}
