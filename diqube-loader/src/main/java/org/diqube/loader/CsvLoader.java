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
package org.diqube.loader;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.inject.Inject;

import org.diqube.context.AutoInstatiate;
import org.diqube.data.TableFactory;
import org.diqube.data.TableShard;
import org.diqube.data.colshard.StandardColumnShard;
import org.diqube.loader.columnshard.ColumnShardBuilderFactory;
import org.diqube.loader.columnshard.ColumnShardBuilderManager;
import org.diqube.loader.util.ParallelLoadAndTransposeHelper;
import org.diqube.threads.ExecutorManager;
import org.diqube.util.BigByteBuffer;
import org.diqube.util.HashingBatchCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opencsv.CSVParser;

/**
 * Simple {@link Loader} that loads CSV files.
 * 
 * <p>
 * This loader does not support hierarchical data.
 * 
 * <p>
 * This loader will return only one TableShard for a whole CSV input file.
 * 
 * @author Bastian Gloeckle
 */
@AutoInstatiate
public class CsvLoader implements Loader {

  private static final Logger logger = LoggerFactory.getLogger(CsvLoader.class);

  /**
   * The rows of the CSV are parsed and loaded into memory in a batched format. Each batch/buffer contains approx. this
   * amount of entries.
   */
  private static final int COLUMN_BUFFER_SIZE = 1_000;

  @Inject
  private ColumnShardBuilderFactory columnShardBuilderManagerFactory;

  @Inject
  private TableFactory tableFactory;

  @Inject
  private ExecutorManager executorManager;

  @Override
  public Collection<TableShard> load(long firstRowId, String filename, String tableName, LoaderColumnInfo columnInfo)
      throws LoadException {
    ColumnShardBuilderManager columnManager;

    logger.info("Reading data for new table '{}' from '{}'.", new Object[] { tableName, filename });

    try (RandomAccessFile f = new RandomAccessFile(filename, "r")) {
      BigByteBuffer buf = new BigByteBuffer(f.getChannel(), MapMode.READ_ONLY, b -> b.load());

      columnManager = readColumnData(firstRowId, buf, tableName, columnInfo);

      // close file as soon as possible and free the ByteBuffer.
      buf = null;
    } catch (IOException e) {
      throw new LoadException("Could not load " + filename, e);
    }

    return createTableShard(columnManager, tableName);
  }

  @Override
  public Collection<TableShard> load(long firstRowId, BigByteBuffer csvBuffer, String tableName,
      LoaderColumnInfo columnInfo) throws LoadException {
    ColumnShardBuilderManager columnManager = readColumnData(firstRowId, csvBuffer, tableName, columnInfo);
    return createTableShard(columnManager, tableName);
  }

  /**
   * Reads all data from the CSV that is provided in a {@link ByteBuffer} and returns a
   * {@link ColumnShardBuilderManager} that is ready for building the columns.
   * 
   * @param firstRowId
   *          The first rowId to be used.
   * @param buf
   *          The input buffer, containing CSV data.
   * @param tableName
   *          The name of the resulting table.
   * @param columnInfo
   *          Information about each column that this CSV contains.
   * 
   * @return A {@link ColumnShardBuilderManager} that has all the data of all the columns of the CSV already added to
   *         it. It is ready for building the columns using {@link ColumnShardBuilderManager#buildAndFree(String)}.
   * @throws LoadException
   *           If something cannot be loaded.
   */
  private ColumnShardBuilderManager readColumnData(long firstRowId, BigByteBuffer buf, String tableName,
      LoaderColumnInfo columnInfo) throws LoadException {
    String[] header;
    ColumnShardBuilderManager columnBuilderManager =
        columnShardBuilderManagerFactory.createColumnShardBuilderManager(columnInfo, firstRowId);

    // Read CSV Header to learn of the columns that we need to import.
    int numChars = 0;
    while (numChars < buf.size() && buf.get(numChars) != '\n')
      numChars++;
    if (numChars >= buf.size())
      throw new LoadException("Could not identify CSV header.");

    byte[] b = new byte[numChars];
    buf.get(0, b, 0, numChars);
    try {
      header = new CSVParser().parseLine(new String(b));
    } catch (IOException e) {
      throw new LoadException("Could not parse CSV header.", e);
    }

    // TODO #16 do auto-recognition of data types of columns (or make it explicitly "enable/disable" in .control file).

    // TODO #17 validate column names

    logger.info("New table '{}' contains {} columns, reading columnar data.",
        new Object[] { tableName, header.length });

    // Initialize the input stream.
    Stream<String> stream = StreamSupport.stream(new LineSpliterator(buf, numChars + 1, buf.size(), numChars, 1), true);

    ParallelLoadAndTransposeHelper transposer =
        new ParallelLoadAndTransposeHelper(executorManager, columnInfo, columnBuilderManager, header, tableName);

    transposer.transpose(firstRowId, new Consumer<ConcurrentLinkedDeque<String[][]>>() {
      @Override
      public void accept(ConcurrentLinkedDeque<String[][]> rowWiseTarget) {
        // Start parsing CSV lines in parallel, bucketing the results into the rowWiseTarget deque from where they
        // will be fetched by the transposer.
        // Arrays are non-colliding, so using HashingBatchCollector is fine.
        stream.parallel().map(CsvLoader::parseCsvLine)
            .collect(new HashingBatchCollector<String[]>( //
                COLUMN_BUFFER_SIZE, // Try to make buckets of this size
                (len) -> new String[len][], // Factory implementation on how to create a new result object.
                a -> rowWiseTarget.add(a)) // When there is a new result, put it into csvLines.
        );
      }
    });

    return columnBuilderManager;
  }

