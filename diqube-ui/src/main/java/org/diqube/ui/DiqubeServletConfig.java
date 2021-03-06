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
package org.diqube.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.servlet.ServletContext;

import org.diqube.context.AutoInstatiate;
import org.diqube.util.Pair;

/**
 * Configuration for the diqube UI.
 * 
 * Loads the config from servlet context init params or chooses defaults if it finds none. The init params can be
 * overwritten in the container somewhere, see container specific documentation.
 *
 * @author Bastian Gloeckle
 */
@AutoInstatiate
public class DiqubeServletConfig {

  public static final String INIT_PARAM_CLUSTER = "diqube.cluster";
  public static final String INIT_PARAM_CLUSTER_RESPONSE = "diqube.clusterresponse";
  public static final String DEFAULT_CLUSTER = "localhost:5101";
  public static final String DEFAULT_CLUSTER_RESPONSE = "http://localhost:8080";

  private List<Pair<String, Short>> clusterServers;

  private String clusterResponseAddr;

  /* package */ void initialize(ServletContext ctx) {
    String clusterLocation = ctx.getInitParameter(INIT_PARAM_CLUSTER);
    if (clusterLocation == null)
      clusterLocation = DEFAULT_CLUSTER;

    clusterServers = new ArrayList<>();
    for (String hostPortStr : clusterLocation.split(",")) {
      String[] split = hostPortStr.split(":");
      clusterServers.add(new Pair<>(split[0], Short.valueOf(split[1])));
    }
    clusterServers = Collections.unmodifiableList(clusterServers);

    String clusterResponse = ctx.getInitParameter(INIT_PARAM_CLUSTER_RESPONSE);
    if (clusterResponse == null)
      clusterResponse = DEFAULT_CLUSTER_RESPONSE;

    clusterResponseAddr = clusterResponse + ctx.getContextPath();
  }

  /**
   * @return List of Pair of hostname, port of the seed cluster nodes.
   */
  public List<Pair<String, Short>> getClusterServers() {
    return clusterServers;
  }

  /**
   * @return The address of this UI that cluster nodes can use to send responses to after we issued a query for example.
   *         Example value: http://localhost:8080/diqube-ui. It does not contain a trailing / and contains the whole URL
   *         up until the root of this web application (no servlet-specific part!).
   */
  public String getClusterResponseAddr() {
    return clusterResponseAddr;
  }
}
