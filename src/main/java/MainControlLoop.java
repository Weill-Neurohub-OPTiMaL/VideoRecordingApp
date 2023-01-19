import io.sentry.Sentry;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.*;
import java.awt.Image;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Calendar;
import java.util.List;

import static org.quartz.impl.matchers.GroupMatcher.jobGroupEquals;

/**
 * Created by strandquistg on 17 May, 2021
 */
@SuppressWarnings("DanglingJavadoc")
public class MainControlLoop implements Runnable{

    /*
     Scheduling fields
     */
    SchedulerFactory sf = null;
    private boolean startOfDay = false;
    private boolean endOfDay = false;
    private boolean duringDay = false;
    private boolean app_init = false;
    private Scheduler sched = null;
    private final JobDescriptionBuilder jdb = new JobDescriptionBuilder();
    private final TriggerBuilder tb = new TriggerBuilder();
    private RecordJobListener recording_listener = new RecordJobListener();
    public int numCameras = 0;
    private String jobGroupName = "record_brio";
    private ArrayList<JobDetail> allJobs = new ArrayList<>();
    private ArrayList<JobKey> allJobKeys = new ArrayList<>();
    private HashMap<String, JobKey> nameToJobKey = new HashMap<>();
    private Map<String, String> configCams = null; //<Arbitrary camera name, USB address>
    private final String user = System.getProperty("user.name");
    private final String bitrate_scale = "31";  //ranges from 1-31, where 1 is best quality/highest bitrate
    private final String recording_sesh_len = "3600";  //in seconds
    private final String segment_len = "120";  //in seconds, chops recording_sesh_len into this size
    private final String fps = "30";
    private final String rez = "4096x2160";
    private final String sp_host = "/media/DATA/raw_videos/"; //directory for videos/logfiles
    private final String sp_singularity = "/home/" + user + "/optimal_aDBS/SingularityDeploy/container_data/"; //only needed if singularity is used as container
    public String currentCommit = "";
    public final String patientID = "RCS07";
    public final String localZone = "America/Los_Angeles";
    public ZoneId pstZone = ZoneId.of(localZone);
    public LocalDateTime ldt;
    public ZonedDateTime zdt;

    /*
     GUI fields
     */
    public final JLabel statusLabel = new JLabel("", JLabel.CENTER);
    public final JTextArea textArea = new JTextArea(20, 20);
    private final JButton suspendButton = new JButton("<html><p>Suspend any ongoing recording</p></html>");
    private final JButton stopButton = new JButton("<html><p>Stop all scheduled recordings for the day</p></html>");
    public Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
    private final int screen_w = size.width;
    private final int screen_h = size.height;
    private ImageIcon record_on = null;
    private ImageIcon record_off = null;
    private final JLabel record_lbl = new JLabel();
    public DateTimeFormatter tz_nice_format = DateTimeFormatter.ofPattern("MM/dd/yyyy - HH:mm:ss z");

    /**
     * @param args
     * @note main initiates run, which will start/control app
     * The main method in the MainControlLoop class is where the
     * executable jar file looks to start the app
     */
    public static void main(String[] args) throws IOException {
        MainControlLoop cl = new MainControlLoop();
        /*
        establish Sentry key and commit ID
         */
        Sentry.init(options -> {
            options.setDsn("https://fa56f16998084faf9bee39e4dc727341@o942791.ingest.sentry.io/5891606");
        });
        cl.setGitCommitID(cl.getGitCommitID());
        /*
        start app!
         */
        cl.run();
    }

