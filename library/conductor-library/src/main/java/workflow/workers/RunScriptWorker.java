package workflow.workers;

import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.gov.gchq.gaffer.script.operation.handler.RunScriptHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class RunScriptWorker implements Worker {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunScriptWorker.class);

    /** The task definition name */
    private String taskDefName;

    /**
     * Instantiates a new worker.
     *
     * @param taskDefName the task def name
     */
    public RunScriptWorker(String taskDefName) {
        this.taskDefName = taskDefName;
    }

    @Override
    public String getTaskDefName() {
        return taskDefName;
    }

    @Override
    public TaskResult execute(Task task) {

        // Check if this is a RunScript task
        if ("runScriptTask".compareTo(task.getReferenceTaskName()) == 0 ) {

            System.out.println("Executing {}." + taskDefName);

            TaskResult result = new TaskResult(task);

            // Do the calculations
            try {
                processTask(task, result);
                result.setStatus(TaskResult.Status.COMPLETED);
            } catch (Exception e) {
                e.printStackTrace();
                result.setStatus(TaskResult.Status.FAILED);
            }

            return result;
        }

        // What to do if this is not the correct task?
        return null;
    }

    /**
     * Process Task
     *
     * @param task the task called from Conductor
     * @param result the result to return to Conductor
     */
    private void processTask(Task task, TaskResult result) {

        System.out.println("Processing task: " + task.getTaskDefName());

        // Get the input to the task
        Map<String, Object> inputMap = task.getInputData();
        System.out.println("inputMap: " + inputMap.toString());

        ArrayList<String> scriptInput = (ArrayList<String>) inputMap.get("scriptInput");
        System.out.println("scriptInput: " + scriptInput);
//		ArrayList<String> input = null;
//		try {
//			input = JSONSerialiser.deserialise(scriptInput, ArrayList.class);
//		} catch (SerialisationException e) {
//			e.printStackTrace();
//			throw new SerialisationException("Failed to deserialise the script input");
//		}
//		System.out.println("input: " + input.toString());

        HashMap<String, Object> scriptParameters = (HashMap<String, Object>) inputMap.get("scriptParameters");
        System.out.println("ScriptParameters: " + scriptParameters);
//		HashMap<String, Object> parameters = null;
//		try {
//			parameters = JSONSerialiser.deserialise(scriptParameters, HashMap.class);
//		} catch (SerialisationException e) {
//			e.printStackTrace();
//			throw new SerialisationException("Failed to deserialise the script parameters");
//		}
//		System.out.println("parameters: " + parameters.toString());

        // Run the task
        System.out.println("Running task: " + task.getTaskDefName());

        // Use the handler to run the operation
        Object output = null;
        try {
            RunScriptHandler handler = new RunScriptHandler();
            handler.setRepoName("test");
            handler.setRepoURI("https://github.com/g609bmsma/test");
            output = handler.run("script1",scriptInput,scriptParameters);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Failed to execute task: " + task.getTaskDefName());
        }
        System.out.println("Output: " + output);

        // Set the output of this task
        String confStarter = task.getInputData().get("start_id") + task.getTaskDefName();
        result.getOutputData().put("conf_starter", confStarter);
    }
}