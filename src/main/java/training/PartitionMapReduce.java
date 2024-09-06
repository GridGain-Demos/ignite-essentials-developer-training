package training;

import org.apache.ignite.compute.ComputeJob;
import org.apache.ignite.compute.JobExecutionContext;
import org.apache.ignite.compute.task.MapReduceJob;
import org.apache.ignite.compute.task.MapReduceTask;
import org.apache.ignite.compute.task.TaskExecutionContext;
import org.apache.ignite.deployment.DeploymentUnit;
import org.apache.ignite.lang.AsyncCursor;
import org.apache.ignite.table.Tuple;
import org.apache.ignite.table.criteria.Criteria;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

public class PartitionMapReduce implements MapReduceTask<List<DeploymentUnit>, String, String, Integer> {
    @Override
    public CompletableFuture<List<MapReduceJob<String, String>>> splitAsync(
            TaskExecutionContext taskContext, List<DeploymentUnit> deploymentUnits) {
        return taskContext.ignite().tables().table("TABLE_NAME").partitionManager().primaryReplicasAsync()
                .thenApply(primaries -> primaries.entrySet().stream().map(entry ->
                                MapReduceJob.<String, String>builder()
                                        .node(entry.getValue())
                                        .args(entry.getKey().toString())
                                        .build())
                        .collect(toList())
                );
    }

    @Override
    public CompletableFuture<Integer> reduceAsync(TaskExecutionContext taskContext, Map<UUID, String> results) {
        // Custom reduce action.
        return CompletableFuture.completedFuture(1);
    }

    private static class CustomJob implements ComputeJob<String, String> {

        @Override
        public CompletableFuture<String> executeAsync(JobExecutionContext context, String partition) {
            // Here data read from KV will be local.
            return context.ignite().tables().table("TABLE_NAME")
                    .keyValueView()
                    .queryAsync(null, Criteria.columnValue(
                            "__part",
                            Criteria.equalTo(partition.getBytes(UTF_8)))
                    )
                    .thenApply(CustomJob::customAction);
        }


        private static String customAction(AsyncCursor<Map.Entry<Tuple, Tuple>> cursor) {
            //Some actions with data.

            return "";
        }
    }
}