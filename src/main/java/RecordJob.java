import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.sentry.Sentry;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Created by strandquistg on 17 May, 2021
 */
public class RecordJob implements InterruptableJob {

    /*
     RecordJob fields
     */
    private JobKey jobkey;
    private boolean suspended = false;
    public Process fullstop = null;  //this Process is essential to brute-force stop separate Processes initiated in execute() method
    private static final Logger log = LoggerFactory.getLogger(RecordJob.class);
    private final DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;

    /**
     * @param jobExecutionContext field allows params to be passed from other classes
     * @throws JobExecutionException
     * @note execute is the single method inherited by Job interface
     * It ideally calls some other class, the intended "job" to run,
     * in the execute() method. Here, we're keeping the job code
     * internal to this Job class, since I need to allow brute-force
     * interruption of external Processes (namely ffmpeg recordings)
     */
    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        /**
         * Get jobExecution info
         */
        jobkey = jobExecutionContext.getJobDetail().getKey();
        JobDetail jobDetail = jobExecutionContext.getJobDetail();
        /**
         * Get all params stored in job
         */
        String camname = jobDetail.getKey().getName();
        String qscale = (String) jobDetail.getJobDataMap().get("qscale");
        String recording_duration = (String) jobDetail.getJobDataMap().get("record_sesh_len"); //in seconds
        String segment_len = (String) jobDetail.getJobDataMap().get("segment_len"); //in seconds
        String sp_host = (String) jobDetail.getJobDataMap().get("save_path_host");
        String sp_singularity = (String) jobDetail.getJobDataMap().get("save_path_singularity");
        String fps = (String) jobDetail.getJobDataMap().get("frame_rate");
        String rez = (String) jobDetail.getJobDataMap().get("resolution");
        String pID = (String) jobDetail.getJobDataMap().get("patientID");
        String gcID = (String) jobDetail.getJobDataMap().get("gitCommitID");
        String[] side = camname.split("/");
        /**
         * mkdir with current datetime
         */
        String before_record_time = LocalDateTime.now().format(formatter).replaceAll(":", "-");
        String date_path = String.join("", LocalDateTime.now().toString().split("T")[0].split("-")) + "/"; //Wasabi directory naming requires date in path
        File timeDir = new File(sp_host + date_path + side[side.length - 1] + "_" + before_record_time);
        timeDir.mkdirs();
        if(timeDir.isDirectory()) {
            System.out.println("Data directory created: " + timeDir);
        } else {
            System.out.println("Failed to create " + timeDir + " as a directory, recording will fail! \nCheck that host directory: " + sp_host + " is bound to Singularity directory (if using Singularity): " + sp_singularity);
            try {
                throw new Exception("RecordJob failed to create " + timeDir + " directory, recording will fail!");
            } catch (Exception e) {
                Sentry.captureException(e);
            }
        }
        /**
         * create writer to test writing out metadata
         */
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        RecordingObject recording = new RecordingObject(pID, gcID, before_record_time, fps, rez, qscale, recording_duration, segment_len);
        File metaFile=new File(timeDir + "/metadata_" + side[side.length-1] + ".json");
        try {
            metaFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
            Sentry.captureException(e);
        }
        File outFile = new File(timeDir + "/stdOut_" + side[side.length-1] + "_" + before_record_time + ".log");
        File errFile = new File(timeDir + "/errOut_" + side[side.length-1] + "_" + before_record_time + ".log");

        /**
         * Create main ffmpeg recording as ProcessBuilder
         */
        String cmd_single = "ffmpeg -y -hide_banner -f v4l2 -s " + rez + " -c:v mjpeg -thread_queue_size 64 -t " +
                recording_duration + " -i " + camname + " -qscale:v " + qscale + " -r " + fps +
                " -c:v mjpeg -map 0 -f segment -strftime 1 -reset_timestamps 1 -segment_time " + segment_len +
                " -vf showinfo " + timeDir + "/" + side[side.length-1] + "_%Y-%m-%d-%H-%M-%S.avi";

        ProcessBuilder pb = new ProcessBuilder().command(cmd_single.split("\\s"));
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(outFile));
        pb.redirectError(ProcessBuilder.Redirect.appendTo(errFile));
        Process p;
        try {
            p = pb.start();
            fullstop = p;
            Long jpid = p.pid();
            jobDetail.getJobDataMap().put("jobPID", jpid);

            String threadName = Thread.currentThread().getName();
            System.out.println("in RecordJob, threadName: " + threadName);
            jobDetail.getJobDataMap().put("threadName", threadName);

            Long threadID = Thread.currentThread().getId();
            System.out.println("in RecordJob, threadID: " + threadID);
            jobDetail.getJobDataMap().put("threadID", threadID);

            if (!suspended) { // periodically check if we've been interrupted...
                log.info("--- " + jobkey + "  -- Recording...");
                p.waitFor();
            }
            /**
             * Update metadata with recording end time
             */
            String after_record_time = LocalDateTime.now().format(formatter);
            recording.setRecordEndTime(after_record_time); //Update metadata with recording end time
            recording.setProcessID(String.valueOf(jpid));
            recording.setThreadName(threadName);
            recording.setThreadID(String.valueOf(threadID));
            String json = mapper.writeValueAsString(recording);
            FileWriter fileWriter = new FileWriter(metaFile);
            fileWriter.write(json);
            fileWriter.flush();
            fileWriter.close();

            System.out.println("-- Job interrupted! --");
            p.getInputStream().close();
            p.getOutputStream().close();
            p.getErrorStream().close();
            p.destroyForcibly();
            suspended = false;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            Sentry.captureException(e);
        }
    } //execute job method

    /**
     * @throws JobExecutionException
     * @note interrupt() allows a Quartz Scheduler object
     * to stop the job's execution.
     * Here since our job is a separate Process using ffmpeg,
     * we have to destroy the process in a brute-force way
     */
    @Override
    public void interrupt() throws UnableToInterruptJobException {
        log.info("---" + jobkey + "  -- INTERRUPTING --");
        suspended = true;
        try {
            fullstop.getInputStream().close();
            fullstop.getOutputStream().close();
            fullstop.getErrorStream().close();
            fullstop.destroyForcibly();
        } catch (IOException e) {
            e.printStackTrace();
            Sentry.captureException(e);
        }
    } //interrupt method

}//RecordJob class
