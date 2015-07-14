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
package org.diqube.plan.execution;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.diqube.data.ColumnType;
import org.diqube.execution.ExecutablePlan;
import org.diqube.execution.consumers.ColumnVersionBuiltConsumer;
import org.diqube.execution.env.ExecutionEnvironment;
import org.diqube.execution.env.VersionedExecutionEnvironment;
import org.diqube.function.AggregationFunction.ValueProvider;
import org.diqube.function.IntermediaryResult;
import org.diqube.function.aggregate.CountFunction;
import org.diqube.plan.util.FunctionBasedColumnNameBuilder;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * This tests the parallel functionality of the query master logic as well as possible.
 * 
 * <p>
 * The QueryMaster heavily uses {@link ColumnVersionBuiltConsumer}s which in turn use
 * {@link VersionedExecutionEnvironment}. This means that the steps executed on the master usually work on intermediary
 * versions of the columns: The first step that creates a column might create just part of that column, because it does
 * not yet have all source information (which is provided by the remotes). These intermediate (or temporary) columns are
 * then stored in a {@link VersionedExecutionEnvironment} and that one is used instead of the default
 * {@link ExecutionEnvironment} by consequent steps to resolve any column values. This test should test this
 * functionality as well as possible - naturally this is a bit hard to test, because all of it is multi-threaded,
 * therefore this uses {@link RemoteEmulation} object to emulate the arrival of results from the remotes in a specific
 * way.
 *
 * @author Bastian Gloeckle
 */
public abstract class QueryMasterParallelDiqlExecutionTest<T> extends AbstractRemoteEmulatingDiqlExecutionTest<T> {

  public QueryMasterParallelDiqlExecutionTest(ColumnType colType, TestDataProvider<T> dp) {
    super(colType, dp);
  }