    /**
     * @note run() is method that keeps the app going forever.
     * Clock is continually checked to determine when to schedule
     * jobs for each new day.
     * run() inits GUI; if GUI is killed program keeps going
     */
    @Override
    public void run() {
        /*
        Check that encrypted hard disk is mounted with proper permissions
         */
        File dataDir = new File(sp_host);
        if (dataDir.isDirectory()){
            System.out.println(dataDir + " was detected as pre-existing directory.");
            if(dataDir.canWrite()){
                System.out.println(user + " has write-permissions for " + dataDir);
            }
            else{
                try {
                    throw new Exception(dataDir + " exists as directory but " + user + " does not have write permission, recordings will fail.");
                } catch (Exception e) {
                    Sentry.captureException(e);
                    e.printStackTrace();
                }
            }
        }
        else{
            try {
                throw new Exception(dataDir + " was not found as an existing directory, check that hard disk is mounted.");
            } catch (Exception e) {
                Sentry.captureException(e);
                e.printStackTrace();
            }
        }

        /*
        Build and display GUI
         */
        try {
            initGUI();
        } catch (Exception e) {
            e.printStackTrace();
            Sentry.captureException(e);
        }

        /*
        Initialize recording schedule components
         */
        try {
            initRecordingApp();
            System.out.println("All jobs: " + allJobs + "\nAll job keys: " + allJobKeys);
        } catch (IOException e) {
            e.printStackTrace();
            Sentry.captureException(e);
        }

        /*
        Main control-loop. This will continually check the time,
        both to detect if recording is occurring, and to check
        the time of day.
        At start-of-day, triggers are re-built.
        At end-of-day, flag is set to notify next day that triggers need to be re-built
         */
        while (true){
            try {
                checkForActiveRecordings(recording_listener.allJobFlags);
            } catch (SchedulerException | InterruptedException e) {
                e.printStackTrace();
                Sentry.captureException(e);
            }

            TimeZone timeZone = TimeZone.getTimeZone(localZone);
            Calendar calendar = Calendar.getInstance(timeZone);
            checkDayPeriod(calendar.get(Calendar.HOUR_OF_DAY));
            if (startOfDay && !app_init){
                try {
                    scheduleDailyJobs();    //build triggers for new day
                    app_init = true;
                } catch (SchedulerException e) {
                    e.printStackTrace();
                    Sentry.captureException(e);
                }
            }//start of day

            if (endOfDay && app_init){
                app_init = false;   //resets so triggers will be rebuilt at start of next day
            }//end of day

            //////////////////////////////////////////////////////////////////
            // Sleep between clock-checking intervals
            LocalDateTime current = LocalDateTime.now();
            ZonedDateTime zdt = current.atZone( pstZone ) ;
            System.out.println("\n************************************************************************\nLocal date check: " + zdt);
            try {
                Thread.sleep(1 * 1000); //checks time every 5 seconds
            } catch (InterruptedException e) {
                e.printStackTrace();
                Sentry.captureException(e);
            }
        }

    }//run loop


    /**
     * @note Initializes on start-up:
     *      --Schedule manager
     *      --Job listener
     *      --JobDetails, one per each camera
     *      --assign jobKeys
     *      --sets up triggers to fire jobs
     * All of these things should only need to be done once upon app start-up,
     * with the exception of setting up triggers, whose life-cycle ends each day
     * after they are fired.
     */
    public void initRecordingApp() throws IOException {
        sf = new StdSchedulerFactory();
        configCams = jdb.getCamAddresses();  //camera addresses from config file
        numCameras = configCams.size();
        configCams.forEach((camName, camAddress) -> {
            allJobs.add(jdb.buildSingleDetail(configCams.get(camName),jobGroupName, bitrate_scale, recording_sesh_len, segment_len, sp_host, sp_singularity, fps, rez, currentCommit, patientID));
            allJobKeys.add(allJobs.get(allJobs.size()-1).getKey());
            nameToJobKey.put(camName, allJobKeys.get(allJobKeys.size()-1));
        });

        try {
            sched = sf.getScheduler(); //create schedule to allow triggering of jobs
            sched.getListenerManager().addJobListener(recording_listener, jobGroupEquals(jobGroupName));
            allJobs.forEach((e) -> {
                try {
                    sched.addJob(e, true);
                } catch (SchedulerException ex) {
                    ex.printStackTrace();
                    Sentry.captureException(ex);
                }
            });
            System.out.println("Added jobs");
            scheduleDailyJobs();
            System.out.println("Scheduled triggers");
            sched.start();
            try {
                Thread.sleep(1 * 1000); //give scheduler a second to start
            } catch (InterruptedException e) {
                e.printStackTrace();
                Sentry.captureException(e);
            }
            app_init = true;
            statusLabel.setText("<html><p>Daily Recording Schedule Initialized!</p></html>");
            System.out.println("Leaving initRecordingApp!");

        } catch (SchedulerException e) {
            e.printStackTrace();
            Sentry.captureException(e);
        }
    }

