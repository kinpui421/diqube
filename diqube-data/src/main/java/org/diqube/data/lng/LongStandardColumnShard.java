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
package org.diqube.data.lng;

import java.util.NavigableMap;

import org.diqube.data.ColumnType;
import org.diqube.data.colshard.AbstractStandardColumnShard;
import org.diqube.data.colshard.ColumnPage;
import org.diqube.data.colshard.StandardColumnShard;
import org.diqube.data.lng.dict.LongDictionary;

/**
 * A {@link StandardColumnShard} of a column containing long values.
 *
 * @author Bastian Gloeckle
 */
public class LongStandardColumnShard extends AbstractStandardColumnShard implements LongColumnShard {

  /** for deserialization */
  public LongStandardColumnShard() {
    super();
  }

  LongStandardColumnShard(String name, NavigableMap<Long, ColumnPage> pages, LongDictionary<?> columnDictionary) {
    super(ColumnType.LONG, name, pages, columnDictionary);
  }

  @Override
  public LongDictionary<?> getColumnShardDictionary() {
    return (LongDictionary<?>) super.getColumnShardDictionary();
  }
}
