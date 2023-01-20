
/**
 * Created by strandquistg on 20 Jul, 2021
 * @note quick object class to allow
 * metadata to be written in json format
 */
public class RecordingObject {

    private String patientID;
    private String commitID;
    private String recordStartTime;
    private String recordEndTime;
    private String frame_rate;
    private String resolution;
    private String bitrate;
    private String recording_sesh_len;
    private String recording_segment_len;
    private String processID;
    private String threadName;
    private String threadID;

    public RecordingObject(){
    }
    public RecordingObject(String patID, String cID, String rST, String fps, String rez, String bit_rate, String sesh_len, String segment_len){
        this.patientID = patID;
        this.commitID = cID;
        this.recordStartTime = rST;
        this.frame_rate = fps;
        this.resolution = rez;
        this.bitrate = bit_rate;
        this.recording_sesh_len = sesh_len;
        this.recording_segment_len = segment_len;
    }

    /**
     Getters and Setters allow (de)serialization
     */
    public String getPatientID() {
        return patientID;
    }
    public void setPatientID(String patientID) {
        this.patientID = patientID;
    }

    public String getCommitID() {
        return commitID;
    }
    public void setCommitID(String commitID) {
        this.commitID = commitID;
    }

    public String getRecordStartTime() {
        return recordStartTime;
    }
    public void setRecordStartTime(String recordStartTime) {
        this.recordStartTime = recordStartTime;
    }

    public String getRecordEndTime() {
        return recordEndTime;
    }
    public void setRecordEndTime(String recordEndTime) {
        this.recordEndTime = recordEndTime;
    }

    public String getResolution() {
        return resolution;
    }
    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public String getBitrate() {
        return bitrate;
    }
    public void setBitrate(String bitrate) {
        this.bitrate = bitrate;
    }

    public String getRecording_sesh_len() {
        return recording_sesh_len;
    }
    public void setRecording_sesh_len(String recording_sesh_len) {
        this.recording_sesh_len = recording_sesh_len;
    }

    public String getRecording_segment_len() {
        return recording_segment_len;
    }
    public void setRecording_segment_len(String recording_segment_len) {
        this.recording_segment_len = recording_segment_len;
    }

    public String getFrame_rate() {
        return frame_rate;
    }
    public void setFrame_rate(String frame_rate) {
        this.frame_rate = frame_rate;
    }


    public String getProcessID() {
        return processID;
    }
    public void setProcessID(String processID) {
        this.processID = processID;
    }

    public String getThreadName() {
        return threadName;
    }
    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }

    public String getThreadID() {
        return threadID;
    }
    public void setThreadID(String threadID) {
        this.threadID = threadID;
    }
}