    /**
     * @note Ideally this should run once per day,
     * since Triggers are currently configured to
     * be "Simple", where they run once at the time
     * specified, and then their life-cycle ends
     *
     * Builds recordTimes ArrayList, which holds
     * recording times per agreed-upon schedule.
     * This is done daily to attain current-day's
     * date, with this current simple daily configuration
     */
    public void scheduleDailyJobs() throws SchedulerException {
        LocalDateTime current = LocalDateTime.now();
        Date currentDate = toJavaUtilDateFromZonedDateTime(current.atZone( pstZone ));
        ArrayList<ZonedDateTime>  recordTimes = tb.setTriggerRecordingTimes(pstZone); //recording times from config file
        System.out.println("In schedule daily jobs, recordTimes: " + recordTimes);

        configCams.forEach((camName, camAddress) -> {
            System.out.println("Camera getting a trigger: " + camName);
            recordTimes.forEach((rt) -> {
                System.out.println("\nRecording times for each day: " + rt);
                Trigger singleTrigger = tb.buildSingleTrigger(rt, camName, nameToJobKey.get(camName));
                //schedule job with trigger
                if (currentDate.compareTo(singleTrigger.getStartTime()) > 0){
                    System.out.println("Skipping past trigger!\nTrigger time: " + singleTrigger.getStartTime() + "\nCurrent time: " + currentDate);
                }
                else{
                    System.out.println("\nScheduling upcoming trigger!\nTrigger time: " + singleTrigger.getStartTime() + "\nCurrent time: " + currentDate);
                    try {
                        sched.scheduleJob(singleTrigger);
                    } catch (SchedulerException e) {
                        e.printStackTrace();
                        Sentry.captureException(e);
                    }
                }
            });//inner loop iterates over all recording times
        });//outer loop iterates over all camera addresses
    }

    static public java.util.Date toJavaUtilDateFromZonedDateTime ( ZonedDateTime zdt ) {
        // Data-loss, going from nanosecond resolution to milliseconds.
        return java.util.Date.from( zdt.toInstant() );
    }

    /**
     * @throws SchedulerException
     * @note If user selects "Stop" button on UI, tihs method
     * gets called to unschedule any remaining recording sessions
     * scheduled for that day
     */
    public void unscheduleAllDailyJobs() throws SchedulerException {
        allJobKeys.forEach(jk -> {
            try {
                List<Trigger> job_triggers = (List<Trigger>) sched.getTriggersOfJob(jk);
                for (int i = 0;i<job_triggers.size();i++){
                    sched.unscheduleJob(job_triggers.get(i).getKey());
                    System.out.println("Unscheduled: " + job_triggers.get(i).getKey());
                }
                //double check all triggers were removed
                java.util.List<Trigger> triggersAfterUnschedule = (List<Trigger>) sched.getTriggersOfJob(jk);
                if (triggersAfterUnschedule.size() == 0 ){
                    System.out.println("Successfully cleared all recordings from " + jk);
                }
                else{
                    System.out.println("Recordings still remain on schedule, something went wrong!");
                    try{
                        throw new Exception ("Failed to clear recordings from job " + jk + " after unscheduleAllDailyJobs was called!");
                    } catch (Exception e) {
                        Sentry.captureException(e);
                    }
                }
            } catch (SchedulerException e) {
                e.printStackTrace();
                Sentry.captureException(e);
            }
        });
    }//unscheduleAllDailyJobs