  @Test
  public void limitDoesNotCutOffOnIntermediaryResults() throws InterruptedException, ExecutionException {
    // GIVEN
    List<Long> shardRowIds = initializeSampleTableShards(2);

    ExecutablePlan executablePlan = buildExecutablePlan( //
        "Select " + COL_A + ", count() from " + TABLE + //
            " group by " + COL_A + //
            " order by count() desc LIMIT 1");

    ExecutorService executor = Executors.newFixedThreadPool(executablePlan.preferredExecutorServiceSize());
    try {
      // WHEN
      // start execution.
      Future<Void> future = executablePlan.executeAsynchronously(executor);

      waitUntilOrFail(remoteEmulationsNotify, () -> "RemoteEmulations not available",
          () -> remoteEmulations.size() == 2);

      RemoteEmulation firstRemote = remoteEmulations.get(shardRowIds.get(0));
      RemoteEmulation secondRemote = remoteEmulations.get(shardRowIds.get(1));

      String countCol = new FunctionBasedColumnNameBuilder().withFunctionName("count").build();

      Object groupByValue1 = dp.v(5);
      Object groupByValue2 = dp.v(10);
      Object groupByValue3 = dp.v(15);

      // let first shard returns some values
      Map<Long, Object> values = new HashMap<>();
      values.put(0L, groupByValue1);
      values.put(1L, groupByValue2);
      values.put(2L, groupByValue3);
      firstRemote.getOutputValues().consume(COL_A, values);
      firstRemote.getOutputGroups().consumeIntermediaryAggregationResult(0L, countCol, intermediary(0),
          intermediary(3));
      firstRemote.getOutputGroups().consumeIntermediaryAggregationResult(1L, countCol, intermediary(0),
          intermediary(1));
      firstRemote.getOutputGroups().consumeIntermediaryAggregationResult(2L, countCol, intermediary(0),
          intermediary(1));

      // now:
      // Group 0: value 3
      // Group 1: value 1 (might be cut off because of limit)
      // Group 2: value 1 (might be cut off because of limit)

      // THEN
      List<Long> expectedRun1 = Arrays.asList(new Long[] { 0L });
      waitUntilOrFail(newOrderedRowIdsNotify, //
          () -> "Not correct ordering value. Was: " + resultOrderRowIds + " Expected: " + expectedRun1.toString(), //
          () -> resultOrderRowIds.equals(expectedRun1));
      long expectedValueRun1 = 3L;
      waitUntilOrFail(newValuesNotify, //
          () -> "Not correct value. Was: " + resultValues.get(countCol).get(0L) + " Expected: " + expectedValueRun1, //
          () -> resultValues.get(countCol) != null && resultValues.get(countCol).get(0L).equals(expectedValueRun1));

      // WHEN second shard found some values
      values.clear();
      values.put(3L, groupByValue1);
      values.put(4L, groupByValue2);
      values.put(5L, groupByValue3);
      secondRemote.getOutputValues().consume(COL_A, values);
      secondRemote.getOutputGroups().consumeIntermediaryAggregationResult(3L, countCol, intermediary(0),
          intermediary(1));
      secondRemote.getOutputGroups().consumeIntermediaryAggregationResult(4L, countCol, intermediary(0),
          intermediary(2));
      secondRemote.getOutputGroups().consumeIntermediaryAggregationResult(5L, countCol, intermediary(0),
          intermediary(1));

      // now:
      // Group 3 matched to group 0, new value: 4
      // Group 4 matched to group 1, new value: 3 (might be cut off because of limit)
      // Group 5 matched to group 2, new value: 2 (might be cut off because of limit)

      // be aware: If the execution somehow forgot the previous values, the top group would now be 4 (which would be
      // wrong!)

      // THEN
      List<Long> expectedRun2 = Arrays.asList(new Long[] { 0L });
      waitUntilOrFail(newOrderedRowIdsNotify, //
          () -> "Not correct ordering value. Was: " + resultOrderRowIds + " Expected: " + expectedRun2.toString(), //
          () -> resultOrderRowIds.equals(expectedRun2));
      long expectedValueRun2 = 4L;
      waitUntilOrFail(newValuesNotify, //
          () -> "Not correct value. Was: " + resultValues.get(countCol).get(0L) + " Expected: " + expectedValueRun2, //
          () -> resultValues.get(countCol) != null && resultValues.get(countCol).get(0L).equals(expectedValueRun2));

      // first shard found some more values.
      firstRemote.getOutputGroups().consumeIntermediaryAggregationResult(0L, countCol, intermediary(3),
          intermediary(4));
      firstRemote.getOutputGroups().consumeIntermediaryAggregationResult(1L, countCol, intermediary(1),
          intermediary(2));
      firstRemote.getOutputGroups().consumeIntermediaryAggregationResult(2L, countCol, intermediary(1),
          intermediary(5));

      // now: (these values are only correct, if orderStep did NOT cut off group 1L in last execution, otherwise 1L is
      // the only group left!)
      // Group 0, new value: 5 (might be cut off because of limit)
      // Group 1, new value: 4 (might be cut off because of limit)
      // Group 2, new value: 6

      // be aware: If the OrderStep did cut-off the previous results of group2 internally, group2 would now not be the
      // top group!

      // THEN
      List<Long> expectedOrderingRun3 = Arrays.asList(new Long[] { 2L });
      waitUntilOrFail(newOrderedRowIdsNotify, //
          () -> "Not correct ordering value. Was: " + resultOrderRowIds + " Expected: "
              + expectedOrderingRun3.toString(), //
          () -> resultOrderRowIds.equals(expectedOrderingRun3));
      long expectedValueRun3 = 6L;
      waitUntilOrFail(newValuesNotify, //
          () -> "Not correct value. Was: " + resultValues.get(countCol).get(2L) + " Expected: " + expectedValueRun3, //
          () -> resultValues.get(countCol) != null && resultValues.get(countCol).get(2L).equals(expectedValueRun3));

      firstRemote.done();
      secondRemote.done();

      future.get(); // wait until fully done.

      // THEN after full completion of pipeline.
      Assert.assertEquals(resultOrderRowIds, Arrays.asList(new Long[] { 2L }),
          "Expected final ordering result to be correct");
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  public void intermediaryResultsDecreaseResult() throws InterruptedException, ExecutionException {
    // GIVEN
    List<Long> shardRowIds = initializeSampleTableShards(2);

    ExecutablePlan executablePlan = buildExecutablePlan( //
        "Select " + COL_A + ", count() from " + TABLE + //
            " group by " + COL_A + //
            " order by count() desc LIMIT 1");

    ExecutorService executor = Executors.newFixedThreadPool(executablePlan.preferredExecutorServiceSize());
    try {
      // WHEN
      // start execution.
      Future<Void> future = executablePlan.executeAsynchronously(executor);

      waitUntilOrFail(remoteEmulationsNotify, () -> "RemoteEmulations not available",
          () -> remoteEmulations.size() == 2);

      RemoteEmulation firstRemote = remoteEmulations.get(shardRowIds.get(0));
      RemoteEmulation secondRemote = remoteEmulations.get(shardRowIds.get(1));

      String countCol = new FunctionBasedColumnNameBuilder().withFunctionName("count").build();

      Object groupByValue1 = dp.v(5);
      Object groupByValue2 = dp.v(10);

      // let first shard returns some values
      Map<Long, Object> values = new HashMap<>();
      values.put(0L, groupByValue1);
      values.put(1L, groupByValue2);
      firstRemote.getOutputValues().consume(COL_A, values);
      firstRemote.getOutputGroups().consumeIntermediaryAggregationResult(0L, countCol, intermediary(0),
          intermediary(5));
      firstRemote.getOutputGroups().consumeIntermediaryAggregationResult(1L, countCol, intermediary(0),
          intermediary(1));

      // now:
      // Group 0: value 3
      // Group 1: value 1 (might be cut off because of limit)
      // Group 2: value 1 (might be cut off because of limit)

      // THEN
      List<Long> expectedRun1 = Arrays.asList(new Long[] { 0L });
      waitUntilOrFail(newOrderedRowIdsNotify, //
          () -> "Not correct ordering value. Was: " + resultOrderRowIds + " Expected: " + expectedRun1.toString(), //
          () -> resultOrderRowIds.equals(expectedRun1));
      long expectedValueRun1 = 5L;
      waitUntilOrFail(newValuesNotify, //
          () -> "Not correct value. Was: " + resultValues.get(countCol).get(0L) + " Expected: " + expectedValueRun1, //
          () -> resultValues.get(countCol) != null && resultValues.get(countCol).get(0L).equals(expectedValueRun1));

      // WHEN second shard found some values
      values.clear();
      values.put(3L, groupByValue1);
      values.put(4L, groupByValue2);
      secondRemote.getOutputValues().consume(COL_A, values);
      secondRemote.getOutputGroups().consumeIntermediaryAggregationResult(3L, countCol, intermediary(0),
          intermediary(1));
      secondRemote.getOutputGroups().consumeIntermediaryAggregationResult(4L, countCol, intermediary(0),
          intermediary(2));

      // now:
      // Group 3 matched to group 0, new value: 6
      // Group 4 matched to group 1, new value: 3 (might be cut off because of limit)

      // THEN
      List<Long> expectedRun2 = Arrays.asList(new Long[] { 0L });
      waitUntilOrFail(newOrderedRowIdsNotify, //
          () -> "Not correct ordering value. Was: " + resultOrderRowIds + " Expected: " + expectedRun2.toString(), //
          () -> resultOrderRowIds.equals(expectedRun2));
      long expectedValueRun2 = 6L;
      waitUntilOrFail(newValuesNotify, //
          () -> "Not correct value. Was: " + resultValues.get(countCol).get(0L) + " Expected: " + expectedValueRun2, //
          () -> resultValues.get(countCol) != null && resultValues.get(countCol).get(0L).equals(expectedValueRun2));

      // first shard found some more values.
      firstRemote.getOutputGroups().consumeIntermediaryAggregationResult(0L, countCol, intermediary(5),
          intermediary(2)); // LOWER!
      firstRemote.getOutputGroups().consumeIntermediaryAggregationResult(1L, countCol, intermediary(1),
          intermediary(2));

      // now: (these values are only correct, if orderStep did NOT cut off group 1L in last execution, otherwise 1L is
      // the only group left!)
      // Group 0, new value: 3 (might be cut off because of limit)
      // Group 1, new value: 4

      // be aware: If the OrderStep did cut-off the previous results of group2 internally, group2 would now not be the
      // top group!

      // THEN
      List<Long> expectedOrderingRun3 = Arrays.asList(new Long[] { 1L });
      waitUntilOrFail(newOrderedRowIdsNotify, //
          () -> "Not correct ordering value. Was: " + resultOrderRowIds + " Expected: "
              + expectedOrderingRun3.toString(), //
          () -> resultOrderRowIds.equals(expectedOrderingRun3));
      long expectedValueRun3 = 4L;
      waitUntilOrFail(newValuesNotify, //
          () -> "Not correct value. Was: " + resultValues.get(countCol).get(1L) + " Expected: " + expectedValueRun3, //
          () -> resultValues.get(countCol) != null && resultValues.get(countCol).get(1L).equals(expectedValueRun3));

      firstRemote.done();
      secondRemote.done();

      future.get(); // wait until fully done.

      // THEN after full completion of pipeline.
      Assert.assertEquals(resultOrderRowIds, Arrays.asList(new Long[] { 1L }),
          "Expected final ordering result to be correct");
    } finally {
      executor.shutdownNow();
    }
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private IntermediaryResult<Object, Object, Object> intermediary(int count) {
    CountFunction fn = new CountFunction();
    fn.addValues(new ValueProvider<Object>() {

      @Override
      public Object[] getValues() {
        return new Object[count];
      }

      @Override
      public long[] getRowIds() {
        return new long[count];
      }

      @Override
      public Long[] getColumnValueIds() {
        return new Long[count];
      }
    });

    return (IntermediaryResult) fn.calculateIntermediary();
  }
}
