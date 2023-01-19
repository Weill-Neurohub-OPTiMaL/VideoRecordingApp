import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;

import java.util.*;

/**
 * Created by strandquistg on 17 May, 2021
 */
public class RecordJobListener implements JobListener {

    JobDescriptionBuilder listener_jdb = new JobDescriptionBuilder();
    Map<String, String> configCams = listener_jdb.getCamAddresses();
    HashMap<String, Boolean> allJobFlags = listener_jdb.buildJobFlags(configCams);
    Date justExecutedFireTime = null;

    @Override
    public String getName() {
        System.out.println("From joblistener, class name: " + getClass().getSimpleName());
        return getClass().getSimpleName();
    }

    @Override
    public void jobToBeExecuted(JobExecutionContext jobExecutionContext) {
        System.out.println("Job " + jobExecutionContext.getJobDetail().getKey().getName() + " about to be executed with fire time: " + jobExecutionContext.getFireTime());
        if (allJobFlags.containsKey(jobExecutionContext.getJobDetail().getKey().getName())){
            allJobFlags.put(jobExecutionContext.getJobDetail().getKey().getName(), true); //name is camera address?
            System.out.println("Updating flags! " + allJobFlags);
        }
        justExecutedFireTime = jobExecutionContext.getFireTime();
        System.out.println("job about to be Executed, all Flags: " + allJobFlags);
    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext jobExecutionContext) {
    }

    @Override
    public void jobWasExecuted(JobExecutionContext jobExecutionContext, JobExecutionException e) {
        System.out.println("Job " + jobExecutionContext.getJobDetail().getKey().getName() + " was executed with PID " + jobExecutionContext.getJobDetail().getJobDataMap().get("jobPID"));
        System.out.println("Thread name:" + Thread.currentThread().getName());
        System.out.println("Thread ID:" + Thread.currentThread().getId());

        if (allJobFlags.containsKey(jobExecutionContext.getJobDetail().getKey().getName())){
            allJobFlags.put(jobExecutionContext.getJobDetail().getKey().getName(), false);
            System.out.println("Updating flags! " + allJobFlags);
        }
        System.out.println("job was Executed, all Flags: " + allJobFlags);
    }


}