    public void checkForActiveRecordings(HashMap<String, Boolean> allJobFlags) throws SchedulerException, InterruptedException {
        System.out.println("map from check for actives: " + allJobFlags);
        if(allJobFlags.containsValue(true)){
            ldt = LocalDateTime.ofInstant(recording_listener.justExecutedFireTime.toInstant(), ZoneId.of(localZone));
            zdt = ldt.atZone( pstZone ) ;
            System.out.println("RECORDING ONGOING, trigger started at: " + zdt + " ending at: " + zdt.plusSeconds(Long.parseLong(recording_sesh_len)));
            record_lbl.setIcon(record_on);
            statusLabel.setText("<html><p>Recording ongoing!<br/>Recording stops at " + zdt.plusSeconds(Long.parseLong(recording_sesh_len)).format(tz_nice_format) + "</p></html>");
        }

        else if(!allJobFlags.containsValue(true)){
            System.out.println("RECORDING NOT GOING");
            record_lbl.setIcon(record_off);
            JobKey jk = allJobKeys.get(0); //grab first job since all jobs have identical trigger times
            try {
                List<Trigger> job_triggers = (List<Trigger>) sched.getTriggersOfJob(jk);
                System.out.println("Size of " + jk + " trigger list:" + job_triggers.size());

                if(job_triggers.size() > 0){
                    System.out.println("trigger list > 0!");
                    Trigger nextTrigger = job_triggers.get(0);
                    System.out.println("next trigger: " + nextTrigger);
                    Date nextFireTime = nextTrigger.getNextFireTime();

                    //What I expect to happen most of the time
                    if (!nextFireTime.equals(null)){
                        System.out.println("nextFireTime not null: " + nextFireTime);
                        ldt = LocalDateTime.ofInstant(nextFireTime.toInstant(), ZoneId.of(localZone));
                        zdt = ldt.atZone( pstZone ) ;
                        System.out.println("will start at: " + zdt + " and should end at: " + zdt.plusSeconds(Long.parseLong(recording_sesh_len)));
                        statusLabel.setText("<html><p>No ongoing recordings.<br/>Next recording starts at " + zdt.format(tz_nice_format) + "<br/>and ends at " + zdt.plusSeconds(Long.parseLong(recording_sesh_len)).format(tz_nice_format) + "</p></html>");
                    }
                    //what might happen if recording starts AFTER entering control-block of no-recordings,
                    // AND when the only trigger left is currently firing, so it has no next fire time
                    else if (nextFireTime.equals(null) && (job_triggers.size() == 1 )){
                        System.out.println("Strange case: recording started after entering control-block of no ongoing recordings, and only remaining trigger is currently firing");
                        statusLabel.setText("<html><p>Recording ongoing!<br/>Next scheduled recording time: " + tb.getNextDayFirstRecordTime(pstZone).format(tz_nice_format) + " </p></html>");

                    }
                    //what might happen if recording starts AFTER entering control-block of no-recordings,
                    //AND when there are other triggers scheduled for the same day
                    else if (nextFireTime.equals(null) && (job_triggers.size() > 1)){
                        System.out.println("Strange case: recording started after entering control-block of no ongoing recordings, and > 1 remaining trigger for the day");
                        nextTrigger = job_triggers.get(1);
                        System.out.println("next trigger: " + nextTrigger );
                        nextFireTime = nextTrigger.getNextFireTime();
                        System.out.println("nextFireTime: " + nextFireTime);
                        ldt = LocalDateTime.ofInstant(nextFireTime.toInstant(), ZoneId.of(localZone));
                        zdt = ldt.atZone( pstZone ) ;
                        //statusLabel.setText("<html><p>Recording ongoing!<br/>Next scheduled time: " + zdt.format(tz_nice_format) + " </p></html>");
                    }
                    else{//trigger lists are empty, no cameras recording, nothing left for the day
                        statusLabel.setText("<html><p>No ongoing recordings.<br/>Next recording starts at " + tb.getNextDayFirstRecordTime(pstZone).format(tz_nice_format) + "<br/>and ends at " + tb.getNextDayFirstRecordTime(pstZone).plusSeconds(Long.parseLong(recording_sesh_len)).format(tz_nice_format) + "</p></html>");
                    }
                }

                else{
                    statusLabel.setText("<html><p>No ongoing recordings.<br/>Next recording starts at " + tb.getNextDayFirstRecordTime(pstZone).format(tz_nice_format) + "<br/>and ends at " + tb.getNextDayFirstRecordTime(pstZone).plusSeconds(Long.parseLong(recording_sesh_len)).format(tz_nice_format) + "</p></html>");
                }
            } catch (SchedulerException e) {
                e.printStackTrace();
                Sentry.captureException(e);
            }
        }
    }

