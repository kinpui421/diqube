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
package org.diqube.listeners;

import org.diqube.context.AutoInstatiate;

/**
 * Listener for information if the local thrift server is serving or not.
 * 
 * All implementing classes need to have a bean in the context (=have the {@link AutoInstatiate} annotation).
 *
 * @author Bastian Gloeckle
 */
public interface ServingListener {
  /**
   * The local thrift server started serving, which means that our process is now reachable by other machines over the
   * network.
   */
  public void localServerStartedServing();

  /**
   * The local thrift server stopped serving, which means that our process is not reachable by anyone anymore.
   */
  public void localServerStoppedServing();
}
