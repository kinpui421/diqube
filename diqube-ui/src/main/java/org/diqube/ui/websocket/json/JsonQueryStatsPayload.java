/**
 * diqube: Distributed Query Base.
 *
 * Copyright (C) 2015 Bastian Gloeckle
 *
 * This file is part of diqube.
 *
 * diqube is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.diqube.ui.websocket.json;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.diqube.remote.query.thrift.RQueryStatistics;
import org.diqube.remote.query.thrift.RQueryStatisticsDetails;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A simple {@link JsonPayload} containing information about results of a query.
 *
 * @author Bastian Gloeckle
 */
public class JsonQueryStatsPayload implements JsonPayload {
  public static final String PAYLOAD_TYPE = "stats";

  @JsonProperty
  public List<String> nodeNames = new ArrayList<>();

  @JsonProperty
  private List<Long> startedUntilDoneMs = new ArrayList<>();

  @JsonProperty
  private List<Integer> numberOfThreads = new ArrayList<>();

  @JsonProperty
  private List<Integer> numberOfTemporaryColumnsCreated = new ArrayList<>();

  @JsonProperty
  private Map<String, List<Long>> stepsActiveMs = new HashMap<>();

  @JsonProperty
  private Map<String, List<Integer>> numberOfPageAccesses = new HashMap<>();

  @JsonProperty
  private Map<String, List<Integer>> numberOfTemporaryPageAccesses = new HashMap<>();

  @JsonProperty
  private List<Integer> numberOfPages = new ArrayList<>();

  @JsonProperty
  private List<Integer> numberOfTemporaryPages = new ArrayList<>();

  @JsonProperty
  private Map<String, List<Integer>> numberOfTemporaryVersionsPerColName = new HashMap<>();

  public void loadFromQueryStatRes(RQueryStatistics stats) {
    List<RQueryStatisticsDetails> allDetails = new ArrayList<>(Arrays.asList(stats.getMaster()));
    allDetails.addAll(stats.getRemotes());

    for (RQueryStatisticsDetails detail : allDetails) {
      for (String stepName : detail.getStepsActiveMs().keySet())
        stepsActiveMs.put(stepName, new ArrayList<>());
      for (String pageName : detail.getNumberOfPageAccesses().keySet())
        numberOfPageAccesses.put(pageName, new ArrayList<>());
      for (String tempPageName : detail.getNumberOfTemporaryPageAccesses().keySet())
        numberOfTemporaryPageAccesses.put(tempPageName, new ArrayList<>());
      for (String tempColName : detail.getNumberOfTemporaryVersionsPerColName().keySet())
        numberOfTemporaryVersionsPerColName.put(tempColName, new ArrayList<>());
    }

    boolean first = true;
    for (RQueryStatisticsDetails detail : allDetails) {
      if (first)
        nodeNames.add(detail.getNode() + " (master)");
      else
        nodeNames.add(detail.getNode());
      first = false;
      startedUntilDoneMs.add(detail.getStartedUntilDoneMs());
      numberOfThreads.add(detail.getNumberOfThreads());
      numberOfTemporaryColumnsCreated.add(detail.getNumberOfTemporaryColumnsCreated());
      numberOfPages.add(detail.getNumberOfPages());
      numberOfTemporaryPages.add(detail.getNumberOfTemporaryPages());

      for (String stepName : stepsActiveMs.keySet())
        stepsActiveMs.get(stepName).add(detail.getStepsActiveMs().get(stepName)); // could be null.
      for (String pageName : numberOfPageAccesses.keySet())
        numberOfPageAccesses.get(pageName).add(detail.getNumberOfPageAccesses().get(pageName)); // could be null.
      for (String tempPageName : numberOfTemporaryPageAccesses.keySet())
        numberOfTemporaryPageAccesses.get(tempPageName)
            .add(detail.getNumberOfTemporaryPageAccesses().get(tempPageName)); // could be null.
      for (String tempColName : numberOfTemporaryVersionsPerColName.keySet())
        numberOfTemporaryVersionsPerColName.get(tempColName)
            .add(detail.getNumberOfTemporaryVersionsPerColName().get(tempColName)); // could be null.
    }
  }

  @Override
  public String getPayloadType() {
    return PAYLOAD_TYPE;
  }
}