    /**
     * @return String of current Git commit ID
     */
    public String getGitCommitID() throws IOException {
        Git git = Git.open(new File(System.getProperty("user.dir")));
        Repository repository = git.getRepository();
        ObjectId lastCommitId = repository.resolve(Constants.HEAD);
        return (String.valueOf(lastCommitId)).split("[\\[\\]]")[1];
    }
    /**
     * @param gcID sets current Commit ID
     */
    public void setGitCommitID(String gcID){
        currentCommit = gcID;
    }

    /**
     * @param currentHour int
     * @note Sets time to "start of day", "end of day", or "during day" range
     */
    public void checkDayPeriod(int currentHour){
        int newDayRange = 2;
        int midnight = 0;
        int endDayRange = 23;
        if (currentHour > midnight && currentHour < newDayRange) {
            System.out.println("Hour " + currentHour + " is in range start-of-day\n************************************************************************\n\n");
            startOfDay = true;
            endOfDay = false;
            duringDay = false;
        }
        else if (currentHour >= endDayRange){
            System.out.println("Hour " + currentHour + " is in range end-of-day\n************************************************************************\n\n");
            endOfDay = true;
            startOfDay = false;
            duringDay = false;
        }
        else if (currentHour == midnight){
            System.out.println("Hour " + currentHour + " is in range midnight-hour\n************************************************************************\n\n");
            endOfDay = false;
            startOfDay = false;
            duringDay = false;
        }
        else {
            System.out.println("Hour " + currentHour + " is in range during-day\n************************************************************************\n\n");
            startOfDay = false;
            endOfDay = false;
            duringDay = true;
        }
    }