  /**
   * Takes a fully filled {@link ColumnShardBuilderManager} and creates a {@link TableShard} out of it.
   * 
   * @param columnManager
   *          The {@link ColumnShardBuilderManager} that has the data of all columns to be created already filled in.
   * @param tableName
   *          Name of the result table.
   * @return The created {@link TableShard}.
   */
  private Collection<TableShard> createTableShard(ColumnShardBuilderManager columnManager, String tableName) {
    logger.info("Read data for new table shard for table {}. Compressing and creating final representation...",
        tableName);

    // Build the columns.
    List<StandardColumnShard> columns = new LinkedList<>();
    for (String colName : columnManager.getAllColumnsWithValues()) {
      StandardColumnShard columnShard = columnManager.buildAndFree(colName);

      columns.add(columnShard);
    }

    logger.info("Columns for new table shard of table {} created, creating table shard...", tableName);
    TableShard tableShard = tableFactory.createTableShard(tableName, columns);

    logger.info("Table shard for table {} created successfully.", tableName);
    return Arrays.asList(tableShard);
  }

  /**
   * Helper method that CSV-parses a single line.
   * 
   * @param line
   *          The input line
   * @return Parsed String values.
   */
  public static String[] parseCsvLine(String line) {
    try {
      return new CSVParser().parseLine(line);
    } catch (Exception e) {
      throw new RuntimeException("Could not parse CSV.", e);
    }
  }

  /**
   * A {@link Spliterator} that splits an input {@link ByteBuffer} by line-ends (\n character).
   * 
   * <p>
   * This Spliterator reports that the data is immutable, so the input ByteBuffer must not be changed while the
   * Spliterator is active.
   *
   * @author Bastian Gloeckle
   */
  private static class LineSpliterator implements Spliterator<String> {

    private BigByteBuffer buf;
    private long startPos;
    private long maxPos;
    private long sumLineLength;
    private long sumLines;

    public LineSpliterator(BigByteBuffer buf, long startPos, long maxPos, long sumLineLength, long sumLines) {
      this.buf = buf;
      this.startPos = startPos;
      this.maxPos = maxPos;
    }

    @Override
    public boolean tryAdvance(Consumer<? super String> action) {
      long pos = startPos;
      while (pos < maxPos && buf.get(pos) != '\n')
        pos++;
      if (pos >= maxPos)
        // ignore last not-full line, as it might have been truncated.
        return false;

      if (pos - startPos > Integer.MAX_VALUE)
        throw new RuntimeException("Cannot load CSV because there's a line that is bigger than 2GB.");

      byte[] b = new byte[(int) (pos - startPos)];
      for (int j = 0; j < b.length; j++)
        b[j] = buf.get(j + startPos);
      action.accept(new String(b));
      startPos = pos + 1;

      sumLineLength += b.length;
      sumLines++;

      return true;
    }

    @Override
    public Spliterator<String> trySplit() {
      long middle = startPos + ((maxPos - startPos) >> 1);
      while (middle < maxPos && buf.get(middle) != '\n')
        middle++;

      if (middle >= maxPos)
        return null;

      LineSpliterator newSplit = new LineSpliterator(buf, middle + 1, maxPos, sumLineLength, sumLines);
      maxPos = middle;

      return newSplit;
    }

    @Override
    public long estimateSize() {
      return (long) ((maxPos - startPos) / ((double) sumLineLength / sumLines));
    }

    @Override
    public int characteristics() {
      return Spliterator.IMMUTABLE | Spliterator.NONNULL;
    }
  }

}
