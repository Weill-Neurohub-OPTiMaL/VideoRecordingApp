import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by strandquistg on 21 May, 2021
 */
public class YamlWriter {

    /**
     * @throws IOException
     * @note writes yaml config for recording times
     */
    public void writeRecordTimes() throws IOException {
        PrintWriter writer = new PrintWriter(new File("./src/main/resources/recordTimes.yml"));
        LinkedHashMap<String, LinkedHashMap> sessions = new LinkedHashMap<>();
        sessions.put("session1", getSessionField(11, 0, 0, 0));
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(options);
        yaml.dump(sessions, writer);
    }

    /**
     * @param h sets hour a single recording will start
     * @param m sets minute a single recording will start
     * @param s sets second a single recording will start
     * @return nested LinkedHashMap of recording sessions with times
     * @note method writes agreed-upon recording sessions as a yaml config
     */
    public LinkedHashMap<String, Integer> getSessionField(Integer h, Integer m, Integer s, Integer ms){
        LinkedHashMap<String, Integer> session_field = new LinkedHashMap();
        session_field.put("hour", h);
        session_field.put("minute",m);
        session_field.put("second", s);
        session_field.put("millisecond", ms);
        return session_field;
    }

    /**
     * @throws IOException
     * @note writes yaml config of location of where
     * webcams are registered for a particular device
     * Running command "v4l2-ctl --list-devices" shows
     * location of any existing webcams; requires
     * v4l2 be installed
     */
    public void writeCameraLocation() throws IOException {
        Map<String, Object> dataMap = new LinkedHashMap();
        dataMap.put("camera0", "/dev/video0");
        dataMap.put("camera4", "/dev/video4");
        dataMap.put("camera8", "/dev/video8");
        DumperOptions options = new DumperOptions();
        options.setIndent(2);
        options.setPrettyFlow(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(options);
        PrintWriter writer = new PrintWriter(new File("./src/main/resources/cameraLocations.yml"));
        yaml.dump(dataMap, writer);
    }


    public static void main(String[] args) throws IOException {
        YamlWriter yaml_writer = new YamlWriter();
        yaml_writer.writeRecordTimes();
        yaml_writer.writeCameraLocation();
    }

}
