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
package org.diqube.server.config;

import org.diqube.server.NewDataWatcher;

/**
 * Configuration keys which can be used to resolve configuration values.
 * 
 * <p>
 * It's easiest to use these constants with the {@link Config} annotation.
 *
 * @author Bastian Gloeckle
 */
public class ConfigKey {
  /**
   * The main TCP port the server should use. The Thrift server will bind to this.
   */
  public static final String PORT = "port";

  /**
   * The number of selector threads used by the Thrift server.
   * 
   * <p>
   * The Thrift server has a single accept thread which will accept new connections. It then hands over those new
   * connections to a thread pool of selector threads which will read and write the connection. The actual processing of
   * the request will not happen within these selector threads, as each connection could be used to issue multiple
   * computations simultaneously. The number of selectorThreads will limit the number of concurrent connections the
   * server can read from and write to simultaneously.
   */
  public static final String SELECTOR_THREADS = "selectorThreads";

  /**
   * The directory which should be watched for new data to be loaded. See {@link NewDataWatcher} for more details.
   * 
   * <p>
   * This can be a relative path which is then interpreted as being relative to the current working directory.
   */
  public static final String DATA_DIR = "dataDir";
}
