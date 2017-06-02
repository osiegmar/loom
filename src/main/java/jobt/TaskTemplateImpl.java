package jobt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jobt.config.BuildConfigImpl;
import jobt.plugin.PluginRegistry;
import jobt.api.TaskGraphNode;
import jobt.api.TaskStatus;
import jobt.api.TaskTemplate;

public class TaskTemplateImpl implements TaskTemplate {

    private final PluginRegistry pluginRegistry;
    private final Map<String, TaskGraphNodeImpl> tasks = new HashMap<>();
    private final List<String> tasksExecucted = new ArrayList<>();

    public TaskTemplateImpl(final BuildConfigImpl buildConfig) {
        this.pluginRegistry = new PluginRegistry(buildConfig, this);
    }

    @Override
    public TaskGraphNode task(final String name) {
        final TaskGraphNode taskGraphNode = tasks.get(name);
        if (taskGraphNode != null) {
            return taskGraphNode;
        }

        final TaskGraphNodeImpl newTask = new TaskGraphNodeImpl(name);
        tasks.put(name, newTask);
        return newTask;
    }

    public void execute(final String task) throws Exception {
        final Set<String> resolvedTasks = resolveTasks(task);

        Progress.log("Will execute "
            + resolvedTasks.stream().reduce((a, b) -> a + " > " + b).get());

        for (final String resolvedTask : resolvedTasks) {
            if (!tasksExecucted.contains(resolvedTask)) {
                Progress.newStatus("Execute Task " + resolvedTask);
                final TaskStatus status = pluginRegistry.trigger(resolvedTask);
                switch (status) {
                    case OK:
                        Progress.ok();
                        break;
                    case UP_TO_DATE:
                        Progress.uptodate();
                        break;
                    case SKIP:
                        Progress.skip();
                        break;
                    case FAIL:
                        Progress.fail();
                        return;
                    default:
                        throw new IllegalStateException("Unknown status " + status);
                }

                tasksExecucted.add(resolvedTask);
            }
        }
    }

    private Set<String> resolveTasks(final String task) {
        final Set<String> resolvedTasks = new LinkedHashSet<>();
        resolveTasks(resolvedTasks, task);
        return resolvedTasks;
    }

    private void resolveTasks(final Set<String> resolvedTasks, final String taskName) {
        final TaskGraphNodeImpl taskGraphNode = tasks.get(taskName);
        for (final TaskGraphNode node : taskGraphNode.getDependentNodes()) {
            resolveTasks(resolvedTasks, ((TaskGraphNodeImpl) node).getName());
        }
        resolvedTasks.add(taskGraphNode.getName());
    }

}