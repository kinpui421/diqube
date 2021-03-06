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
package org.diqube.loader.columnshard;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.diqube.data.TableShard;
import org.diqube.data.colshard.ColumnPageFactory;
import org.diqube.data.colshard.ColumnShardFactory;
import org.diqube.data.colshard.StandardColumnShard;
import org.diqube.loader.LoaderColumnInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;

/**
 * Manages all {@link ColumnShardBuilder}s for loading one {@link TableShard}.
 * 
 * <p>
 * This class is a convenience class for the loaders to use, so the loaders do not need to take care of generics
 * themselves.
 *
 * @author Bastian Gloeckle
 */
public class ColumnShardBuilderManager {
  private static final Logger logger = LoggerFactory.getLogger(ColumnShardBuilderManager.class);

  private volatile Map<String, ColumnShardBuilder<String>> stringBuilders = new ConcurrentHashMap<>();
  private volatile Map<String, ColumnShardBuilder<Long>> longBuilders = new ConcurrentHashMap<>();
  private volatile Map<String, ColumnShardBuilder<Double>> doubleBuilders = new ConcurrentHashMap<>();

  private ColumnShardFactory columnShardFactory;
  private ColumnPageFactory columnPageFactory;
  private LoaderColumnInfo columnInfo;
  private long firstRowIdInShard;
  private AtomicLong maxRow = new AtomicLong(-1L);

  public ColumnShardBuilderManager(ColumnShardFactory columnShardFactory, ColumnPageFactory columnPageFactory,
      LoaderColumnInfo columnInfo, long firstRowIdInShard) {
    this.columnShardFactory = columnShardFactory;
    this.columnPageFactory = columnPageFactory;
    this.columnInfo = columnInfo;
    this.firstRowIdInShard = firstRowIdInShard;
  }

  /**
   * @return Column Names of all columns that received some data with {@link #addValues(String, Object[], long)}.
   */
  public Set<String> getAllColumnsWithValues() {
    Set<String> res = new HashSet<>(stringBuilders.keySet());
    res.addAll(longBuilders.keySet());
    res.addAll(doubleBuilders.keySet());
    return res;
  }

  /**
   * Walks along all rows that have been added for the given column and sets the given default value into those rows
   * that do not have a value set. This is done or all rows up to the maximum row added to this
   * {@link ColumnShardBuilderManager} for any row.
   */
  public void fillEmptyRowsWithValue(String colName, Object value) {
    logger.trace("Filling col {} with fallback value {} up to row {}", colName, value, maxRow.get());
    switch (columnInfo.getFinalColumnType(colName)) {
    case STRING:
      stringBuilders.get(colName).fillEmptyRowsWithValue((String) value, maxRow.get());
      break;
    case LONG:
      longBuilders.get(colName).fillEmptyRowsWithValue((Long) value, maxRow.get());
      break;
    case DOUBLE:
      doubleBuilders.get(colName).fillEmptyRowsWithValue((Double) value, maxRow.get());
      break;
    }
  }

  /**
   * Calls {@link ColumnShardBuilder#addValues(Object[], Long)} for the column accordingly.
   * 
   * @param colName
   * @param values
   * @param firstRowId
   */
  public void addValues(String colName, Object[] values, long firstRowId) {
    switch (columnInfo.getFinalColumnType(colName)) {
    case STRING:
      if (!stringBuilders.containsKey(colName)) {
        synchronized (stringBuilders) {
          if (!stringBuilders.containsKey(colName)) {
            stringBuilders.put(colName,
                new ColumnShardBuilder<String>(columnShardFactory, columnPageFactory, colName, firstRowIdInShard));
          }
        }
      }
      stringBuilders.get(colName).addValues((String[]) values, firstRowId);
      break;
    case LONG:
      if (!longBuilders.containsKey(colName)) {
        synchronized (longBuilders) {
          if (!longBuilders.containsKey(colName)) {
            longBuilders.put(colName,
                new ColumnShardBuilder<Long>(columnShardFactory, columnPageFactory, colName, firstRowIdInShard));
          }
        }
      }
      longBuilders.get(colName).addValues((Long[]) values, firstRowId);
      break;
    case DOUBLE:
      if (!doubleBuilders.containsKey(colName)) {
        synchronized (doubleBuilders) {
          if (!doubleBuilders.containsKey(colName)) {
            doubleBuilders.put(colName,
                new ColumnShardBuilder<Double>(columnShardFactory, columnPageFactory, colName, firstRowIdInShard));
          }
        }
      }
      doubleBuilders.get(colName).addValues((Double[]) values, firstRowId);
      break;
    }
    maxRow.getAndUpdate(oldVal -> Math.max(oldVal, firstRowId + values.length - 1));
  }

  /**
   * Make this manager expect values up to the specified row (including).
   * 
   * When the {@link #buildAndFree(String)} method is called, empty rows of columns are filled with default values.
   * 
   * If this method is called, it is ensured on a call to {@link #buildAndFree(String)} that all rows up to and
   * including the given rowId are filled with default values.
   */
  public void expectToFillDataUpToRow(long rowId) {
    maxRow.getAndUpdate(oldVal -> Math.max(oldVal, rowId));
  }

  /**
   * Executes {@link ColumnShardBuilder#build()} and frees up the memory of the {@link ColumnShardBuilder} after that.
   * 
   * If this method is called after adding values for /all/ columns, the columns returned by this method will all have
   * the same length (= same number of rows). Any empty rows of the column will be filled with default values. This is
   * needed to make the column compatible e.g. with ColumnAggregationStep which resolves values before checking the
   * length of repeated fields.
   * 
   * If a caller wants to make sure that all columns contain values up to a specific rowId, call
   * {@link #expectToFillDataUpToRow(long)} before calling this method.
   */
  public StandardColumnShard buildAndFree(String colName) {
    ColumnShardBuilder<?> colBuilder = null;
    switch (columnInfo.getFinalColumnType(colName)) {
    case STRING:
      fillEmptyRowsWithValue(colName, LoaderColumnInfo.DEFAULT_STRING);
      colBuilder = stringBuilders.get(colName);
      stringBuilders.remove(colName);
      break;
    case LONG:
      fillEmptyRowsWithValue(colName, LoaderColumnInfo.DEFAULT_LONG);
      colBuilder = longBuilders.get(colName);
      longBuilders.remove(colName);
      break;
    case DOUBLE:
      fillEmptyRowsWithValue(colName, LoaderColumnInfo.DEFAULT_DOUBLE);
      colBuilder = doubleBuilders.get(colName);
      doubleBuilders.remove(colName);
      break;
    }

    if (colBuilder == null)
      return null;
    return colBuilder.build();
  }

  /**
   * Returns an approximation of the memory consumption by all ColumnShardBuilders.
   * 
   * @see ColumnShardBuilder#calculateApproximateMemoryConsumption().
   */
  public long calculateApproximateSizeInBytes() {
    long res = 0;
    for (ColumnShardBuilder<?> builder : Iterables.concat(stringBuilders.values(), longBuilders.values(),
        doubleBuilders.values())) {
      res += builder.calculateApproximateSizeInBytes();
    }
    return res;
  }
}
