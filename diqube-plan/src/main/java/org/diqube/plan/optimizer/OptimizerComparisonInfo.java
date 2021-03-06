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
package org.diqube.plan.optimizer;

import org.diqube.plan.request.ComparisonRequest;
import org.diqube.plan.request.ComparisonRequest.Operator;

/**
 * Additional information on one {@link ComparisonRequest} which is needed during optimization.
 *
 * @author Bastian Gloeckle
 */
public class OptimizerComparisonInfo {
  private boolean transitivelyContainsOnlyEquals;

  private ComparisonRequest parent;

  private int optimizerId;

  public OptimizerComparisonInfo(int optimizerId) {
    this.optimizerId = optimizerId;
  }

  /**
   * @return true if all leaf comparison requests that are transitive children of this one have the operator
   *         {@link Operator#EQ}.
   */
  public boolean isTransitivelyContainsOnlyEquals() {
    return transitivelyContainsOnlyEquals;
  }

  public void setTransitivelyContainsOnlyEquals(boolean transitievlyContainsOnlyEquals) {
    this.transitivelyContainsOnlyEquals = transitievlyContainsOnlyEquals;
  }

  /**
   * @return The parent {@link ComparisonRequest} of this one or <code>null</code> if there is none.
   */
  public ComparisonRequest getParent() {
    return parent;
  }

  public void setParent(ComparisonRequest parent) {
    this.parent = parent;
  }

  /**
   * @return A temporary unique int ID for this {@link ComparisonRequest}.
   */
  public int getOptimizerId() {
    return optimizerId;
  }
}
