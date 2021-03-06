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
package org.diqube.data.colshard;

import org.diqube.data.lng.array.CompressedLongArray;
import org.diqube.data.lng.dict.LongDictionary;
import org.diqube.data.serialize.DataSerializable;
import org.diqube.data.serialize.DataSerialization;
import org.diqube.data.serialize.DeserializationException;
import org.diqube.data.serialize.SerializationException;
import org.diqube.data.serialize.thrift.v1.SColumnPage;
import org.diqube.data.serialize.thrift.v1.SLongCompressedArray;
import org.diqube.data.serialize.thrift.v1.SLongDictionary;

/**
 * {@link ColumnPage} holds the data of a specific set of consecutive rows of one {@link ColumnShard}.
 * 
 * <p>
 * A {@link ColumnPage} contains a 'Column Page Dictionary' which acts similar to the 'Column Dictionary' which is
 * defined in {@link ColumnShard}: It maps the Column Value IDs of the values stored in this {@link ColumnPage} to
 * 'Column Page Value IDs'. These 'Column Page Value IDs' are then available in an array, where there is one entry for
 * each row stored by this {@link ColumnPage}. These entries in the column page value array can then be mapped to column
 * value IDs using the Column Page Dictionary and can then in turn be mapped to uncompressed values using the Column
 * Dictionary.
 *
 * @author Bastian Gloeckle
 */
@DataSerializable(thriftClass = SColumnPage.class)
public class ColumnPage implements DataSerialization<SColumnPage> {
  private LongDictionary<?> columnPageDict;

  private CompressedLongArray<?> values;

  private long firstRowId;

  private String name;

  /** for deserialization */
  public ColumnPage() {

  }

  /* package */ ColumnPage(LongDictionary<?> columnPageDict, CompressedLongArray<?> values, long firstRowId,
      String name) {
    this.columnPageDict = columnPageDict;
    this.values = values;
    this.firstRowId = firstRowId;
    this.name = name;
  }

  public LongDictionary<?> getColumnPageDict() {
    return columnPageDict;
  }

  public CompressedLongArray<?> getValues() {
    return values;
  }

  /**
   * @return The Row ID of the row represented by the first entry in {@link #getValues()}.
   */
  public long getFirstRowId() {
    return firstRowId;
  }

  /**
   * @return Number of rows available in this {@link ColumnPage}.
   */
  public int size() {
    return values.size();
  }

  public String getName() {
    return name;
  }

  @Override
  public void serialize(DataSerializationHelper mgr, SColumnPage target) throws SerializationException {
    target.setName(name);
    target.setFirstRowId(firstRowId);
    target.setPageDict(mgr.serializeChild(SLongDictionary.class, columnPageDict));
    target.setValues(mgr.serializeChild(SLongCompressedArray.class, values));
  }

  @SuppressWarnings("unchecked")
  @Override
  public void deserialize(DataSerializationHelper mgr, SColumnPage source) throws DeserializationException {
    name = source.getName();
    firstRowId = source.getFirstRowId();
    columnPageDict = mgr.deserializeChild(LongDictionary.class, source.getPageDict());
    values = mgr.deserializeChild(CompressedLongArray.class, source.getValues());
  }

  /* package */void setFirstRowId(long firstRowId) {
    this.firstRowId = firstRowId;
  }

  /**
   * @return An approximate number of bytes taken up by this {@link ColumnPage}. Note that this is only an
   *         approximation!
   */
  public long calculateApproximateSizeInBytes() {
    return 16 + // object header of this.
        columnPageDict.calculateApproximateSizeInBytes() + values.calculateApproximateSizeInBytes();
  }
}
