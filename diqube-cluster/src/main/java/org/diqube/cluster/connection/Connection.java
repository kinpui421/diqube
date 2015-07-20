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
package org.diqube.cluster.connection;

import java.io.Closeable;
import java.io.IOException;
import java.util.UUID;

import org.apache.thrift.transport.TTransport;
import org.diqube.remote.base.thrift.RNodeAddress;

/**
 * A connection to a server.
 * 
 * This is {@link Closeable}: When closed, {@link ConnectionPool#releaseConnection(Connection)} will be called.
 *
 * @author Bastian Gloeckle
 */
public class Connection<T> implements Closeable {
  private T service;
  private TTransport transport;
  private RNodeAddress address;
  private ConnectionPool parentPool;
  private Class<T> serviceClientClass;
  private UUID executionUuid = null;

  /* package */ Connection(ConnectionPool parentPool, Class<T> serviceClientClass, T service, TTransport transport,
      RNodeAddress address) {
    this.parentPool = parentPool;
    this.serviceClientClass = serviceClientClass;
    this.service = service;
    this.transport = transport;
    this.address = address;
  }

  /**
   * @return Easy to use service bean - each method call on the returned object will actually trigger a remote call.
   */
  public T getService() {
    return service;
  }

  /* package */TTransport getTransport() {
    return transport;
  }

  /* package */ RNodeAddress getAddress() {
    return address;
  }

  /* package */ Class<T> getServiceClientClass() {
    return serviceClientClass;
  }

  /**
   * @return The executionUuid this connection was working for. Can be <code>null</code>.
   */
  /* package */UUID getExecutionUuid() {
    return executionUuid;
  }

  /**
   * For {@link ConnectionPool} only: Set the executionUuid this connection is working either when this connection is
   * being reserved or is being released.
   */
  /* package */void setExecutionUuid(UUID executionUuid) {
    this.executionUuid = executionUuid;
  }

  @Override
  public void close() throws IOException {
    parentPool.releaseConnection(this);
  }
}