import org.quartz.JobDetail;
import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;
import java.util.*;
import static org.quartz.JobBuilder.newJob;

/**
 * Created by strandquistg on 18 May, 2021
 */
public class JobDescriptionBuilder {


    /**
     * @param jName
     * @param jGroup
     * @return single JobDetail
     */
    public JobDetail buildSingleDetail(String jName, String jGroup, String qscale, String recording_len,
                                       String segment_len, String sp_host, String sp_sing, String fps, String rez, String commitID, String pID){
        JobDetail singleJob = newJob(RecordJob.class)
                .withIdentity(jName, jGroup)
                .storeDurably() //job persists even after triggers for the day are fired/removed
                .build();
        singleJob.getJobDataMap().put("qscale", qscale);
        singleJob.getJobDataMap().put("record_sesh_len", recording_len);
        singleJob.getJobDataMap().put("segment_len", segment_len);
        singleJob.getJobDataMap().put("save_path_host", sp_host);
        singleJob.getJobDataMap().put("save_path_singularity", sp_sing);
        singleJob.getJobDataMap().put("frame_rate", fps);
        singleJob.getJobDataMap().put("resolution", rez);
        singleJob.getJobDataMap().put("gitCommitID", commitID);
        singleJob.getJobDataMap().put("patientID", pID);
        return singleJob;
    }

    public Map<String, String> getCamAddresses(){
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("cameraLocations.yml");
        Yaml camConfig = new Yaml();
        return camConfig.load(inputStream);
    }

    public HashMap<String, Boolean> buildJobFlags(Map<String, String> configCams){
        HashMap<String, Boolean> allJobFlags = new HashMap<>();
        configCams.forEach((camName,camAddress) -> allJobFlags.put(camAddress, false));
        return allJobFlags;
    }


}//class JobDescriptionBuilder