    /**
     * @note Sets up listener for UI buttons
     */
    public final ActionListener buttonActions = actionEvent -> {
        JButton source = (JButton) actionEvent.getSource();
        if (source == suspendButton) { //abort an ongoing recording session
            System.out.println("Got Suspend signal!");
            textArea.setText(null);
            if (recording_listener.allJobFlags.containsValue(true)){
                allJobKeys.forEach((jk) ->  {
                    try {
                        sched.interrupt(jk);
                        record_lbl.setIcon(record_off);
                        statusLabel.setText("<html><p>Current recording session cancelled</p></html>");
                    } catch (UnableToInterruptJobException e) {
                        e.printStackTrace();
                        Sentry.captureException(e);
                    }
                });
            }
            else{
                statusLabel.setText("<html><p>No active recordings available to cancel</p></html>");
            }

        } else if (source == stopButton) { //Cancel all scheduled recordings for the day, will pick back up the next day
            System.out.println("Got Stop signal!");
            allJobKeys.forEach((jk) -> {
                if (jk != null) {
                    try {
                        sched.interrupt(jk);
                        unscheduleAllDailyJobs();
                        System.out.println("Back in stop button block");
                        record_lbl.setIcon(record_off);
                        ZonedDateTime tomorrowNext = tb.getNextDayFirstRecordTime(pstZone);
                        System.out.println("Got next record time: " + tomorrowNext);
                        statusLabel.setText("<html><p>Canceled all recordings for the day!<br/>Next scheduled time: " + tomorrowNext.format(tz_nice_format) + " </p></html>");
                    } catch (SchedulerException e) {
                        e.printStackTrace();
                        Sentry.captureException(e);
                    }
                }
                else{
                    statusLabel.setText("<html><p>No jobs scheduled for today.</p></html>");
                    try {
                        Thread.sleep(1 * 1000); //Give GUI a second to display button effect
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });

        }
    };//actionlistenr

    public void initGUI() throws Exception{
        /**
         * Base-level GUI frame holds contents in main panel
         */
        JFrame frame = new JFrame();
        frame.setPreferredSize(new Dimension(screen_w, screen_h));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); //entire app stops if GUI window is closed
        frame.setLocationByPlatform(true);
        frame.setVisible(true);

        /**
         * Set layout of main panel
         */
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panel.setLayout(new BorderLayout(50, 50));

        /**
         * Adjust size of "recording light" icon,
         * app initializes to light=off
         */
        ImageIcon small_on = new ImageIcon(ImageIO.read(Objects.requireNonNull(getClass().getResource("/record_on.png"))));
        ImageIcon small_off = new ImageIcon(ImageIO.read(Objects.requireNonNull(getClass().getResource("/record_off.png"))));
        record_on = resizeIcon(small_on);
        record_off = resizeIcon(small_off);
        record_lbl.setHorizontalAlignment(JLabel.CENTER);
        record_lbl.setVerticalAlignment(JLabel.CENTER);
        record_lbl.setIcon(record_off);

        /**
         * Set up buttons
         */
        Dimension buttonDims = new Dimension(screen_w/4, screen_h - (screen_h/8));
        stopButton.setPreferredSize(buttonDims);
        suspendButton.setPreferredSize(buttonDims);
        stopButton.setFont(new Font("Ariel", Font.BOLD, (int) (buttonDims.width/8.0)));
        suspendButton.setFont(new Font("Ariel", Font.BOLD, (int) (buttonDims.width/8.0)));
        stopButton.addActionListener(buttonActions);
        suspendButton.addActionListener(buttonActions);
        JPanel buttonPanelStop = new JPanel();
        buttonPanelStop.add(stopButton);
        stopButton.setEnabled(true);
        stopButton.setBackground(Color.decode("#bd241e"));
        stopButton.setFocusable(false);
        JPanel buttonPanelSuspend = new JPanel();
        buttonPanelSuspend.add(suspendButton);
        suspendButton.setEnabled(true);
        suspendButton.setBackground(Color.decode("#ebd334"));
        suspendButton.setFocusable(false);

        /**
         * Set up text area where recording status updates are shown
         */
        Dimension statusLabelDims = new Dimension(screen_w, screen_h/6);
        statusLabel.setPreferredSize(statusLabelDims);
        statusLabel.setFont(new Font("Ariel", Font.PLAIN, statusLabelDims.width/50));
        statusLabel.setText("<html><p>Recording system initializing...</p></html>");
        /**
         * Add components to panel
         */
        panel.add(statusLabel, BorderLayout.NORTH);
        panel.add(buttonPanelStop, BorderLayout.WEST);
        panel.add(buttonPanelSuspend, BorderLayout.EAST);
        panel.add(record_lbl, BorderLayout.CENTER);

        /**
         * Add panel to frame
         */
        frame.setContentPane(panel);
        frame.pack();
        System.out.println("GUI initialized!");
    }//initGUI

    /**
     * @param ic receives ImageIcon to format for GUI
     * @return resized ImageIcon, slightly larger than actual jpeg to fill GUI better
     */
    public ImageIcon resizeIcon(ImageIcon ic){
        Image imageOff = ic.getImage(); // transform it
        BufferedImage temp = (BufferedImage) imageOff;
        Image newOff = imageOff.getScaledInstance(temp.getWidth()+100, temp.getHeight()+100,  java.awt.Image.SCALE_SMOOTH); // scale it the smooth way
        return new ImageIcon(newOff);
    }

}//MainControlLoop class
